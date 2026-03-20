package net.thunderbird.feature.taskmail.internal.ui.detail.component

import androidx.compose.runtime.Composable
import net.thunderbird.feature.taskmail.internal.domain.model.TaskRichTextDocument
import net.thunderbird.feature.taskmail.internal.ui.detail.TaskTimelineItemUi

@Composable
internal fun TimelineMessageContent(
    item: TaskTimelineItemUi,
    richDocument: TaskRichTextDocument?,
    hasVisibleBody: Boolean,
    onOpenAttachment: (String) -> Unit,
    onSaveAttachment: (String) -> Unit,
) {
    if (richDocument != null) {
        TaskRichTextBody(
            document = richDocument,
            attachments = item.attachments,
        )
    } else if (item.plainText.isNotBlank()) {
        PlainTextBody(text = item.plainText)
    }

    if (item.attachments.isNotEmpty()) {
        if (hasVisibleBody) {
            app.k9mail.core.ui.compose.designsystem.atom.DividerHorizontal()
        }
        TimelineAttachments(
            attachments = item.attachments,
            onOpenAttachment = onOpenAttachment,
            onSaveAttachment = onSaveAttachment,
        )
    }
}
