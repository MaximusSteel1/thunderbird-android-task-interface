package net.thunderbird.feature.taskmail.internal.ui.detail

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import net.thunderbird.core.ui.compose.theme2.k9mail.K9MailTheme2
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionDetail
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskQuestionCapsule
import net.thunderbird.feature.taskmail.internal.preview.TaskMailPreviewData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
internal class TaskSessionDetailRepresentativeSamplesTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `content should render representative external delivery sample`() {
        composeTestRule.setContent {
            K9MailTheme2 {
                TaskSessionDetailContent(
                    state = TaskSessionDetailContract.State(
                        detail = uiState(TaskMailPreviewData.richExternalDeliveriesSessionDetail),
                    ),
                    onEvent = {},
                    onPickAttachments = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag("TaskSessionDetailList")
            .performScrollToNode(hasText("External Deliveries"))

        composeTestRule.onNodeWithText("External Deliveries").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "Delivered via COS (46.7 MB, expires 2026-03-26T16:25:17+00:00)",
            substring = true,
        ).assertIsDisplayed()
    }

    @Test
    fun `content should render representative attachment notice sample`() {
        composeTestRule.setContent {
            K9MailTheme2 {
                TaskSessionDetailContent(
                    state = TaskSessionDetailContract.State(
                        detail = uiState(TaskMailPreviewData.richAttachmentNoticesSessionDetail),
                    ),
                    onEvent = {},
                    onPickAttachments = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag("TaskSessionDetailList")
            .performScrollToNode(hasText("Attachment Notices"))

        composeTestRule.onNodeWithText("Attachment Notices").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "COS default domain blocks direct APK distribution.",
            substring = true,
        ).assertIsDisplayed()
    }

    @Test
    fun `content should render representative failed long error sample`() {
        composeTestRule.setContent {
            K9MailTheme2 {
                TaskSessionDetailContent(
                    state = TaskSessionDetailContract.State(
                        detail = uiState(TaskMailPreviewData.richFailedLongErrorSessionDetail),
                    ),
                    onEvent = {},
                    onPickAttachments = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag("TaskSessionDetailList")
            .performScrollToNode(hasText("Failure Output"))

        composeTestRule.onNodeWithText("Failure Output").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "Codex Exec exited with code 1",
            substring = true,
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "at async main (file:///E:/projects/mail_based_task_manager/" +
                "scripts/codex_sdk_sidecar/dist/index.js:151:22)",
            substring = true,
        ).assertIsDisplayed()
    }

    private fun uiState(detail: TaskSessionDetail): TaskSessionDetailUiState {
        return TaskSessionDetailUiState(
            sessionName = detail.sessionName,
            backend = detail.backend.name,
            status = detail.status.name,
            repoPath = detail.repoPath,
            workdir = detail.workdir,
            lastSummary = detail.lastSummary,
            pendingQuestions = detail.pendingQuestions.map { question ->
                question.toPendingQuestionUi()
            }.toImmutableList(),
            quickAnswerChoices = detail.pendingQuestions
                .singleOrNull()
                ?.toPendingQuestionUi()
                ?.choices
                ?: persistentListOf(),
            timeline = detail.timeline.map { item ->
                TaskTimelineItemUi(
                    id = item.id,
                    timestamp = item.timestamp,
                    direction = item.direction.name,
                    statusLabel = item.statusLabel?.name,
                    summary = item.summary,
                    plainText = item.body.plainText,
                    renderMode = item.body.renderMode,
                    richDocument = item.body.richDocument,
                )
            }.toImmutableList(),
        )
    }

    private fun TaskQuestionCapsule.toPendingQuestionUi(): TaskPendingQuestionUi {
        return TaskPendingQuestionUi(
            questionId = questionId,
            questionText = questionText,
            choices = choices.map { choice ->
                TaskPendingQuestionChoiceUi(
                    value = choice,
                    label = choiceLabels[choice] ?: choice,
                )
            }.toImmutableList(),
            isRequired = required,
        )
    }
}
