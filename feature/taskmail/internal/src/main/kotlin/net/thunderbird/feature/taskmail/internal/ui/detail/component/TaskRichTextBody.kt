@file:Suppress("TooManyFunctions", "UnstableCollections")

package net.thunderbird.feature.taskmail.internal.ui.detail.component

import androidx.compose.foundation.background
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.k9mail.core.ui.compose.designsystem.atom.card.CardOutlined
import app.k9mail.core.ui.compose.designsystem.atom.image.RemoteImage
import app.k9mail.core.ui.compose.designsystem.atom.image.rememberPreviewPlaceholder
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodyMedium
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodySmall
import app.k9mail.core.ui.compose.designsystem.atom.text.TextHeadlineSmall
import app.k9mail.core.ui.compose.designsystem.atom.text.TextLabelMedium
import app.k9mail.core.ui.compose.designsystem.atom.text.TextTitleLarge
import net.thunderbird.core.ui.compose.designsystem.atom.icon.Icon
import net.thunderbird.core.ui.compose.designsystem.atom.icon.Icons
import net.thunderbird.core.ui.compose.theme2.MainTheme
import net.thunderbird.feature.taskmail.internal.domain.model.TaskRichTextBlock
import net.thunderbird.feature.taskmail.internal.domain.model.TaskRichTextDocument
import net.thunderbird.feature.taskmail.internal.domain.model.TaskRichTextInline
import net.thunderbird.feature.taskmail.internal.ui.detail.TaskTimelineAttachmentUi

@Composable
internal fun TaskRichTextBody(
    document: TaskRichTextDocument,
    modifier: Modifier = Modifier,
    attachments: List<TaskTimelineAttachmentUi> = emptyList(),
) {
    Column(
        modifier = modifier.testTag("TaskRichTextBody"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        document.blocks.forEachIndexed { index, block ->
            TaskRichTextBlockView(
                block = block,
                attachments = attachments,
                modifier = Modifier.testTag("TaskRichTextBlock:$index"),
            )
        }
    }
}

@Composable
private fun TaskRichTextBlockView(
    block: TaskRichTextBlock,
    attachments: List<TaskTimelineAttachmentUi>,
    modifier: Modifier = Modifier,
) {
    when (block) {
        is TaskRichTextBlock.Paragraph -> RichParagraph(
            inlines = block.inlines,
            modifier = modifier,
        )

        is TaskRichTextBlock.Heading -> RichHeading(
            level = block.level,
            inlines = block.inlines,
            modifier = modifier,
        )

        is TaskRichTextBlock.Quote -> RichQuote(
            blocks = block.blocks,
            attachments = attachments,
            modifier = modifier,
        )

        is TaskRichTextBlock.CodeBlock -> RichCodeBlock(
            text = block.text,
            languageHint = block.languageHint,
            modifier = modifier,
        )

        is TaskRichTextBlock.BulletList -> RichList(
            items = block.items,
            attachments = attachments,
            modifier = modifier,
        ) { index -> "\u2022" }

        is TaskRichTextBlock.OrderedList -> RichList(
            items = block.items,
            attachments = attachments,
            modifier = modifier,
        ) { index -> "${index + 1}." }

        is TaskRichTextBlock.Table -> RichTable(
            table = block,
            modifier = modifier,
        )

        is TaskRichTextBlock.InlineImage -> RichInlineImage(
            image = block,
            attachments = attachments,
            modifier = modifier,
        )

        TaskRichTextBlock.Divider -> Box(modifier = modifier) {
            app.k9mail.core.ui.compose.designsystem.atom.DividerHorizontal()
        }

        is TaskRichTextBlock.UnsupportedHtml -> RichUnsupportedHtml(
            fallbackText = block.fallbackText,
            modifier = modifier,
        )
    }
}

@Composable
private fun RichParagraph(
    inlines: List<TaskRichTextInline>,
    modifier: Modifier = Modifier,
) {
    val text = buildRichText(inlines)
    if (text.text.isBlank()) return

    TextBodyMedium(
        text = text,
        modifier = modifier,
        color = MainTheme.colors.onSurface,
    )
}

@Composable
private fun RichHeading(
    level: Int,
    inlines: List<TaskRichTextInline>,
    modifier: Modifier = Modifier,
) {
    val text = buildRichText(inlines)
    if (text.text.isBlank()) return

    when (level) {
        1, 2 -> TextHeadlineSmall(
            text = text,
            modifier = modifier,
            color = MainTheme.colors.onSurface,
        )

        else -> TextTitleLarge(
            text = text,
            modifier = modifier,
            color = MainTheme.colors.onSurface,
        )
    }
}

@Composable
private fun RichQuote(
    blocks: List<TaskRichTextBlock>,
    attachments: List<TaskTimelineAttachmentUi>,
    modifier: Modifier = Modifier,
) {
    if (blocks.isEmpty()) return

    CardOutlined(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(MainTheme.colors.outlineVariant),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DisableSelection {
                    TextLabelMedium(
                        text = "Quoted content",
                        color = MainTheme.colors.onSurfaceVariant,
                    )
                }
                blocks.forEach { block ->
                    TaskRichTextBlockView(
                        block = block,
                        attachments = attachments,
                    )
                }
            }
        }
    }
}

