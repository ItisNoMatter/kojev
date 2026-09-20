package io.github.itisnomatter.kojev

import io.github.itisnomatter.kojev.wire.SystemOneResponseDto

/**
 * The typed answers to every question asked in one request. Look an answer up with the same
 * [QuestionKey] used to ask it - `decision[key]` never returns null and never silently
 * substitutes a default; a missing, mistyped, or out-of-range answer throws instead.
 */
class Decision internal constructor(
    private val response: SystemOneResponseDto,
) {
    /** The versioned model id that actually answered - may differ from an alias in the request. */
    val model: String = response.model

    operator fun <T> get(key: QuestionKey<T>): T {
        val answer = response.answers[key.name] ?: throw MissingAnswerException(key)
        return key.parse(answer)
    }
}
