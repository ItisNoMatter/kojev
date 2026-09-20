package io.github.itisnomatter.kojev

/**
 * Asks whether a statement holds, or a yes/no question, against the request's state. The
 * answer is the probability of "yes" - not a confidence value, Noul doesn't have one.
 */
class NoulQuestion(
    val instructions: String,
    val whenTrue: String? = null,
    val whenFalse: String? = null,
)

/**
 * Asks the model to pick one option out of [criteria]'s keys.
 *
 * @param label the exact string sent on the wire for each option, and matched back against
 *   the response. Must be injective: two options must never produce the same label.
 */
class ChoiceQuestion<T : Any>(
    val instructions: String,
    val criteria: Map<T, String?>,
    val label: (T) -> String,
) {
    init {
        require(criteria.isNotEmpty()) { "Choice criteria must not be empty." }
    }
}

/**
 * Asks the model to rate the state against an ordered rubric. [levels] are in ascending
 * order; the API assigns level numbers 0, 1, 2, ... by position - there is no way to request
 * custom level numbers.
 */
class ScoreQuestion(
    val instructions: String,
    val levels: List<String>,
) {
    init {
        require(levels.size in 2..10) { "Score levels must be between 2 and 10, got ${levels.size}." }
    }
}
