package io.github.itisnomatter.kojev.wire

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
internal data class SystemOneRequestDto(
    /** A JSON string, object, or array - the three shapes the API accepts. */
    val state: JsonElement,
    val model: String,
    val questions: Map<String, QuestionDto>,
)
