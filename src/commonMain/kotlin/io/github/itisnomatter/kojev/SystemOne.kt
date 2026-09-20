package io.github.itisnomatter.kojev

import io.github.itisnomatter.kojev.wire.QuestionDto
import io.github.itisnomatter.kojev.wire.SystemOneRequestDto
import io.github.itisnomatter.kojev.wire.callSystemOne
import io.ktor.client.HttpClient

/**
 * Assembles one request out of every question in [keys], sharing the same [state]. Every
 * question in a request is evaluated in parallel against that state - that's Jev's primary use.
 */
internal fun buildRequest(
    model: String,
    state: String,
    keys: List<QuestionKey<*>>,
): SystemOneRequestDto {
    require(keys.isNotEmpty()) { "At least one question is required." }
    val questions = LinkedHashMap<String, QuestionDto>(keys.size)
    for (key in keys) {
        val previous = questions.put(key.name, key.toDto())
        require(previous == null) { "Duplicate question name '${key.name}'." }
    }
    return SystemOneRequestDto(state = state, model = model, questions = questions)
}

/**
 * Sends [keys] as one request against [state] and returns their typed answers.
 */
internal suspend fun requestDecision(
    httpClient: HttpClient,
    baseUrl: String,
    model: String,
    state: String,
    keys: List<QuestionKey<*>>,
): Decision {
    val request = buildRequest(model, state, keys)
    val response = callSystemOne(httpClient, baseUrl, request)
    return Decision(response)
}
