package net.thunderbird.feature.taskmail.internal.ui.detail.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonFilledTonal
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonOutlined
import app.k9mail.core.ui.compose.designsystem.atom.card.CardOutlined
import net.thunderbird.feature.taskmail.internal.ui.component.TaskSectionHeader

@Composable
internal fun TaskSecondaryActionsCard(
    canQueryStatus: Boolean,
    showGuide: Boolean,
    showResume: Boolean,
    showStopRunning: Boolean,
    showDeactivate: Boolean,
    isActionEnabled: Boolean,
    onStatusQuery: () -> Unit,
    onGuide: () -> Unit,
    onResume: () -> Unit,
    onStopRunning: () -> Unit,
    onDeactivate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CardOutlined(
        modifier = modifier
            .fillMaxWidth()
            .testTag("TaskSessionDetailSecondaryActions"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TaskSectionHeader(
                title = "Session actions",
                supportingText = "Secondary and destructive actions stay below the current task flow.",
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (canQueryStatus) {
                    ButtonOutlined(
                        text = "View status",
                        onClick = onStatusQuery,
                        enabled = isActionEnabled,
                    )
                }
                if (showGuide) {
                    ButtonFilledTonal(
                        text = "Guide",
                        onClick = onGuide,
                        enabled = isActionEnabled,
                    )
                }
                if (showResume) {
                    ButtonFilledTonal(
                        text = "Resume",
                        onClick = onResume,
                        enabled = isActionEnabled,
                    )
                }
                if (showStopRunning) {
                    ButtonFilledTonal(
                        text = "Stop running",
                        onClick = onStopRunning,
                        enabled = isActionEnabled,
                    )
                }
                if (showDeactivate) {
                    ButtonOutlined(
                        text = "Deactivate",
                        onClick = onDeactivate,
                        enabled = isActionEnabled,
                    )
                }
            }
        }
    }
}
