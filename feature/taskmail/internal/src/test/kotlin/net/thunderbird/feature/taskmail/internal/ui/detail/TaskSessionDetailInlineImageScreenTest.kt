package net.thunderbird.feature.taskmail.internal.ui.detail

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import kotlinx.collections.immutable.persistentListOf
import net.thunderbird.core.ui.compose.theme2.k9mail.K9MailTheme2
import net.thunderbird.feature.taskmail.internal.domain.model.TaskBodyRenderMode
import net.thunderbird.feature.taskmail.internal.domain.model.TaskRichTextBlock
import net.thunderbird.feature.taskmail.internal.domain.model.TaskRichTextDocument
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
internal class TaskSessionDetailInlineImageScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `content should render raster inline image preview when attachment uri is available`() {
        composeTestRule.setContent {
            K9MailTheme2 {
                TaskSessionDetailContent(
                    state = TaskSessionDetailContract.State(
                        detail = inlineImageDetail(
                            richDocument = TaskRichTextDocument(
                                blocks = listOf(
                                    TaskRichTextBlock.InlineImage(
                                        attachmentId = "content://taskmail/result-chart",
                                        contentId = "chart-preview",
                                        altText = "Result chart",
                                        mimeType = "image/png",
                                    ),
                                ),
                            ),
                            attachment = TaskTimelineAttachmentUi(
                                id = "content://taskmail/result-chart",
                                displayName = "result_chart.png",
                                contentType = "image/png",
                                isInline = true,
                                isImage = true,
                                internalUriString = "content://taskmail/result-chart",
                                isActionAvailable = true,
                            ),
                        ),
                    ),
                    onEvent = {},
                    onPickAttachments = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag("TaskSessionDetailList")
            .performScrollToNode(hasTestTag("TaskRichTextInlineImagePreview:content://taskmail/result-chart"))

        composeTestRule
            .onNodeWithTag("TaskRichTextInlineImagePreview:content://taskmail/result-chart")
            .assertIsDisplayed()
    }

    @Test
    fun `content should keep svg inline image on fallback path`() {
        composeTestRule.setContent {
            K9MailTheme2 {
                TaskSessionDetailContent(
                    state = TaskSessionDetailContract.State(
                        detail = inlineImageDetail(
                            richDocument = TaskRichTextDocument(
                                blocks = listOf(
                                    TaskRichTextBlock.InlineImage(
                                        attachmentId = "content://taskmail/result-chart",
                                        contentId = "chart-preview",
                                        altText = "Result chart",
                                        mimeType = "image/svg",
                                        isSvg = true,
                                    ),
                                ),
                            ),
                            attachment = TaskTimelineAttachmentUi(
                                id = "content://taskmail/result-chart",
                                displayName = "result_chart.svg",
                                contentType = "image/svg",
                                isInline = true,
                                isImage = true,
                                internalUriString = "content://taskmail/result-chart",
                                isActionAvailable = true,
                            ),
                        ),
                    ),
                    onEvent = {},
                    onPickAttachments = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag("TaskSessionDetailList")
            .performScrollToNode(hasTestTag("TaskRichTextInlineImageFallback:content://taskmail/result-chart"))

        composeTestRule
            .onNodeWithTag("TaskRichTextInlineImageFallback:content://taskmail/result-chart")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Inline SVG").assertIsDisplayed()
    }

    private fun inlineImageDetail(
        richDocument: TaskRichTextDocument,
        attachment: TaskTimelineAttachmentUi,
    ): TaskSessionDetailUiState {
        return TaskSessionDetailUiState(
            sessionName = "Build TaskMail Phase 1",
            backend = "Codex",
            status = "Done",
            repoPath = "E:/projects/android_task_manager",
            workdir = "feature/taskmail",
            timeline = persistentListOf(
                TaskTimelineItemUi(
                    id = "timeline_rich_image_001",
                    timestamp = 1L,
                    direction = "System",
                    statusLabel = "Done",
                    summary = "Rich image projection available",
                    plainText = "Fallback plain text",
                    renderMode = TaskBodyRenderMode.RichText,
                    richDocument = richDocument,
                    attachments = persistentListOf(attachment),
                ),
            ),
        )
    }
}
