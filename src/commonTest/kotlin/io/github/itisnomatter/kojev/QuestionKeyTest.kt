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
    private enum class Department { BILLING, TECHNICAL, SALES }

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
    fun `choice toDto and parse`() {
        val key =
            choice(
                "department",
                ChoiceQuestion(
                    instructions = "Which team should handle this?",
                    criteria =
                        linkedMapOf(
                            Department.BILLING to "Payments, invoicing, refunds",
                            Department.TECHNICAL to "Bugs, outages, integrations",
                            Department.SALES to "Pricing, upgrades, new accounts",
                        ),
                    label = { it.name.lowercase() },
                ),
            )

        assertEquals(
            ChoiceQuestionDto(
                instructions = "Which team should handle this?",
                criteria =
                    linkedMapOf(
                        "billing" to "Payments, invoicing, refunds",
                        "technical" to "Bugs, outages, integrations",
                        "sales" to "Pricing, upgrades, new accounts",
                    ),
            ),
            key.toDto(),
        )

        val answer =
            key.parse(
                ChoiceAnswerDto(
                    choice = "technical",
                    probabilities = mapOf("billing" to 0.08, "technical" to 0.85, "sales" to 0.07),
                    confidence = 0.82,
                ),
            )
        assertEquals(Department.TECHNICAL, answer.value)
        assertEquals(0.82, answer.confidence)
        assertEquals(
            mapOf(Department.BILLING to 0.08, Department.TECHNICAL to 0.85, Department.SALES to 0.07),
            answer.probabilities,
        )
    }

    @Test
    fun `choice rejects a label function that collapses two options`() {
        assertFailsWith<IllegalArgumentException> {
            choice(
                "department",
                ChoiceQuestion(
                    instructions = "...",
                    criteria = linkedMapOf(Department.BILLING to null, Department.TECHNICAL to null),
                    label = { "same" },
                ),
            )
        }
    }

    @Test
    fun `choice parse rejects an unknown wire label`() {
        val key =
            choice(
                "department",
                ChoiceQuestion(
                    instructions = "...",
                    criteria = linkedMapOf(Department.BILLING to null),
                    label = { it.name.lowercase() },
                ),
            )
        val exception =
            assertFailsWith<UnknownChoiceLabelException> {
                key.parse(ChoiceAnswerDto(choice = "unknown_option", probabilities = mapOf("billing" to 1.0), confidence = 1.0))
            }
        assertEquals("unknown_option", exception.label)
    }

    @Test
    fun `score toDto and parse`() {
        val key =
            score(
                "bug_severity",
                ScoreQuestion(
                    instructions = "How severe is the reported issue?",
                    levels =
                        listOf(
                            "Cosmetic; no impact to functionality",
                            "Broken or degraded feature, but workaround exists",
                            "Blocking issue; no workaround exists",
                        ),
                ),
            )

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

        val answer =
            key.parse(
                ScoreAnswerDto(
                    score = 1.3,
                    confidence = 0.54,
                    legend =
                        mapOf(
                            "0" to "Cosmetic; no impact to functionality",
                            "1" to "Broken or degraded feature, but workaround exists",
                            "2" to "Blocking issue; no workaround exists",
                        ),
                    probabilities = mapOf("0" to 0.0, "1" to 0.7, "2" to 0.3),
                ),
            )
        assertEquals(1.3, answer.value)
        assertEquals(0.54, answer.confidence)
        assertEquals(listOf(0.0, 0.7, 0.3), answer.probabilities)
    }

    @Test
    fun `score parse rejects a response missing a level's probability`() {
        val key = score("bug_severity", ScoreQuestion(instructions = "...", levels = listOf("low", "high")))
        val exception =
            assertFailsWith<MalformedScoreAnswerException> {
                key.parse(
                    ScoreAnswerDto(
                        score = 0.5,
                        confidence = 0.5,
                        legend = mapOf("0" to "low", "1" to "high"),
                        probabilities = mapOf("0" to 1.0),
                    ),
                )
            }
        assertEquals(1, exception.level)
    }
}
