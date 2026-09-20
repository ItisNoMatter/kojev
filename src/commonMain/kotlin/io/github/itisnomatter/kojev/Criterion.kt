package io.github.itisnomatter.kojev

/**
 * One entry of a question's criteria: a Choice option, or a Score level.
 *
 * Implement it on your own enum so that every constant must carry its [description] - a
 * forgotten one is a compile error, not a runtime surprise - and so the description sits next
 * to the thing it describes:
 *
 * ```
 * enum class Department(override val description: String) : Criterion {
 *     BILLING("Payments, invoicing, refunds"),
 *     TECHNICAL("Bugs, outages, integrations"),
 * }
 * ```
 *
 * For a Score, declaration order is the rubric order, lowest level first; the level numbers the
 * API reports are the constants' ordinals.
 *
 * For a Choice, the constant's `name.lowercase()` is sent as the option's wire label. The model
 * reads labels as text alongside the descriptions, and every official example uses lowercase
 * words (`billing`, `wrong_size`), so `TECHNICAL_SUPPORT` goes out as `technical_support`. Pass
 * an explicit label function to send something else.
 */
interface Criterion {
    val description: String
}
