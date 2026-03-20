package net.thunderbird.feature.taskmail.internal.data

import net.thunderbird.feature.taskmail.internal.domain.model.TaskMessageAttachment
import net.thunderbird.feature.taskmail.internal.domain.model.TaskRichTextBlock
import net.thunderbird.feature.taskmail.internal.domain.model.TaskRichTextDocument
import net.thunderbird.feature.taskmail.internal.domain.model.TaskRichTextInline
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

@Suppress("TooManyFunctions")
internal class TaskMailRichTextProjector {

    fun project(
        html: String,
        attachments: List<TaskMessageAttachment>,
    ): TaskRichTextDocument? {
        val article = Jsoup.parse(html).selectFirst("article.task-mail") ?: return null
        val blocks = projectChildBlocks(
            element = article,
            attachments = attachments,
        )

        return blocks.takeIf { it.isNotEmpty() }?.let(::TaskRichTextDocument)
    }

    private fun projectChildBlocks(
        element: Element,
        attachments: List<TaskMessageAttachment>,
    ): List<TaskRichTextBlock> {
        return element.childNodes().flatMap { node ->
            projectNode(
                node = node,
                attachments = attachments,
            )
        }
    }

    private fun projectNode(
        node: Node,
        attachments: List<TaskMessageAttachment>,
    ): List<TaskRichTextBlock> {
        return when (node) {
            is TextNode -> node.text()
                .takeIf { it.isNotBlank() }
                ?.let { text ->
                    listOf(
                        TaskRichTextBlock.Paragraph(
                            inlines = listOf(TaskRichTextInline.Text(text.trim())),
                        ),
                    )
                }
                .orEmpty()

            is Element -> projectElement(node, attachments)
            else -> emptyList()
        }
    }

    private fun projectElement(
        element: Element,
        attachments: List<TaskMessageAttachment>,
    ): List<TaskRichTextBlock> {
        return when (element.tagName().lowercase()) {
            "article", "section", "div" -> projectChildBlocks(element, attachments)
            "p" -> projectParagraph(
                element = element,
                attachments = attachments,
            )
            "h1", "h2", "h3", "h4", "h5", "h6" -> listOf(
                TaskRichTextBlock.Heading(
                    level = element.tagName().drop(1).toIntOrNull() ?: 2,
                    inlines = projectInlines(element.childNodes()),
                ),
            )

            "blockquote" -> listOf(
                TaskRichTextBlock.Quote(
                    blocks = projectChildBlocks(element, attachments).ifEmpty {
                        listOf(
                            TaskRichTextBlock.Paragraph(
                                inlines = inlineText(element.text()),
                            ),
                        )
                    },
                ),
            )

            "pre" -> listOf(
                TaskRichTextBlock.CodeBlock(
                    languageHint = element.classNames().firstOrNull(),
                    text = element.wholeText().takeIf { it.isNotBlank() } ?: element.text(),
                ),
            )

            "ul" -> listOf(
                TaskRichTextBlock.BulletList(
                    items = projectListItems(element, attachments),
                ),
            )

            "ol" -> listOf(
                TaskRichTextBlock.OrderedList(
                    items = projectListItems(element, attachments),
                ),
            )

            "table" -> listOf(projectTable(element))
            "img" -> listOf(projectInlineImage(element, attachments))
            "hr" -> listOf(TaskRichTextBlock.Divider)
            else -> projectFallback(element, attachments)
        }
    }

    private fun projectParagraph(
        element: Element,
        attachments: List<TaskMessageAttachment>,
    ): List<TaskRichTextBlock> {
        val inlineImage = element.children()
            .singleOrNull()
            ?.takeIf { child -> child.tagName().equals("img", ignoreCase = true) && element.ownText().isBlank() }

        return if (inlineImage != null) {
            listOf(projectInlineImage(inlineImage, attachments))
        } else {
            listOf(
                TaskRichTextBlock.Paragraph(
                    inlines = projectInlines(element.childNodes()),
                ),
            )
        }
    }

    private fun projectListItems(
        element: Element,
        attachments: List<TaskMessageAttachment>,
    ): List<List<TaskRichTextBlock>> {
        return element.children()
            .filter { child -> child.tagName().equals("li", ignoreCase = true) }
            .map { item ->
                projectChildBlocks(item, attachments).ifEmpty {
                    listOf(
                        TaskRichTextBlock.Paragraph(
                            inlines = inlineText(item.text()),
                        ),
                    )
                }
            }
    }

    private fun projectTable(element: Element): TaskRichTextBlock.Table {
        val rows = element.select("tr")
        val headerRow = rows.firstOrNull { row -> row.select("th").isNotEmpty() }
        val headers = headerRow
            ?.select("th")
            ?.map { cell -> inlineText(cell.text()) }
            .orEmpty()
        val bodyRows = rows
            .filterNot { row -> row == headerRow }
            .map { row ->
                row.select("th,td").map { cell -> inlineText(cell.text()) }
            }

        return TaskRichTextBlock.Table(
            headers = headers,
            rows = bodyRows,
        )
    }

