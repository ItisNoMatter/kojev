package io.github.itisnomatter.kojev.wire

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * Sends one `POST /v1/systemone` request and parses its response body.
 *
 * This does not configure auth, retries, or error mapping - the caller's [httpClient] is
 * expected to already carry whatever headers it needs, and a non-2xx response surfaces as
 * whatever exception Ktor itself throws. Those concerns belong to the client layer built on
 * top of this function, not here.
 */
internal suspend fun callSystemOne(
    httpClient: HttpClient,
    baseUrl: String,
    request: SystemOneRequestDto,
): SystemOneResponseDto {
    val response =
        httpClient.post("$baseUrl/v1/systemone") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    return response.body()
}
