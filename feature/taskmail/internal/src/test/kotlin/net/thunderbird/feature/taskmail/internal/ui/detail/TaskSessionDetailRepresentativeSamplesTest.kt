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
            .performScrollToNode(hasText("thunderbird-taskmail-foss-debug.apk"))

        composeTestRule.onNodeWithText("TaskMail External Delivery Sample").assertIsDisplayed()
        composeTestRule.onNodeWithText("thunderbird-taskmail-foss-debug.apk").assertIsDisplayed()
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

        composeTestRule.onNodeWithText("TaskMail Attachment Notice Sample").assertIsDisplayed()
        composeTestRule.onNodeWithText("Attachment Notices").assertIsDisplayed()
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

        composeTestRule.onNodeWithText("TaskMail Failed Long Error Sample").assertIsDisplayed()
        composeTestRule.onNodeWithText("Failure Output").assertIsDisplayed()
    }

    private fun uiState(detail: TaskSessionDetail): TaskSessionDetailUiState {
        val timelineItems = detail.timeline.map { item ->
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
        }.toImmutableList()
        val statusLabel = detail.status.name

        return TaskSessionDetailUiState(
            sessionName = detail.sessionName,
            backend = detail.backend.name,
            status = statusLabel,
            pageMode = statusLabel.toPageModeForTest(),
            repoPath = detail.repoPath,
            workdir = detail.workdir,
            lastSummary = detail.lastSummary,
            resultSummary = TaskResultSummaryUi(
                headline = statusLabel.toHeadlineForTest(),
                supportingText = detail.lastSummary,
                statusLabel = statusLabel,
            ),
            resultBody = timelineItems.findLatestResultBodyCandidate(
                summary = detail.lastSummary,
                statusLabel = statusLabel,
            ),
            pendingQuestions = detail.pendingQuestions.map { question ->
                question.toPendingQuestionUi()
            }.toImmutableList(),
            quickAnswerChoices = detail.pendingQuestions
                .singleOrNull()
                ?.toPendingQuestionUi()
                ?.choices
                ?: persistentListOf(),
            timeline = timelineItems,
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

private fun String.toPageModeForTest(): TaskSessionPageMode {
    return when (uppercase()) {
        "QUEUED", "RUNNING" -> TaskSessionPageMode.ActiveRun
        "WAITINGUSER", "PAUSED" -> TaskSessionPageMode.AwaitingReply
        else -> TaskSessionPageMode.Terminal
    }
}

private fun String.toHeadlineForTest(): String {
    return when (uppercase()) {
        "DONE" -> "Latest run completed"
        "FAILED" -> "Latest run failed"
        "WAITINGUSER" -> "Waiting for your reply"
        "PAUSED" -> "Session is paused"
        "QUEUED" -> "Task is queued"
        "RUNNING" -> "Run in progress"
        else -> "Latest session state"
    }
}
