package io.github.itisnomatter.kojev

import io.github.itisnomatter.kojev.wire.ChoiceAnswerDto
import io.github.itisnomatter.kojev.wire.ChoiceQuestionDto
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The wire label is derived with the no-argument `String.lowercase()`, which the stdlib
 * documents as using "Unicode mapping rules of the invariant locale". This pins that down under
 * the classic counterexample: with a Turkish default locale, a locale-sensitive lowercasing
 * turns `I` into dotless `ı` (U+0131), which would silently change what the model sees and
 * break the round trip back to the constant. Only the JVM has a process-wide default locale,
 * hence a JVM-only test.
 */
class LabelLocaleJvmTest {
    private enum class Billing(
        override val description: String,
    ) : Criterion {
        INVOICE("An invoice"),
        ITEM("A line item"),
    }

    @Test
    fun `labels do not depend on the default locale`() {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("tr-TR"))
        try {
            // Sanity check that the locale really is the hostile one.
            assertEquals("ınvoıce", "INVOICE".lowercase(Locale.getDefault()))

            val key = choice<Billing>("billing", instructions = "...")
            val criteria = (key.toDto() as ChoiceQuestionDto).criteria
            assertEquals(listOf("invoice", "item"), criteria.keys.toList())

            val answer =
                key.parse(
                    ChoiceAnswerDto(choice = "invoice", probabilities = mapOf("invoice" to 0.9, "item" to 0.1), confidence = 0.9),
                )
            assertEquals(Billing.INVOICE, answer.value)
        } finally {
            Locale.setDefault(previous)
        }
    }
}
