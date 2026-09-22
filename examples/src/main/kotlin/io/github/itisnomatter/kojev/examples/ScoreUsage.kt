package io.github.itisnomatter.kojev.examples

import io.github.itisnomatter.kojev.Criterion
import io.github.itisnomatter.kojev.JevClient
import io.github.itisnomatter.kojev.ScoreAnswer
import io.github.itisnomatter.kojev.decide
import io.github.itisnomatter.kojev.score
import kotlin.math.roundToInt

// What a Score gives you, and which part to use for which decision. A Score's answer is not a
// level: `score` is the probability-weighted mean of the level numbers, `mostLikely` is the mode,
// and `probabilities` is the whole distribution. The library never rounds the mean for you.

enum class Severity(
    override val description: String,
) : Criterion {
    COSMETIC("Cosmetic; no impact to functionality"),
    DEGRADED("Broken or degraded feature, but a workaround exists"),
    BLOCKING("Blocking issue; no workaround exists"),
}

val severityQ = score<Severity>("severity", "How severe is the reported issue?")

/** Probability that the level is at least [level] - a threshold the caller chooses, summed by the caller. */
fun ScoreAnswer<Severity>.probabilityAtLeast(level: Severity): Double = probabilities.filterKeys { it >= level }.values.sum()

suspend fun scoreUsage(jev: JevClient) {
    val reports =
        listOf(
            "The export button crashes the settings page in Safari. It works in Chrome, but a few of our customers only use Safari.",
            "The logo on the login page is a few pixels off-centre.",
            "Nobody can log in since the 14:00 deploy. Every attempt returns a 500.",
        )

    val answers = reports.map { report -> report to jev.decide(report) { ask(severityQ) }[severityQ] }

    for ((report, answer) in answers) {
        println("\"${report.take(60)}...\"")
        println("  distribution: ${answer.probabilities.entries.joinToString { "${it.key} ${it.value.percent()}" }}")
        println("  mostLikely:   ${answer.mostLikely}  (the mode - use this for a categorical read)")
        println("  score:        ${"%.2f".format(answer.score)}  (mean level number - use this to order or threshold)")

        // Rounding the mean into a level is a choice this code makes explicitly, not the library.
        val nearest = Severity.entries[answer.score.roundToInt()]
        println("  nearest:      $nearest  (rounded mean; can differ from mostLikely on a split distribution)")

        // "Is it at least DEGRADED?" is a cumulative probability over the rubric.
        val pAtLeastDegraded = answer.probabilityAtLeast(Severity.DEGRADED)
        println("  P(>= DEGRADED): ${pAtLeastDegraded.percent()} -> ${if (pAtLeastDegraded >= 0.7) "prioritise" else "backlog"}")

        // Paging is about the top level only: a direct lookup, no cumulative needed.
        val pBlocking = answer.probabilities.getValue(Severity.BLOCKING)
        println("  P(BLOCKING):    ${pBlocking.percent()} -> ${if (pBlocking >= 0.5) "page on-call" else "no page"}")
    }

    // Re-ranking: the mean is an ordinal expectation, so it orders reports directly.
    println("by severity, most severe first:")
    for ((report, answer) in answers.sortedByDescending { it.second.score }) {
        println("  ${"%.2f".format(answer.score)}  ${report.take(50)}...")
    }
}
