package io.github.itisnomatter.kojev.wire

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every expected JSON string here is copied verbatim from `docs/api-notes.md`.
 */
class QuestionDtoTest {
    @Test
    fun `noul question without criteria`() {
        val dto: QuestionDto = NoulQuestionDto(instructions = "Does this convey urgency?")
        assertEquals(
            """{"type":"noul","instructions":"Does this convey urgency?"}""",
            jevJson.encodeToString(QuestionDto.serializer(), dto),
        )
    }

    @Test
    fun `noul question with criteria`() {
        val dto: QuestionDto =
            NoulQuestionDto(
                instructions = "Does this convey urgency?",
                criteria =
                    NoulCriteriaDto(
                        whenTrue = "Explicitly time-sensitive",
                        whenFalse = "No urgency expressed",
                    ),
            )
        assertEquals(
            """{"type":"noul","instructions":"Does this convey urgency?",""" +
                """"criteria":{"true":"Explicitly time-sensitive","false":"No urgency expressed"}}""",
            jevJson.encodeToString(QuestionDto.serializer(), dto),
        )
    }

    @Test
    fun `choice question`() {
        val dto: QuestionDto =
            ChoiceQuestionDto(
                instructions = "Which team should handle this?",
                criteria =
                    linkedMapOf(
                        "billing" to "Payments, invoicing, refunds",
                        "technical" to "Bugs, outages, integrations",
                        "sales" to "Pricing, upgrades, new accounts",
                    ),
            )
        assertEquals(
            """{"type":"choice","instructions":"Which team should handle this?","criteria":{""" +
                """"billing":"Payments, invoicing, refunds",""" +
                """"technical":"Bugs, outages, integrations",""" +
                """"sales":"Pricing, upgrades, new accounts"}}""",
            jevJson.encodeToString(QuestionDto.serializer(), dto),
        )
    }

    @Test
    fun `score question`() {
        val dto: QuestionDto =
            ScoreQuestionDto(
                instructions = "How severe is the reported issue?",
                criteria =
                    listOf(
                        "Cosmetic; no impact to functionality",
                        "Broken or degraded feature, but workaround exists",
                        "Blocking issue; no workaround exists",
                    ),
            )
        assertEquals(
            """{"type":"score","instructions":"How severe is the reported issue?","criteria":[""" +
                """"Cosmetic; no impact to functionality",""" +
                """"Broken or degraded feature, but workaround exists",""" +
                """"Blocking issue; no workaround exists"]}""",
            jevJson.encodeToString(QuestionDto.serializer(), dto),
        )
    }
}
