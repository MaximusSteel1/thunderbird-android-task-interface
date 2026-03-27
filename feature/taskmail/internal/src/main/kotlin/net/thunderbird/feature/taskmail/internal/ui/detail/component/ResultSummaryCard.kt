package net.thunderbird.feature.taskmail.internal.ui.detail.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.k9mail.core.ui.compose.designsystem.atom.card.CardOutlined
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodyMedium
import app.k9mail.core.ui.compose.designsystem.atom.text.TextTitleMedium
import net.thunderbird.core.ui.compose.theme2.MainTheme
import net.thunderbird.feature.taskmail.internal.ui.component.TaskBackendBadge
import net.thunderbird.feature.taskmail.internal.ui.component.TaskBadgeRow
import net.thunderbird.feature.taskmail.internal.ui.component.TaskSectionHeader
import net.thunderbird.feature.taskmail.internal.ui.component.TaskStatusBadge
import net.thunderbird.feature.taskmail.internal.ui.detail.TaskResultSummaryUi

@Composable
internal fun ResultSummaryCard(
    result: TaskResultSummaryUi,
    title: String = "Latest result",
    supportingText: String = "Current result projection for this session.",
    speakerLabel: String? = null,
    modifier: Modifier = Modifier,
) {
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
            TaskBadgeRow {
                TaskStatusBadge(text = result.statusLabel)
                speakerLabel?.let {
                    TaskBackendBadge(text = it)
                }
            }
            speakerLabel?.let {
                TextTitleMedium(text = "$it:")
            }
            TextTitleMedium(text = result.headline)
            result.supportingText?.let { text ->
                TextBodyMedium(
                    text = text,
                    color = MainTheme.colors.onSurfaceVariant,
                )
            }
            result.effectiveExecutionSummary?.let { text ->
                TextBodyMedium(
                    text = text,
                    color = MainTheme.colors.onSurfaceVariant,
                )
            }
        }
    }
}
