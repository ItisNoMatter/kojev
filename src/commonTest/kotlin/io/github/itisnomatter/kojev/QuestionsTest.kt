package io.github.itisnomatter.kojev

import io.github.itisnomatter.kojev.wire.ChoiceAnswerDto
import io.github.itisnomatter.kojev.wire.ChoiceQuestionDto
import io.github.itisnomatter.kojev.wire.NoulCriteriaDto
import io.github.itisnomatter.kojev.wire.NoulQuestionDto
import io.github.itisnomatter.kojev.wire.ScoreQuestionDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** The public builders, checked at the wire boundary. */
class QuestionsTest {
    private enum class Intent(
        override val description: String,
    ) : Criterion {
        REFUND("Refunds and cancellations"),
        TECHNICAL_SUPPORT("Bugs and technical problems"),
    }

    private enum class Urgency(
        override val description: String,
    ) : Criterion {
        LATER("Not time-sensitive"),
        NOW("Needs immediate attention"),
    }

    private sealed interface Tone {
        data object Calm : Tone

        data object Angry : Tone
    }

    private val Tone.wireName: String
        get() =
            when (this) {
                Tone.Calm -> "calm"
                Tone.Angry -> "angry"
            }

    @Test
    fun `noul without criteria`() {
        val key = noul("is_angry", "Is the customer expressing anger?")
        assertEquals("is_angry", key.name)
        assertEquals(NoulQuestionDto(instructions = "Is the customer expressing anger?"), key.toDto())
    }

    @Test
    fun `noul with criteria`() {
        val key =
            noul("is_angry", "Is the customer expressing anger?") {
                whenTrue = "Clear irritation or forceful tone"
                whenFalse = "Neutral or calm"
            }
        assertEquals(
            NoulQuestionDto(
                instructions = "Is the customer expressing anger?",
                criteria = NoulCriteriaDto("Clear irritation or forceful tone", "Neutral or calm"),
            ),
            key.toDto(),
        )
    }

    @Test
    fun `choice over a Criterion enum`() {
        val key = choice<Intent>("intent", "Which team should handle this request?")
        assertEquals(
            ChoiceQuestionDto(
                instructions = "Which team should handle this request?",
                criteria =
                    linkedMapOf(
                        "refund" to "Refunds and cancellations",
                        "technical_support" to "Bugs and technical problems",
                    ),
            ),
            key.toDto(),
        )
    }

    @Test
    fun `choice with explicit descriptions keeps declaration order and allows a null description`() {
        val key =
            choice<Tone>("tone", "What is the customer's tone?", label = { it.wireName }) {
                Tone.Angry describedAs "Hostile or upset"
                Tone.Calm describedAs null
            }
        assertEquals(
            ChoiceQuestionDto(
                instructions = "What is the customer's tone?",
                criteria = linkedMapOf("angry" to "Hostile or upset", "calm" to null),
            ),
            key.toDto(),
        )
        val answer = key.parse(ChoiceAnswerDto(choice = "calm", probabilities = mapOf("angry" to 0.2, "calm" to 0.8), confidence = 0.8))
        assertEquals(Tone.Calm, answer.value)
    }

    @Test
    fun `choice with explicit descriptions can re-describe a Criterion enum with its own labels`() {
        val key =
            choice<Intent>("intent", "Where does this go?", label = { it.name }) {
                Intent.REFUND describedAs "Money back"
            }
        assertEquals(
            ChoiceQuestionDto(instructions = "Where does this go?", criteria = linkedMapOf("REFUND" to "Money back")),
            key.toDto(),
        )
    }

    @Test
    fun `choice with explicit descriptions rejects describing an option twice`() {
        assertFailsWith<IllegalArgumentException> {
            choice<Tone>("tone", "...", label = { it.wireName }) {
                Tone.Calm describedAs "a"
                Tone.Calm describedAs "b"
            }
        }
    }

    @Test
    fun `choice with explicit descriptions rejects an empty option set`() {
        assertFailsWith<IllegalArgumentException> {
            choice<Tone>("tone", "...", label = { it.wireName }) {}
        }
    }

    @Test
    fun `score over a Criterion enum`() {
        val key = score<Urgency>("urgency", "How urgently does this need a response?")
        assertEquals(
            ScoreQuestionDto(
                instructions = "How urgently does this need a response?",
                criteria = listOf("Not time-sensitive", "Needs immediate attention"),
            ),
            key.toDto(),
        )
    }
}
