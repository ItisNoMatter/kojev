package io.github.itisnomatter.kojev

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** The client and `decide`, end to end through a mocked transport. */
class JevClientTest {
    private enum class Intent(
        override val description: String,
    ) : Criterion {
        REFUND("Refunds and cancellations"),
        OTHER("Anything else"),
    }

    private enum class Urgency(
        override val description: String,
    ) : Criterion {
        LATER("Not time-sensitive"),
        NOW("Needs immediate attention"),
    }

    @Serializable
    private data class Ticket(
        val subject: String,
        val body: String,
        val priorContacts: Int,
    )

    private class NotSerializable(
        val x: Int,
    )

    private val threeAnswers =
        """{"model":"jev-1.13.0","answers":{""" +
            """"intent":{"type":"choice","choice":"refund","probabilities":{"refund":0.9,"other":0.1},"confidence":0.9},""" +
            """"is_angry":{"type":"noul","noul":0.7},""" +
            """"urgency":{"type":"score","score":0.8,"confidence":0.6,"legend":{"0":"a","1":"b"},"probabilities":{"0":0.2,"1":0.8}}},""" +
            """"usage":{"input_tokens":120,"output_tokens":12}}"""

    private fun client(
        requests: MutableList<HttpRequestData>,
        status: HttpStatusCode = HttpStatusCode.OK,
        body: String = threeAnswers,
        configure: JevClientConfig.() -> Unit = {},
    ): JevClient {
        val engine =
            MockEngine { request ->
                requests += request
                respond(
                    content = body,
                    status = status,
                    headers =
                        headersOf(
                            HttpHeaders.ContentType to listOf("application/json"),
                            "x-typesafe-request-id" to listOf("req-abc"),
                        ),
                )
            }
        return JevClient(apiKey = "test-key", engine = engine, configure = configure)
    }

    private fun HttpRequestData.bodyText(): String = (body as OutgoingContent.ByteArrayContent).bytes().decodeToString()

    @Test
    fun `defaults match the official SDKs`() {
        val jev = client(mutableListOf())
        assertEquals("https://api.typesafe.ai", jev.baseUrl)
        assertEquals("jev-latest", jev.model)
        assertEquals(10.seconds, jev.timeout)
    }

    @Test
    fun `configuration is applied and a trailing slash on baseUrl is dropped`() {
        val jev =
            client(mutableListOf()) {
                baseUrl = "https://gateway.example.com/"
                model = "jev-1.13.0"
                timeout = 3.seconds
            }
        assertEquals("https://gateway.example.com", jev.baseUrl)
        assertEquals("jev-1.13.0", jev.model)
        assertEquals(3.seconds, jev.timeout)
    }

    @Test
    fun `rejects a blank api key and a non-positive timeout`() {
        assertFailsWith<IllegalArgumentException> { JevClient(apiKey = " ", engine = MockEngine { respond("") }) }
        assertFailsWith<IllegalArgumentException> { client(mutableListOf()) { timeout = 0.seconds } }
    }

    @Test
    fun `decide sends one request with the key and model and a string state and every question`() =
        runTest {
            val requests = mutableListOf<HttpRequestData>()
            val jev = client(requests)
            val intentQ = choice<Intent>("intent", "Which team?")
            val angryQ = noul("is_angry", "Angry?")
            val urgencyQ = score<Urgency>("urgency", "How urgent?")

            val result =
                jev.decide("Cancel my order and refund me right now.") {
                    ask(intentQ, angryQ)
                    ask(urgencyQ)
                }

            val request = requests.single()
            assertEquals("https://api.typesafe.ai/v1/systemone", request.url.toString())
            assertEquals("Bearer test-key", request.headers[HttpHeaders.Authorization])
            assertEquals(
                """{"state":"Cancel my order and refund me right now.","model":"jev-latest","questions":{""" +
                    """"intent":{"type":"choice","instructions":"Which team?",""" +
                    """"criteria":{"refund":"Refunds and cancellations","other":"Anything else"}},""" +
                    """"is_angry":{"type":"noul","instructions":"Angry?"},""" +
                    """"urgency":{"type":"score","instructions":"How urgent?","criteria":["Not time-sensitive","Needs immediate attention"]}}}""",
                request.bodyText(),
            )

            assertEquals(Intent.REFUND, result[intentQ].value)
            assertEquals(0.7, result[angryQ])
            assertEquals(Urgency.NOW, result[urgencyQ].mostLikely)
            assertEquals(0.8, result[urgencyQ].score)
            assertEquals("jev-1.13.0", result.model)
            assertEquals("req-abc", result.requestId)
            assertEquals(120, result.usage.inputTokens)
            assertEquals(12, result.usage.outputTokens)
        }

