package io.github.itisnomatter.kojev.wire

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class SystemOneResponseDto(
    val model: String,
    val answers: Map<String, AnswerDto>,
    val usage: UsageDto,
)

@Serializable
internal data class UsageDto(
    @SerialName("input_tokens") val inputTokens: Int,
    @SerialName("output_tokens") val outputTokens: Int,
)
