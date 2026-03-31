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
import app.k9mail.core.ui.compose.designsystem.atom.text.TextLabelMedium
import app.k9mail.core.ui.compose.designsystem.atom.text.TextTitleMedium
import net.thunderbird.core.ui.compose.theme2.MainTheme
import net.thunderbird.feature.taskmail.internal.ui.component.TaskSectionHeader

@Composable
internal fun TaskSessionMetaCard(
    headline: String,
    lines: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
) {
    CardOutlined(
        modifier = modifier
            .fillMaxWidth()
            .testTag("TaskSessionDetailSessionMeta"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TaskSectionHeader(
                title = "Session info",
                supportingText = "Technical context stays at the bottom by default.",
            )
            TextTitleMedium(text = headline)
            lines.forEach { (label, value) ->
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
