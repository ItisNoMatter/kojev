package io.github.itisnomatter.kojev

import kotlin.enums.enumEntries

// The public way to build questions. Throughout this DSL, what is required is a parameter -
// forgetting it is a compile error - and what is optional goes in the trailing lambda.

/** The optional `true` / `false` descriptions of a Noul. */
class NoulCriteria internal constructor() {
    /** What counts as a "yes". */
    var whenTrue: String? = null

    /** What counts as a "no". */
    var whenFalse: String? = null
}

/**
 * Asks whether a statement holds, or a yes/no question, against the request's state. The answer
 * is the probability of "yes" - not a confidence value, Noul doesn't have one.
 *
 * ```
 * val angryQ = noul("is_angry", "Is the customer expressing anger?") {
 *     whenTrue = "Clear irritation or forceful tone"
 *     whenFalse = "Neutral or calm"
 * }
 * ```
 */
fun noul(
    name: String,
    instructions: String,
    criteria: NoulCriteria.() -> Unit = {},
): QuestionKey<Double> {
    val declared = NoulCriteria().apply(criteria)
    return noul(name, NoulQuestion(instructions, declared.whenTrue, declared.whenFalse))
}

/**
 * Asks the model to pick one of [T]'s constants. Each constant describes itself through
 * [Criterion], and its `name.lowercase()` is the wire label.
 *
 * ```
 * enum class Intent(override val description: String) : Criterion {
 *     REFUND("Refunds and cancellations"),
 *     TECHNICAL_SUPPORT("Bugs and technical problems"),
 * }
 * val intentQ = choice<Intent>("intent", "Which team should handle this request?")
 * ```
 *
 * For an enum you don't own, a `sealed interface`, or a different wording of the same type, use
 * the overload that takes explicit descriptions.
 */
inline fun <reified T> choice(
    name: String,
    instructions: String,
): QuestionKey<ChoiceAnswer<T>> where T : Enum<T>, T : Criterion = choiceOfEntries(name, instructions, enumEntries<T>())

@PublishedApi
internal fun <T> choiceOfEntries(
    name: String,
    instructions: String,
    entries: List<T>,
): QuestionKey<ChoiceAnswer<T>> where T : Enum<T>, T : Criterion =
    choice(name, ChoiceQuestion(instructions, entries, { it.name.lowercase() }) { it.description })

/** The options of a Choice built with explicit descriptions, in the order they are declared. */
class ChoiceOptions<T : Any> internal constructor() {
    internal val declared = LinkedHashMap<T, String?>()

    /** Declares this value as an option. A `null` description sends the label alone. */
    infix fun T.describedAs(description: String?) {
        require(this !in declared) { "Option $this is described twice." }
        declared[this] = description
    }
}

/**
 * Asks the model to pick one of the options declared in [options], with explicit wire labels and
 * descriptions - the escape hatch for enums you don't own, `sealed interface` objects, or asking
 * the same type with a different wording.
 *
 * ```
 * val toneQ = choice<Tone>("tone", "What is the customer's tone?", label = { it.wireName }) {
 *     Tone.Calm describedAs null            // label alone
 *     Tone.Angry describedAs "Hostile or upset"
 * }
 * ```
 *
 * @param label the exact string sent for each option and matched back against the response;
 *   two options must never produce the same label.
 */
fun <T : Any> choice(
    name: String,
    instructions: String,
    label: (T) -> String,
    options: ChoiceOptions<T>.() -> Unit,
): QuestionKey<ChoiceAnswer<T>> {
    val declared = ChoiceOptions<T>().apply(options).declared
    return choice(name, ChoiceQuestion(instructions, declared.keys.toList(), label) { declared[it] })
}

/**
 * Asks the model to rate the state against [T]'s constants as a rubric, in declaration order,
 * lowest level first. Each constant describes its level through [Criterion].
 *
 * ```
 * enum class Urgency(override val description: String) : Criterion {
 *     LATER("Not time-sensitive"),
 *     TODAY("Should be handled today"),
 *     NOW("Needs immediate attention"),
 * }
 * val urgencyQ = score<Urgency>("urgency", "How urgently does this need a response?")
 * ```
 */
inline fun <reified T> score(
    name: String,
    instructions: String,
): QuestionKey<ScoreAnswer<T>> where T : Enum<T>, T : Criterion = scoreOfEntries(name, instructions, enumEntries<T>())

@PublishedApi
internal fun <T> scoreOfEntries(
    name: String,
    instructions: String,
    entries: List<T>,
): QuestionKey<ScoreAnswer<T>> where T : Enum<T>, T : Criterion = score(name, ScoreQuestion(instructions, entries) { it.description })