    private fun projectInlineImage(
        element: Element,
        attachments: List<TaskMessageAttachment>,
    ): TaskRichTextBlock {
        val src = element.attr("src").trim()
        val normalizedContentId = src
            .takeIf { it.startsWith("cid:", ignoreCase = true) }
            ?.removePrefix("cid:")
            ?.removePrefix("CID:")
            ?.normalizeContentId()
        val matchingAttachment = normalizedContentId?.let { contentId ->
            attachments.firstOrNull { attachment ->
                attachment.contentId.normalizeContentId() == contentId
            }
        }
        if (normalizedContentId == null || matchingAttachment == null) {
            return TaskRichTextBlock.UnsupportedHtml(
                fallbackText = element.attr("alt")
                    .trim()
                    .ifBlank { element.attr("title").trim() }
                    .ifBlank { "Inline image preview unavailable." },
            )
        }

        val mimeType = matchingAttachment.contentType
            ?: element.attr("type").takeIf { it.isNotBlank() }
        val altText = element.attr("alt").trim().takeIf { it.isNotBlank() }

        return TaskRichTextBlock.InlineImage(
            attachmentId = matchingAttachment.id,
            contentId = normalizedContentId,
            altText = altText,
            caption = element.attr("title").trim().takeIf { it.isNotBlank() },
            mimeType = mimeType,
            isSvg = mimeType.isSvgMimeType() ||
                src.contains(".svg", ignoreCase = true),
        )
    }

    private fun projectFallback(
        element: Element,
        attachments: List<TaskMessageAttachment>,
    ): List<TaskRichTextBlock> {
        if (element.children().isNotEmpty()) {
            val nestedBlocks = projectChildBlocks(element, attachments)
            if (nestedBlocks.isNotEmpty()) return nestedBlocks
        }

        val fallbackText = element.text().trim()
        return fallbackText
            .takeIf { it.isNotBlank() }
            ?.let { listOf(TaskRichTextBlock.UnsupportedHtml(fallbackText = it)) }
            .orEmpty()
    }

    private fun projectInlines(nodes: List<Node>): List<TaskRichTextInline> {
        val projected = nodes.flatMap(::projectInlineNode)
        return mergeAdjacentText(projected)
            .ifEmpty { inlineText("") }
    }

    private fun projectInlineNode(node: Node): List<TaskRichTextInline> {
        return when (node) {
            is TextNode -> inlineText(node.text())
            is Element -> when (node.tagName().lowercase()) {
                "strong", "b" -> inlineText(node.text()).map { inline ->
                    TaskRichTextInline.Strong(text = inline.textValue())
                }

                "em", "i" -> inlineText(node.text()).map { inline ->
                    TaskRichTextInline.Emphasis(text = inline.textValue())
                }

                "code" -> inlineText(node.text()).map { inline ->
                    TaskRichTextInline.Code(text = inline.textValue())
                }

                "a" -> {
                    val href = node.attr("href").trim()
                    val text = node.text().trim().ifBlank { href }
                    if (href.isBlank() || text.isBlank()) {
                        inlineText(node.text())
                    } else {
                        listOf(TaskRichTextInline.Link(text = text, href = href))
                    }
                }

                "br" -> listOf(TaskRichTextInline.Text(text = "\n"))
                else -> projectInlines(node.childNodes())
            }

            else -> emptyList()
        }
    }

    private fun inlineText(text: String): List<TaskRichTextInline> {
        return text
            .takeIf { it.isNotEmpty() }
            ?.let { listOf(TaskRichTextInline.Text(text = it)) }
            .orEmpty()
    }

    private fun mergeAdjacentText(inlines: List<TaskRichTextInline>): List<TaskRichTextInline> {
        val merged = mutableListOf<TaskRichTextInline>()
        inlines.forEach { inline ->
            val previous = merged.lastOrNull()
            if (previous is TaskRichTextInline.Text && inline is TaskRichTextInline.Text) {
                merged[merged.lastIndex] = TaskRichTextInline.Text(previous.text + inline.text)
            } else {
                merged += inline
            }
        }
        return merged.filterNot { inline -> inline is TaskRichTextInline.Text && inline.text.isEmpty() }
    }

    private fun TaskRichTextInline.textValue(): String {
        return when (this) {
            is TaskRichTextInline.Text -> text
            is TaskRichTextInline.Strong -> text
            is TaskRichTextInline.Emphasis -> text
            is TaskRichTextInline.Code -> text
            is TaskRichTextInline.Link -> text
        }
    }

    private fun String?.normalizeContentId(): String? {
        return this
            ?.trim()
            ?.removePrefix("<")
            ?.removeSuffix(">")
            ?.takeIf { it.isNotBlank() }
            ?.lowercase()
    }

    private fun String?.isSvgMimeType(): Boolean {
        return this.equals("image/svg+xml", ignoreCase = true) ||
            this.equals("image/svg", ignoreCase = true)
    }
}
