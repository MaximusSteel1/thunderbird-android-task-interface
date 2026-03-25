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
import kotlinx.collections.immutable.ImmutableList
import net.thunderbird.core.ui.compose.theme2.MainTheme
import net.thunderbird.feature.taskmail.internal.ui.component.TaskSectionHeader
import net.thunderbird.feature.taskmail.internal.ui.detail.TaskSessionArtifactUi

@Composable
internal fun ArtifactSection(
    artifacts: ImmutableList<TaskSessionArtifactUi>,
    modifier: Modifier = Modifier,
) {
    if (artifacts.isEmpty()) return

    CardOutlined(
        modifier = modifier
            .fillMaxWidth()
            .testTag("TaskSessionDetailArtifacts"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TaskSectionHeader(
                title = "Files",
                supportingText = "Files currently visible from the session timeline.",
            )
            artifacts.forEach { artifact ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextBodyMedium(text = artifact.title)
                    artifact.supportingText?.let { text ->
                        TextLabelMedium(
                            text = text,
                            color = MainTheme.colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
