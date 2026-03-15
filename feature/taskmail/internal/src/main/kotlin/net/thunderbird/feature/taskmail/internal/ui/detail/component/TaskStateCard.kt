package net.thunderbird.feature.taskmail.internal.ui.detail.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.k9mail.core.ui.compose.designsystem.atom.DividerHorizontal
import app.k9mail.core.ui.compose.designsystem.atom.card.CardOutlined
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodyMedium
import app.k9mail.core.ui.compose.designsystem.atom.text.TextLabelMedium
import app.k9mail.core.ui.compose.designsystem.atom.text.TextTitleMedium
import net.thunderbird.core.ui.compose.theme2.MainTheme
import net.thunderbird.feature.taskmail.internal.ui.component.TaskSectionHeader

@Composable
internal fun TaskStateCard(
    repoPath: String,
    workdir: String?,
    lastSummary: String?,
    modifier: Modifier = Modifier,
) {
    CardOutlined(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TaskSectionHeader(title = "Workspace")
            TextTitleMedium(text = repoPath)
            workdir?.let {
                TextBodyMedium(
                    text = it,
                    color = MainTheme.colors.onSurfaceVariant,
                )
            }
            DividerHorizontal()
            TaskSectionHeader(title = "Latest summary")
            if (lastSummary != null) {
                TextBodyMedium(text = lastSummary)
            } else {
                TextLabelMedium(
                    text = "No summary available yet",
                    color = MainTheme.colors.onSurfaceVariant,
                )
            }
        }
    }
}
