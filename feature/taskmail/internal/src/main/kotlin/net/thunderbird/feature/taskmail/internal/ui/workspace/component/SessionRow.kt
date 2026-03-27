package net.thunderbird.feature.taskmail.internal.ui.workspace.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.k9mail.core.ui.compose.designsystem.atom.card.CardOutlined
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodyMedium
import app.k9mail.core.ui.compose.designsystem.atom.text.TextLabelMedium
import app.k9mail.core.ui.compose.designsystem.atom.text.TextTitleMedium
import net.thunderbird.core.ui.compose.theme2.MainTheme
import net.thunderbird.feature.taskmail.internal.ui.component.TaskBackendBadge
import net.thunderbird.feature.taskmail.internal.ui.component.TaskBadgeRow
import net.thunderbird.feature.taskmail.internal.ui.component.TaskStatusBadge
import net.thunderbird.feature.taskmail.internal.ui.workspace.TaskSessionItemUi
import net.thunderbird.feature.taskmail.internal.ui.workspace.isActiveSession

@Composable
internal fun SessionRow(
    session: TaskSessionItemUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                onClick(action = {
                    onClick()
                    true
                })
            }
            .clickable(onClick = onClick)
            .testTag("TaskWorkspaceSessionRow:${session.stableId}"),
    ) {
        CardOutlined(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    TextTitleMedium(
                        text = session.sessionName,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                TaskBadgeRow {
                    TaskStatusBadge(
                        text = if (session.isActiveSession()) {
                            "Active"
                        } else {
                            "Inactive"
                        },
                    )
                    TaskStatusBadge(text = session.status)
                    TaskBackendBadge(text = session.backend)
                    if (session.pendingQuestion) {
                        TaskStatusBadge(text = "Waiting")
                    }
                }

                session.routeLabel?.let { routeLabel ->
                    TextLabelMedium(
                        text = routeLabel,
                        color = MainTheme.colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                session.lastSummary?.let {
                    TextBodyMedium(
                        text = it,
                        color = MainTheme.colors.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                } ?: TextLabelMedium(
                    text = "No summary yet",
                    color = MainTheme.colors.onSurfaceVariant,
                )
            }
        }
    }
}
