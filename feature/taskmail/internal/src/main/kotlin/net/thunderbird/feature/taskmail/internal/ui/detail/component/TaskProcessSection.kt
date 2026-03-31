package net.thunderbird.feature.taskmail.internal.ui.detail.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import net.thunderbird.feature.taskmail.internal.ui.detail.TaskProcessSectionUi
import net.thunderbird.feature.taskmail.internal.ui.detail.TaskTimelineItemUi

@Composable
internal fun TaskProcessSection(
    section: TaskProcessSectionUi,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onOpenAttachment: (String) -> Unit,
    onSaveAttachment: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("TaskProcessSection:${section.title}"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ProcessFoldCard(
            timelineCount = section.visibleItems.size,
            isExpanded = isExpanded,
            onToggle = onToggle,
            title = section.title,
            supportingText = section.supportingText,
            previewText = section.previewText,
            emptyText = section.emptyText,
            countText = ::assistantCountText,
        )

        if (isExpanded && section.visibleItems.isNotEmpty()) {
            TaskProcessItems(
                items = section.visibleItems,
                onOpenAttachment = onOpenAttachment,
                onSaveAttachment = onSaveAttachment,
            )
        }
    }
}

@Composable
private fun TaskProcessItems(
    items: ImmutableList<TaskTimelineItemUi>,
    onOpenAttachment: (String) -> Unit,
    onSaveAttachment: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEach { item ->
            TimelineMessageCard(
                item = item,
                style = TimelineMessageCardStyle.ProcessRecord,
                onOpenAttachment = onOpenAttachment,
                onSaveAttachment = onSaveAttachment,
            )
        }
    }
}

private fun assistantCountText(count: Int): String {
    return if (count == 1) {
        "1 assistant update available."
    } else {
        "$count assistant updates available."
    }
}
