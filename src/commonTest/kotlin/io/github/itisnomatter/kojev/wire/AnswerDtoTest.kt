package io.github.itisnomatter.kojev.wire

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every JSON string here is copied verbatim from `docs/api-notes.md`.
 */
class AnswerDtoTest {
    @Test
    fun `noul answer has no confidence field`() {
        val answer = jevJson.decodeFromString(AnswerDto.serializer(), """{"type":"noul","noul":0.92}""")
        assertEquals(NoulAnswerDto(noul = 0.92), answer)
    }

    @Test
    fun `choice answer`() {
        val json =
            """{"type":"choice","choice":"technical",""" +
                """"probabilities":{"billing":0.08,"technical":0.85,"sales":0.07},"confidence":0.82}"""
        val answer = jevJson.decodeFromString(AnswerDto.serializer(), json)
        assertEquals(
            ChoiceAnswerDto(
                choice = "technical",
                probabilities = mapOf("billing" to 0.08, "technical" to 0.85, "sales" to 0.07),
                confidence = 0.82,
            ),
            answer,
        )
    }

    @Test
    fun `score answer`() {
        val json =
            """{"type":"score","score":1.3,"confidence":0.54,""" +
                """"legend":{"0":"Cosmetic; no impact to functionality",""" +
                """"1":"Broken or degraded feature, but workaround exists",""" +
                """"2":"Blocking issue; no workaround exists"},""" +
                """"probabilities":{"0":0.0,"1":0.7,"2":0.3}}"""
        val answer = jevJson.decodeFromString(AnswerDto.serializer(), json)
        assertEquals(
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
            answer,
        )
    }

    @Test
    fun `unknown fields are ignored for forward compatibility`() {
        val answer =
            jevJson.decodeFromString(AnswerDto.serializer(), """{"type":"noul","noul":0.5,"future_field":"x"}""")
        assertEquals(NoulAnswerDto(noul = 0.5), answer)
    }
}
