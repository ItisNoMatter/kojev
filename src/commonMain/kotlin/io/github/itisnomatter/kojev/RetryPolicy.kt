package io.github.itisnomatter.kojev

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * How a decision is retried. Configured inside [JevClient]'s lambda; every setting is optional:
 *
 * ```
 * val jev = JevClient(apiKey, engine) {
 *     retry {
 *         maxRetries = 3
 *         totalBudget = null
 *     }
 * }
 * ```
 *
 * Defaults are the official SDKs' (`docs/api-notes.md`): retry 408, 429, and 5xx, connection
 * errors, and timeouts; up to 2 retries; exponential backoff from 0.5 s doubling to 5 s with up
 * to 25 % subtracted at random; a server-supplied `Retry-After` wins when it is at most 60 s;
 * and a 30 s budget for the whole decision.
 *
 * **The budget bounds the whole decision, waits and attempts alike.** Before each retry the
 * client computes the wait; if the time already spent plus that wait would reach [totalBudget],
 * it does not wait - it throws the last failure immediately. That failure carries the server's
 * requested delay in [JevApiException.retryAfter], so the caller can decide whether to wait that
 * long. With a 30 s budget and a `Retry-After: 45`, for example, the [JevRateLimitException] is
 * thrown at once with `retryAfter = 45.seconds`. And an attempt never runs past the budget
 * either: its timeout is the client's timeout or what remains of the budget, whichever is
 * shorter, so with the defaults two 10 s timeouts leave the third attempt 8.5 s, not 10.
 * (The official Python SDK's budget bounds only the waits; see `docs/api-notes.md`.)
 */
class RetryPolicy internal constructor() {
    /** Retries after the first attempt. `0` disables retrying. */
    var maxRetries: Int = 2

    /** The wait before the first retry; doubles on each further retry. `Duration.ZERO` disables backoff. */
    var initialBackoff: Duration = 500.milliseconds

    /** The longest computed backoff. */
    var maxBackoff: Duration = 5.seconds

    /** The fraction of each backoff randomly subtracted from it, between 0 and 1. */
    var jitter: Double = 0.25

    /** HTTP statuses that are retried. */
    var retryOnStatuses: Set<Int> = setOf(408, 429) + (500..599)

    /** Whether a `retry-after-ms` or `Retry-After` response header replaces the computed backoff. */
    var respectRetryAfter: Boolean = true

    /** A server-requested wait longer than this is ignored in favour of the computed backoff. */
    var maxRetryAfter: Duration = 60.seconds

    /** Whether a request that never got a response is retried. */
    var retryOnConnectionError: Boolean = true

    /** Whether an attempt that exceeded the client's timeout is retried. */
    var retryOnTimeout: Boolean = true

    /**
     * The most time one decision may take across all attempts and waits, measured from the start
     * of the first attempt. `null` removes the limit. A wait that would reach it is not taken,
     * and an attempt's timeout is shortened to what remains of it; see the class documentation.
     */
    var totalBudget: Duration? = 30.seconds

    internal fun snapshot(): RetrySettings {
        require(maxRetries >= 0) { "retry.maxRetries must not be negative, got $maxRetries." }
        require(!initialBackoff.isNegative()) { "retry.initialBackoff must not be negative, got $initialBackoff." }
        require(!maxBackoff.isNegative()) { "retry.maxBackoff must not be negative, got $maxBackoff." }
        require(jitter in 0.0..1.0) { "retry.jitter must be between 0 and 1, got $jitter." }
        require(!maxRetryAfter.isNegative()) { "retry.maxRetryAfter must not be negative, got $maxRetryAfter." }
        totalBudget?.let { require(it.isPositive()) { "retry.totalBudget must be positive or null, got $it." } }
        return RetrySettings(
            maxRetries = maxRetries,
            initialBackoff = initialBackoff,
            maxBackoff = maxBackoff,
            jitter = jitter,
            retryOnStatuses = retryOnStatuses.toSet(),
            respectRetryAfter = respectRetryAfter,
            maxRetryAfter = maxRetryAfter,
            retryOnConnectionError = retryOnConnectionError,
            retryOnTimeout = retryOnTimeout,
            totalBudget = totalBudget,
        )
    }
}

/** An immutable copy of a [RetryPolicy], taken when the client is built. */
internal class RetrySettings(
    val maxRetries: Int,
    val initialBackoff: Duration,
    val maxBackoff: Duration,
    val jitter: Double,
    val retryOnStatuses: Set<Int>,
    val respectRetryAfter: Boolean,
    val maxRetryAfter: Duration,
    val retryOnConnectionError: Boolean,
    val retryOnTimeout: Boolean,
    val totalBudget: Duration?,
)
