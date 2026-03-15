package net.thunderbird.feature.taskmail.internal.ui.detail.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.k9mail.core.ui.compose.designsystem.atom.card.CardElevated
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodyMedium
import app.k9mail.core.ui.compose.designsystem.atom.text.TextHeadlineSmall
import net.thunderbird.core.ui.compose.theme2.MainTheme
import net.thunderbird.feature.taskmail.internal.ui.component.TaskBackendBadge
import net.thunderbird.feature.taskmail.internal.ui.component.TaskBadgeRow
import net.thunderbird.feature.taskmail.internal.ui.component.TaskStatusBadge

@Composable
internal fun SessionHeader(
    sessionName: String,
    status: String,
    backend: String,
    modifier: Modifier = Modifier,
) {
    CardElevated(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextHeadlineSmall(text = sessionName)
            TaskBadgeRow {
                TaskStatusBadge(text = status)
                TaskBackendBadge(text = backend)
            }
            TextBodyMedium(
                text = "Current session state, backend, and latest task context.",
                color = MainTheme.colors.onSurfaceVariant,
            )
        }
    }
}
