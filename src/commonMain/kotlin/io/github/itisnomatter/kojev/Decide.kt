package io.github.itisnomatter.kojev

import io.github.itisnomatter.kojev.wire.jevJson
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.serializer

/** The questions of one decision. */
class DecisionBuilder internal constructor() {
    internal val keys = ArrayList<QuestionKey<*>>()

    /** Adds questions. Every question in one decision is evaluated in parallel against the same state. */
    fun ask(vararg questions: QuestionKey<*>) {
        keys += questions
    }
}

/**
 * Evaluates every question asked in [block] against [state], in one request.
 *
 * [state] is what the questions are about, and it is a parameter rather than a call inside the
 * block so that the boundary between the state and the questions stays readable when several
 * questions are asked:
 *
 * ```
 * val result = jev.decide(ticket) {
 *     ask(intentQ, angryQ, urgencyQ)
 * }
 * val intent: Intent = result[intentQ].value
 * ```
 *
 * Any `@Serializable` value works as [state] - a plain `String`, or a structure the API receives
 * as a JSON object or array. Prefer a structure built for the decision over your whole domain
 * object: the model reads all of it, and accuracy falls as the state grows with content unrelated
 * to the questions (`docs/api-notes.md`, known weaknesses). A [state] that would serialize to a
 * number, boolean, or `null` is rejected, since the API accepts only string, object, or array.
 *
 * A type without a serializer fails with a `SerializationException` at the call; for a type you
 * cannot annotate, use the overload that takes a serializer.
 */
suspend inline fun <reified S : Any> JevClient.decide(
    state: S,
    noinline block: DecisionBuilder.() -> Unit,
): Decision = decide(state, serializer<S>(), block)

/** [decide] with an explicit [serializer] for [state]. */
suspend fun <S : Any> JevClient.decide(
    state: S,
    serializer: SerializationStrategy<S>,
    block: DecisionBuilder.() -> Unit,
): Decision {
    val encoded = jevJson.encodeToJsonElement(serializer, state)
    require(encoded is JsonObject || encoded is JsonArray || (encoded is JsonPrimitive && encoded.isString)) {
        "state must serialize to a JSON string, object, or array, but ${state::class.simpleName} serialized to $encoded."
    }
    val keys = DecisionBuilder().apply(block).keys
    require(keys.isNotEmpty()) { "decide { } must ask at least one question." }
    return requestDecision(httpClient, baseUrl, model, encoded, keys)
}
