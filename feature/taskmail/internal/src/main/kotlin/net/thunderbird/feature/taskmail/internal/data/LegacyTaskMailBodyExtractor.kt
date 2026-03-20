package net.thunderbird.feature.taskmail.internal.data

@Suppress("TooManyFunctions")
internal class LegacyTaskMailBodyExtractor {

    fun extractUserMessageText(bodyText: String): String {
        val normalizedText = normalizeText(bodyText)
        val textWithoutCapsules = stripCapsules(normalizedText)
        val delta = extractReplyDelta(textWithoutCapsules)

        return if (delta.isNotBlank()) {
            delta
        } else {
            textWithoutCapsules
        }
    }

    fun extractSystemMessageText(bodyText: String): String {
        val text = stripCapsules(normalizeText(bodyText))
        val extractedText = listOfNotNull(
            text.takeUnless(String::isBlank)?.let(::extractReplyText),
            extractSummaryText(text),
            extractNarrativeAfterStructuredHeader(text),
            extractIntroText(text),
            extractQuestionText(text),
            extractLastSummaryText(text),
            extractStatusText(text),
            text.takeUnless(String::isBlank)?.let(::stripStructuredMetadata),
            text.takeUnless(String::isBlank)?.let(String::trim),
        ).firstOrNull()

        return extractedText.orEmpty()
    }

    private fun extractReplyText(text: String): String? {
        return REPLY_BLOCK_REGEX.find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.let(::extractReplyDelta)
            ?.takeIf(String::isNotEmpty)
    }

    private fun extractSummaryText(text: String): String? {
        return SUMMARY_LINE_REGEX.find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
    }

    private fun extractIntroText(text: String): String? {
        val lines = text.lines()
        val statusLineIndex = lines.indexOfFirst { it.startsWith(STATUS_PREFIX) }

        return if (statusLineIndex > 0) {
            lines.take(statusLineIndex)
                .joinToString("\n")
                .trim()
                .takeIf(String::isNotEmpty)
        } else {
            null
        }
    }

    private fun extractQuestionText(text: String): String? {
        val question = QUESTION_LINE_REGEX.find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: return null
        val choices = CHOICES_LINE_REGEX.find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()

        return buildString {
            append("Question: ")
            append(question)
            if (!choices.isNullOrEmpty()) {
                append("\nChoices: ")
                append(choices)
            }
        }
    }

    @Suppress("ReturnCount")
    private fun extractNarrativeAfterStructuredHeader(text: String): String? {
        val lines = text.lines()
        val firstContentIndex = lines.indexOfFirst { it.isNotBlank() }
        if (firstContentIndex < 0) return null

        var structuredIndex = firstContentIndex
        while (structuredIndex < lines.size && isStructuredMetadataLine(lines[structuredIndex])) {
            structuredIndex += 1
        }

        if (structuredIndex == firstContentIndex) return null

        val narrative = lines
            .drop(structuredIndex)
            .filterNot(::isStructuredMetadataLine)
            .joinToString(separator = "\n")
            .trim()

        return narrative.takeIf(String::isNotEmpty)
    }

    private fun extractLastSummaryText(text: String): String? {
        return LAST_SUMMARY_LINE_REGEX.find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
    }

    private fun extractStatusText(text: String): String? {
        return STATUS_LINE_REGEX.find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let { status -> "Status: $status" }
    }

    private fun extractReplyDelta(text: String): String {
        val cutPoint = QUOTE_SPLIT_PATTERNS
            .asSequence()
            .mapNotNull { regex -> regex.find(text)?.range?.first }
            .minOrNull()

        return if (cutPoint != null) {
            text.substring(0, cutPoint).trim()
        } else {
            text.trim()
        }
    }

    private fun stripCapsules(text: String): String {
        return text
            .replace(STATE_BLOCK_REGEX, "")
            .replace(QUESTION_BLOCK_REGEX, "")
            .trim()
    }

    private fun stripStructuredMetadata(text: String): String {
        return text.lines()
            .filterNot(::isStructuredMetadataLine)
            .joinToString(separator = "\n")
            .trim()
    }

    private fun isStructuredMetadataLine(line: String): Boolean {
        val normalizedLine = line.trim()
        if (normalizedLine.isEmpty()) return false

        return STRUCTURED_METADATA_PREFIXES.any { prefix ->
            normalizedLine.startsWith(prefix, ignoreCase = true)
        }
    }

    private fun normalizeText(value: String): String {
        return value
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace('\u00A0', ' ')
            .let(::normalizeStructuredBreaks)
            .trim()
    }

    private fun normalizeStructuredBreaks(text: String): String {
        val textWithCapsuleBreaks = TASK_CAPSULE_MARKERS.fold(text) { current, marker ->
            current.replace(
                Regex("\\s*${Regex.escape(marker)}\\s*"),
                "\n$marker\n",
            )
        }

        return textWithCapsuleBreaks
            .replace(INLINE_QUOTE_MARKER_BREAK_REGEX, "\n")
            .replace(STRUCTURED_FIELD_BREAK_REGEX, "\n")
            .replace(MULTIPLE_BLANK_LINES_REGEX, "\n\n")
    }

