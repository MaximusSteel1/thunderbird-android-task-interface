package net.thunderbird.feature.taskmail.internal.domain.model

internal data class TaskMessageBody(
    val plainTextFallback: String,
    val renderMode: TaskBodyRenderMode = TaskBodyRenderMode.PlainTextOnly,
    val richDocument: TaskRichTextDocument? = null,
    val sourceHtml: String? = null,
) {
    constructor(
        plainText: String,
        @Suppress("UNUSED_PARAMETER") markdownCandidate: Boolean,
    ) : this(
        plainTextFallback = plainText,
    )

    val plainText: String
        get() = plainTextFallback
}

internal enum class TaskBodyRenderMode {
    PlainTextOnly,
    RichText,
}

internal data class TaskRichTextDocument(
    val blocks: List<TaskRichTextBlock>,
)

internal sealed interface TaskRichTextBlock {
    data class Paragraph(
        val inlines: List<TaskRichTextInline>,
    ) : TaskRichTextBlock

    data class Heading(
        val level: Int,
        val inlines: List<TaskRichTextInline>,
    ) : TaskRichTextBlock

    data class Quote(
        val blocks: List<TaskRichTextBlock>,
    ) : TaskRichTextBlock

    data class CodeBlock(
        val languageHint: String? = null,
        val text: String,
    ) : TaskRichTextBlock

    data class BulletList(
        val items: List<List<TaskRichTextBlock>>,
    ) : TaskRichTextBlock

    data class OrderedList(
        val items: List<List<TaskRichTextBlock>>,
    ) : TaskRichTextBlock

    data class Table(
        val headers: List<List<TaskRichTextInline>>,
        val rows: List<List<List<TaskRichTextInline>>>,
    ) : TaskRichTextBlock

    data class InlineImage(
        val attachmentId: String? = null,
        val contentId: String? = null,
        val altText: String? = null,
        val caption: String? = null,
        val mimeType: String? = null,
        val isSvg: Boolean = false,
    ) : TaskRichTextBlock

    object Divider : TaskRichTextBlock

    data class UnsupportedHtml(
        val fallbackText: String,
    ) : TaskRichTextBlock
}

internal sealed interface TaskRichTextInline {
    data class Text(
        val text: String,
    ) : TaskRichTextInline

    data class Strong(
        val text: String,
    ) : TaskRichTextInline

    data class Emphasis(
        val text: String,
    ) : TaskRichTextInline

    data class Code(
        val text: String,
    ) : TaskRichTextInline

    data class Link(
        val text: String,
        val href: String,
    ) : TaskRichTextInline
}
