package net.thunderbird.feature.taskmail.internal.ui.detail.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.k9mail.core.ui.compose.designsystem.atom.card.CardElevated
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodyMedium
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodySmall
import app.k9mail.core.ui.compose.designsystem.atom.text.TextLabelMedium
import app.k9mail.core.ui.compose.designsystem.atom.text.TextTitleMedium
import net.thunderbird.core.ui.compose.theme2.MainTheme
import net.thunderbird.feature.taskmail.internal.domain.model.TaskReplyAttachment
import net.thunderbird.feature.taskmail.internal.ui.component.TaskBadgeRow
import net.thunderbird.feature.taskmail.internal.ui.component.TaskSectionHeader
import net.thunderbird.feature.taskmail.internal.ui.component.TaskStatusBadge

@Composable
internal fun CurrentRoundInputCard(
    status: String,
    body: String,
    permissionLabel: String,
    attachments: List<TaskReplyAttachment>,
    modifier: Modifier = Modifier,
) {
    CardElevated(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TaskSectionHeader(
                title = "Current round",
                supportingText = "The current round is now leading the page. Latest input stays above the previous result.",
            )
            TaskBadgeRow {
                TaskStatusBadge(text = status)
            }
            TextTitleMedium(text = "You:")
            TextBodyMedium(text = body)
            TextLabelMedium(
                text = "Permission · $permissionLabel",
                color = MainTheme.colors.onSurfaceVariant,
            )

            if (attachments.isNotEmpty()) {
                TaskSectionHeader(
                    title = "Input attachments",
                    supportingText = "Files selected for the current round stay above process records.",
                )

                attachments.forEach { attachment ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            TextBodyMedium(
                                text = attachment.displayName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            attachment.contentType?.let {
                                TextBodySmall(
                                    text = it,
                                    color = MainTheme.colors.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
