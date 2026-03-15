package net.thunderbird.feature.taskmail.internal.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.k9mail.core.ui.compose.designsystem.atom.Surface
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodyMedium
import app.k9mail.core.ui.compose.designsystem.atom.text.TextLabelMedium
import net.thunderbird.core.ui.compose.theme2.MainTheme

@Composable
internal fun TaskSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TextLabelMedium(
            text = title,
            color = MainTheme.colors.onSurfaceVariant,
        )
        supportingText?.let {
            TextBodyMedium(
                text = it,
                color = MainTheme.colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun TaskStatusBadge(
    text: String,
    modifier: Modifier = Modifier,
) {
    val palette = badgePalette(text)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = palette.container,
        contentColor = palette.content,
        tonalElevation = MainTheme.elevations.level0,
    ) {
        TextLabelMedium(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = palette.content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun TaskBackendBadge(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = MainTheme.colors.surfaceContainerHigh,
        contentColor = MainTheme.colors.onSurface,
        tonalElevation = MainTheme.elevations.level0,
    ) {
        TextLabelMedium(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = MainTheme.colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun TaskBadgeRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(8.dp),
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
    ) {
        content()
    }
}

private fun badgePalette(text: String): TaskBadgePalette {
    return when (text.lowercase()) {
        "done" -> doneBadgePalette
        "running" -> runningBadgePalette
        "waitinguser", "waiting", "question" -> waitingBadgePalette
        "failed", "killed" -> failedBadgePalette
        else -> defaultBadgePalette
    }
}

private data class TaskBadgePalette(
    val container: Color,
    val content: Color,
)

private val doneBadgePalette = TaskBadgePalette(
    container = Color(DONE_CONTAINER_COLOR),
    content = Color(DONE_CONTENT_COLOR),
)

private val runningBadgePalette = TaskBadgePalette(
    container = Color(RUNNING_CONTAINER_COLOR),
    content = Color(RUNNING_CONTENT_COLOR),
)

private val waitingBadgePalette = TaskBadgePalette(
    container = Color(WAITING_CONTAINER_COLOR),
    content = Color(WAITING_CONTENT_COLOR),
)

private val failedBadgePalette = TaskBadgePalette(
    container = Color(FAILED_CONTAINER_COLOR),
    content = Color(FAILED_CONTENT_COLOR),
)

private val defaultBadgePalette = TaskBadgePalette(
    container = Color(DEFAULT_CONTAINER_COLOR),
    content = Color(DEFAULT_CONTENT_COLOR),
)

private const val DONE_CONTAINER_COLOR = 0xFFE3F6E8
private const val DONE_CONTENT_COLOR = 0xFF1B5E20
private const val RUNNING_CONTAINER_COLOR = 0xFFE3F2FD
private const val RUNNING_CONTENT_COLOR = 0xFF0D47A1
private const val WAITING_CONTAINER_COLOR = 0xFFFFF4D6
private const val WAITING_CONTENT_COLOR = 0xFF8A5A00
private const val FAILED_CONTAINER_COLOR = 0xFFFDE7E9
private const val FAILED_CONTENT_COLOR = 0xFFB3261E
private const val DEFAULT_CONTAINER_COLOR = 0xFFEDEFF3
private const val DEFAULT_CONTENT_COLOR = 0xFF3A4453
