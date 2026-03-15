package net.thunderbird.feature.taskmail.internal.ui.detail.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodyMedium
import net.thunderbird.core.ui.compose.theme2.MainTheme

@Composable
internal fun PlainTextBody(
    text: String,
    modifier: Modifier = Modifier,
) {
    TextBodyMedium(
        text = text,
        modifier = modifier,
        color = MainTheme.colors.onSurface,
    )
}
