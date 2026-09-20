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
import kotlin.enums.enumEntries

/**
 * A named question, tied to the type that looking it up in a [Decision] returns. There is no
 * way to end up with a [QuestionKey] whose type doesn't match the question it names.
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
        val dto = answer.expect<NoulAnswerDto>(this, "noul")
        return dto.noul.checkedUnitInterval(this, "noul")
    }
}

private class ChoiceKey<T : Any>(
    override val name: String,
    private val question: ChoiceQuestion<T>,
) : QuestionKey<ChoiceAnswer<T>>() {
    private val byLabel: Map<String, T> = question.options.associateBy(question.label)

    override fun toDto(): QuestionDto =
        ChoiceQuestionDto(
            instructions = question.instructions,
            criteria = question.options.associate { question.label(it) to question.description(it) },
        )

    override fun parse(answer: AnswerDto): ChoiceAnswer<T> {
        val dto = answer.expect<ChoiceAnswerDto>(this, "choice")
        val value = byLabel[dto.choice] ?: throw UnknownChoiceLabelException(this, dto.choice)
        for (label in dto.probabilities.keys) {
            if (label !in byLabel) throw UnknownChoiceLabelException(this, label)
        }
        val probabilities = LinkedHashMap<T, Double>(question.options.size)
        for (option in question.options) {
            val label = question.label(option)
            val probability =
                dto.probabilities[label] ?: throw MalformedAnswerException(this, "no probability for option '$label'")
            probabilities[option] = probability.checkedUnitInterval(this, "probabilities['$label']")
        }
        return ChoiceAnswer(value, probabilities, dto.confidence.checkedUnitInterval(this, "confidence"))
    }
}

private class ScoreKey<T : Any>(
    override val name: String,
    private val question: ScoreQuestion<T>,
) : QuestionKey<ScoreAnswer<T>>() {
    override fun toDto(): QuestionDto =
        ScoreQuestionDto(
            instructions = question.instructions,
            criteria = question.levels.map(question.description),
        )

    override fun parse(answer: AnswerDto): ScoreAnswer<T> {
        val dto = answer.expect<ScoreAnswerDto>(this, "score")
        val levels = question.levels
        if (dto.probabilities.size != levels.size) {
            throw MalformedAnswerException(
                this,
                "expected probabilities for ${levels.size} levels, got ${dto.probabilities.size}",
            )
        }
        val probabilities = LinkedHashMap<T, Double>(levels.size)
        var mostLikely = levels[0]
        var highest = -1.0
        levels.forEachIndexed { number, level ->
            val probability =
                dto.probabilities[number.toString()] ?: throw MalformedAnswerException(this, "no probability for level $number")
            probabilities[level] = probability.checkedUnitInterval(this, "probabilities['$number']")
            if (probability > highest) {
                highest = probability
                mostLikely = level
            }
        }
        val topLevel = (levels.size - 1).toDouble()
        if (dto.score.isNaN() || dto.score < 0.0 || dto.score > topLevel) {
            throw MalformedAnswerException(this, "score ${dto.score} is outside 0..$topLevel")
        }
        return ScoreAnswer(dto.score, mostLikely, probabilities, dto.confidence.checkedUnitInterval(this, "confidence"))
    }
}

private inline fun <reified D : AnswerDto> AnswerDto.expect(
    key: QuestionKey<*>,
    expected: String,
): D = this as? D ?: throw UnexpectedAnswerTypeException(key, expected, wireType)

private fun Double.checkedUnitInterval(
    key: QuestionKey<*>,
    field: String,
): Double {
    if (isNaN() || this < 0.0 || this > 1.0) throw MalformedAnswerException(key, "$field is $this, outside 0..1")
    return this
}

internal fun noul(
    name: String,
    question: NoulQuestion,
): QuestionKey<Double> = NoulKey(name, question)

internal fun <T : Any> choice(
    name: String,
    question: ChoiceQuestion<T>,
): QuestionKey<ChoiceAnswer<T>> = ChoiceKey(name, question)

internal fun <T : Any> score(
    name: String,
    question: ScoreQuestion<T>,
): QuestionKey<ScoreAnswer<T>> = ScoreKey(name, question)

/** A Choice over every constant of [T], described by the constants themselves. */
internal inline fun <reified T> choice(
    name: String,
    instructions: String,
    noinline label: (T) -> String = { it.name.lowercase() },
): QuestionKey<ChoiceAnswer<T>> where T : Enum<T>, T : Criterion =
    choice(name, ChoiceQuestion(instructions, enumEntries<T>(), label) { it.description })

/** A Score whose rubric is every constant of [T] in declaration order, lowest level first. */
internal inline fun <reified T> score(
    name: String,
    instructions: String,
): QuestionKey<ScoreAnswer<T>> where T : Enum<T>, T : Criterion =
    score(name, ScoreQuestion(instructions, enumEntries<T>()) { it.description })
