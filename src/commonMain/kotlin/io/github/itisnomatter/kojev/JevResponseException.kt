package io.github.itisnomatter.kojev

/**
 * A 2xx response could not be matched against the questions that asked for it. This always means
 * a server/library contract mismatch (a stale client against a newer API, or a bug) - kojev
 * never turns a missing, mistyped, or out-of-range answer into a default or a null.
 */
sealed class JevResponseException(
    message: String,
    cause: Throwable? = null,
) : JevException(message, cause)

/** The 2xx response body was not a System One response at all - not JSON, or not the documented shape. */
class JevUnreadableResponseException internal constructor(
    /** The `x-typesafe-request-id` header, if the response carried one. */
    val requestId: String?,
    cause: Throwable,
) : JevResponseException(
        if (requestId == null) {
            "The response body is not a System One response."
        } else {
            "The response body is not a System One response (request id: $requestId)."
        },
        cause,
    )

/** The response has no answer under the key's name at all. */
class MissingAnswerException internal constructor(
    val key: QuestionKey<*>,
) : JevResponseException("No answer for question '${key.name}' in the response.")

/** The answer under the key's name is of a different primitive than the question asked. */
class UnexpectedAnswerTypeException internal constructor(
    val key: QuestionKey<*>,
    val expected: String,
    val actual: String,
) : JevResponseException(
        "Expected a '$expected' answer for question '${key.name}', but the response had a '$actual' answer instead.",
    )

/** A Choice answer referenced a wire label that isn't one of the options that were sent. */
class UnknownChoiceLabelException internal constructor(
    val key: QuestionKey<*>,
    val label: String,
) : JevResponseException(
        "Response for question '${key.name}' referenced choice label '$label', which isn't one of the question's criteria.",
    )

/** An answer of the right type is structurally incomplete or has a value outside its documented range. */
class MalformedAnswerException internal constructor(
    val key: QuestionKey<*>,
    val detail: String,
) : JevResponseException("Malformed answer for question '${key.name}': $detail")
