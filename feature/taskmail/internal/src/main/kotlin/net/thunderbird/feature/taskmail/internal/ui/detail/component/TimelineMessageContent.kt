package net.thunderbird.feature.taskmail.internal.ui.detail.component

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
        SelectionContainer(
            modifier = Modifier.testTag("TimelineMessageSelectableBody:${item.id}"),
        ) {
            TaskRichTextBody(
                document = richDocument,
                attachments = item.attachments,
            )
        }
    } else if (item.plainText.isNotBlank()) {
        SelectionContainer(
            modifier = Modifier.testTag("TimelineMessageSelectableBody:${item.id}"),
        ) {
            PlainTextBody(text = item.plainText)
        }
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
