package io.github.itisnomatter.kojev

/**
 * A Choice answer: the option the model picked, the full probability distribution over every
 * option in the question's criteria, and a confidence derived from that distribution's shape.
 */
class ChoiceAnswer<T> internal constructor(
    val value: T,
    /** Probability of each option, in the order the options were declared. Sums to approximately 1. */
    val probabilities: Map<T, Double>,
    /** 0-1, derived from the shape of [probabilities]: concentrated is high, spread out is low. */
    val confidence: Double,
)

/**
 * A Score answer.
 *
 * There is deliberately no `value` here, unlike [ChoiceAnswer]. The API's answer to a Score is
 * [score], a probability-weighted mean that usually falls between levels and can even land on a
 * level the model gave zero probability. Turning it into a single level would mean this library
 * picking a rounding policy on the caller's behalf - so it doesn't. Use [mostLikely] for the
 * mode, [probabilities] for the distribution, and round [score] yourself if that's what you want.
 */
class ScoreAnswer<T> internal constructor(
    /**
     * The API's `score`: the probability-weighted mean of the level numbers (0-based, in
     * declaration order). A `Double` between 0 and `levels - 1` - not itself a level.
     */
    val score: Double,
    /** The level with the highest probability. An exact tie resolves to the lowest level. */
    val mostLikely: T,
    /** Probability of each level, lowest level first. Sums to approximately 1. */
    val probabilities: Map<T, Double>,
    /** 0-1, derived from the shape of [probabilities]: concentrated is high, spread out is low. */
    val confidence: Double,
)
