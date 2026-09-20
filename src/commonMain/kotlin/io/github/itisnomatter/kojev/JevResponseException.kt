package io.github.itisnomatter.kojev

/**
 * A response could not be matched against the questions that asked for it. This always means
 * a server/library contract mismatch (a stale client against a newer API, or a bug) - kojev
 * never turns a missing, mistyped, or out-of-range answer into a default or a null.
 */
sealed class JevResponseException(
    message: String,
) : Exception(message)

class MissingAnswerException internal constructor(
    val key: QuestionKey<*>,
) : JevResponseException("No answer for question '${key.name}' in the response.")

class UnexpectedAnswerTypeException internal constructor(
    val key: QuestionKey<*>,
    val expected: String,
    val actual: String,
) : JevResponseException(
        "Expected a '$expected' answer for question '${key.name}', but the response had a '$actual' answer instead.",
    )

class UnknownChoiceLabelException internal constructor(
    val key: QuestionKey<*>,
    val label: String,
) : JevResponseException(
        "Response for question '${key.name}' referenced choice label '$label', which isn't one of the question's criteria.",
    )

class MalformedScoreAnswerException internal constructor(
    val key: QuestionKey<*>,
    val level: Int,
) : JevResponseException(
        "Response for question '${key.name}' is missing a probability for level $level.",
    )
