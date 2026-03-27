package net.thunderbird.feature.taskmail.internal.ui.detail.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonText
import app.k9mail.core.ui.compose.designsystem.atom.card.CardOutlined
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodyMedium
import app.k9mail.core.ui.compose.designsystem.atom.text.TextLabelMedium
import net.thunderbird.core.ui.compose.theme2.MainTheme
import net.thunderbird.feature.taskmail.internal.ui.component.TaskSectionHeader

@Composable
internal fun SessionMetadataCard(
    lines: List<Pair<String, String>>,
    canQueryStatus: Boolean,
    onStatusQuery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CardOutlined(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TaskSectionHeader(
                title = "Session details",
                supportingText = "Low-priority detail remains at the bottom so the page stays focused on the current round.",
            )

            lines.forEach { (label, value) ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextLabelMedium(
                        text = label,
                        color = MainTheme.colors.onSurfaceVariant,
                    )
                    TextBodyMedium(text = value)
                }
            }

            if (canQueryStatus) {
                ButtonText(
                    text = "View status",
                    onClick = onStatusQuery,
                )
            }
        }
    }
}
