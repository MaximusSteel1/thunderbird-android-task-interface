package net.thunderbird.feature.taskmail.internal.ui.detail

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.thunderbird.core.ui.compose.theme2.k9mail.K9MailTheme2
import net.thunderbird.feature.taskmail.internal.domain.model.TaskReplyAttachment
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TaskSessionDetailScreenKtTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `content should render question and timeline text`() {
        composeTestRule.setContent {
            K9MailTheme2 {
                TaskSessionDetailContent(
                    state = TaskSessionDetailContract.State(
                        detail = TaskSessionDetailUiState(
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
                                        TaskPendingQuestionChoiceUi(value = "yes"),
                                        TaskPendingQuestionChoiceUi(value = "no"),
                                    ),
                                ),
                            ),
                            quickAnswerChoices = persistentListOf("yes", "no"),
                            timeline = persistentListOf(
                                TaskTimelineItemUi(
                                    id = "timeline_001",
                                    timestamp = 1L,
                                    direction = "System",
                                    statusLabel = "Question",
                                    summary = "Need confirmation",
                                    plainText = "Parser layer is complete. Waiting for the next step.",
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
            .performScrollToNode(hasText("Should I proceed?"))

        composeTestRule.onNodeWithText("Should I proceed?").assertIsDisplayed()
        composeTestRule.onNodeWithText("yes").assertIsDisplayed()
        composeTestRule.onNodeWithText("no").assertIsDisplayed()
        composeTestRule
            .onNodeWithTag("TaskSessionDetailList")
            .performScrollToNode(hasText("Parser layer is complete. Waiting for the next step."))
        composeTestRule.onNodeWithText("Parser layer is complete. Waiting for the next step.").assertIsDisplayed()
    }

    @Test
    fun `content should render multi question composer without quick answers`() {
        composeTestRule.setContent {
            K9MailTheme2 {
                TaskSessionDetailContent(
                    state = TaskSessionDetailContract.State(
                        draftText = "Answers:\nquestion_001: yes",
                        detail = replyCapableDetail(
                            pendingQuestions = persistentListOf(
                                TaskPendingQuestionUi(
                                    questionId = "question_001",
                                    questionText = "Should I proceed?",
                                    choices = persistentListOf(
                                        TaskPendingQuestionChoiceUi(value = "yes"),
                                        TaskPendingQuestionChoiceUi(value = "no"),
                                    ),
                                ),
                                TaskPendingQuestionUi(
                                    questionId = "question_002",
                                    questionText = "Who owns the icons?",
                                    choices = persistentListOf(
                                        TaskPendingQuestionChoiceUi(
                                            value = "provide",
                                            label = "You provide",
                                        ),
                                        TaskPendingQuestionChoiceUi(
                                            value = "reuse",
                                            label = "Reuse existing",
                                        ),
                                    ),
                                ),
                            ),
                            quickAnswerChoices = persistentListOf(),
                            requiresStructuredReply = true,
                            structuredReplyTemplate = "Answers:\nquestion_001:\nquestion_002:",
                            replyLabel = "Answers",
                            replySupportingText = MULTI_QUESTION_REPLY_SUPPORTING_TEXT,
                        ),
                    ),
                    onEvent = {},
                    onPickAttachments = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag("TaskSessionDetailList")
            .performScrollToNode(hasText("question_id: question_002"))

        composeTestRule.onNodeWithText("question_id: question_001").assertIsDisplayed()
        composeTestRule.onNodeWithText("question_id: question_002").assertIsDisplayed()
        composeTestRule
            .onNodeWithTag("TaskSessionDetailList")
            .performScrollToNode(hasText("Send answers"))
        composeTestRule.onNodeWithText("Send answers").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Quick answers").assertCountEquals(0)
    }

    @Test
    fun `content should show reply unavailable reason instead of dead controls`() {
        composeTestRule.setContent {
            K9MailTheme2 {
                TaskSessionDetailContent(
                    state = TaskSessionDetailContract.State(
                        detail = replyCapableDetail(
                            canReply = false,
                            canQueryStatus = false,
                            replyUnavailableReason = "Reply unavailable because this session spans multiple accounts.",
                        ),
                    ),
                    onEvent = {},
                    onPickAttachments = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag("TaskSessionDetailList")
            .performScrollToNode(hasText("Reply unavailable because this session spans multiple accounts."))

        composeTestRule
            .onAllNodesWithText("Reply unavailable because this session spans multiple accounts.")
            .assertCountEquals(1)
        composeTestRule.onAllNodesWithText("Send reply").assertCountEquals(0)
    }

    @Test
    fun `content should show send failure without clearing detail content`() {
        composeTestRule.setContent {
            K9MailTheme2 {
                TaskSessionDetailContent(
                    state = TaskSessionDetailContract.State(
                        sendError = "Failed to send TaskMail reply.",
                        detail = replyCapableDetail(),
                    ),
                    onEvent = {},
                    onPickAttachments = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag("TaskSessionDetailList")
            .performScrollToNode(hasText("TaskMail reply failed"))

        composeTestRule.onAllNodesWithText("TaskMail reply failed").assertCountEquals(1)
        composeTestRule.onAllNodesWithText("Failed to send TaskMail reply.").assertCountEquals(1)
        composeTestRule
            .onNodeWithTag("TaskSessionDetailList")
            .performScrollToNode(hasText("Parser layer is complete. Waiting for the next step."))
        composeTestRule.onAllNodesWithText("Parser layer is complete. Waiting for the next step.").assertCountEquals(1)
    }

    @Test
    fun `content should render timeline attachments`() {
        composeTestRule.setContent {
            K9MailTheme2 {
                TaskSessionDetailContent(
                    state = TaskSessionDetailContract.State(
                        detail = replyCapableDetail(
                            timeline = persistentListOf(
                                TaskTimelineItemUi(
                                    id = "timeline_001",
                                    timestamp = 1L,
                                    direction = "System",
                                    statusLabel = "Done",
                                    summary = "Attached the generated chart.",
                                    plainText = "The latest report is attached below.",
                                    attachments = persistentListOf(
                                        TaskTimelineAttachmentUi(
                                            id = "content://taskmail/result-chart",
                                            displayName = "result_chart.png",
                                            contentType = "image/png",
                                            isInline = true,
                                            isImage = true,
                                            isActionAvailable = true,
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
            .performScrollToNode(hasTestTag("TimelineAttachmentOpen:content://taskmail/result-chart"))

        composeTestRule.onNodeWithText("1 attachment").assertIsDisplayed()
        composeTestRule.onNodeWithText("result_chart.png").assertIsDisplayed()
        composeTestRule.onNodeWithText("Inline image").assertIsDisplayed()
        composeTestRule.onNodeWithTag("TimelineAttachmentOpen:content://taskmail/result-chart").assertIsDisplayed()
        composeTestRule.onNodeWithTag("TimelineAttachmentSave:content://taskmail/result-chart").assertIsDisplayed()
    }

    @Test
    fun `content should dispatch back clicked when back is pressed`() {
        var backClicked = false

        composeTestRule.setContent {
            K9MailTheme2 {
                TaskSessionDetailContent(
                    state = TaskSessionDetailContract.State(),
                    onEvent = { event ->
                        if (event == TaskSessionDetailContract.Event.BackClicked) {
                            backClicked = true
                        }
                    },
                    onPickAttachments = {},
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Navigate up").performClick()

        assertThat(backClicked).isEqualTo(true)
    }

    @Test
    fun `content should render selected reply attachments and disable status query`() {
        composeTestRule.setContent {
            K9MailTheme2 {
                TaskSessionDetailContent(
                    state = TaskSessionDetailContract.State(
                        replyAttachments = persistentListOf(
                            TaskReplyAttachment(
                                id = "uri://report",
                                uriString = "content://taskmail/report",
                                displayName = "final_report.md",
                                contentType = "text/markdown",
                                sizeBytes = 4_096L,
                            ),
                        ),
                        detail = replyCapableDetail(),
                    ),
                    onEvent = {},
                    onPickAttachments = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag("TaskSessionDetailList")
            .performScrollToNode(hasText("final_report.md"))

        composeTestRule.onNodeWithText("final_report.md").assertIsDisplayed()
        composeTestRule.onNodeWithText("Remove").assertIsDisplayed()
        composeTestRule.onNodeWithTag("TaskReplyComposerStatusButton").assertIsNotEnabled()
    }

    @Test
    fun `content should invoke attachment picker callback`() {
        var pickerInvoked = false

        composeTestRule.setContent {
            K9MailTheme2 {
                TaskSessionDetailContent(
                    state = TaskSessionDetailContract.State(
                        detail = replyCapableDetail(),
                    ),
                    onEvent = {},
                    onPickAttachments = {
                        pickerInvoked = true
                    },
                )
            }
        }

        composeTestRule
            .onNodeWithTag("TaskSessionDetailList")
            .performScrollToNode(hasText("Add files"))
        composeTestRule.onNodeWithText("Add files").performClick()

        assertThat(pickerInvoked).isEqualTo(true)
    }

    @Test
    fun `content should dispatch timeline attachment open and save callbacks`() {
        var openedAttachmentId: String? = null
        var savedAttachmentId: String? = null

        composeTestRule.setContent {
            K9MailTheme2 {
                TaskSessionDetailContent(
                    state = TaskSessionDetailContract.State(
                        detail = replyCapableDetail(
                            timeline = persistentListOf(
                                TaskTimelineItemUi(
                                    id = "timeline_001",
                                    timestamp = 1L,
                                    direction = "System",
                                    statusLabel = "Done",
                                    summary = "Attached the generated chart.",
                                    plainText = "The latest report is attached below.",
                                    attachments = persistentListOf(
                                        TaskTimelineAttachmentUi(
                                            id = "content://taskmail/result-chart",
                                            displayName = "result_chart.png",
                                            contentType = "image/png",
                                            isInline = true,
                                            isImage = true,
                                            isActionAvailable = true,
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                    onEvent = {},
                    onPickAttachments = {},
                    onOpenTimelineAttachment = { openedAttachmentId = it },
                    onSaveTimelineAttachment = { savedAttachmentId = it },
                )
            }
        }

        composeTestRule
            .onNodeWithTag("TaskSessionDetailList")
            .performScrollToNode(hasTestTag("TimelineAttachmentOpen:content://taskmail/result-chart"))
        composeTestRule.onNodeWithTag("TimelineAttachmentOpen:content://taskmail/result-chart").performClick()
        composeTestRule.onNodeWithTag("TimelineAttachmentSave:content://taskmail/result-chart").performClick()

        assertThat(openedAttachmentId).isEqualTo("content://taskmail/result-chart")
        assertThat(savedAttachmentId).isEqualTo("content://taskmail/result-chart")
    }

    private fun replyCapableDetail(
        canReply: Boolean = true,
        canQueryStatus: Boolean = true,
        replyUnavailableReason: String? = null,
        pendingQuestions: ImmutableList<TaskPendingQuestionUi> = persistentListOf(
            TaskPendingQuestionUi(
                questionId = "question_001",
                questionText = "Should I proceed?",
                choices = persistentListOf(
                    TaskPendingQuestionChoiceUi(value = "approve"),
                    TaskPendingQuestionChoiceUi(value = "decline"),
                ),
            ),
        ),
        quickAnswerChoices: ImmutableList<String> = persistentListOf(
            "approve",
            "decline",
        ),
        requiresStructuredReply: Boolean = false,
        structuredReplyTemplate: String? = null,
        replyLabel: String = "Reply to this task",
        replySupportingText: String = "Send a plain-text reply, attach files, or use a quick TaskMail action.",
        timeline: ImmutableList<TaskTimelineItemUi> = persistentListOf(
            TaskTimelineItemUi(
                id = "timeline_001",
                timestamp = 1L,
                direction = "System",
                statusLabel = "Question",
                summary = "Need confirmation",
                plainText = "Parser layer is complete. Waiting for the next step.",
            ),
        ),
    ): TaskSessionDetailUiState {
        return TaskSessionDetailUiState(
            sessionName = "Build TaskMail Phase 1",
            backend = "Codex",
            status = "WaitingUser",
            repoPath = "E:/projects/android_task_manager",
            workdir = "feature/taskmail",
            lastSummary = "Parser layer is complete.",
            pendingQuestions = pendingQuestions,
            quickAnswerChoices = quickAnswerChoices,
            requiresStructuredReply = requiresStructuredReply,
            structuredReplyTemplate = structuredReplyTemplate,
            replyLabel = replyLabel,
            replySupportingText = replySupportingText,
            canReply = canReply,
            canQueryStatus = canQueryStatus,
            replyUnavailableReason = replyUnavailableReason,
            timeline = timeline,
        )
    }
}

private const val MULTI_QUESTION_REPLY_SUPPORTING_TEXT =
    "Use one line per question in the form question_id: value. " +
        "The draft is prefilled with a copy-ready template, and you can attach files if needed."