    private companion object {
        private const val STATUS_PREFIX = "Status:"

        private val STATE_BLOCK_REGEX = Regex(
            pattern = "---TASK-STATE-BEGIN---.*?---TASK-STATE-END---",
            options = setOf(RegexOption.DOT_MATCHES_ALL),
        )
        private val QUESTION_BLOCK_REGEX = Regex(
            pattern = "---TASK-QUESTION-BEGIN---.*?---TASK-QUESTION-END---",
            options = setOf(RegexOption.DOT_MATCHES_ALL),
        )

        private val SUMMARY_LINE_REGEX = Regex("(?m)^Summary:\\s*(.+)$")
        private val REPLY_BLOCK_REGEX = Regex("(?is)(?:^|\\n)Reply:\\s*(.+)")
        private val QUESTION_LINE_REGEX = Regex("(?m)^Question:\\s*(.+)$")
        private val CHOICES_LINE_REGEX = Regex("(?m)^Choices:\\s*(.+)$")
        private val STATUS_LINE_REGEX = Regex("(?m)^Status:\\s*(.+)$")
        private val LAST_SUMMARY_LINE_REGEX = Regex("(?im)^last_summary:\\s*(.+)$")

        private val QUOTE_SPLIT_PATTERNS = listOf(
            Regex("(?im)^On .+wrote:\\s*$"),
            Regex("(?im)\\s+On .+wrote:\\s*$"),
            Regex("(?im)^\\u56de\\u590d\\s*[:\\uFF1A]\\s*$"),
            Regex("(?im)^\\u7B54\\u590D\\s*[:\\uFF1A]\\s*$"),
            Regex("(?im)^-----Original Message-----\\s*$"),
            Regex("(?im)\\s+-----Original Message-----\\s*"),
            Regex("(?im)^From:\\s+.+\\nSent:\\s+.+\\nTo:\\s+.+\\nSubject:\\s+.+$"),
            Regex("(?im)^-----\\u539F\\u59CB\\u90AE\\u4EF6-----\\s*$"),
            Regex("(?im)\\s+-----\\u539F\\u59CB\\u90AE\\u4EF6-----\\s*"),
            Regex("(?im)^---\\u539F\\u59CB\\u90AE\\u4EF6---\\s*$"),
            Regex("(?im)\\s+---\\u539F\\u59CB\\u90AE\\u4EF6---\\s*"),
            Regex(
                "(?im)^\\u53D1\\u4EF6\\u4EBA\\s*[:\\uFF1A]\\s+.+\\n" +
                    "\\u53D1\\u9001\\u65F6\\u95F4\\s*[:\\uFF1A]\\s+.+\\n" +
                    "\\u6536\\u4EF6\\u4EBA\\s*[:\\uFF1A]\\s+.+\\n" +
                    "\\u4E3B\\u9898\\s*[:\\uFF1A]\\s+.+$",
            ),
            Regex("(?im)^From:\\s+.+$"),
            Regex("(?im)^\\u53D1\\u4EF6\\u4EBA\\s*[:\\uFF1A]\\s+.+$"),
            Regex("(?m)^>+"),
        )

        private val TASK_CAPSULE_MARKERS = listOf(
            "---TASK-STATE-BEGIN---",
            "---TASK-STATE-END---",
            "---TASK-QUESTION-BEGIN---",
            "---TASK-QUESTION-END---",
        )

        private val STRUCTURED_METADATA_PREFIXES = listOf(
            "Status:",
            "Session ID:",
            "Thread ID:",
            "Task ID:",
            "Backend:",
            "Repo:",
            "Workdir:",
            "Summary:",
            "Reply:",
            "Question Set ID:",
            "Question ID:",
            "Question:",
            "Choices:",
            "Received Answers:",
            "Allowed values:",
            "Answers:",
            "thread_id:",
            "workspace_id:",
            "session_id:",
            "session_name:",
            "task_id:",
            "backend:",
            "repo_path:",
            "workdir:",
            "mode:",
            "status:",
            "last_summary:",
            "---TASK-STATE-BEGIN---",
            "---TASK-STATE-END---",
            "---TASK-QUESTION-BEGIN---",
            "---TASK-QUESTION-END---",
        )

        private val STRUCTURED_FIELD_BREAK_REGEX = Regex(
            pattern = "[ \\t]+(?=(?:${
                STRUCTURED_METADATA_PREFIXES.joinToString(separator = "|") { Regex.escape(it) }
            }))",
        )
        private val QUOTE_MARKER_PREFIXES = listOf(
            "-----Original Message-----",
            "-----\u539F\u59CB\u90AE\u4EF6-----",
            "---\u539F\u59CB\u90AE\u4EF6---",
            "From:",
            "Sent:",
            "To:",
            "Subject:",
            "\u53D1\u4EF6\u4EBA:",
            "\u53D1\u9001\u65F6\u95F4:",
            "\u6536\u4EF6\u4EBA:",
            "\u4E3B\u9898:",
        )
        private val INLINE_QUOTE_MARKER_BREAK_REGEX = Regex(
            pattern = "[ \\t]+(?=(?:${
                QUOTE_MARKER_PREFIXES.joinToString(separator = "|") { Regex.escape(it) }
            }))",
        )
        private val MULTIPLE_BLANK_LINES_REGEX = Regex("\\n{3,}")
    }
}
