package net.thunderbird.feature.taskmail.internal.ui.detail.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonText
import app.k9mail.core.ui.compose.designsystem.atom.card.CardOutlined
import app.k9mail.core.ui.compose.designsystem.atom.text.TextLabelMedium
import net.thunderbird.core.ui.compose.theme2.MainTheme
import net.thunderbird.feature.taskmail.internal.ui.component.TaskCodeLocatorText
import net.thunderbird.feature.taskmail.internal.ui.component.TaskCodeLocatorTextStyle
import net.thunderbird.feature.taskmail.internal.ui.component.TaskSectionHeader
import net.thunderbird.feature.taskmail.internal.ui.detail.TaskRecentContextUi

@Composable
internal fun RecentContextCard(
    context: TaskRecentContextUi,
    modifier: Modifier = Modifier,
    showHistoryButton: Boolean = false,
    onHistoryClick: () -> Unit = {},
) {
    CardOutlined(
        modifier = modifier
            .fillMaxWidth()
            .testTag("TaskSessionDetailRecentContext"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TaskSectionHeader(
                title = "Recent context",
                supportingText = "Use the latest context before you continue this session.",
            )
            ContextLine(
                label = "You last said",
                value = context.latestUserMessage,
                enableLocatorCollapse = false,
            )
            ContextLine(
                label = "Latest session output",
                value = context.latestAssistantMessage,
            )
            ContextLine(
                label = "Waiting for",
                value = context.waitingForUserText,
            )
            if (showHistoryButton) {
                ButtonText(
                    text = "View history",
                    onClick = onHistoryClick,
                    modifier = Modifier.testTag("TaskSessionDetailRecentContextHistoryButton"),
                )
            }
        }
    }
}

@Composable
private fun ContextLine(
    label: String,
    value: String?,
    enableLocatorCollapse: Boolean = true,
) {
    if (value.isNullOrBlank()) return

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TextLabelMedium(
            text = label,
            color = MainTheme.colors.onSurfaceVariant,
        )
        if (enableLocatorCollapse) {
            TaskCodeLocatorText(
                text = value,
                style = TaskCodeLocatorTextStyle.BodyMedium,
                color = MainTheme.colors.onSurface,
            )
        } else {
            app.k9mail.core.ui.compose.designsystem.atom.text.TextBodyMedium(text = value)
        }
    }
}
