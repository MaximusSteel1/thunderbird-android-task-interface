package net.thunderbird.feature.taskmail.internal.ui.detail.component

import android.content.Context
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonFilled
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonOutlined
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonText
import app.k9mail.core.ui.compose.designsystem.atom.card.CardElevated
import app.k9mail.core.ui.compose.designsystem.atom.card.CardOutlined
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodyMedium
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodySmall
import app.k9mail.core.ui.compose.designsystem.atom.text.TextLabelMedium
import app.k9mail.core.ui.compose.designsystem.atom.textfield.TextFieldOutlined
import app.k9mail.core.ui.compose.designsystem.organism.banner.inline.ErrorBannerInlineNotificationCard
import app.k9mail.core.ui.compose.designsystem.organism.banner.inline.WarningBannerInlineNotificationCard
import kotlinx.collections.immutable.ImmutableList
import net.thunderbird.core.ui.compose.designsystem.atom.icon.Icon
import net.thunderbird.core.ui.compose.designsystem.atom.icon.Icons
import net.thunderbird.core.ui.compose.theme2.MainTheme
import net.thunderbird.feature.taskmail.internal.domain.model.TaskReplyAttachment
import net.thunderbird.feature.taskmail.internal.ui.component.TaskSectionHeader
import net.thunderbird.feature.taskmail.internal.ui.detail.TaskPendingQuestionChoiceUi

@Composable
internal fun TaskReplyComposer(
    state: TaskReplyComposerState,
    onDraftChanged: (String) -> Unit,
    onPickAttachments: () -> Unit,
    onSendReply: () -> Unit,
    onStatusQuery: () -> Unit,
    onSendChoice: (String) -> Unit,
    onDismissSendError: () -> Unit,
    modifier: Modifier = Modifier,
    onRemoveAttachment: (String) -> Unit = {},
) {
    val hasQuickAnswers = state.quickAnswerChoices.isNotEmpty()

    CardElevated(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ReplyComposerHeader(state = state)
            ReplyComposerError(
                sendError = state.sendError,
                onDismissSendError = onDismissSendError,
            )

            if (!state.canReply && !state.canQueryStatus && !hasQuickAnswers) {
                ReplyUnavailableWarning(reason = state.replyUnavailableReason)
                return@CardElevated
            }

            if (!state.canReply) {
                ReplyUnavailableWarning(reason = state.replyUnavailableReason)
            } else {
                ReplyComposerInput(
                    state = state,
                    onDraftChanged = onDraftChanged,
                )
                ReplyAttachmentSection(
                    replyAttachments = state.replyAttachments,
                    isSending = state.isSending,
                    onPickAttachments = onPickAttachments,
                    onRemoveAttachment = onRemoveAttachment,
                )
            }
            ReplyComposerActions(
                state = state,
                onSendReply = onSendReply,
                onStatusQuery = onStatusQuery,
            )
            if (hasQuickAnswers) {
                QuickAnswersSection(
                    quickAnswerChoices = state.quickAnswerChoices,
                    isSending = state.isSending,
                    onSendChoice = onSendChoice,
                )
            }
        }
    }
}

@Composable
private fun ReplyComposerHeader(state: TaskReplyComposerState) {
    TaskSectionHeader(
        title = "Reply",
        supportingText = if (state.canReply) {
            state.replySupportingText
        } else {
            "Reply is currently unavailable for this session."
        },
    )
}

@Composable
private fun ReplyComposerError(
    sendError: String?,
    onDismissSendError: () -> Unit,
) {
    sendError?.let { error ->
        ErrorBannerInlineNotificationCard(
            title = "TaskMail reply failed",
            supportingText = error,
            actions = {
                ButtonText(
                    text = "Dismiss",
                    onClick = onDismissSendError,
                )
            },
        )
    }
}

@Composable
private fun ReplyUnavailableWarning(reason: String?) {
    WarningBannerInlineNotificationCard(
        title = "Reply unavailable",
        supportingText = reason ?: "Reply unavailable for this session.",
        actions = {},
    )
}

