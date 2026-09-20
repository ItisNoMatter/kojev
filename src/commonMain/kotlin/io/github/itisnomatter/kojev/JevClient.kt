package io.github.itisnomatter.kojev

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

    /** How long one request may take. */
    var timeout: Duration = 10.seconds
}

/**
 * Creates a client. What is required is a parameter, what is optional is configured in the
 * lambda:
 *
 * ```
 * val jev = JevClient(apiKey = System.getenv("TYPESAFE_API_KEY"), engine = CIO.create()) {
 *     model = "jev-1.13.0"
 *     timeout = 5.seconds
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
    require(config.timeout.isPositive()) { "timeout must be positive, got ${config.timeout}." }
    val httpClient =
        HttpClient(engine) {
            expectSuccess = true
            install(ContentNegotiation) { json(jevJson) }
            install(HttpTimeout) { requestTimeoutMillis = config.timeout.inWholeMilliseconds }
            defaultRequest { header(HttpHeaders.Authorization, "Bearer $apiKey") }
        }
    return JevClient(httpClient, config.baseUrl.trimEnd('/'), config.model, config.timeout)
}

/**
 * A handle on the Jev API. Thread-safe: create one per application and share it. Ask it
 * questions with [decide].
 *
 * A non-2xx response currently surfaces as Ktor's own `ResponseException`; a response that
 * doesn't match what was asked surfaces as a [JevResponseException].
 */
class JevClient internal constructor(
    internal val httpClient: HttpClient,
    val baseUrl: String,
    val model: String,
    val timeout: Duration,
) : AutoCloseable {
    /** Releases the `HttpClient` this client created. The client cannot be used afterwards. */
    override fun close() = httpClient.close()
}
