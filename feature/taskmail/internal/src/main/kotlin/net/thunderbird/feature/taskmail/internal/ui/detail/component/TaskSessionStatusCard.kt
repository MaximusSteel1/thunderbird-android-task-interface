package net.thunderbird.feature.taskmail.internal.ui.detail.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.k9mail.core.ui.compose.designsystem.atom.card.CardElevated
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodyMedium
import app.k9mail.core.ui.compose.designsystem.atom.text.TextLabelMedium
import app.k9mail.core.ui.compose.designsystem.atom.text.TextTitleMedium
import net.thunderbird.core.ui.compose.theme2.MainTheme
import net.thunderbird.feature.taskmail.internal.ui.component.TaskBadgeRow
import net.thunderbird.feature.taskmail.internal.ui.component.TaskSectionHeader
import net.thunderbird.feature.taskmail.internal.ui.component.TaskStatusBadge

@Composable
internal fun TaskSessionStatusCard(
    status: String,
    headline: String,
    actorHint: String,
    submissionMessage: String? = null,
    supportingText: String? = null,
    timingRows: List<Pair<String, String>> = emptyList(),
    modifier: Modifier = Modifier,
) {
    CardElevated(
        modifier = modifier
            .fillMaxWidth()
            .testTag("TaskSessionDetailStatusCard"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TaskSectionHeader(
                title = "Status",
                supportingText = "This card summarizes who is waiting on whom right now.",
            )
            TaskBadgeRow {
                TaskStatusBadge(text = status)
            }
            TextTitleMedium(text = headline)
            TextBodyMedium(text = actorHint)
            submissionMessage?.let { message ->
                TextBodyMedium(
                    text = message,
                    color = MainTheme.colors.onSurfaceVariant,
                )
            }
            supportingText?.let { text ->
                TextBodyMedium(text = text)
            }
            timingRows.forEach { (label, value) ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextLabelMedium(
                        text = label,
                        color = MainTheme.colors.onSurfaceVariant,
                    )
                    TextBodyMedium(text = value)
                }
            }
        }
    }
}
