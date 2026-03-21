package net.thunderbird.feature.taskmail.internal.data.cache

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import net.thunderbird.feature.taskmail.internal.domain.model.TaskBodyRenderMode
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailStatusLabel
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMessageAttachment
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMessageBody
import net.thunderbird.feature.taskmail.internal.domain.model.TaskRichTextBlock
import net.thunderbird.feature.taskmail.internal.domain.model.TaskRichTextDocument
import net.thunderbird.feature.taskmail.internal.domain.model.TaskRichTextInline
import net.thunderbird.feature.taskmail.internal.domain.model.TaskTimelineDirection
import net.thunderbird.feature.taskmail.internal.domain.model.TaskTimelineItem
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskQuestionCapsule
import net.thunderbird.feature.taskmail.internal.preview.TaskMailPreviewData

class TaskSessionDetailJsonCodecTest {

    @Test
    @Suppress("LongMethod")
    fun `encode and decode should round trip rich detail snapshots`() {
        val codec = TaskSessionDetailJsonCodec()
        val firstQuestion = TaskQuestionCapsule(
            questionSetId = "phase2_clarifications",
            questionId = "phase2_entry_position",
            questionType = "single_choice",
            questionText = "Where should the Tasks drawer entry be placed?",
            choices = listOf("top", "below"),
            choiceLabels = mapOf(
                "top" to "Account list top",
                "below" to "Account list bottom",
            ),
        )
        val secondQuestion = TaskQuestionCapsule(
            questionSetId = "phase2_clarifications",
            questionId = "phase2_icon_strings",
            questionType = "single_choice",
            questionText = "Who provides icon and string resources?",
            choices = listOf("provide", "reuse"),
            required = false,
        )
        val detail = TaskMailPreviewData.sessionDetails.first().copy(
            question = secondQuestion,
            pendingQuestions = listOf(firstQuestion, secondQuestion),
            timeline = listOf(
                TaskTimelineItem(
                    id = "timeline-1",
                    timestamp = 123L,
                    direction = TaskTimelineDirection.System,
                    statusLabel = TaskMailStatusLabel.Running,
                    summary = "Attached the refreshed workspace chart.",
                    body = TaskMessageBody(
                        plainTextFallback = "Attached the refreshed workspace chart.",
                        renderMode = TaskBodyRenderMode.RichText,
                        richDocument = TaskRichTextDocument(
                            blocks = listOf(
                                TaskRichTextBlock.Heading(
                                    level = 2,
                                    inlines = listOf(TaskRichTextInline.Text("Latest chart preview")),
                                ),
                                TaskRichTextBlock.Paragraph(
                                    inlines = listOf(
                                        TaskRichTextInline.Text("Attached the refreshed workspace chart as an "),
                                        TaskRichTextInline.Strong("inline PNG"),
                                        TaskRichTextInline.Text("."),
                                    ),
                                ),
                                TaskRichTextBlock.InlineImage(
                                    attachmentId = "content://taskmail/chart-preview",
                                    contentId = "chart-preview",
                                    altText = "Chart preview",
                                    caption = "Updated task chart",
                                    mimeType = "image/png",
                                ),
                                TaskRichTextBlock.Table(
                                    headers = listOf(
                                        listOf(TaskRichTextInline.Text("Metric")),
                                        listOf(TaskRichTextInline.Text("Value")),
                                    ),
                                    rows = listOf(
                                        listOf(
                                            listOf(TaskRichTextInline.Text("Sessions")),
                                            listOf(TaskRichTextInline.Text("12")),
                                        ),
                                    ),
                                ),
                                TaskRichTextBlock.Divider,
                            ),
                        ),
                        sourceHtml = "<h2>Latest chart preview</h2><p>Attached the refreshed workspace chart.</p>",
                    ),
                    attachments = listOf(
                        TaskMessageAttachment(
                            id = "content://taskmail/chart-preview",
                            displayName = "result_chart.png",
                            contentType = "image/png",
                            sizeBytes = 2_048L,
                            isInline = true,
                            isImage = true,
                            contentId = "chart-preview",
                            internalUriString = "content://taskmail/chart-preview",
                            accountUuid = "account-1",
                            folderId = 1L,
                            messageServerId = "server-1",
                            partId = 42L,
                            isContentAvailable = true,
                        ),
                    ),
                    businessEventKeys = listOf(
                        "status/running/2026-03-21T18:00:00",
                        "reply/2026-03-21T18:00:00",
                    ),
                ),
            ),
        )

        val result = codec.decode(codec.encode(detail))

        assertThat(result).isEqualTo(detail)
    }
}
