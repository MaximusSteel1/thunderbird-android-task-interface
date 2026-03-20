package net.thunderbird.feature.taskmail.internal.domain.parser

internal class TaskQuestionCapsuleParser {

    fun parseAll(text: String): List<TaskQuestionCapsule> {
        val parsedBlocks = questionBlockRegex.findAll(text)
            .mapNotNull { match ->
                parseBlock(match.groupValues[1].trim())
            }
            .toList()
        val explicitQuestionSetIds = parsedBlocks
            .mapNotNull(TaskQuestionCapsule::questionSetId)
            .filter(String::isNotBlank)
            .distinct()
        val sharedQuestionSetId = explicitQuestionSetIds.singleOrNull()

        return if (parsedBlocks.isEmpty() || explicitQuestionSetIds.size > 1) {
            emptyList()
        } else if (sharedQuestionSetId != null) {
            parsedBlocks.map { capsule ->
                capsule.takeIf { it.questionSetId == sharedQuestionSetId }
                    ?: capsule.copy(questionSetId = sharedQuestionSetId)
            }
        } else {
            parsedBlocks
        }
    }

    fun parse(text: String): TaskQuestionCapsule? {
        return parseAll(text).lastOrNull()
    }

    private fun parseBlock(content: String): TaskQuestionCapsule? {
        val fields = parseFields(content)
        return fields["question_id"]?.let { questionId ->
            fields["question_text"]?.let { questionText ->
                val choices = fields["choices"]
                    ?.split('|')
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    .orEmpty()
                val questionSetId = fields["question_set_id"]?.takeIf { it.isNotBlank() }
                val questionType = fields["question_type"]
                    ?.takeIf { it.isNotBlank() }
                    ?: choices.takeIf { it.isNotEmpty() }?.let { "single_choice" }
                val required = fields["required"]?.let(::parseRequiredFlag) ?: true
                val choiceLabels = parseChoiceLabels(fields["choice_labels"])

                TaskQuestionCapsule(
                    questionId = questionId,
                    questionText = questionText,
                    choices = choices,
                    questionSetId = questionSetId,
                    questionType = questionType,
                    required = required,
                    choiceLabels = choiceLabels,
                )
            }
        }
    }

    private fun parseFields(content: String): Map<String, String> {
        val fields = linkedMapOf<String, String>()
        var currentKey: String? = null

        normalizeFieldBreaks(content).lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { line ->
                val separatorIndex = line.indexOf(':')

                if (separatorIndex > 0) {
                    val key = line.substring(0, separatorIndex).trim()
                    val value = normalizeWhitespace(line.substring(separatorIndex + 1))
                    fields[key] = value
                    currentKey = key
                } else if (currentKey != null) {
                    val previousValue = fields.getValue(currentKey)
                    fields[currentKey] = normalizeWhitespace("$previousValue $line")
                }
            }

        return fields
    }

    private fun normalizeFieldBreaks(content: String): String {
        return content.replace(questionFieldBreakRegex, "\n")
    }

    private fun normalizeWhitespace(value: String): String {
        return value.replace(whitespaceRegex, " ").trim()
    }

    private fun parseRequiredFlag(value: String): Boolean {
        return when (value.trim().lowercase()) {
            "false", "no", "0" -> false
            else -> true
        }
    }

    private fun parseChoiceLabels(value: String?): Map<String, String> {
        return value
            ?.split('|')
            ?.mapNotNull { entry ->
                val separatorIndex = entry.indexOf('=')
                if (separatorIndex <= 0) return@mapNotNull null

                val key = entry.substring(0, separatorIndex).trim()
                val label = normalizeWhitespace(entry.substring(separatorIndex + 1))
                if (key.isEmpty() || label.isEmpty()) return@mapNotNull null

                key to label
            }
            ?.toMap()
            .orEmpty()
    }

    private companion object {
        val questionBlockRegex = Regex(
            pattern = "---TASK-QUESTION-BEGIN---(.*?)---TASK-QUESTION-END---",
            options = setOf(RegexOption.DOT_MATCHES_ALL),
        )
        private val questionFieldBreakRegex = Regex(
            pattern = "[ \\t]+(?=(?:${
                listOf(
                    "question_set_id:",
                    "question_id:",
                    "question_type:",
                    "required:",
                    "question_text:",
                    "choices:",
                    "choice_labels:",
                ).joinToString(separator = "|") { Regex.escape(it) }
            }))",
            options = setOf(RegexOption.IGNORE_CASE),
        )
        val whitespaceRegex = Regex("\\s+")
    }
}
