package io.github.itisnomatter.kojev

import io.github.itisnomatter.kojev.wire.QuestionDto
import io.github.itisnomatter.kojev.wire.SystemOneRequestDto
import io.github.itisnomatter.kojev.wire.Transport
import kotlinx.serialization.json.JsonElement

/**
 * Assembles one request out of every question in [keys], sharing the same [state]. Every
 * question in a request is evaluated in parallel against that state - that's Jev's primary use.
 */
internal fun buildRequest(
    model: String,
    state: JsonElement,
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
    transport: Transport,
    model: String,
    state: JsonElement,
    keys: List<QuestionKey<*>>,
): Decision {
    val request = buildRequest(model, state, keys)
    val result = transport.systemOne(request)
    return Decision(result.response, result.requestId)
}
