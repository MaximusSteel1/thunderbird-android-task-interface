package net.thunderbird.feature.taskmail.internal.ui.detail.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.k9mail.core.ui.compose.designsystem.atom.card.CardOutlined
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodyMedium
import app.k9mail.core.ui.compose.designsystem.atom.text.TextLabelMedium
import kotlinx.collections.immutable.ImmutableList
import net.thunderbird.core.ui.compose.designsystem.organism.ModalBottomSheet
import net.thunderbird.core.ui.compose.theme2.MainTheme
import net.thunderbird.feature.taskmail.internal.ui.component.TaskSectionHeader
import net.thunderbird.feature.taskmail.internal.ui.component.TaskStatusBadge
import net.thunderbird.feature.taskmail.internal.ui.detail.TaskHistoryRoundUi

@Composable
internal fun HistoryContextSheet(
    historyPreview: ImmutableList<TaskHistoryRoundUi>,
    onDismissRequest: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .testTag("TaskSessionDetailHistorySheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TaskSectionHeader(
                title = "History context",
                supportingText = "Recent rounds are shown newest first.",
            )
            if (historyPreview.isEmpty()) {
                TextBodyMedium(
                    text = "No earlier context is available yet.",
                    color = MainTheme.colors.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(historyPreview, key = TaskHistoryRoundUi::id) { round ->
                        HistoryRoundCard(round = round)
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRoundCard(round: TaskHistoryRoundUi) {
    CardOutlined(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            round.statusLabel?.let { label ->
                TaskStatusBadge(text = label)
            }
            TextBodyMedium(text = round.title)
            round.summary?.let { text ->
                TextLabelMedium(
                    text = text,
                    color = MainTheme.colors.onSurfaceVariant,
                )
            }
            round.messagePreview?.let { text ->
                TextBodyMedium(text = text)
            }
        }
    }
}
