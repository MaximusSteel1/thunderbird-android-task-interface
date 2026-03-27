package net.thunderbird.feature.taskmail.internal.ui.detail.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonFilledTonal
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonOutlined
import app.k9mail.core.ui.compose.designsystem.atom.card.CardOutlined
import net.thunderbird.feature.taskmail.internal.ui.component.TaskSectionHeader

@Composable
internal fun SessionControlRail(
    canQueryStatus: Boolean,
    isActionEnabled: Boolean,
    onStatusQuery: () -> Unit,
    onGuide: () -> Unit,
    onStopRunning: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CardOutlined(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TaskSectionHeader(
                title = "Controls",
                supportingText = "Running sessions expose status, guide, and stop controls here.",
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ButtonOutlined(
                    text = "View status",
                    onClick = onStatusQuery,
                    enabled = canQueryStatus && isActionEnabled,
                )
                ButtonFilledTonal(
                    text = "Guide",
                    onClick = onGuide,
                    enabled = isActionEnabled,
                )
                ButtonFilledTonal(
                    text = "Stop running",
                    onClick = onStopRunning,
                    enabled = isActionEnabled,
                )
            }
        }
    }
}
