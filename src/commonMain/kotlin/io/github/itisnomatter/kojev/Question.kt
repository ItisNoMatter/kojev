package io.github.itisnomatter.kojev

// These are the internal representations behind a QuestionKey. The public way to build a
// question is the DSL layer; nothing here is part of the published API.

internal class NoulQuestion(
    val instructions: String,
    val whenTrue: String? = null,
    val whenFalse: String? = null,
)

internal class ChoiceQuestion<T : Any>(
    val instructions: String,
    val options: List<T>,
    val label: (T) -> String,
    val description: (T) -> String?,
) {
    init {
        require(options.isNotEmpty()) { "Choice criteria must not be empty." }
        require(options.size <= MAX_OPTIONS) { "Choice criteria may have at most $MAX_OPTIONS options, got ${options.size}." }
        require(options.toSet().size == options.size) { "Choice options must be distinct." }
        val labels = options.map(label)
        require(labels.toSet().size == labels.size) { "Choice labels must be distinct, got $labels." }
    }

    private companion object {
        const val MAX_OPTIONS = 255
    }
}

internal class ScoreQuestion<T : Any>(
    val instructions: String,
    val levels: List<T>,
    val description: (T) -> String,
) {
    init {
        require(levels.size in MIN_LEVELS..MAX_LEVELS) {
            "Score levels must be between $MIN_LEVELS and $MAX_LEVELS, got ${levels.size}."
        }
        require(levels.toSet().size == levels.size) { "Score levels must be distinct." }
    }

    private companion object {
        const val MIN_LEVELS = 2
        const val MAX_LEVELS = 10
    }
}
