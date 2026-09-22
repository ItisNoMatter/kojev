package io.github.itisnomatter.kojev.wire

import io.github.itisnomatter.kojev.JevApiException
import io.github.itisnomatter.kojev.JevAuthenticationException
import io.github.itisnomatter.kojev.JevBadRequestException
import io.github.itisnomatter.kojev.JevConnectionException
import io.github.itisnomatter.kojev.JevNotFoundException
import io.github.itisnomatter.kojev.JevPermissionDeniedException
import io.github.itisnomatter.kojev.JevRateLimitException
import io.github.itisnomatter.kojev.JevRequestTimeoutException
import io.github.itisnomatter.kojev.JevRequestValidationException
import io.github.itisnomatter.kojev.JevServerException
import io.github.itisnomatter.kojev.JevUnexpectedStatusException
import io.github.itisnomatter.kojev.RetryPolicy
import io.github.itisnomatter.kojev.RetrySettings
import io.github.itisnomatter.kojev.UnreadableResponseException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The retry loop and the error mapping, through a mocked transport under virtual time: every
 * wait below is asserted from `currentTime`, so the schedule is checked exactly and the tests
 * take no real time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TransportTest {
    private val okBody =
        """{"model":"jev-1.13.0","answers":{"q":{"type":"noul","noul":0.5}},""" +
            """"usage":{"input_tokens":1,"output_tokens":1}}"""
    private val request = SystemOneRequestDto(JsonPrimitive("hello"), "jev-latest", mapOf("q" to NoulQuestionDto("Hi?")))

    private fun MockRequestHandleScope.reply(
        status: HttpStatusCode,
        body: String = okBody,
        vararg headers: Pair<String, String>,
    ): HttpResponseData {
        val all = listOf(HttpHeaders.ContentType to "application/json") + headers
        return respond(body, status, headersOf(*all.map { it.first to listOf(it.second) }.toTypedArray()))
    }

    /** Replays [responses] in order (the last one repeats); an entry may throw to simulate a transport failure. */
    private fun TestScope.transport(
        requests: MutableList<HttpRequestData> = mutableListOf(),
        random: Double = 0.0,
        configure: RetryPolicy.() -> Unit = {},
        vararg responses: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): Transport {
        var index = 0
        val engine =
            MockEngine { request ->
                requests += request
                val handler = responses[minOf(index, responses.lastIndex)]
                index++
                handler(request)
            }
        val client = HttpClient(engine) { install(ContentNegotiation) { json(jevJson) } }
        return Transport(
            httpClient = client,
            baseUrl = "https://api.typesafe.ai",
            timeout = 10.seconds,
            retry = settings(configure),
            timeSource = testScheduler.timeSource,
            random = { random },
            sleep = { delay(it) },
        )
    }

    private fun settings(configure: RetryPolicy.() -> Unit): RetrySettings = RetryPolicy().apply(configure).snapshot()

    // ---- success ----

    @Test
    fun `a 2xx response is parsed and carries the request id`() =
        runTest {
            val requests = mutableListOf<HttpRequestData>()
            val transport = transport(requests, responses = arrayOf({ reply(HttpStatusCode.OK, okBody, REQUEST_ID_HEADER to "req-1") }))
            val result = transport.systemOne(request)
            assertEquals("req-1", result.requestId)
            assertEquals("jev-1.13.0", result.response.model)
            assertEquals(1, requests.size)
            assertNull(requests.single().headers[RETRY_COUNT_HEADER])
            assertEquals("https://api.typesafe.ai/v1/systemone", requests.single().url.toString())
        }

    @Test
    fun `a 2xx body that is not a System One response is an UnreadableResponseException`() =
        runTest {
            val transport =
                transport(responses = arrayOf({ reply(HttpStatusCode.OK, """{"unexpected":true}""", REQUEST_ID_HEADER to "req-9") }))
            val e = assertFailsWith<UnreadableResponseException> { transport.systemOne(request) }
            assertEquals("req-9", e.requestId)
        }

    // ---- status mapping ----

    private suspend fun TestScope.failWith(
        status: HttpStatusCode,
        body: String = """{"message":"nope"}""",
    ): JevApiException {
        val transport =
            transport(configure = { maxRetries = 0 }, responses = arrayOf({ reply(status, body, REQUEST_ID_HEADER to "req-x") }))
        return assertFailsWith<JevApiException> { transport.systemOne(request) }
    }

    @Test
    fun `each documented status maps to its own exception with status request id and body`() =
        runTest {
            assertIs<JevBadRequestException>(failWith(HttpStatusCode.BadRequest))
            assertIs<JevAuthenticationException>(failWith(HttpStatusCode.Unauthorized))
            assertIs<JevPermissionDeniedException>(failWith(HttpStatusCode.Forbidden))
            assertIs<JevNotFoundException>(failWith(HttpStatusCode.NotFound))
            assertIs<JevRateLimitException>(failWith(HttpStatusCode.TooManyRequests))
            assertIs<JevServerException>(failWith(HttpStatusCode.InternalServerError))
            assertIs<JevServerException>(failWith(HttpStatusCode(529, "Overloaded")))
            assertIs<JevUnexpectedStatusException>(failWith(HttpStatusCode.Conflict))

            val e = failWith(HttpStatusCode.Unauthorized, """{"message":"bad key"}""")
            assertEquals(401, e.status)
            assertEquals("req-x", e.requestId)
            assertEquals("""{"message":"bad key"}""", e.body)
            assertEquals("401 bad key (request id: req-x)", e.message)
        }

    @Test
    fun `a 422 exposes the documented validation errors`() =
        runTest {
            val body =
                """{"detail":[{"loc":["body","questions","urgency","criteria"],"msg":"Field required","type":"missing",""" +
                    """"input":{"type":"score"},"ctx":{"min_length":1}}]}"""
            val e = assertIs<JevRequestValidationException>(failWith(HttpStatusCode.UnprocessableEntity, body))
            assertEquals(1, e.errors.size)
            assertEquals(listOf("body", "questions", "urgency", "criteria"), e.errors[0].location)
            assertEquals("Field required", e.errors[0].message)
            assertEquals("missing", e.errors[0].type)
            assertEquals("422 body.questions.urgency.criteria: Field required (missing) (request id: req-x)", e.message)
        }

    // ---- retry schedule ----

    @Test
    fun `a retryable status is retried with exponential backoff and the retry-count header`() =
        runTest {
            val requests = mutableListOf<HttpRequestData>()
            val transport =
                transport(
                    requests,
                    responses =
                        arrayOf(
                            { reply(HttpStatusCode.TooManyRequests, "{}") },
                            { reply(HttpStatusCode.ServiceUnavailable, "{}") },
                            { reply(HttpStatusCode.OK) },
                        ),
                )
            transport.systemOne(request)
            assertEquals(3, requests.size)
            assertEquals(listOf(null, "1", "2"), requests.map { it.headers[RETRY_COUNT_HEADER] })
            assertEquals(500 + 1000, currentTime)
        }

    @Test
    fun `retries stop after maxRetries and the last failure is thrown`() =
        runTest {
            val requests = mutableListOf<HttpRequestData>()
            val transport = transport(requests, responses = arrayOf({ reply(HttpStatusCode.InternalServerError, "{}") }))
            assertFailsWith<JevServerException> { transport.systemOne(request) }
            assertEquals(3, requests.size)
            assertEquals(500 + 1000, currentTime)
        }

    @Test
    fun `backoff is capped at maxBackoff`() =
        runTest {
            val transport =
                transport(configure = { maxRetries = 5 }, responses = arrayOf({ reply(HttpStatusCode.InternalServerError, "{}") }))
            assertFailsWith<JevServerException> { transport.systemOne(request) }
            assertEquals(500 + 1000 + 2000 + 4000 + 5000, currentTime)
        }

    @Test
    fun `jitter subtracts up to the configured fraction`() =
        runTest {
            val transport =
                transport(
                    random = 1.0,
                    configure = { maxRetries = 1 },
                    responses = arrayOf({ reply(HttpStatusCode.InternalServerError, "{}") }),
                )
            assertFailsWith<JevServerException> { transport.systemOne(request) }
            assertEquals(375, currentTime)
        }

    @Test
    fun `a non-retryable status is not retried`() =
        runTest {
            val requests = mutableListOf<HttpRequestData>()
            val transport = transport(requests, responses = arrayOf({ reply(HttpStatusCode.BadRequest, "{}") }))
            assertFailsWith<JevBadRequestException> { transport.systemOne(request) }
            assertEquals(1, requests.size)
            assertEquals(0, currentTime)
        }

    @Test
    fun `maxRetries zero disables retrying`() =
        runTest {
            val requests = mutableListOf<HttpRequestData>()
            val transport =
                transport(requests, configure = { maxRetries = 0 }, responses = arrayOf({ reply(HttpStatusCode.TooManyRequests, "{}") }))
            assertFailsWith<JevRateLimitException> { transport.systemOne(request) }
            assertEquals(1, requests.size)
        }

    // ---- Retry-After ----

    @Test
    fun `Retry-After in seconds replaces the backoff`() =
        runTest {
            val transport =
                transport(
                    responses =
                        arrayOf(
                            { reply(HttpStatusCode.TooManyRequests, "{}", "Retry-After" to "2") },
                            { reply(HttpStatusCode.OK) },
                        ),
                )
            transport.systemOne(request)
            assertEquals(2000, currentTime)
        }

    @Test
    fun `retry-after-ms wins over Retry-After`() =
        runTest {
            val transport =
                transport(
                    responses =
                        arrayOf(
                            { reply(HttpStatusCode.TooManyRequests, "{}", "Retry-After" to "2", "retry-after-ms" to "250") },
                            { reply(HttpStatusCode.OK) },
                        ),
                )
            transport.systemOne(request)
            assertEquals(250, currentTime)
        }

    @Test
    fun `a Retry-After above maxRetryAfter is ignored in favour of the backoff`() =
        runTest {
            val transport =
                transport(
                    responses =
                        arrayOf(
                            { reply(HttpStatusCode.ServiceUnavailable, "{}", "Retry-After" to "120") },
                            { reply(HttpStatusCode.OK) },
                        ),
                )
            transport.systemOne(request)
            assertEquals(500, currentTime)
        }

    @Test
    fun `respectRetryAfter false uses the backoff`() =
        runTest {
            val transport =
                transport(
                    configure = { respectRetryAfter = false },
                    responses =
                        arrayOf(
                            { reply(HttpStatusCode.TooManyRequests, "{}", "Retry-After" to "2") },
                            { reply(HttpStatusCode.OK) },
                        ),
                )
            transport.systemOne(request)
            assertEquals(500, currentTime)
        }

    // ---- total budget ----

    @Test
    fun `a Retry-After the budget cannot afford is thrown at once with retryAfter set`() =
        runTest {
            val requests = mutableListOf<HttpRequestData>()
            val transport =
                transport(requests, responses = arrayOf({ reply(HttpStatusCode.TooManyRequests, "{}", "Retry-After" to "45") }))
            val e = assertFailsWith<JevRateLimitException> { transport.systemOne(request) }
            assertEquals(45.seconds, e.retryAfter)
            assertEquals(1, requests.size)
            assertEquals(0, currentTime)
        }

    @Test
    fun `a retry whose wait would reach the budget is not attempted`() =
        runTest {
            val requests = mutableListOf<HttpRequestData>()
            val transport =
                transport(
                    requests,
                    configure = { totalBudget = 1200.milliseconds },
                    responses = arrayOf({ reply(HttpStatusCode.InternalServerError, "{}") }),
                )
            assertFailsWith<JevServerException> { transport.systemOne(request) }
            // 500 ms wait fits; the next 1000 ms wait would reach 1500 ms > 1200 ms, so it is skipped.
            assertEquals(2, requests.size)
            assertEquals(500, currentTime)
        }

    @Test
    fun `a null budget never stops a retry`() =
        runTest {
            val transport =
                transport(
                    configure = { totalBudget = null },
                    responses =
                        arrayOf(
                            { reply(HttpStatusCode.TooManyRequests, "{}", "Retry-After" to "45") },
                            { reply(HttpStatusCode.OK) },
                        ),
                )
            transport.systemOne(request)
            assertEquals(45_000, currentTime)
        }

    // ---- transport failures ----

    @Test
    fun `a connection failure is retried and finally wrapped`() =
        runTest {
            val requests = mutableListOf<HttpRequestData>()
            val cause = RuntimeException("connection reset")
            val transport = transport(requests, responses = arrayOf({ throw cause }))
            val e = assertFailsWith<JevConnectionException> { transport.systemOne(request) }
            // Compared by message: coroutine stack-trace recovery may hand back a copy of the cause.
            assertEquals(cause.message, assertIs<RuntimeException>(e.cause).message)
            assertEquals(3, requests.size)
        }

    @Test
    fun `a connection failure followed by success succeeds`() =
        runTest {
            val transport = transport(responses = arrayOf({ throw RuntimeException("reset") }, { reply(HttpStatusCode.OK) }))
            assertEquals("jev-1.13.0", transport.systemOne(request).response.model)
            assertEquals(500, currentTime)
        }

    @Test
    fun `a timeout is a JevRequestTimeoutException carrying the configured timeout`() =
        runTest {
            val requests = mutableListOf<HttpRequestData>()
            val transport =
                transport(
                    requests,
                    configure = { retryOnTimeout = false },
                    responses = arrayOf({ throw HttpRequestTimeoutException("https://api.typesafe.ai/v1/systemone", 10_000) }),
                )
            val e = assertFailsWith<JevRequestTimeoutException> { transport.systemOne(request) }
            assertEquals(10.seconds, e.timeout)
            assertEquals(1, requests.size)
        }

    @Test
    fun `retryOnConnectionError false does not retry connection failures`() =
        runTest {
            val requests = mutableListOf<HttpRequestData>()
            val transport =
                transport(requests, configure = { retryOnConnectionError = false }, responses = arrayOf({ throw RuntimeException("x") }))
            assertFailsWith<JevConnectionException> { transport.systemOne(request) }
            assertEquals(1, requests.size)
        }

    @Test
    fun `cancellation passes through unwrapped and is not retried`() =
        runTest {
            val requests = mutableListOf<HttpRequestData>()
            val transport = transport(requests, responses = arrayOf({ throw CancellationException("stop") }))
            assertFailsWith<CancellationException> { transport.systemOne(request) }
            assertEquals(1, requests.size)
        }
}
