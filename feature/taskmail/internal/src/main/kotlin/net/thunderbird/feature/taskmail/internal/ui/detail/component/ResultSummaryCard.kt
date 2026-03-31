package net.thunderbird.feature.taskmail.internal.ui.detail.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.k9mail.core.ui.compose.designsystem.atom.DividerHorizontal
import app.k9mail.core.ui.compose.designsystem.atom.card.CardOutlined
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodySmall
import app.k9mail.core.ui.compose.designsystem.atom.text.TextTitleMedium
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import net.thunderbird.core.ui.compose.theme2.MainTheme
import net.thunderbird.feature.taskmail.internal.domain.model.TaskBodyRenderMode
import net.thunderbird.feature.taskmail.internal.ui.component.TaskCodeLocatorText
import net.thunderbird.feature.taskmail.internal.ui.component.TaskCodeLocatorTextStyle
import net.thunderbird.feature.taskmail.internal.ui.component.TaskBackendBadge
import net.thunderbird.feature.taskmail.internal.ui.component.TaskBadgeRow
import net.thunderbird.feature.taskmail.internal.ui.component.TaskSectionHeader
import net.thunderbird.feature.taskmail.internal.ui.component.TaskStatusBadge
import net.thunderbird.feature.taskmail.internal.ui.detail.TaskResultSummaryUi
import net.thunderbird.feature.taskmail.internal.ui.detail.TaskTimelineAttachmentUi
import net.thunderbird.feature.taskmail.internal.ui.detail.TaskTimelineItemUi

@Composable
internal fun ResultSummaryCard(
    result: TaskResultSummaryUi,
    title: String = "Latest result",
    supportingText: String = "Current result projection for this session.",
    speakerLabel: String? = null,
    resultBody: TaskTimelineItemUi? = null,
    supplementalAttachments: ImmutableList<TaskTimelineAttachmentUi> = persistentListOf(),
    timestampText: String? = null,
    modifier: Modifier = Modifier,
    onOpenAttachment: (String) -> Unit = {},
    onSaveAttachment: (String) -> Unit = {},
) {
    val mergedAttachments = buildList {
        addAll(resultBody?.attachments.orEmpty())
        supplementalAttachments.forEach { attachment ->
            if (none { existing -> existing.id == attachment.id }) {
                add(attachment)
            }
        }
    }.toImmutableList()
    val displayBody = when {
        resultBody != null -> resultBody.copy(
            id = "result_summary:${resultBody.id}",
            attachments = mergedAttachments,
        )
        mergedAttachments.isNotEmpty() -> TaskTimelineItemUi(
            id = "result_summary:attachments",
            timestamp = 0L,
            direction = speakerLabel ?: "System",
            plainText = "",
            attachments = mergedAttachments,
        )
        else -> null
    }
    val richDocument = displayBody?.richDocument
        .takeIf { displayBody?.renderMode == TaskBodyRenderMode.RichText }
    CardOutlined(
        modifier = modifier
            .fillMaxWidth()
            .testTag("TaskSessionDetailResultSummary"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TaskSectionHeader(
                title = title,
                supportingText = supportingText,
            )
            displayBody?.let { body ->
                Column(modifier = Modifier.testTag("TaskSessionDetailResultBody")) {
                    TimelineMessageContent(
                        item = body,
                        richDocument = richDocument,
                        hasVisibleBody = true,
                        onOpenAttachment = onOpenAttachment,
                        onSaveAttachment = onSaveAttachment,
                    )
                }
                DividerHorizontal()
            }
            Column(
                modifier = Modifier.testTag("TaskSessionDetailResultConclusion"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TaskBadgeRow {
                    TaskStatusBadge(text = result.statusLabel)
                    speakerLabel?.let {
                        TaskBackendBadge(text = it)
                    }
                }
                timestampText?.let {
                    TextBodySmall(
                        text = it,
                        color = MainTheme.colors.onSurfaceVariant,
                    )
                }
                TextTitleMedium(text = result.headline)
                result.supportingText?.let { text ->
                    TaskCodeLocatorText(
                        text = text,
                        style = TaskCodeLocatorTextStyle.BodyMedium,
                        color = MainTheme.colors.onSurfaceVariant,
                    )
                }
                result.effectiveExecutionSummary?.let { text ->
                    TaskCodeLocatorText(
                        text = text,
                        style = TaskCodeLocatorTextStyle.BodyMedium,
                        color = MainTheme.colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
