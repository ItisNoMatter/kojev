package io.github.itisnomatter.kojev.wire

import kotlinx.serialization.json.Json

/**
 * The Json configuration this library requires. `ignoreUnknownKeys` protects against the API
 * adding new response fields in a way that would otherwise break deserialization here - per
 * `docs/api-notes.md`, only the fields recorded there are relied upon.
 *
 * Whatever `HttpClient` is passed to [callSystemOne] must install its ContentNegotiation
 * `json()` converter with this instance.
 */
internal val jevJson: Json = Json { ignoreUnknownKeys = true }
