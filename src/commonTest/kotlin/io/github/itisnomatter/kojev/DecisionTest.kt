package io.github.itisnomatter.kojev

import io.github.itisnomatter.kojev.wire.NoulAnswerDto
import io.github.itisnomatter.kojev.wire.SystemOneResponseDto
import io.github.itisnomatter.kojev.wire.UsageDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DecisionTest {
    @Test
    fun `get looks up the answer by the key's name`() {
        val angryQ = noul("is_angry", NoulQuestion(instructions = "..."))
        val decision =
            Decision(
                SystemOneResponseDto(
                    model = "jev-1.13.0",
                    answers = mapOf("is_angry" to NoulAnswerDto(noul = 0.7)),
                    usage = UsageDto(inputTokens = 1, outputTokens = 1),
                ),
            )

        assertEquals(0.7, decision[angryQ])
        assertEquals("jev-1.13.0", decision.model)
    }

    @Test
    fun `get throws when the response has no answer for the key`() {
        val angryQ = noul("is_angry", NoulQuestion(instructions = "..."))
        val decision =
            Decision(
                SystemOneResponseDto(
                    model = "jev-1.13.0",
                    answers = emptyMap(),
                    usage = UsageDto(inputTokens = 1, outputTokens = 1),
                ),
            )

        val exception = assertFailsWith<MissingAnswerException> { decision[angryQ] }
        assertEquals(angryQ, exception.key)
    }
}
