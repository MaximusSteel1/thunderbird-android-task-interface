package net.thunderbird.feature.taskmail.internal.ui.detail.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonText
import app.k9mail.core.ui.compose.designsystem.atom.card.CardOutlined
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodySmall
import net.thunderbird.core.ui.compose.theme2.MainTheme
import net.thunderbird.feature.taskmail.internal.ui.component.TaskSectionHeader

@Composable
internal fun ProcessFoldCard(
    timelineCount: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    title: String = "Run records",
    supportingText: String? = null,
    emptyText: String = "No preserved records available yet.",
    countText: (Int) -> String = { count -> "$count preserved record(s) available." },
    modifier: Modifier = Modifier,
) {
    CardOutlined(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TaskSectionHeader(
                    title = title,
                    supportingText = supportingText,
                )
                TextBodySmall(
                    text = if (timelineCount == 0) {
                        emptyText
                    } else {
                        countText(timelineCount)
                    },
                    color = MainTheme.colors.onSurfaceVariant,
                )
            }

            ButtonText(
                text = if (isExpanded) "Collapse" else "Expand",
                onClick = onToggle,
                enabled = timelineCount > 0,
            )
        }
    }
}
