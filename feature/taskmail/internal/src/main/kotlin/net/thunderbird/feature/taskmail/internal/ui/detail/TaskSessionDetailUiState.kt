package net.thunderbird.feature.taskmail.internal.ui.detail

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.thunderbird.feature.taskmail.internal.domain.model.TaskBodyRenderMode
import net.thunderbird.feature.taskmail.internal.domain.model.TaskRichTextDocument
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionReplyContext

@Immutable
internal data class TaskSessionDetailUiState(
    val sessionId: String? = null,
    val workspaceId: String? = null,
    val sessionName: String,
    val backend: String,
    val status: String,
    val repoPath: String,
    val workdir: String? = null,
    val lastSummary: String? = null,
    val recentContext: TaskRecentContextUi? = null,
    val resultSummary: TaskResultSummaryUi? = null,
    val artifacts: ImmutableList<TaskSessionArtifactUi> = persistentListOf(),
    val historyPreview: ImmutableList<TaskHistoryRoundUi> = persistentListOf(),
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
internal data class TaskRecentContextUi(
    val latestUserMessage: String? = null,
    val latestAssistantMessage: String? = null,
    val waitingForUserText: String? = null,
)

@Immutable
internal data class TaskResultSummaryUi(
    val headline: String,
    val supportingText: String? = null,
    val statusLabel: String,
    val effectiveExecutionSummary: String? = null,
)

@Immutable
internal data class TaskSessionArtifactUi(
    val id: String,
    val title: String,
    val supportingText: String? = null,
)

@Immutable
internal data class TaskHistoryRoundUi(
    val id: String,
    val title: String,
    val summary: String? = null,
    val statusLabel: String? = null,
    val messagePreview: String? = null,
)

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
