package io.github.itisnomatter.kojev.wire

import io.github.itisnomatter.kojev.JevApiException
import io.github.itisnomatter.kojev.JevAuthenticationException
import io.github.itisnomatter.kojev.JevBadRequestException
import io.github.itisnomatter.kojev.JevConnectionException
import io.github.itisnomatter.kojev.JevException
import io.github.itisnomatter.kojev.JevNotFoundException
import io.github.itisnomatter.kojev.JevPermissionDeniedException
import io.github.itisnomatter.kojev.JevRateLimitException
import io.github.itisnomatter.kojev.JevRequestTimeoutException
import io.github.itisnomatter.kojev.JevRequestValidationException
import io.github.itisnomatter.kojev.JevServerException
import io.github.itisnomatter.kojev.JevUnexpectedStatusException
import io.github.itisnomatter.kojev.RetrySettings
import io.github.itisnomatter.kojev.UnreadableResponseException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.fromHttpToGmtDate
import io.ktor.http.isSuccess
import io.ktor.util.date.GMTDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

internal const val REQUEST_ID_HEADER = "x-typesafe-request-id"
internal const val RETRY_COUNT_HEADER = "X-TypeSafe-Retry-Count"
private const val RETRY_AFTER_MS_HEADER = "retry-after-ms"
private const val RETRY_AFTER_HEADER = "Retry-After"

/** A parsed 2xx response together with its request id. */
internal class SystemOneResult(
    val response: SystemOneResponseDto,
    val requestId: String?,
)

/**
 * Sends `POST /v1/systemone` with retries, and turns every failure into a [JevException].
 *
 * The clock, the random source, and the sleep are injectable so that the retry schedule can be
 * tested deterministically under `runTest`'s virtual time.
 */
internal class Transport(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val timeout: Duration,
    private val retry: RetrySettings,
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val random: () -> Double = { Random.nextDouble() },
    private val sleep: suspend (Duration) -> Unit = { delay(it) },
) {
    suspend fun systemOne(request: SystemOneRequestDto): SystemOneResult {
        val started = timeSource.markNow()
        var attempt = 0
        while (true) {
            val failure =
                try {
                    return attemptOnce(request, attempt)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: JevException) {
                    e
                }
            if (attempt >= retry.maxRetries || !isRetryable(failure)) throw failure
            val wait = waitBefore(retryNumber = attempt + 1, failure)
            val budget = retry.totalBudget
            if (budget != null && started.elapsedNow() + wait >= budget) throw failure
            sleep(wait)
            attempt++
        }
    }

    private suspend fun attemptOnce(
        request: SystemOneRequestDto,
        attempt: Int,
    ): SystemOneResult {
        val response =
            try {
                httpClient.post("$baseUrl/v1/systemone") {
                    contentType(ContentType.Application.Json)
                    if (attempt > 0) header(RETRY_COUNT_HEADER, attempt.toString())
                    setBody(request)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpRequestTimeoutException) {
                throw JevRequestTimeoutException(timeout, e)
            } catch (e: ConnectTimeoutException) {
                throw JevRequestTimeoutException(timeout, e)
            } catch (e: SocketTimeoutException) {
                throw JevRequestTimeoutException(timeout, e)
            } catch (e: Throwable) {
                throw JevConnectionException(e)
            }
        val requestId = response.headers[REQUEST_ID_HEADER]
        if (!response.status.isSuccess()) throw apiException(response, requestId)
        val body =
            try {
                response.body<SystemOneResponseDto>()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                throw UnreadableResponseException(requestId, e)
            }
        return SystemOneResult(body, requestId)
    }

    private fun isRetryable(failure: JevException): Boolean =
        when (failure) {
            is JevApiException -> failure.status in retry.retryOnStatuses
            is JevConnectionException -> retry.retryOnConnectionError
            is JevRequestTimeoutException -> retry.retryOnTimeout
            else -> false
        }

    /** The wait before retry number [retryNumber] (1 for the first retry). */
    private fun waitBefore(
        retryNumber: Int,
        failure: JevException,
    ): Duration {
        if (retry.respectRetryAfter && failure is JevApiException) {
            val requested = failure.retryAfter
            if (requested != null && requested <= retry.maxRetryAfter) return requested
        }
        if (retry.initialBackoff == Duration.ZERO || retry.maxBackoff == Duration.ZERO) return Duration.ZERO
        val exponential = minOf(retry.initialBackoff * (1 shl (retryNumber - 1).coerceAtMost(30)), retry.maxBackoff)
        return exponential * (1 - random() * retry.jitter)
    }

    private suspend fun apiException(
        response: HttpResponse,
        requestId: String?,
    ): JevApiException {
        val status = response.status.value
        val body = runCatching { response.bodyAsText() }.getOrNull()?.takeIf { it.isNotEmpty() }
        val retryAfter = parseRetryAfter(response.headers[RETRY_AFTER_MS_HEADER], response.headers[RETRY_AFTER_HEADER])
        val parsed = ErrorBody.parse(body)
        val message = parsed.message ?: response.status.description
        return when (status) {
            400 -> JevBadRequestException(requestId, body, retryAfter, message)
            401 -> JevAuthenticationException(requestId, body, retryAfter, message)
            403 -> JevPermissionDeniedException(requestId, body, retryAfter, message)
            404 -> JevNotFoundException(requestId, body, retryAfter, message)
            422 -> JevRequestValidationException(requestId, body, retryAfter, message, parsed.validationErrors)
            429 -> JevRateLimitException(requestId, body, retryAfter, message)
            in 500..599 -> JevServerException(status, requestId, body, retryAfter, message)
            else -> JevUnexpectedStatusException(status, requestId, body, retryAfter, message)
        }
    }
}

/**
 * `retry-after-ms` (milliseconds) wins over `Retry-After` (seconds, or an HTTP date), as in both
 * official SDKs. Unparseable or negative values are ignored.
 */
internal fun parseRetryAfter(
    retryAfterMs: String?,
    retryAfter: String?,
    now: () -> GMTDate = { GMTDate() },
): Duration? {
    retryAfterMs?.trim()?.toDoubleOrNull()?.let { ms ->
        if (ms.isFinite() && ms >= 0) return ms.milliseconds
    }
    val raw = retryAfter?.trim() ?: return null
    raw.toDoubleOrNull()?.let { s ->
        return if (s.isFinite() && s >= 0) s.seconds else null
    }
    val at = runCatching { raw.fromHttpToGmtDate() }.getOrNull() ?: return null
    return (at.timestamp - now().timestamp).coerceAtLeast(0).milliseconds
}
