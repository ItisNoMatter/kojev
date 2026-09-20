package io.github.itisnomatter.kojev.wire

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire shape of a question, as documented in `docs/api-notes.md`. The `type` field is the
 * kotlinx.serialization class discriminator; each subtype's [SerialName] is the exact string
 * the API expects.
 */
@Serializable
internal sealed interface QuestionDto

@Serializable
@SerialName("noul")
internal data class NoulQuestionDto(
    val instructions: String,
    val criteria: NoulCriteriaDto? = null,
) : QuestionDto

@Serializable
internal data class NoulCriteriaDto(
    @SerialName("true") val whenTrue: String? = null,
    @SerialName("false") val whenFalse: String? = null,
)

@Serializable
@SerialName("choice")
internal data class ChoiceQuestionDto(
    val instructions: String,
    val criteria: Map<String, String?>,
) : QuestionDto

@Serializable
@SerialName("score")
internal data class ScoreQuestionDto(
    val instructions: String,
    val criteria: List<String>,
) : QuestionDto
