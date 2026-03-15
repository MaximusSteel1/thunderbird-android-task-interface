package net.thunderbird.feature.taskmail.internal.data

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
            extractIntroText(text),
            extractQuestionText(text),
            extractStatusText(text),
            text.takeUnless(String::isBlank),
        ).firstOrNull()

        return extractedText.orEmpty()
    }

    private fun extractReplyText(text: String): String? {
        val replyIndex = text.indexOf(REPLY_MARKER)
        return if (replyIndex >= 0) {
            text.substring(replyIndex + REPLY_MARKER.length).trim()
        } else {
            null
        }
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

    private fun normalizeText(value: String): String {
        return value
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace('\u00A0', ' ')
            .trim()
    }

    private companion object {
        private const val REPLY_MARKER = "\nReply:\n"
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
        private val QUESTION_LINE_REGEX = Regex("(?m)^Question:\\s*(.+)$")
        private val CHOICES_LINE_REGEX = Regex("(?m)^Choices:\\s*(.+)$")
        private val STATUS_LINE_REGEX = Regex("(?m)^Status:\\s*(.+)$")

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
            Regex("(?m)^>+"),
        )
    }
}
