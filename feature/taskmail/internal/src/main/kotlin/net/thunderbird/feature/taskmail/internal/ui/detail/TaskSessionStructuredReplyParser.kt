package net.thunderbird.feature.taskmail.internal.ui.detail

import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailSessionQuestionAnswer

internal fun validateStructuredReply(
    draftText: String,
    pendingQuestions: List<TaskPendingQuestionUi>,
): StructuredReplyValidationResult {
    return parseStructuredReplyAnswers(draftText, pendingQuestions)
        .fold(
            onSuccess = { StructuredReplyValidationResult.Valid },
            onFailure = { error ->
                when (error) {
                    is StructuredReplyParseException -> error.validationResult
                    else -> StructuredReplyValidationResult.MissingRequiredAnswers
                }
            },
        )
}

internal fun parseStructuredReplyAnswers(
    draftText: String,
    pendingQuestions: List<TaskPendingQuestionUi>,
): Result<List<TaskMailSessionQuestionAnswer>> {
    if (pendingQuestions.isEmpty()) {
        return Result.failure(
            StructuredReplyParseException(StructuredReplyValidationResult.MissingRequiredAnswers),
        )
    }

    val questionsById = pendingQuestions.associateBy(TaskPendingQuestionUi::questionId)
    val lines = structuredReplyLines(draftText)
    val answers = linkedMapOf<String, TaskMailSessionQuestionAnswer>()

    var index = 0
    while (index < lines.size) {
        when (
            val parseResult = parseStructuredReplyLine(
                lines = lines,
                index = index,
                questionsById = questionsById,
            )
        ) {
            is StructuredReplyLineParseResult.Skip -> index = parseResult.nextIndex
            is StructuredReplyLineParseResult.ParsedAnswer -> {
                answers[parseResult.questionAnswer.questionId] = parseResult.questionAnswer
                index = parseResult.nextIndex
            }

            is StructuredReplyLineParseResult.Invalid -> {
                return Result.failure(StructuredReplyParseException(parseResult.result))
            }
        }
    }

    val hasMissingRequiredAnswers = pendingQuestions.any { question ->
        question.isRequired && answers[question.questionId]?.value.isNullOrBlank()
    }
    if (hasMissingRequiredAnswers) {
        return Result.failure(
            StructuredReplyParseException(StructuredReplyValidationResult.MissingRequiredAnswers),
        )
    }

    return Result.success(answers.values.toList())
}

private fun structuredReplyLines(draftText: String): List<String> {
    return draftText.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toList()
}

private fun parseStructuredReplyLine(
    lines: List<String>,
    index: Int,
    questionsById: Map<String, TaskPendingQuestionUi>,
): StructuredReplyLineParseResult {
    val line = lines[index]
    val parsedAnswer = if (line.equals("Answers:", ignoreCase = true)) {
        null
    } else {
        parseStructuredAnswerLine(line)
    }

    return if (parsedAnswer == null) {
        StructuredReplyLineParseResult.Skip(nextIndex = index + 1)
    } else {
        val (key, value) = parsedAnswer
        if (key.equals(QUESTION_ID_KEY, ignoreCase = true)) {
            parseTwoLineStructuredReply(
                lines = lines,
                index = index,
                questionId = value,
                questionsById = questionsById,
            )
        } else {
            parseSingleLineStructuredReply(
                index = index,
                questionId = key,
                value = value,
                questionsById = questionsById,
            )
        }
    }
}

private fun parseSingleLineStructuredReply(
    index: Int,
    questionId: String,
    value: String,
    questionsById: Map<String, TaskPendingQuestionUi>,
): StructuredReplyLineParseResult {
    val question = questionsById[questionId]
        ?: return StructuredReplyLineParseResult.Invalid(StructuredReplyValidationResult.UnknownQuestionId)

    return if (question.hasValidStructuredAnswer(value)) {
        StructuredReplyLineParseResult.ParsedAnswer(
            questionAnswer = TaskMailSessionQuestionAnswer(
                questionId = question.questionId,
                value = value,
            ),
            nextIndex = index + 1,
        )
    } else {
        StructuredReplyLineParseResult.Invalid(StructuredReplyValidationResult.InvalidChoiceValue)
    }
}

private fun parseTwoLineStructuredReply(
    lines: List<String>,
    index: Int,
    questionId: String,
    questionsById: Map<String, TaskPendingQuestionUi>,
): StructuredReplyLineParseResult {
    val question = questionsById[questionId]
    val nextLine = lines.getOrNull(index + 1)
    val answerValue = nextLine
        ?.takeIf { candidate ->
            candidate.isNotBlank() &&
                !candidate.equals("Answers:", ignoreCase = true) &&
                !candidate.isStructuredAnswerDeclaration(questionsById.keys)
        }
    return when {
        question == null -> {
            StructuredReplyLineParseResult.Invalid(StructuredReplyValidationResult.UnknownQuestionId)
        }

        answerValue == null -> {
            StructuredReplyLineParseResult.Invalid(StructuredReplyValidationResult.MissingRequiredAnswers)
        }

        question.hasValidStructuredAnswer(answerValue) -> {
            StructuredReplyLineParseResult.ParsedAnswer(
                questionAnswer = TaskMailSessionQuestionAnswer(
                    questionId = question.questionId,
                    value = answerValue,
                ),
                nextIndex = index + 2,
            )
        }

        else -> {
            StructuredReplyLineParseResult.Invalid(StructuredReplyValidationResult.InvalidChoiceValue)
        }
    }
}

private fun String.isStructuredAnswerDeclaration(
    pendingQuestionIds: Set<String>,
): Boolean {
    val parsedAnswer = parseStructuredAnswerLine(this) ?: return false
    val (key, _) = parsedAnswer

    return key.equals(QUESTION_ID_KEY, ignoreCase = true) || key in pendingQuestionIds
}

private fun TaskPendingQuestionUi.hasValidStructuredAnswer(value: String): Boolean {
    if (value.isBlank()) {
        return false
    }

    return choices.isEmpty() || choices.any { choice -> choice.value == value }
}

private fun parseStructuredAnswerLine(line: String): Pair<String, String>? {
    val separatorIndex = line.indexOfFirst { it == ':' || it == '\uFF1A' }
    val key = line
        .takeIf { separatorIndex > 0 }
        ?.substring(0, separatorIndex)
        ?.trim()
        .orEmpty()
    val value = line
        .takeIf { separatorIndex > 0 }
        ?.substring(separatorIndex + 1)
        ?.trim()

    return value
        ?.takeIf { key.isNotBlank() }
        ?.let { key to it }
}

internal enum class StructuredReplyValidationResult {
    Valid,
    MissingRequiredAnswers,
    UnknownQuestionId,
    InvalidChoiceValue,
}

internal class StructuredReplyParseException(
    val validationResult: StructuredReplyValidationResult,
) : IllegalArgumentException(validationResult.name)

private sealed interface StructuredReplyLineParseResult {
    data class Skip(
        val nextIndex: Int,
    ) : StructuredReplyLineParseResult

    data class ParsedAnswer(
        val questionAnswer: TaskMailSessionQuestionAnswer,
        val nextIndex: Int,
    ) : StructuredReplyLineParseResult

    data class Invalid(
        val result: StructuredReplyValidationResult,
    ) : StructuredReplyLineParseResult
}

private const val QUESTION_ID_KEY = "question_id"
