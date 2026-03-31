package net.thunderbird.feature.taskmail.internal.ui.detail.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.thunderbird.core.ui.compose.theme2.MainTheme
import net.thunderbird.feature.taskmail.internal.ui.component.TaskCodeLocatorText
import net.thunderbird.feature.taskmail.internal.ui.component.TaskCodeLocatorTextStyle

@Composable
internal fun PlainTextBody(
    text: String,
    modifier: Modifier = Modifier,
) {
    TaskCodeLocatorText(
        text = text,
        style = TaskCodeLocatorTextStyle.BodyMedium,
        modifier = modifier,
        color = MainTheme.colors.onSurface,
    )
}
