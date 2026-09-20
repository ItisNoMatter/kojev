package io.github.itisnomatter.kojev.wire

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire shape of an answer, as documented in `docs/api-notes.md`. Note that [NoulAnswerDto] has
 * no confidence field — that's not an omission, the API genuinely doesn't return one for Noul.
 */
@Serializable
internal sealed interface AnswerDto

@Serializable
@SerialName("noul")
internal data class NoulAnswerDto(
    val noul: Double,
) : AnswerDto

@Serializable
@SerialName("choice")
internal data class ChoiceAnswerDto(
    val choice: String,
    val probabilities: Map<String, Double>,
    val confidence: Double,
) : AnswerDto

@Serializable
@SerialName("score")
internal data class ScoreAnswerDto(
    val score: Double,
    val confidence: Double,
    val legend: Map<String, String>,
    val probabilities: Map<String, Double>,
) : AnswerDto

internal val AnswerDto.wireType: String
    get() =
        when (this) {
            is NoulAnswerDto -> "noul"
            is ChoiceAnswerDto -> "choice"
            is ScoreAnswerDto -> "score"
        }
