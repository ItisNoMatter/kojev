package io.github.itisnomatter.kojev

import io.github.itisnomatter.kojev.wire.AnswerDto
import io.github.itisnomatter.kojev.wire.ChoiceAnswerDto
import io.github.itisnomatter.kojev.wire.ChoiceQuestionDto
import io.github.itisnomatter.kojev.wire.NoulAnswerDto
import io.github.itisnomatter.kojev.wire.NoulCriteriaDto
import io.github.itisnomatter.kojev.wire.NoulQuestionDto
import io.github.itisnomatter.kojev.wire.QuestionDto
import io.github.itisnomatter.kojev.wire.ScoreAnswerDto
import io.github.itisnomatter.kojev.wire.ScoreQuestionDto
import io.github.itisnomatter.kojev.wire.wireType

/**
 * A named question, tied to the type that looking it up in a [Decision] returns. Only [noul],
 * [choice], and [score] create one - there is no way to end up with a [QuestionKey] whose type
 * doesn't match the question it names.
 */
sealed class QuestionKey<out T> {
    abstract val name: String

    internal abstract fun toDto(): QuestionDto

    internal abstract fun parse(answer: AnswerDto): T
}

private class NoulKey(
    override val name: String,
    private val question: NoulQuestion,
) : QuestionKey<Double>() {
    override fun toDto(): QuestionDto =
        NoulQuestionDto(
            instructions = question.instructions,
            criteria =
                if (question.whenTrue == null && question.whenFalse == null) {
                    null
                } else {
                    NoulCriteriaDto(question.whenTrue, question.whenFalse)
                },
        )

    override fun parse(answer: AnswerDto): Double {
        val noulAnswer = answer as? NoulAnswerDto ?: throw UnexpectedAnswerTypeException(this, "noul", answer.wireType)
        return noulAnswer.noul
    }
}

private class ChoiceKey<T : Any>(
    override val name: String,
    private val question: ChoiceQuestion<T>,
) : QuestionKey<ChoiceAnswer<T>>() {
    private val byLabel: Map<String, T> = question.criteria.keys.associateBy(question.label)

    init {
        require(byLabel.size == question.criteria.size) {
            "ChoiceQuestion.label must be injective: two options for question '$name' produced the same wire label."
        }
    }

    override fun toDto(): QuestionDto =
        ChoiceQuestionDto(
            instructions = question.instructions,
            criteria = question.criteria.entries.associate { (value, description) -> question.label(value) to description },
        )

    override fun parse(answer: AnswerDto): ChoiceAnswer<T> {
        val choiceAnswer = answer as? ChoiceAnswerDto ?: throw UnexpectedAnswerTypeException(this, "choice", answer.wireType)
        val value = byLabel[choiceAnswer.choice] ?: throw UnknownChoiceLabelException(this, choiceAnswer.choice)
        val probabilities =
            choiceAnswer.probabilities.entries.associate { (label, probability) ->
                (byLabel[label] ?: throw UnknownChoiceLabelException(this, label)) to probability
            }
        return ChoiceAnswer(value, probabilities, choiceAnswer.confidence)
    }
}

private class ScoreKey(
    override val name: String,
    private val question: ScoreQuestion,
) : QuestionKey<ScoreAnswer>() {
    override fun toDto(): QuestionDto = ScoreQuestionDto(instructions = question.instructions, criteria = question.levels)

    override fun parse(answer: AnswerDto): ScoreAnswer {
        val scoreAnswer = answer as? ScoreAnswerDto ?: throw UnexpectedAnswerTypeException(this, "score", answer.wireType)
        val probabilities =
            List(question.levels.size) { level ->
                scoreAnswer.probabilities[level.toString()] ?: throw MalformedScoreAnswerException(this, level)
            }
        return ScoreAnswer(
            value = scoreAnswer.score,
            confidence = scoreAnswer.confidence,
            levels = question.levels,
            probabilities = probabilities,
        )
    }
}

fun noul(
    name: String,
    question: NoulQuestion,
): QuestionKey<Double> = NoulKey(name, question)

fun <T : Any> choice(
    name: String,
    question: ChoiceQuestion<T>,
): QuestionKey<ChoiceAnswer<T>> = ChoiceKey(name, question)

fun score(
    name: String,
    question: ScoreQuestion,
): QuestionKey<ScoreAnswer> = ScoreKey(name, question)