@Composable
private fun ReplyComposerInput(
    state: TaskReplyComposerState,
    onDraftChanged: (String) -> Unit,
) {
    TextFieldOutlined(
        value = state.draftText,
        onValueChange = onDraftChanged,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("TaskReplyComposerInput"),
        label = state.replyLabel,
        isEnabled = !state.isSending,
        isSingleLine = false,
    )
}

@Composable
private fun ReplyAttachmentSection(
    replyAttachments: ImmutableList<TaskReplyAttachment>,
    isSending: Boolean,
    onPickAttachments: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TaskSectionHeader(
            title = "Attachments",
            supportingText = if (replyAttachments.isEmpty()) {
                "Add screenshots, documents, or other files to this reply."
            } else {
                "Selected files will be submitted together with this reply."
            },
        )

        ButtonOutlined(
            text = if (replyAttachments.isEmpty()) "Add files" else "Add more files",
            onClick = onPickAttachments,
            modifier = Modifier.testTag("TaskReplyComposerAddFilesButton"),
            enabled = !isSending,
        )

        replyAttachments.forEach { attachment ->
            ReplyAttachmentRow(
                attachment = attachment,
                onRemoveAttachment = onRemoveAttachment,
                isEnabled = !isSending,
            )
        }
    }
}

@Composable
private fun ReplyAttachmentRow(
    attachment: TaskReplyAttachment,
    onRemoveAttachment: (String) -> Unit,
    isEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val metadata = buildReplyAttachmentMetadataText(context, attachment)

    CardOutlined(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = if (attachment.isImage) Icons.Outlined.Image else Icons.Outlined.Attachment,
                modifier = Modifier.size(20.dp),
                contentDescription = null,
                tint = MainTheme.colors.onSurfaceVariant,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextBodyMedium(
                    text = attachment.displayName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                metadata?.let {
                    TextBodySmall(
                        text = it,
                        color = MainTheme.colors.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (attachment.isImage) {
                    TextLabelMedium(
                        text = "Image",
                        color = MainTheme.colors.onSurfaceVariant,
                    )
                }
            }
            ButtonText(
                text = "Remove",
                onClick = { onRemoveAttachment(attachment.id) },
                modifier = Modifier.testTag("TaskReplyComposerRemove_${attachment.id.hashCode()}"),
                enabled = isEnabled,
            )
        }
    }
}

@Composable
private fun ReplyComposerActions(
    state: TaskReplyComposerState,
    onSendReply: () -> Unit,
    onStatusQuery: () -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.canReply) {
            ButtonFilled(
                text = state.sendButtonText,
                onClick = onSendReply,
                modifier = Modifier.testTag("TaskReplyComposerSendButton"),
                enabled = !state.isSending && state.canSendReply,
            )
        }
        ButtonOutlined(
            text = "/status",
            onClick = onStatusQuery,
            modifier = Modifier.testTag("TaskReplyComposerStatusButton"),
            enabled = state.canQueryStatus && !state.isSending,
        )
    }
}

private fun buildReplyAttachmentMetadataText(
    context: Context,
    attachment: TaskReplyAttachment,
): String? {
    val metadata = buildList {
        attachment.contentType?.let(::add)
        attachment.sizeBytes
            ?.takeIf { it >= 0L }
            ?.let { sizeBytes ->
                add(Formatter.formatShortFileSize(context, sizeBytes))
            }
    }

    return metadata.takeIf { it.isNotEmpty() }?.joinToString(separator = " | ")
}

@Composable
private fun QuickAnswersSection(
    quickAnswerChoices: ImmutableList<TaskPendingQuestionChoiceUi>,
    isSending: Boolean,
    onSendChoice: (String) -> Unit,
) {
    if (quickAnswerChoices.isEmpty()) return

    TaskSectionHeader(
        title = "Quick answers",
        supportingText = "Send one of the pending question choices directly.",
    )

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        quickAnswerChoices.forEach { choice ->
            ButtonOutlined(
                text = choice.label,
                onClick = { onSendChoice(choice.value) },
                modifier = Modifier.testTag("TaskReplyComposerChoice_${choice.value}"),
                enabled = !isSending,
            )
        }
    }
}
