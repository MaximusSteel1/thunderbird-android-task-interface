package net.thunderbird.feature.taskmail.internal.ui.detail.component

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.thunderbird.feature.taskmail.internal.domain.model.TaskReplyAttachment

@Immutable
internal data class TaskReplyComposerState(
    val draftText: String,
    val isSending: Boolean,
    val sendError: String? = null,
    val canReply: Boolean,
    val canSendReply: Boolean,
    val canQueryStatus: Boolean,
    val replyUnavailableReason: String? = null,
    val quickAnswerChoices: ImmutableList<String> = persistentListOf(),
    val replyAttachments: ImmutableList<TaskReplyAttachment> = persistentListOf(),
    val requiresStructuredReply: Boolean,
    val replyLabel: String,
    val replySupportingText: String,
) {
    val sendButtonText: String
        get() = when {
            isSending -> "Sending..."
            requiresStructuredReply -> "Send answers"
            else -> "Send reply"
        }
}
