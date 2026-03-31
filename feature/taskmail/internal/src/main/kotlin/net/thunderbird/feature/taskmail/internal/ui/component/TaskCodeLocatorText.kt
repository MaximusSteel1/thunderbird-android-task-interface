package net.thunderbird.feature.taskmail.internal.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonText
import app.k9mail.core.ui.compose.designsystem.atom.card.CardOutlined
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodyMedium
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodySmall
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import net.thunderbird.core.ui.compose.theme2.MainTheme

internal enum class TaskCodeLocatorTextStyle {
    BodyMedium,
    BodySmall,
}

@Composable
internal fun TaskCodeLocatorText(
    text: String,
    style: TaskCodeLocatorTextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE,
    onCopyLocator: ((String) -> Unit)? = null,
) {
    val segments = remember(text) {
        TaskCodeLocatorParser.parse(text)
    }
    val locatorSegments = remember(segments) {
        segments.filterIsInstance<TaskCodeLocatorSegment.Locator>()
    }

    if (locatorSegments.isEmpty()) {
        RenderTaskCodeLocatorText(
            text = AnnotatedString(text),
            style = style,
            modifier = modifier,
            color = color,
            overflow = overflow,
            maxLines = maxLines,
        )
        return
    }

    val clipboardManager = LocalClipboardManager.current
    val copyLocator = onCopyLocator ?: { rawText: String ->
        clipboardManager.setText(AnnotatedString(rawText))
    }
    var activeLocatorRawText by rememberSaveable(text) {
        mutableStateOf<String?>(null)
    }
    val displayText = remember(segments) {
        buildLocatorDisplayAnnotatedString(segments)
    }
    val tokenTextStyle = locatorTokenTextStyle(style = style)
    val inlineContent = rememberLocatorInlineContent(
        locatorSegments = locatorSegments,
        style = style,
        tokenTextStyle = tokenTextStyle,
        activeLocatorRawText = activeLocatorRawText,
        onToggleLocator = { locatorRawText ->
            activeLocatorRawText = if (activeLocatorRawText == locatorRawText) {
                null
            } else {
                locatorRawText
            }
        },
        onDismissLocator = { activeLocatorRawText = null },
        onCopyLocator = { locatorRawText ->
            copyLocator(locatorRawText)
            activeLocatorRawText = null
        },
    )

    DisableSelection {
        RenderTaskCodeLocatorText(
            text = displayText,
            style = style,
            modifier = modifier.testTag("TaskCodeLocatorText"),
            color = color,
            overflow = overflow,
            maxLines = maxLines,
            inlineContent = inlineContent,
        )
    }
}

@Composable
private fun rememberLocatorInlineContent(
    locatorSegments: List<TaskCodeLocatorSegment.Locator>,
    style: TaskCodeLocatorTextStyle,
    tokenTextStyle: TextStyle,
    activeLocatorRawText: String?,
    onToggleLocator: (String) -> Unit,
    onDismissLocator: () -> Unit,
    onCopyLocator: (String) -> Unit,
): ImmutableMap<String, InlineTextContent> {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    return remember(
        locatorSegments,
        style,
        tokenTextStyle,
        activeLocatorRawText,
        density.density,
        density.fontScale,
    ) {
        val contentBuilder = persistentMapOf<String, InlineTextContent>().builder()

        locatorSegments.forEachIndexed { index, segment ->
            val placeholder = measureLocatorPlaceholder(
                displayText = segment.displayText,
                tokenTextStyle = tokenTextStyle,
                textMeasurer = textMeasurer,
                density = density,
            )
            val inlineKey = locatorInlineKey(index)
            contentBuilder[inlineKey] = InlineTextContent(
                placeholder = placeholder,
            ) {
                TaskCodeLocatorInlineToken(
                    displayText = segment.displayText,
                    rawText = segment.rawText,
                    style = style,
                    isActive = activeLocatorRawText == segment.rawText,
                    onToggle = { onToggleLocator(segment.rawText) },
                    onDismiss = onDismissLocator,
                    onCopy = { onCopyLocator(segment.rawText) },
                )
            }
        }

        contentBuilder.build()
    }
}

