package net.thunderbird.feature.taskmail.internal.ui.detail.component

import android.content.Context
import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import app.k9mail.core.ui.compose.designsystem.atom.DividerHorizontal
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonText
import app.k9mail.core.ui.compose.designsystem.atom.card.CardOutlined
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodyMedium
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodySmall
import app.k9mail.core.ui.compose.designsystem.atom.text.TextLabelMedium
import app.k9mail.core.ui.compose.designsystem.atom.text.TextTitleMedium
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.collections.immutable.ImmutableList
import net.thunderbird.core.ui.compose.designsystem.atom.icon.Icon
import net.thunderbird.core.ui.compose.designsystem.atom.icon.Icons
import net.thunderbird.core.ui.compose.theme2.MainTheme
import net.thunderbird.feature.taskmail.internal.domain.model.TaskBodyRenderMode
import net.thunderbird.feature.taskmail.internal.ui.component.TaskBackendBadge
import net.thunderbird.feature.taskmail.internal.ui.component.TaskStatusBadge
import net.thunderbird.feature.taskmail.internal.ui.detail.TaskTimelineAttachmentUi
import net.thunderbird.feature.taskmail.internal.ui.detail.TaskTimelineItemUi

internal enum class TimelineMessageCardStyle {
    Default,
    ProcessRecord,
}

@Composable
internal fun TimelineMessageCard(
    item: TaskTimelineItemUi,
    style: TimelineMessageCardStyle = TimelineMessageCardStyle.Default,
    modifier: Modifier = Modifier,
    onOpenAttachment: (String) -> Unit = {},
    onSaveAttachment: (String) -> Unit = {},
) {
    val headerTitle = item.headerTitle(style = style)
    val headerSubtitle = item.headerSubtitle(style = style)
    val displayItem = item.withCollapsedDuplicateBody(
        style = style,
        headerTitle = headerTitle,
    )
    val displaySummary = item.summary
        ?.takeIf(String::isNotBlank)
        ?.takeUnless { summary ->
            shouldHideSummary(summary = summary, plainText = item.plainText) || summary == headerTitle
        }
    val richDocument = displayItem.richDocument.takeIf { displayItem.renderMode == TaskBodyRenderMode.RichText }
    val hasRichBody = richDocument != null
    val hasVisibleBody = hasRichBody || displayItem.plainText.isNotBlank()

    CardOutlined(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    TextTitleMedium(text = headerTitle)
                    TextLabelMedium(
                        text = headerSubtitle,
                        color = MainTheme.colors.onSurfaceVariant,
                    )
                }
                item.statusLabel?.let {
                    TaskStatusBadge(text = it)
                } ?: TaskBackendBadge(text = item.direction)
            }
            displaySummary?.let {
                TextBodyMedium(
                    text = it,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                DividerHorizontal()
            }
            TimelineMessageContent(
                item = displayItem,
                richDocument = richDocument,
                hasVisibleBody = hasVisibleBody,
                onOpenAttachment = onOpenAttachment,
                onSaveAttachment = onSaveAttachment,
            )
        }
    }
}

@Composable
internal fun TimelineAttachments(
    attachments: ImmutableList<TaskTimelineAttachmentUi>,
    onOpenAttachment: (String) -> Unit,
    onSaveAttachment: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextLabelMedium(
            text = attachmentSectionTitle(attachments.size),
            color = MainTheme.colors.onSurfaceVariant,
        )

        attachments.forEach { attachment ->
            TimelineAttachmentRow(
                attachment = attachment,
                onOpenAttachment = onOpenAttachment,
                onSaveAttachment = onSaveAttachment,
            )
        }
    }
}

