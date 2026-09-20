package io.github.itisnomatter.kojev

/**
 * A [choice] answer: the option the model picked, the full probability distribution over
 * every option in the question's criteria, and a confidence derived from that distribution's
 * shape.
 */
class ChoiceAnswer<T> internal constructor(
    val value: T,
    val probabilities: Map<T, Double>,
    val confidence: Double,
)

/**
 * A [score] answer. [value] is the probability-weighted mean of the rubric's level numbers -
 * a `Double`, not necessarily an integer, and not necessarily one of the level numbers itself.
 * [levels] are the level descriptions in order; [probabilities] is indexed the same way.
 */
class ScoreAnswer internal constructor(
    val value: Double,
    val confidence: Double,
    val levels: List<String>,
    val probabilities: List<Double>,
)
