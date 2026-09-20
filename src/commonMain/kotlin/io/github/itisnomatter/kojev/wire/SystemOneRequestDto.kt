package io.github.itisnomatter.kojev.wire

import kotlinx.serialization.Serializable

@Serializable
internal data class SystemOneRequestDto(
    val state: String,
    val model: String,
    val questions: Map<String, QuestionDto>,
)
