package net.thunderbird.feature.taskmail.internal.ui.workspace.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.k9mail.core.ui.compose.designsystem.atom.DividerHorizontal
import app.k9mail.core.ui.compose.designsystem.atom.card.CardElevated
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodyMedium
import app.k9mail.core.ui.compose.designsystem.atom.text.TextHeadlineSmall
import app.k9mail.core.ui.compose.designsystem.atom.text.TextLabelMedium
import net.thunderbird.core.ui.compose.theme2.MainTheme
import net.thunderbird.feature.taskmail.internal.ui.component.TaskSectionHeader
import net.thunderbird.feature.taskmail.internal.ui.workspace.TaskWorkspaceItemUi

@Composable
internal fun WorkspaceCard(
    workspace: TaskWorkspaceItemUi,
    modifier: Modifier = Modifier,
    sessionContent: @Composable () -> Unit,
) {
    CardElevated(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextHeadlineSmall(text = workspace.title)
                workspace.subtitle?.let {
                    TextBodyMedium(
                        text = it,
                        color = MainTheme.colors.onSurfaceVariant,
                    )
                }
                TextLabelMedium(
                    text = workspace.sessionCountLabel,
                    color = MainTheme.colors.onSurfaceVariant,
                )
            }
            DividerHorizontal()
            TaskSectionHeader(title = "Sessions")
            sessionContent()
        }
    }
}
