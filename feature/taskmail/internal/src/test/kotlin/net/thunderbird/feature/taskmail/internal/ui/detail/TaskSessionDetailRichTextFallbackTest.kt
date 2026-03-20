package net.thunderbird.feature.taskmail.internal.ui.detail

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.thunderbird.core.ui.compose.theme2.k9mail.K9MailTheme2
import net.thunderbird.feature.taskmail.internal.domain.model.TaskBodyRenderMode
import net.thunderbird.feature.taskmail.internal.domain.model.TaskRichTextBlock
import net.thunderbird.feature.taskmail.internal.domain.model.TaskRichTextDocument
import net.thunderbird.feature.taskmail.internal.domain.model.TaskRichTextInline
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TaskSessionDetailRichTextFallbackTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `content should render unmatched image fallback text without inline image chrome`() {
        composeTestRule.setContent {
            K9MailTheme2 {
                TaskSessionDetailContent(
                    state = TaskSessionDetailContract.State(
                        detail = replyCapableDetail(
                            timeline = persistentListOf(
                                TaskTimelineItemUi(
                                    id = "timeline_fallback_001",
                                    timestamp = 1L,
                                    direction = "System",
                                    statusLabel = "Done",
                                    summary = "Fallback projection available",
                                    plainText = "Fallback plain text",
                                    renderMode = TaskBodyRenderMode.RichText,
                                    richDocument = TaskRichTextDocument(
                                        blocks = listOf(
                                            TaskRichTextBlock.Heading(
                                                level = 2,
                                                inlines = listOf(
                                                    TaskRichTextInline.Text("Remote preview fallback"),
                                                ),
                                            ),
                                            TaskRichTextBlock.UnsupportedHtml(
                                                fallbackText = "External chart preview unavailable.",
                                            ),
                                        ),
                                    ),
                                ),
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
            .performScrollToNode(hasText("External chart preview unavailable."))

        composeTestRule.onNodeWithText("Remote preview fallback").assertIsDisplayed()
        composeTestRule.onNodeWithText("External chart preview unavailable.").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Inline image").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Inline SVG").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Fallback plain text").assertCountEquals(0)
    }

    private fun replyCapableDetail(
        timeline: ImmutableList<TaskTimelineItemUi>,
    ): TaskSessionDetailUiState {
        return TaskSessionDetailUiState(
            sessionName = "Build TaskMail Phase 1",
            backend = "Codex",
            status = "WaitingUser",
            repoPath = "E:/projects/android_task_manager",
            workdir = "feature/taskmail",
            lastSummary = "Parser layer is complete.",
            pendingQuestions = persistentListOf(
                TaskPendingQuestionUi(
                    questionId = "question_001",
                    questionText = "Should I proceed?",
                    choices = persistentListOf(
                        TaskPendingQuestionChoiceUi(value = "approve"),
                        TaskPendingQuestionChoiceUi(value = "decline"),
                    ),
                ),
            ),
            quickAnswerChoices = persistentListOf(
                TaskPendingQuestionChoiceUi(value = "approve", label = "approve"),
                TaskPendingQuestionChoiceUi(value = "decline", label = "decline"),
            ),
            canReply = true,
            canQueryStatus = true,
            timeline = timeline,
        )
    }
}
