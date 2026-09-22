package io.github.itisnomatter.kojev

import kotlin.time.Duration

/**
 * The root of everything kojev throws. `catch (e: JevException)` catches every failure of a
 * decision: a non-2xx response ([JevApiException]), no response at all ([JevConnectionException],
 * [JevRequestTimeoutException]), or a response that doesn't match what was asked
 * ([JevResponseException]). Coroutine cancellation is never wrapped.
 */
sealed class JevException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * The API answered with a non-2xx status. One subclass per documented status, so that callers
 * can catch exactly what they can handle.
 *
 * @property status the HTTP status.
 * @property requestId the `x-typesafe-request-id` header, for support requests; absent if the
 *   response didn't carry one.
 * @property body the raw response body, for diagnostics. The API documents a body shape only for
 *   422 (see [JevRequestValidationException.errors]).
 * @property retryAfter the server-requested wait, when the response carried `retry-after-ms` or
 *   `Retry-After`. Present here even when the client did not wait for it - for example because
 *   waiting would have exceeded the retry budget - so the caller can decide to.
 */
sealed class JevApiException(
    val status: Int,
    val requestId: String?,
    val body: String?,
    val retryAfter: Duration?,
    message: String,
) : JevException(describe(status, requestId, message))

private fun describe(
    status: Int,
    requestId: String?,
    message: String,
): String = if (requestId == null) "$status $message" else "$status $message (request id: $requestId)"

/** 400. */
class JevBadRequestException internal constructor(
    requestId: String?,
    body: String?,
    retryAfter: Duration?,
    message: String,
) : JevApiException(400, requestId, body, retryAfter, message)

/** 401: the API key is missing or invalid. */
class JevAuthenticationException internal constructor(
    requestId: String?,
    body: String?,
    retryAfter: Duration?,
    message: String,
) : JevApiException(401, requestId, body, retryAfter, message)

/** 403. */
class JevPermissionDeniedException internal constructor(
    requestId: String?,
    body: String?,
    retryAfter: Duration?,
    message: String,
) : JevApiException(403, requestId, body, retryAfter, message)

/** 404. */
class JevNotFoundException internal constructor(
    requestId: String?,
    body: String?,
    retryAfter: Duration?,
    message: String,
) : JevApiException(404, requestId, body, retryAfter, message)

/**
 * 422: the request failed the API's validation - a missing field, a malformed question. This is
 * the one error whose body the API documents, so it is available structured as [errors].
 */
class JevRequestValidationException internal constructor(
    requestId: String?,
    body: String?,
    retryAfter: Duration?,
    message: String,
    val errors: List<ValidationError>,
) : JevApiException(422, requestId, body, retryAfter, message)

/** One entry of a 422 response's `detail`. */
class ValidationError internal constructor(
    /** Path to the invalid value, e.g. `["body", "questions", "urgency", "criteria"]`. */
    val location: List<String>,
    /** Human-readable explanation. */
    val message: String,
    /** Machine-readable code, e.g. `missing`. */
    val type: String,
) {
    override fun toString(): String = "${location.joinToString(".")}: $message ($type)"
}

/** 429: the rate limit was exceeded. [retryAfter] says how long the server asked to wait, if it did. */
class JevRateLimitException internal constructor(
    requestId: String?,
    body: String?,
    retryAfter: Duration?,
    message: String,
) : JevApiException(429, requestId, body, retryAfter, message)

/** 5xx, including 529 (TypeSafe is temporarily overloaded). */
class JevServerException internal constructor(
    status: Int,
    requestId: String?,
    body: String?,
    retryAfter: Duration?,
    message: String,
) : JevApiException(status, requestId, body, retryAfter, message)

/** Any other non-2xx status the API isn't documented to return. */
class JevUnexpectedStatusException internal constructor(
    status: Int,
    requestId: String?,
    body: String?,
    retryAfter: Duration?,
    message: String,
) : JevApiException(status, requestId, body, retryAfter, message)

/** The request never got a response: the host could not be reached, or the connection failed. */
class JevConnectionException internal constructor(
    cause: Throwable,
) : JevException("The request could not reach the API: ${cause.message ?: cause::class.simpleName}", cause)

/**
 * One attempt exceeded its [timeout]. Timeouts apply per attempt; retries get a fresh one. The
 * value is the client's timeout, or what remained of the retry budget if that was shorter.
 */
class JevRequestTimeoutException internal constructor(
    val timeout: Duration,
    cause: Throwable,
) : JevException("The request did not complete within $timeout.", cause)