@Composable
private fun TaskCodeLocatorInlineToken(
    displayText: String,
    rawText: String,
    style: TaskCodeLocatorTextStyle,
    isActive: Boolean,
    onToggle: () -> Unit,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
) {
    val shape = RoundedCornerShape(6.dp)
    val popupOffsetY = with(LocalDensity.current) { 4.dp.roundToPx() }
    val tokenBackground = if (isActive) {
        MainTheme.colors.primaryContainer.copy(alpha = 0.28f)
    } else {
        MainTheme.colors.surfaceContainerHighest
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.CenterStart,
    ) {
        RenderLocatorTokenText(
            displayText = displayText,
            style = style,
            modifier = Modifier
                .testTag("TaskCodeLocatorToken:$rawText")
                .background(
                    color = tokenBackground,
                    shape = shape,
                )
                .clickable(onClick = onToggle)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )

        if (isActive) {
            Popup(
                alignment = Alignment.BottomStart,
                offset = IntOffset(x = 0, y = popupOffsetY),
                onDismissRequest = onDismiss,
                properties = PopupProperties(focusable = true),
            ) {
                CardOutlined {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 320.dp)
                            .padding(12.dp)
                            .testTag("TaskCodeLocatorMenu"),
                    ) {
                        TextBodySmall(
                            text = rawText,
                            modifier = Modifier.testTag("TaskCodeLocatorMenuText"),
                            color = MainTheme.colors.onSurface,
                        )
                        ButtonText(
                            text = "Copy full reference",
                            onClick = onCopy,
                            modifier = Modifier.testTag("TaskCodeLocatorCopyButton"),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RenderLocatorTokenText(
    displayText: String,
    style: TaskCodeLocatorTextStyle,
    modifier: Modifier = Modifier,
) {
    when (style) {
        TaskCodeLocatorTextStyle.BodyMedium -> {
            TextBodyMedium(
                text = buildAnnotatedString {
                    withStyle(
                        style = SpanStyle(fontFamily = FontFamily.Monospace),
                    ) {
                        append(displayText)
                    }
                },
                modifier = modifier,
                color = MainTheme.colors.primary,
            )
        }

        TaskCodeLocatorTextStyle.BodySmall -> {
            TextBodySmall(
                text = buildAnnotatedString {
                    withStyle(
                        style = SpanStyle(fontFamily = FontFamily.Monospace),
                    ) {
                        append(displayText)
                    }
                },
                modifier = modifier,
                color = MainTheme.colors.primary,
            )
        }
    }
}

@Composable
private fun locatorTokenTextStyle(style: TaskCodeLocatorTextStyle): TextStyle {
    return when (style) {
        TaskCodeLocatorTextStyle.BodyMedium -> MainTheme.typography.bodyMedium
        TaskCodeLocatorTextStyle.BodySmall -> MainTheme.typography.bodySmall
    }.copy(fontFamily = FontFamily.Monospace)
}

private fun buildLocatorDisplayAnnotatedString(
    segments: List<TaskCodeLocatorSegment>,
): AnnotatedString {
    return buildAnnotatedString {
        var locatorIndex = 0

        segments.forEach { segment ->
            when (segment) {
                is TaskCodeLocatorSegment.Plain -> append(segment.text)
                is TaskCodeLocatorSegment.Locator -> {
                    appendInlineContent(
                        id = locatorInlineKey(locatorIndex),
                        alternateText = segment.displayText,
                    )
                    locatorIndex += 1
                }
            }
        }
    }
}

private fun measureLocatorPlaceholder(
    displayText: String,
    tokenTextStyle: TextStyle,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    density: Density,
): Placeholder {
    val layoutResult = textMeasurer.measure(
        text = displayText,
        style = tokenTextStyle,
        maxLines = 1,
    )

    val horizontalPadding = with(density) { 12.dp.roundToPx() }
    val verticalPadding = with(density) { 4.dp.roundToPx() }
    val width = with(density) { (layoutResult.size.width + horizontalPadding).toSp() }
    val height = with(density) { (layoutResult.size.height + verticalPadding).toSp() }

    return Placeholder(
        width = width,
        height = height,
        placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
    )
}

@Composable
private fun RenderTaskCodeLocatorText(
    text: AnnotatedString,
    style: TaskCodeLocatorTextStyle,
    modifier: Modifier,
    color: Color,
    overflow: TextOverflow,
    maxLines: Int,
    inlineContent: ImmutableMap<String, InlineTextContent> = persistentMapOf(),
) {
    when (style) {
        TaskCodeLocatorTextStyle.BodyMedium -> {
            TextBodyMedium(
                text = text,
                modifier = modifier,
                color = color,
                overflow = overflow,
                maxLines = maxLines,
                inlineContent = inlineContent,
            )
        }

        TaskCodeLocatorTextStyle.BodySmall -> {
            TextBodySmall(
                text = text,
                modifier = modifier,
                color = color,
                overflow = overflow,
                maxLines = maxLines,
                inlineContent = inlineContent,
            )
        }
    }
}

private fun locatorInlineKey(index: Int): String = "taskmail_code_locator_$index"
