package io.github.itisnomatter.kojev

/** Token usage of one request, as reported by the API. */
class Usage internal constructor(
    /** Billable input tokens. */
    val inputTokens: Int,
    /** Output tokens used to answer; the API schema states these are currently free of charge. */
    val outputTokens: Int,
)