@Composable
private fun TimelineAttachmentRow(
    attachment: TaskTimelineAttachmentUi,
    onOpenAttachment: (String) -> Unit,
    onSaveAttachment: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val metadata = buildAttachmentMetadataText(context = context, attachment = attachment)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("TimelineAttachmentRow:${attachment.id}"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TimelineAttachmentSummaryRow(
            attachment = attachment,
            metadata = metadata,
            onOpenAttachment = onOpenAttachment,
        )
        TimelineAttachmentActionsRow(
            attachment = attachment,
            onOpenAttachment = onOpenAttachment,
            onSaveAttachment = onSaveAttachment,
        )
    }
}

@Composable
private fun TimelineAttachmentSummaryRow(
    attachment: TaskTimelineAttachmentUi,
    metadata: String?,
    onOpenAttachment: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = attachment.isActionAvailable,
                onClick = { onOpenAttachment(attachment.id) },
            ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = attachment.icon,
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
        }
        attachment.badgeText?.let {
            TaskBackendBadge(text = it)
        }
    }
}

@Composable
private fun TimelineAttachmentActionsRow(
    attachment: TaskTimelineAttachmentUi,
    onOpenAttachment: (String) -> Unit,
    onSaveAttachment: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ButtonText(
            text = "Open",
            enabled = attachment.isActionAvailable,
            onClick = { onOpenAttachment(attachment.id) },
            modifier = Modifier.testTag("TimelineAttachmentOpen:${attachment.id}"),
        )
        ButtonText(
            text = "Save",
            enabled = attachment.isActionAvailable,
            onClick = { onSaveAttachment(attachment.id) },
            modifier = Modifier.testTag("TimelineAttachmentSave:${attachment.id}"),
        )
    }
}

private fun buildAttachmentMetadataText(
    context: Context,
    attachment: TaskTimelineAttachmentUi,
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

private val TaskTimelineAttachmentUi.icon get() = when {
    isImage -> Icons.Outlined.Image
    else -> Icons.Outlined.Attachment
}

private val TaskTimelineAttachmentUi.badgeText: String?
    get() = when {
        isInline && isImage -> "Inline image"
        isInline -> "Inline"
        isImage -> "Image"
        else -> null
    }

private fun attachmentSectionTitle(count: Int): String {
    return if (count == 1) "1 attachment" else "$count attachments"
}

private fun formatTimestamp(timestamp: Long): String {
    if (timestamp <= 0L) return "Unknown time"
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

private fun TaskTimelineItemUi.headerTitle(style: TimelineMessageCardStyle): String {
    return when (style) {
        TimelineMessageCardStyle.Default -> direction
        TimelineMessageCardStyle.ProcessRecord -> {
            summary
                ?.takeIf(String::isNotBlank)
                ?: statusLabel
                ?: direction
        }
    }
}

private fun TaskTimelineItemUi.headerSubtitle(style: TimelineMessageCardStyle): String {
    return when (style) {
        TimelineMessageCardStyle.Default -> formatTimestamp(timestamp)
        TimelineMessageCardStyle.ProcessRecord -> {
            listOf(direction.takeIf(String::isNotBlank), formatTimestamp(timestamp))
                .filterNotNull()
                .joinToString(separator = " · ")
        }
    }
}

private fun TaskTimelineItemUi.withCollapsedDuplicateBody(
    style: TimelineMessageCardStyle,
    headerTitle: String,
): TaskTimelineItemUi {
    if (style != TimelineMessageCardStyle.ProcessRecord) return this

    return if (plainText.normalizeForComparison() == headerTitle.normalizeForComparison()) {
        copy(plainText = "")
    } else {
        this
    }
}

private fun shouldHideSummary(summary: String, plainText: String): Boolean {
    val normalizedSummary = summary.normalizeForComparison()
    val normalizedBody = plainText.normalizeForComparison()

    return normalizedSummary.isNotEmpty() &&
        normalizedBody.isNotEmpty() &&
        (normalizedBody == normalizedSummary || normalizedBody.startsWith(normalizedSummary))
}

private fun String.normalizeForComparison(): String {
    return replace(Regex("\\s+"), " ").trim()
}