    @Test
    fun `decide sends a serializable state as a JSON object`() =
        runTest {
            val requests = mutableListOf<HttpRequestData>()
            val jev = client(requests) { model = "jev-1.13.0" }
            val angryQ = noul("is_angry", "Angry?")

            jev.decide(Ticket(subject = "Charged twice", body = "Please fix this ASAP.", priorContacts = 2)) { ask(angryQ) }

            assertEquals(
                """{"state":{"subject":"Charged twice","body":"Please fix this ASAP.","priorContacts":2},"model":"jev-1.13.0",""" +
                    """"questions":{"is_angry":{"type":"noul","instructions":"Angry?"}}}""",
                requests.single().bodyText(),
            )
        }

    @Test
    fun `decide sends a list state as a JSON array`() =
        runTest {
            val requests = mutableListOf<HttpRequestData>()
            val jev = client(requests)

            jev.decide(listOf("first message", "second message")) { ask(noul("q", "...")) }

            assertTrue(requests.single().bodyText().startsWith("""{"state":["first message","second message"],"""))
        }

    @Test
    fun `decide rejects a state that would not be a string or object or array`() =
        runTest {
            val requests = mutableListOf<HttpRequestData>()
            val jev = client(requests)
            assertFailsWith<IllegalArgumentException> { jev.decide(42) { ask(noul("q", "...")) } }
            assertFailsWith<IllegalArgumentException> { jev.decide(true) { ask(noul("q", "...")) } }
            assertEquals(0, requests.size)
        }

    @Test
    fun `decide rejects a state without a serializer`() =
        runTest {
            val jev = client(mutableListOf())
            assertFailsWith<SerializationException> { jev.decide(NotSerializable(1)) { ask(noul("q", "...")) } }
        }

    @Test
    fun `decide requires at least one question and distinct names`() =
        runTest {
            val requests = mutableListOf<HttpRequestData>()
            val jev = client(requests)
            assertFailsWith<IllegalArgumentException> { jev.decide("x") {} }
            assertFailsWith<IllegalArgumentException> {
                jev.decide("x") { ask(noul("dup", "a"), noul("dup", "b")) }
            }
            assertEquals(0, requests.size)
        }

    @Test
    fun `a non-2xx response is a JevApiException for its status`() =
        runTest {
            val jev = client(mutableListOf(), status = HttpStatusCode.Unauthorized, body = """{"message":"bad key"}""")
            val e = assertFailsWith<JevAuthenticationException> { jev.decide("x") { ask(noul("q", "...")) } }
            assertEquals(401, e.status)
            assertEquals("req-abc", e.requestId)
            assertEquals("401 bad key (request id: req-abc)", e.message)
        }

    @Test
    fun `retry settings are applied`() =
        runTest {
            val requests = mutableListOf<HttpRequestData>()
            val jev = client(requests, status = HttpStatusCode.ServiceUnavailable, body = "{}") { retry { maxRetries = 4 } }
            assertFailsWith<JevServerException> { jev.decide("x") { ask(noul("q", "...")) } }
            assertEquals(5, requests.size)
        }

    @Test
    fun `invalid retry settings are rejected when the client is built`() {
        assertFailsWith<IllegalArgumentException> { client(mutableListOf()) { retry { maxRetries = -1 } } }
        assertFailsWith<IllegalArgumentException> { client(mutableListOf()) { retry { jitter = 1.5 } } }
        assertFailsWith<IllegalArgumentException> { client(mutableListOf()) { retry { totalBudget = 0.seconds } } }
        assertFailsWith<IllegalArgumentException> { client(mutableListOf()) { retry { totalBudget = (-1).seconds } } }
        assertFailsWith<IllegalArgumentException> { client(mutableListOf()) { retry { totalBudget = 0.5.milliseconds } } }
        assertFailsWith<IllegalArgumentException> { client(mutableListOf()) { retry { initialBackoff = (-1).seconds } } }
    }

    @Test
    fun `timeout and budget must be at least a millisecond`() {
        assertFailsWith<IllegalArgumentException> { client(mutableListOf()) { timeout = 0.5.milliseconds } }
        client(mutableListOf()) { timeout = 1.milliseconds }
        client(mutableListOf()) { retry { totalBudget = 1.milliseconds } }
        client(mutableListOf()) { retry { totalBudget = null } }
    }

    @Test
    fun `close releases the http client`() =
        runTest {
            val jev = client(mutableListOf())
            jev.close()
            assertFailsWith<Exception> { jev.decide("x") { ask(noul("q", "...")) } }
        }
}
