package net.thunderbird.feature.taskmail.internal.ui.detail.component

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.thunderbird.feature.taskmail.internal.domain.model.TaskReplyAttachment
import net.thunderbird.feature.taskmail.internal.ui.detail.TaskPendingQuestionChoiceUi

@Immutable
internal data class TaskReplyComposerState(
    val draftText: String,
    val isSending: Boolean,
    val sendError: String? = null,
    val canReply: Boolean,
    val canSendReply: Boolean,
    val canQueryStatus: Boolean,
    val replyUnavailableReason: String? = null,
    val quickAnswerChoices: ImmutableList<TaskPendingQuestionChoiceUi> = persistentListOf(),
    val replyAttachments: ImmutableList<TaskReplyAttachment> = persistentListOf(),
    val requiresStructuredReply: Boolean,
    val requiresResumeBeforeReply: Boolean = false,
    val isQuestionReply: Boolean = false,
    val replyLabel: String,
    val replySupportingText: String,
) {
    val sendButtonText: String
        get() = when {
            isSending -> "Sending..."
            requiresResumeBeforeReply && requiresStructuredReply -> "Resume and send answers"
            requiresResumeBeforeReply -> "Resume and send"
            requiresStructuredReply -> "Send answers"
            isQuestionReply -> "Answer and send"
            else -> "Send reply"
        }
}
