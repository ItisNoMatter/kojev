package io.github.itisnomatter.kojev

import io.github.itisnomatter.kojev.wire.ChoiceAnswerDto
import io.github.itisnomatter.kojev.wire.ChoiceQuestionDto
import io.github.itisnomatter.kojev.wire.NoulAnswerDto
import io.github.itisnomatter.kojev.wire.NoulCriteriaDto
import io.github.itisnomatter.kojev.wire.NoulQuestionDto
import io.github.itisnomatter.kojev.wire.ScoreAnswerDto
import io.github.itisnomatter.kojev.wire.ScoreQuestionDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class QuestionKeyTest {
    private enum class Department(
        override val description: String,
    ) : Criterion {
        BILLING("Payments, invoicing, refunds"),
        TECHNICAL_SUPPORT("Bugs, outages, integrations"),
        SALES("Pricing, upgrades, new accounts"),
    }

    private enum class Severity(
        override val description: String,
    ) : Criterion {
        COSMETIC("Cosmetic; no impact to functionality"),
        DEGRADED("Broken or degraded feature, but workaround exists"),
        BLOCKING("Blocking issue; no workaround exists"),
    }

    // ---- Noul ----

    @Test
    fun `noul toDto and parse`() {
        val key = noul("is_urgent", NoulQuestion(instructions = "Does this convey urgency?"))
        assertEquals(NoulQuestionDto(instructions = "Does this convey urgency?"), key.toDto())
        assertEquals(0.92, key.parse(NoulAnswerDto(noul = 0.92)))
    }

    @Test
    fun `noul toDto includes criteria when present`() {
        val key =
            noul(
                "is_urgent",
                NoulQuestion(
                    instructions = "Does this convey urgency?",
                    whenTrue = "Explicitly time-sensitive",
                    whenFalse = "No urgency expressed",
                ),
            )
        assertEquals(
            NoulQuestionDto(
                instructions = "Does this convey urgency?",
                criteria = NoulCriteriaDto("Explicitly time-sensitive", "No urgency expressed"),
            ),
            key.toDto(),
        )
    }

    @Test
    fun `noul parse rejects a differently typed answer`() {
        val key = noul("is_urgent", NoulQuestion(instructions = "..."))
        val exception =
            assertFailsWith<UnexpectedAnswerTypeException> {
                key.parse(ChoiceAnswerDto(choice = "x", probabilities = mapOf("x" to 1.0), confidence = 1.0))
            }
        assertEquals(key, exception.key)
        assertEquals("noul", exception.expected)
        assertEquals("choice", exception.actual)
    }

    @Test
    fun `noul parse rejects a probability outside 0 to 1`() {
        val key = noul("is_urgent", NoulQuestion(instructions = "..."))
        assertFailsWith<MalformedAnswerException> { key.parse(NoulAnswerDto(noul = 1.5)) }
    }

    // ---- Choice, Criterion form ----

    @Test
    fun `choice sends lowercased constant names as labels and the enum's descriptions`() {
        val key = choice<Department>("department", instructions = "Which team should handle this?")
        assertEquals(
            ChoiceQuestionDto(
                instructions = "Which team should handle this?",
                criteria =
                    linkedMapOf(
                        "billing" to "Payments, invoicing, refunds",
                        "technical_support" to "Bugs, outages, integrations",
                        "sales" to "Pricing, upgrades, new accounts",
                    ),
            ),
            key.toDto(),
        )
    }

    @Test
    fun `choice parse maps labels back to the caller's constants in declaration order`() {
        val key = choice<Department>("department", instructions = "...")
        val answer =
            key.parse(
                ChoiceAnswerDto(
                    choice = "technical_support",
                    probabilities = mapOf("sales" to 0.07, "billing" to 0.08, "technical_support" to 0.85),
                    confidence = 0.82,
                ),
            )
        assertEquals(Department.TECHNICAL_SUPPORT, answer.value)
        assertEquals(0.82, answer.confidence)
        assertEquals(listOf(Department.BILLING, Department.TECHNICAL_SUPPORT, Department.SALES), answer.probabilities.keys.toList())
        assertEquals(
            mapOf(Department.BILLING to 0.08, Department.TECHNICAL_SUPPORT to 0.85, Department.SALES to 0.07),
            answer.probabilities,
        )
    }

    private enum class CaseClash(
        override val description: String,
    ) : Criterion {
        FOO("one"),
        Foo("two"),
    }

    @Test
    fun `choice rejects constants whose labels collide`() {
        assertFailsWith<IllegalArgumentException> { choice<CaseClash>("clash", instructions = "...") }
    }

    @Test
    fun `choice parse rejects an unknown chosen label`() {
        val key = choice<Department>("department", instructions = "...")
        val exception =
            assertFailsWith<UnknownChoiceLabelException> {
                key.parse(ChoiceAnswerDto(choice = "legal", probabilities = mapOf("billing" to 1.0), confidence = 1.0))
            }
        assertEquals("legal", exception.label)
    }

    @Test
    fun `choice parse rejects an unknown label in the distribution`() {
        val key = choice<Department>("department", instructions = "...")
        val exception =
            assertFailsWith<UnknownChoiceLabelException> {
                key.parse(
                    ChoiceAnswerDto(
                        choice = "billing",
                        probabilities = mapOf("billing" to 0.5, "technical_support" to 0.2, "sales" to 0.2, "legal" to 0.1),
                        confidence = 0.5,
                    ),
                )
            }
        assertEquals("legal", exception.label)
    }

    @Test
    fun `choice parse rejects a distribution missing one of the sent options`() {
        val key = choice<Department>("department", instructions = "...")
        assertFailsWith<MalformedAnswerException> {
            key.parse(ChoiceAnswerDto(choice = "billing", probabilities = mapOf("billing" to 0.6, "sales" to 0.4), confidence = 0.6))
        }
    }

    // ---- Choice, explicit-map form (the escape hatch) ----

    private sealed interface Tone {
        data object Calm : Tone

        data object Angry : Tone
    }

    @Test
    fun `choice over sealed objects works with explicit labels and descriptions`() {
        val key =
            choice(
                "tone",
                ChoiceQuestion(
                    instructions = "What is the customer's tone?",
                    options = listOf(Tone.Calm, Tone.Angry),
                    label = { if (it is Tone.Calm) "calm" else "angry" },
                    description = { null },
                ),
            )
        assertEquals(
            ChoiceQuestionDto(instructions = "What is the customer's tone?", criteria = linkedMapOf("calm" to null, "angry" to null)),
            key.toDto(),
        )
        val answer = key.parse(ChoiceAnswerDto(choice = "angry", probabilities = mapOf("calm" to 0.1, "angry" to 0.9), confidence = 0.9))
        assertEquals(Tone.Angry, answer.value)
    }

    // ---- Score ----

    @Test
    fun `score sends the enum's descriptions in declaration order`() {
        val key = score<Severity>("bug_severity", instructions = "How severe is the reported issue?")
        assertEquals(
            ScoreQuestionDto(
                instructions = "How severe is the reported issue?",
                criteria =
                    listOf(
                        "Cosmetic; no impact to functionality",
                        "Broken or degraded feature, but workaround exists",
                        "Blocking issue; no workaround exists",
                    ),
            ),
            key.toDto(),
        )
    }

    @Test
    fun `score parse keys the distribution by the caller's constants`() {
        val key = score<Severity>("bug_severity", instructions = "...")
        val answer =
            key.parse(
                ScoreAnswerDto(
                    score = 1.3,
                    confidence = 0.54,
                    legend = mapOf("0" to "x", "1" to "y", "2" to "z"),
                    probabilities = mapOf("0" to 0.0, "1" to 0.7, "2" to 0.3),
                ),
            )
        assertEquals(1.3, answer.score)
        assertEquals(0.54, answer.confidence)
        assertEquals(Severity.DEGRADED, answer.mostLikely)
        assertEquals(
            mapOf(Severity.COSMETIC to 0.0, Severity.DEGRADED to 0.7, Severity.BLOCKING to 0.3),
            answer.probabilities,
        )
    }

    @Test
    fun `score mostLikely resolves an exact tie to the lowest level`() {
        val key = score<Severity>("bug_severity", instructions = "...")
        val answer =
            key.parse(
                ScoreAnswerDto(
                    score = 1.0,
                    confidence = 0.5,
                    legend = emptyMap(),
                    probabilities = mapOf("0" to 0.5, "1" to 0.0, "2" to 0.5),
                ),
            )
        assertEquals(Severity.COSMETIC, answer.mostLikely)
        assertEquals(1.0, answer.score)
    }

    @Test
    fun `score parse rejects a distribution missing a level`() {
        val key = score<Severity>("bug_severity", instructions = "...")
        assertFailsWith<MalformedAnswerException> {
            key.parse(ScoreAnswerDto(score = 0.5, confidence = 0.5, legend = emptyMap(), probabilities = mapOf("0" to 0.5, "2" to 0.5)))
        }
    }

    @Test
    fun `score parse rejects a distribution with an extra level`() {
        val key = score<Severity>("bug_severity", instructions = "...")
        assertFailsWith<MalformedAnswerException> {
            key.parse(
                ScoreAnswerDto(
                    score = 0.5,
                    confidence = 0.5,
                    legend = emptyMap(),
                    probabilities = mapOf("0" to 0.5, "1" to 0.5, "2" to 0.0, "3" to 0.0),
                ),
            )
        }
    }

    @Test
    fun `score parse rejects a score outside the rubric's range`() {
        val key = score<Severity>("bug_severity", instructions = "...")
        assertFailsWith<MalformedAnswerException> {
            key.parse(
                ScoreAnswerDto(
                    score = 2.5,
                    confidence = 0.5,
                    legend = emptyMap(),
                    probabilities = mapOf("0" to 0.0, "1" to 0.0, "2" to 1.0),
                ),
            )
        }
    }

    @Test
    fun `score parse rejects a confidence outside 0 to 1`() {
        val key = score<Severity>("bug_severity", instructions = "...")
        assertFailsWith<MalformedAnswerException> {
            key.parse(
                ScoreAnswerDto(
                    score = 1.0,
                    confidence = 1.2,
                    legend = emptyMap(),
                    probabilities = mapOf("0" to 0.0, "1" to 1.0, "2" to 0.0),
                ),
            )
        }
    }

    private enum class OneLevel(
        override val description: String,
    ) : Criterion {
        ONLY("only"),
    }

    @Test
    fun `score requires at least two levels`() {
        assertFailsWith<IllegalArgumentException> { score<OneLevel>("s", instructions = "...") }
    }
}
