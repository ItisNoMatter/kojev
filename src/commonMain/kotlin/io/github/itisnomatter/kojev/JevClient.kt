package io.github.itisnomatter.kojev

import io.github.itisnomatter.kojev.wire.MIN_TIMEOUT
import io.github.itisnomatter.kojev.wire.Transport
import io.github.itisnomatter.kojev.wire.jevJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** The optional settings of a [JevClient]. Defaults match the official SDKs (`docs/api-notes.md`). */
class JevClientConfig internal constructor() {
    /** Where requests go. Override for a proxy or gateway. A trailing `/` is ignored. */
    var baseUrl: String = "https://api.typesafe.ai"

    /**
     * The model every decision is made with. `jev-latest` is an alias that moves when a new
     * release ships; pin a versioned id such as `jev-1.13.0` once you have tuned thresholds
     * against one.
     */
    var model: String = "jev-latest"

    /** How long one attempt may take. Retries each get a fresh timeout; [RetryPolicy.totalBudget] bounds the whole decision. */
    var timeout: Duration = 10.seconds

    internal val retry = RetryPolicy()

    /** Adjusts how a decision is retried. See [RetryPolicy] for the settings and their defaults. */
    fun retry(configure: RetryPolicy.() -> Unit) {
        retry.configure()
    }
}

/**
 * Creates a client. What is required is a parameter, what is optional is configured in the
 * lambda:
 *
 * ```
 * val jev = JevClient(apiKey = System.getenv("TYPESAFE_API_KEY"), engine = CIO.create()) {
 *     model = "jev-1.13.0"
 *     timeout = 5.seconds
 *     retry { maxRetries = 3 }
 * }
 * ```
 *
 * kojev pins no Ktor engine; pass the one your platform uses. The client owns the `HttpClient` it
 * builds on that engine and releases it in [JevClient.close].
 */
fun JevClient(
    apiKey: String,
    engine: HttpClientEngine,
    configure: JevClientConfig.() -> Unit = {},
): JevClient {
    require(apiKey.isNotBlank()) { "apiKey must not be blank." }
    val config = JevClientConfig().apply(configure)
    require(config.baseUrl.isNotBlank()) { "baseUrl must not be blank." }
    require(config.model.isNotBlank()) { "model must not be blank." }
    require(config.timeout >= MIN_TIMEOUT) { "timeout must be at least $MIN_TIMEOUT, got ${config.timeout}." }
    val retry = config.retry.snapshot()
    val httpClient =
        HttpClient(engine) {
            install(ContentNegotiation) { json(jevJson) }
            install(HttpTimeout) { requestTimeoutMillis = config.timeout.inWholeMilliseconds }
            defaultRequest { header(HttpHeaders.Authorization, "Bearer $apiKey") }
        }
    val baseUrl = config.baseUrl.trimEnd('/')
    return JevClient(
        httpClient = httpClient,
        transport = Transport(httpClient, baseUrl, config.timeout, retry),
        baseUrl = baseUrl,
        model = config.model,
        timeout = config.timeout,
    )
}

/**
 * A handle on the Jev API. Thread-safe: create one per application and share it. Ask it
 * questions with [decide].
 *
 * Everything a decision can fail with is a [JevException]: a non-2xx status is a
 * [JevApiException] subclass for that status, no response is a [JevConnectionException] or
 * [JevRequestTimeoutException], and a response that doesn't match the questions is a
 * [JevResponseException]. Retries happen before any of these reaches the caller, per the
 * client's [RetryPolicy].
 */
class JevClient internal constructor(
    private val httpClient: HttpClient,
    internal val transport: Transport,
    val baseUrl: String,
    val model: String,
    val timeout: Duration,
) : AutoCloseable {
    /** Releases the `HttpClient` this client created. The client cannot be used afterwards. */
    override fun close() = httpClient.close()
}