@Composable
private fun RichCodeBlock(
    text: String,
    languageHint: String?,
    modifier: Modifier = Modifier,
) {
    if (text.isBlank()) return

    CardOutlined(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            languageHint?.takeIf(String::isNotBlank)?.let { hint ->
                TextLabelMedium(
                    text = hint,
                    color = MainTheme.colors.onSurfaceVariant,
                )
            }
            TextBodySmall(
                text = text,
                color = MainTheme.colors.onSurface,
            )
        }
    }
}

@Composable
private fun RichList(
    items: List<List<TaskRichTextBlock>>,
    attachments: List<TaskTimelineAttachmentUi>,
    modifier: Modifier = Modifier,
    markerForIndex: (Int) -> String,
) {
    if (items.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEachIndexed { index, itemBlocks ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextBodyMedium(
                    text = markerForIndex(index),
                    color = MainTheme.colors.onSurfaceVariant,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemBlocks.forEach { block ->
                        TaskRichTextBlockView(
                            block = block,
                            attachments = attachments,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RichTable(
    table: TaskRichTextBlock.Table,
    modifier: Modifier = Modifier,
) {
    CardOutlined(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (table.headers.isNotEmpty()) {
                TextLabelMedium(
                    text = table.headers.joinToString(separator = " | ") { cell -> cell.toPlainText() },
                    color = MainTheme.colors.onSurfaceVariant,
                )
                app.k9mail.core.ui.compose.designsystem.atom.DividerHorizontal()
            }

            table.rows.forEachIndexed { index, row ->
                TextBodySmall(
                    text = row.joinToString(separator = " | ") { cell -> cell.toPlainText() },
                    modifier = Modifier.testTag("TaskRichTextTableRow:$index"),
                    color = MainTheme.colors.onSurface,
                )
            }
        }
    }
}

@Composable
private fun RichInlineImage(
    image: TaskRichTextBlock.InlineImage,
    attachments: List<TaskTimelineAttachmentUi>,
    modifier: Modifier = Modifier,
) {
    val attachment = image.attachmentId?.let { attachmentId ->
        attachments.firstOrNull { candidate -> candidate.id == attachmentId }
    }
    val previewUriString = attachment
        ?.takeIf { it.isImage && !image.isSvg }
        ?.internalUriString

    if (previewUriString != null) {
        RichInlineImagePreview(
            image = image,
            attachment = attachment,
            previewUriString = previewUriString,
            modifier = modifier,
        )
    } else {
        RichInlineImageFallback(
            image = image,
            attachment = attachment,
            modifier = modifier,
        )
    }
}

@Composable
private fun RichInlineImagePreview(
    image: TaskRichTextBlock.InlineImage,
    attachment: TaskTimelineAttachmentUi?,
    previewUriString: String,
    modifier: Modifier = Modifier,
) {
    CardOutlined(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DisableSelection {
                TextLabelMedium(
                    text = "Inline image",
                    color = MainTheme.colors.onSurfaceVariant,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(INLINE_IMAGE_PREVIEW_ASPECT_RATIO)
                    .background(MainTheme.colors.surfaceContainerHighest)
                    .testTag("TaskRichTextInlineImagePreview:${image.testKey()}"),
                contentAlignment = Alignment.Center,
            ) {
                RemoteImage(
                    url = previewUriString,
                    modifier = Modifier.fillMaxSize(),
                    contentDescription = image.altText
                        ?: image.caption
                        ?: attachment?.displayName
                        ?: "TaskMail inline image preview",
                    contentScale = ContentScale.Fit,
                    placeholder = {
                        InlineImagePreviewPlaceholder()
                    },
                    previewPlaceholder = rememberPreviewPlaceholder(
                        image = Icons.Outlined.Image,
                        tint = MainTheme.colors.onSurfaceVariant,
                        padding = 24.dp,
                    ),
                )
            }
            inlineImageSupportingText(
                image = image,
                attachment = attachment,
                includeGenericFallback = false,
            )?.let { supportingText ->
                TextBodySmall(
                    text = supportingText,
                    color = MainTheme.colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RichInlineImageFallback(
    image: TaskRichTextBlock.InlineImage,
    attachment: TaskTimelineAttachmentUi?,
    modifier: Modifier = Modifier,
) {
    CardOutlined(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .testTag("TaskRichTextInlineImageFallback:${image.testKey()}"),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            DisableSelection {
                TextLabelMedium(
                    text = if (image.isSvg) "Inline SVG" else "Inline image",
                    color = MainTheme.colors.onSurfaceVariant,
                )
            }
            TextBodyMedium(
                text = inlineImageSupportingText(
                    image = image,
                    attachment = attachment,
                    includeGenericFallback = true,
                ) ?: "This message contains an inline image preview.",
                color = MainTheme.colors.onSurface,
            )
        }
    }
}

@Composable
private fun InlineImagePreviewPlaceholder(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Image,
            contentDescription = null,
            tint = MainTheme.colors.onSurfaceVariant,
            modifier = Modifier.width(48.dp),
        )
    }
}

@Composable
private fun RichUnsupportedHtml(
    fallbackText: String,
    modifier: Modifier = Modifier,
) {
    if (fallbackText.isBlank()) return

    TextBodySmall(
        text = fallbackText,
        modifier = modifier,
        color = MainTheme.colors.onSurfaceVariant,
    )
}

@Composable
private fun buildRichText(inlines: List<TaskRichTextInline>): AnnotatedString {
    val strongStyle = SpanStyle(fontWeight = FontWeight.Bold)
    val emphasisStyle = SpanStyle(fontStyle = FontStyle.Italic)
    val codeStyle = SpanStyle(
        fontFamily = FontFamily.Monospace,
        background = MainTheme.colors.surfaceContainerHighest,
    )
    val linkStyle = SpanStyle(
        color = MainTheme.colors.primary,
        textDecoration = TextDecoration.Underline,
    )

    return buildAnnotatedString {
        inlines.forEach { inline ->
            when (inline) {
                is TaskRichTextInline.Text -> append(inline.text)
                is TaskRichTextInline.Strong -> withStyle(strongStyle) { append(inline.text) }
                is TaskRichTextInline.Emphasis -> withStyle(emphasisStyle) { append(inline.text) }
                is TaskRichTextInline.Code -> withStyle(codeStyle) { append(inline.text) }
                is TaskRichTextInline.Link -> withStyle(linkStyle) { append(inline.text) }
            }
        }
    }
}

private fun inlineImageSupportingText(
    image: TaskRichTextBlock.InlineImage,
    attachment: TaskTimelineAttachmentUi?,
    includeGenericFallback: Boolean,
): String? {
    return image.altText
        ?.takeIf { it.isNotBlank() }
        ?: image.caption?.takeIf { it.isNotBlank() }
        ?: attachment?.displayName?.takeIf { it.isNotBlank() }
        ?: image.contentId?.let { contentId -> "content-id: $contentId" }
        ?: if (includeGenericFallback) "This message contains an inline image preview." else null
}

private fun TaskRichTextBlock.InlineImage.testKey(): String {
    return attachmentId ?: contentId ?: "unknown"
}

private const val INLINE_IMAGE_PREVIEW_ASPECT_RATIO = 4f / 3f

private fun List<TaskRichTextInline>.toPlainText(): String {
    return buildString {
        forEach { inline ->
            when (inline) {
                is TaskRichTextInline.Text -> append(inline.text)
                is TaskRichTextInline.Strong -> append(inline.text)
                is TaskRichTextInline.Emphasis -> append(inline.text)
                is TaskRichTextInline.Code -> append(inline.text)
                is TaskRichTextInline.Link -> append(inline.text)
            }
        }
    }
}
