package net.thunderbird.feature.taskmail.internal.ui.component

internal sealed interface TaskCodeLocatorSegment {
    val text: String

    data class Plain(
        override val text: String,
    ) : TaskCodeLocatorSegment

    data class Locator(
        val rawText: String,
        val displayText: String,
    ) : TaskCodeLocatorSegment {
        override val text: String = rawText
    }
}

internal object TaskCodeLocatorParser {
    fun parse(text: String): List<TaskCodeLocatorSegment> {
        if (text.isEmpty()) return listOf(TaskCodeLocatorSegment.Plain(text))

        val segments = mutableListOf<TaskCodeLocatorSegment>()
        var currentIndex = 0

        while (currentIndex < text.length) {
            val match = nextReferenceMatch(text = text, startIndex = currentIndex) ?: break
            val startIndex = match.startIndex
            if (startIndex > currentIndex) {
                segments += TaskCodeLocatorSegment.Plain(
                    text = text.substring(currentIndex, startIndex),
                )
            }

            val rawText = match.value
            segments += TaskCodeLocatorSegment.Locator(
                rawText = rawText,
                displayText = match.displayText,
            )
            currentIndex = match.endIndexExclusive
        }

        if (currentIndex < text.length) {
            segments += TaskCodeLocatorSegment.Plain(
                text = text.substring(currentIndex),
            )
        }

        return if (segments.isEmpty()) {
            listOf(TaskCodeLocatorSegment.Plain(text))
        } else {
            segments
        }
    }

    private fun nextReferenceMatch(
        text: String,
        startIndex: Int,
    ): ReferenceMatch? {
        val markdownMatch = markdownReferenceRegex.find(text, startIndex)
            ?.toMarkdownReferenceMatch()
        val rawPathMatch = rawPathRegex.find(text, startIndex)
            ?.toRawPathReferenceMatch()

        return listOfNotNull(markdownMatch, rawPathMatch)
            .minWithOrNull(compareBy<ReferenceMatch>({ it.startIndex }, { it.priority }))
    }
}

private data class ReferenceMatch(
    val startIndex: Int,
    val endIndexExclusive: Int,
    val value: String,
    val displayText: String,
    val priority: Int,
)

private fun MatchResult.toMarkdownReferenceMatch(): ReferenceMatch {
    val label = groups[1]?.value.orEmpty()
    val target = groups[2]?.value.orEmpty()

    return ReferenceMatch(
        startIndex = range.first,
        endIndexExclusive = range.last + 1,
        value = value,
        displayText = label.toReferenceDisplayText(
            fallbackTarget = target,
        ),
        priority = 0,
    )
}

private fun MatchResult.toRawPathReferenceMatch(): ReferenceMatch {
    val path = groups[1]?.value.orEmpty()
    val suffix = groups[2]?.value.orEmpty()

    return ReferenceMatch(
        startIndex = range.first,
        endIndexExclusive = range.last + 1,
        value = value,
        displayText = path.toCollapsedPathTail() + suffix,
        priority = 1,
    )
}

private fun String.toReferenceDisplayText(fallbackTarget: String): String {
    val trimmed = trim()
    if (trimmed.isBlank()) {
        return fallbackTarget.toCollapsedPathDisplayText()
    }

    return if (rawPathDisplayRegex.matches(trimmed)) {
        trimmed.toCollapsedPathDisplayText()
    } else {
        trimmed
    }
}

private fun String.toCollapsedPathDisplayText(): String {
    val match = rawPathDisplayRegex.matchEntire(this) ?: return this
    val path = match.groups[1]?.value.orEmpty()
    val suffix = match.groups[2]?.value.orEmpty()

    return path.toCollapsedPathTail() + suffix
}

private fun String.toCollapsedPathTail(): String {
    return removePrefix("file:///")
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .ifBlank { this }
}

private const val ABSOLUTE_PATH_PATTERN =
    """(?:file:///|/)?[A-Za-z]:(?:[/\\][^\s:()\[\]{}<>"',]+)+"""
private const val REFERENCE_SUFFIX_PATTERN =
    """(?:#L\d+(?:C\d+)?|:\d+(?::\d+)?)?"""

private val markdownReferenceRegex = Regex(
    pattern = """\[([^\]]+)]\((($ABSOLUTE_PATH_PATTERN)($REFERENCE_SUFFIX_PATTERN))\)""",
)

private val rawPathRegex = Regex(
    pattern = """($ABSOLUTE_PATH_PATTERN)($REFERENCE_SUFFIX_PATTERN)""",
)

private val rawPathDisplayRegex = Regex(
    pattern = """^($ABSOLUTE_PATH_PATTERN)($REFERENCE_SUFFIX_PATTERN)$""",
)
