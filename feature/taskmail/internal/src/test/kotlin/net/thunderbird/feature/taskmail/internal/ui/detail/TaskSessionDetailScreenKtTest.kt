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
import net.thunderbird.feature.taskmail.internal.domain.model.TaskBodyRenderMode
import net.thunderbird.feature.taskmail.internal.domain.model.RelayBootstrapStatus
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailDirectOutcome
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailDirectSendEvidence
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailDirectSwitchGate
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSessionActionSendRecord
import net.thunderbird.feature.taskmail.internal.domain.model.TaskReplyAttachment
import net.thunderbird.feature.taskmail.internal.domain.model.TaskRichTextBlock
import net.thunderbird.feature.taskmail.internal.domain.model.TaskRichTextDocument
import net.thunderbird.feature.taskmail.internal.domain.model.TaskRichTextInline
import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailDirectSessionActionTarget
import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailDirectSessionActionType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Suppress("LargeClass")
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
                            quickAnswerChoices = persistentListOf(
                                TaskPendingQuestionChoiceUi(value = "yes"),
                                TaskPendingQuestionChoiceUi(value = "no"),
                            ),
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
        composeTestRule.onNodeWithTag("TimelineMessageSelectableBody:timeline_001").assertIsDisplayed()
    }

    @Test
    fun `content should render rich text timeline body when rich document is available`() {
        composeTestRule.setContent {
            K9MailTheme2 {
                TaskSessionDetailContent(
                    state = TaskSessionDetailContract.State(
                        detail = replyCapableDetail(
                            timeline = persistentListOf(
                                TaskTimelineItemUi(
                                    id = "timeline_rich_001",
                                    timestamp = 1L,
                                    direction = "System",
                                    statusLabel = "Done",
                                    summary = "Rich projection available",
                                    plainText = "Fallback plain text",
                                    renderMode = TaskBodyRenderMode.RichText,
                                    richDocument = TaskRichTextDocument(
                                        blocks = listOf(
                                            TaskRichTextBlock.Heading(
                                                level = 2,
                                                inlines = listOf(TaskRichTextInline.Text("Rendered heading")),
                                            ),
                                            TaskRichTextBlock.Paragraph(
                                                inlines = listOf(
                                                    TaskRichTextInline.Text("Rendered paragraph with "),
                                                    TaskRichTextInline.Strong("rich text"),
                                                    TaskRichTextInline.Text("."),
                                                ),
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
            .performScrollToNode(hasTestTag("TaskRichTextBody"))

        composeTestRule.onNodeWithText("Rendered heading").assertIsDisplayed()
        composeTestRule.onNodeWithText("Rendered paragraph with rich text.").assertIsDisplayed()
        composeTestRule.onNodeWithTag("TimelineMessageSelectableBody:timeline_rich_001").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Fallback plain text").assertCountEquals(0)
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
    fun `content should show refresh failure without clearing detail content`() {
        composeTestRule.setContent {
            K9MailTheme2 {
                TaskSessionDetailContent(
                    state = TaskSessionDetailContract.State(
                        refreshError = "Failed to refresh TaskMail session detail.",
                        detail = replyCapableDetail(),
                    ),
                    onEvent = {},
                    onPickAttachments = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag("TaskSessionDetailList")
            .performScrollToNode(hasText("TaskMail update failed"))

        composeTestRule.onAllNodesWithText("TaskMail update failed").assertCountEquals(1)
        composeTestRule.onAllNodesWithText("Failed to refresh TaskMail session detail.").assertCountEquals(1)
        composeTestRule
            .onNodeWithTag("TaskSessionDetailList")
            .performScrollToNode(hasText("Parser layer is complete. Waiting for the next step."))
        composeTestRule.onAllNodesWithText("Parser layer is complete. Waiting for the next step.").assertCountEquals(1)
    }

    @Test
    fun `content should render latest direct evidence card when session-action record exists`() {
        composeTestRule.setContent {
            K9MailTheme2 {
                TaskSessionDetailContent(
                    state = TaskSessionDetailContract.State(
                        latestDirectSessionActionRecord = TaskMailSessionActionSendRecord(
                            recordedAt = 200L,
                            actionType = TaskMailDirectSessionActionType.Status,
                            target = TaskMailDirectSessionActionTarget(
                                workspaceId = "workspace_001",
                                sessionId = "session_001",
                                threadId = "thread_001",
                            ),
                            evidence = TaskMailDirectSendEvidence(
                                bootstrapStatus = RelayBootstrapStatus.HelloAck,
                                outcome = TaskMailDirectOutcome.DirectAccepted,
                                switchGate = TaskMailDirectSwitchGate.KeepDirectDefault,
                                requestId = "req_002",
                                receiptId = "receipt-2",
                                transportMessageId = "transport-2",
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
            .performScrollToNode(hasTestTag("TaskSessionDetailLatestDirectEvidence"))

        composeTestRule.onNodeWithTag("TaskSessionDetailLatestDirectEvidence").assertIsDisplayed()
        composeTestRule
            .onNodeWithTag("TaskSessionDetailList")
            .performScrollToNode(hasText("Request ID"))
        composeTestRule
            .onNodeWithTag("TaskSessionDetailList")
            .performScrollToNode(hasText("req_002"))
        composeTestRule.onNodeWithText("Request ID").assertIsDisplayed()
        composeTestRule.onNodeWithText("req_002").assertIsDisplayed()
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
    fun `content should hide duplicate timeline summary when body already starts with it`() {
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
                                    summary = "Repeated summary line",
                                    plainText = "Repeated summary line",
                                ),
                            ),
                        ).copy(lastSummary = "Workspace summary"),
                    ),
                    onEvent = {},
                    onPickAttachments = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag("TaskSessionDetailList")
            .performScrollToNode(hasText("Repeated summary line"))

        composeTestRule.onAllNodesWithText("Repeated summary line").assertCountEquals(1)
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
    fun `content should render quick answer labels instead of canonical values`() {
        composeTestRule.setContent {
            K9MailTheme2 {
                TaskSessionDetailContent(
                    state = TaskSessionDetailContract.State(
                        detail = replyCapableDetail(
                            pendingQuestions = persistentListOf(
                                TaskPendingQuestionUi(
                                    questionId = "question_001",
                                    questionText = "Should I proceed?",
                                    choices = persistentListOf(
                                        TaskPendingQuestionChoiceUi(
                                            value = "approve",
                                            label = "Ship it",
                                        ),
                                        TaskPendingQuestionChoiceUi(
                                            value = "decline",
                                            label = "Not yet",
                                        ),
                                    ),
                                ),
                            ),
                            quickAnswerChoices = persistentListOf(
                                TaskPendingQuestionChoiceUi(
                                    value = "approve",
                                    label = "Ship it",
                                ),
                                TaskPendingQuestionChoiceUi(
                                    value = "decline",
                                    label = "Not yet",
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
            .performScrollToNode(hasText("Ship it"))

        composeTestRule.onNodeWithText("Ship it").assertIsDisplayed()
        composeTestRule.onNodeWithText("Not yet").assertIsDisplayed()
        composeTestRule.onNodeWithTag("TaskReplyComposerChoice_approve").assertIsDisplayed()
        composeTestRule.onNodeWithTag("TaskReplyComposerChoice_decline").assertIsDisplayed()
    }

    @Test
    fun `content should show resume and send for paused sessions`() {
        composeTestRule.setContent {
            K9MailTheme2 {
                TaskSessionDetailContent(
                    state = TaskSessionDetailContract.State(
                        detail = replyCapableDetail(
                            pendingQuestions = persistentListOf(),
                            quickAnswerChoices = persistentListOf(),
                            requiresResumeBeforeReply = true,
                            replySupportingText =
                            "This session is paused. Sending will prepend /resume before continuing.",
                        ),
                    ),
                    onEvent = {},
                    onPickAttachments = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag("TaskSessionDetailList")
            .performScrollToNode(hasText("Resume and send"))

        composeTestRule.onNodeWithText("Resume and send").assertIsDisplayed()
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
        quickAnswerChoices: ImmutableList<TaskPendingQuestionChoiceUi> = persistentListOf(
            TaskPendingQuestionChoiceUi(
                value = "approve",
                label = "approve",
            ),
            TaskPendingQuestionChoiceUi(
                value = "decline",
                label = "decline",
            ),
        ),
        requiresStructuredReply: Boolean = false,
        requiresResumeBeforeReply: Boolean = false,
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
            requiresResumeBeforeReply = requiresResumeBeforeReply,
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
