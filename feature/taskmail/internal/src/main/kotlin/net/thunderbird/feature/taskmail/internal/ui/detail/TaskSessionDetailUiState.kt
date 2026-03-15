package net.thunderbird.feature.taskmail.internal.ui.detail

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
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
    val quickAnswerChoices: ImmutableList<String> = persistentListOf(),
    val requiresStructuredReply: Boolean = false,
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
            attachmentCount > 0 ||
                hasStructuredAnswer(
                    draftText = draftText,
                    pendingQuestionIds = pendingQuestions.map(TaskPendingQuestionUi::questionId).toSet(),
                )
        } else {
            draftText.isNotBlank() || attachmentCount > 0
        }
    }

    fun canUseQuickAnswer(choice: String): Boolean {
        return !requiresStructuredReply && choice in quickAnswerChoices
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
    val isActionAvailable: Boolean = false,
)

private fun hasStructuredAnswer(
    draftText: String,
    pendingQuestionIds: Set<String>,
): Boolean {
    val lines = draftText.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toList()

    return pendingQuestionIds.isNotEmpty() &&
        lines.indices.any { index ->
            val line = lines[index]
            !line.equals("Answers:", ignoreCase = true) &&
                line.containsStructuredAnswer(
                    nextLine = lines.getOrNull(index + 1),
                    pendingQuestionIds = pendingQuestionIds,
                )
        }
}

private fun String.containsStructuredAnswer(
    nextLine: String?,
    pendingQuestionIds: Set<String>,
): Boolean {
    val parsedAnswer = parseStructuredAnswerLine(this) ?: return false
    val (key, value) = parsedAnswer

    val matchesDirectAnswer = key in pendingQuestionIds && value.isNotBlank()
    val matchesTwoLineAnswer = key.equals(QUESTION_ID_KEY, ignoreCase = true) &&
        value in pendingQuestionIds &&
        !nextLine.isNullOrBlank()

    return matchesDirectAnswer || matchesTwoLineAnswer
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

private const val QUESTION_ID_KEY = "question_id"
