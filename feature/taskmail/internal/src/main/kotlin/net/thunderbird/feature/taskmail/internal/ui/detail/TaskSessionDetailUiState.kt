package net.thunderbird.feature.taskmail.internal.ui.detail

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.thunderbird.feature.taskmail.internal.domain.model.TaskBodyRenderMode
import net.thunderbird.feature.taskmail.internal.domain.model.TaskRichTextDocument
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionReplyContext

@Immutable
internal data class TaskSessionDetailUiState(
    val sessionName: String,
    val backend: String,
    val status: String,
    val repoPath: String,
    val workdir: String? = null,
    val lastSummary: String? = null,
    val pendingQuestions: ImmutableList<TaskPendingQuestionUi> = persistentListOf(),
    val quickAnswerChoices: ImmutableList<TaskPendingQuestionChoiceUi> = persistentListOf(),
    val requiresStructuredReply: Boolean = false,
    val requiresResumeBeforeReply: Boolean = false,
    val structuredReplyTemplate: String? = null,
    val replyLabel: String = "Reply to this task",
    val replySupportingText: String = "Send a plain-text reply or use a quick TaskMail action.",
    val replyContext: TaskSessionReplyContext? = null,
    val canReply: Boolean = false,
    val canQueryStatus: Boolean = false,
    val replyUnavailableReason: String? = null,
    val timeline: ImmutableList<TaskTimelineItemUi> = persistentListOf(),
) {
    fun canSendReply(
        draftText: String,
        attachmentCount: Int = 0,
    ): Boolean {
        return if (!canReply) {
            false
        } else if (requiresStructuredReply) {
            validateStructuredReply(
                draftText = draftText,
                pendingQuestions = pendingQuestions,
            ) == StructuredReplyValidationResult.Valid
        } else {
            draftText.isNotBlank() || attachmentCount > 0
        }
    }

    fun canUseQuickAnswer(choice: String): Boolean {
        return !requiresStructuredReply && quickAnswerChoices.any { it.value == choice }
    }
}

@Immutable
internal data class TaskPendingQuestionUi(
    val questionId: String,
    val questionText: String,
    val choices: ImmutableList<TaskPendingQuestionChoiceUi> = persistentListOf(),
    val isRequired: Boolean = true,
)

@Immutable
internal data class TaskPendingQuestionChoiceUi(
    val value: String,
    val label: String = value,
)

@Immutable
internal data class TaskTimelineItemUi(
    val id: String,
    val timestamp: Long,
    val direction: String,
    val statusLabel: String? = null,
    val summary: String? = null,
    val plainText: String,
    val renderMode: TaskBodyRenderMode = TaskBodyRenderMode.PlainTextOnly,
    val richDocument: TaskRichTextDocument? = null,
    val attachments: ImmutableList<TaskTimelineAttachmentUi> = persistentListOf(),
)

@Immutable
internal data class TaskTimelineAttachmentUi(
    val id: String,
    val displayName: String,
    val contentType: String? = null,
    val sizeBytes: Long? = null,
    val isInline: Boolean = false,
    val isImage: Boolean = false,
    val internalUriString: String? = null,
    val isActionAvailable: Boolean = false,
)

private fun validateStructuredReply(
    draftText: String,
    pendingQuestions: List<TaskPendingQuestionUi>,
): StructuredReplyValidationResult {
    if (pendingQuestions.isEmpty()) {
        return StructuredReplyValidationResult.MissingRequiredAnswers
    }

    val questionsById = pendingQuestions.associateBy(TaskPendingQuestionUi::questionId)
    val lines = structuredReplyLines(draftText)
    val answers = linkedMapOf<String, String>()
    var validationResult = StructuredReplyValidationResult.Valid

    var index = 0
    while (index < lines.size && validationResult == StructuredReplyValidationResult.Valid) {
        when (
            val parseResult = parseStructuredReplyLine(
                lines = lines,
                index = index,
                questionsById = questionsById,
            )
        ) {
            is StructuredReplyLineParseResult.Skip -> {
                index = parseResult.nextIndex
            }

            is StructuredReplyLineParseResult.ParsedAnswer -> {
                answers[parseResult.questionId] = parseResult.value
                index = parseResult.nextIndex
            }

            is StructuredReplyLineParseResult.Invalid -> {
                validationResult = parseResult.result
            }
        }
    }

    val hasMissingRequiredAnswers = pendingQuestions.any { question ->
        question.isRequired && answers[question.questionId].isNullOrBlank()
    }

    return if (validationResult != StructuredReplyValidationResult.Valid) {
        validationResult
    } else if (hasMissingRequiredAnswers) {
        StructuredReplyValidationResult.MissingRequiredAnswers
    } else {
        StructuredReplyValidationResult.Valid
    }
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
            questionId = question.questionId,
            value = value,
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
                questionId = question.questionId,
                value = answerValue,
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

private enum class StructuredReplyValidationResult {
    Valid,
    MissingRequiredAnswers,
    UnknownQuestionId,
    InvalidChoiceValue,
}

private sealed interface StructuredReplyLineParseResult {
    data class Skip(
        val nextIndex: Int,
    ) : StructuredReplyLineParseResult

    data class ParsedAnswer(
        val questionId: String,
        val value: String,
        val nextIndex: Int,
    ) : StructuredReplyLineParseResult

    data class Invalid(
        val result: StructuredReplyValidationResult,
    ) : StructuredReplyLineParseResult
}

private const val QUESTION_ID_KEY = "question_id"
