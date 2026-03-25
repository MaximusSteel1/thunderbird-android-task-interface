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
import net.thunderbird.feature.taskmail.internal.data.controlplane.protocol.ControlPlaneArtifactManifestMessage
import net.thunderbird.feature.taskmail.internal.data.controlplane.protocol.ControlPlaneCommandAckMessage
import net.thunderbird.feature.taskmail.internal.data.controlplane.protocol.ControlPlaneEventMessage
import net.thunderbird.feature.taskmail.internal.data.controlplane.protocol.ControlPlaneProtocolJsonCodec
import net.thunderbird.feature.taskmail.internal.data.controlplane.protocol.ControlPlaneResultMessage
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
    fun `content should render recent context result summary and artifacts`() {
        composeTestRule.setContent {
            K9MailTheme2 {
                TaskSessionDetailContent(
                    state = TaskSessionDetailContract.State(
                        detail = replyCapableDetail().copy(
                            recentContext = TaskRecentContextUi(
                                latestUserMessage = "Please continue with the cleanup.",
                                latestAssistantMessage = "Cleanup finished and waiting for review.",
                                waitingForUserText = "Confirm whether to merge the cleanup.",
                            ),
                            resultSummary = TaskResultSummaryUi(
                                headline = "Waiting for your reply",
                                supportingText = "Cleanup finished · 1 file",
                                statusLabel = "WaitingUser",
                            ),
                            artifacts = persistentListOf(
                                TaskSessionArtifactUi(
                                    id = "artifact_001",
                                    title = "cleanup_report.md",
                                    supportingText = "text/markdown · 4096 B",
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
            .performScrollToNode(hasTestTag("TaskSessionDetailRecentContext"))

        composeTestRule.onNodeWithTag("TaskSessionDetailRecentContext").assertIsDisplayed()
        composeTestRule.onNodeWithText("Please continue with the cleanup.").assertIsDisplayed()
        composeTestRule
            .onNodeWithTag("TaskSessionDetailList")
            .performScrollToNode(hasTestTag("TaskSessionDetailResultSummary"))
        composeTestRule.onNodeWithTag("TaskSessionDetailResultSummary").assertIsDisplayed()
        composeTestRule.onNodeWithText("Waiting for your reply").assertIsDisplayed()
        composeTestRule
            .onNodeWithTag("TaskSessionDetailList")
            .performScrollToNode(hasTestTag("TaskSessionDetailArtifacts"))
        composeTestRule.onNodeWithTag("TaskSessionDetailArtifacts").assertIsDisplayed()
        composeTestRule.onNodeWithText("cleanup_report.md").assertIsDisplayed()
    }

    @Test
    fun `content should render control plane overlay from representative json`() {
        val codec = ControlPlaneProtocolJsonCodec()
        val commandAck = codec.decodeMessage(
            """
            {
              "schema_version": "v1",
              "type": "command_ack",
              "message_id": "msg_ack_01",
              "trace_id": "trace_cmd_01",
              "pc_id": "pc_home",
              "connection_epoch": 12,
              "sent_at": "2026-03-25T10:01:00Z",
              "payload": {
                "command_id": "cmd_01",
                "ack_status": "accepted_but_queued",
                "queue_position": 1
              }
            }
            """.trimIndent(),
        ) as ControlPlaneCommandAckMessage
        val event = codec.decodeMessage(
            """
            {
              "schema_version": "v1",
              "type": "event",
              "message_id": "msg_evt_01",
              "trace_id": "trace_cmd_01",
              "pc_id": "pc_home",
              "connection_epoch": 12,
              "sent_at": "2026-03-25T10:01:10Z",
              "payload": {
                "event_id": "evt_01",
                "command_id": "cmd_01",
                "workspace_id": "workspace_android_app",
                "session_id": "session_001",
                "run_id": "run_01",
                "event_type": "running",
                "payload": {
                  "summary": "Applying route/key cutover and local readiness checks."
                },
                "emitted_at": "2026-03-25T10:01:09Z"
              }
            }
            """.trimIndent(),
        ) as ControlPlaneEventMessage
        val result = codec.decodeMessage(
            """
            {
              "schema_version": "v1",
              "type": "result",
              "message_id": "msg_res_01",
              "trace_id": "trace_cmd_01",
              "pc_id": "pc_home",
              "connection_epoch": 12,
              "sent_at": "2026-03-25T10:02:00Z",
              "payload": {
                "result_id": "res_01",
                "command_id": "cmd_01",
                "workspace_id": "workspace_android_app",
                "session_id": "session_001",
                "run_id": "run_01",
                "final_status": "done",
                "summary": "Completed the Android VPS-first readiness slice.",
                "effective_execution": {
                  "backend": "codex",
                  "profile": "strong",
                  "permission": "highest",
                  "backend_transport": "sdk",
                  "resolved_model": "gpt-5-codex"
                },
                "structured_payload": {
                  "kind": "task_outcome",
                  "changed_files": ["TaskNewTaskViewModel.kt", "TaskWorkspaceViewModel.kt"]
                },
                "generated_at": "2026-03-25T10:01:59Z"
              }
            }
            """.trimIndent(),
        ) as ControlPlaneResultMessage
        val artifactManifest = codec.decodeMessage(
            """
            {
              "schema_version": "v1",
              "type": "artifact_manifest",
              "message_id": "msg_art_01",
              "trace_id": "trace_cmd_01",
              "pc_id": "pc_home",
              "connection_epoch": 12,
              "sent_at": "2026-03-25T10:02:05Z",
              "payload": {
                "run_id": "run_01",
                "artifacts": [
                  {
                    "artifact_id": "art_01",
                    "name": "summary.md",
                    "kind": "file",
                    "role": "output",
                    "content_type": "text/markdown",
                    "size": 1024,
                    "download_ref": {
                      "kind": "vps_file",
                      "file_id": "file_01"
                    }
                  }
                ]
              }
            }
            """.trimIndent(),
        ) as ControlPlaneArtifactManifestMessage
        val overlay = buildTaskSessionControlPlaneOverlay(
            commandAck = commandAck.payload,
            events = listOf(event.payload),
            result = result.payload,
            artifactManifest = artifactManifest.payload,
        )

        composeTestRule.setContent {
            K9MailTheme2 {
                TaskSessionDetailContent(
                    state = TaskSessionDetailContract.State(
                        detail = replyCapableDetail().withControlPlaneOverlay(overlay),
                    ),
                    onEvent = {},
                    onPickAttachments = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag("TaskSessionDetailList")
            .performScrollToNode(hasTestTag("TaskSessionDetailRecentContext"))
        composeTestRule.onNodeWithText("Completed the Android VPS-first readiness slice.").assertIsDisplayed()
        composeTestRule
            .onNodeWithTag("TaskSessionDetailList")
            .performScrollToNode(hasTestTag("TaskSessionDetailResultSummary"))
        composeTestRule.onNodeWithText("Done").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "backend=codex · profile=strong · permission=highest · transport=sdk · model=gpt-5-codex",
        ).assertIsDisplayed()
        composeTestRule
            .onNodeWithTag("TaskSessionDetailList")
            .performScrollToNode(hasTestTag("TaskSessionDetailArtifacts"))
        composeTestRule.onNodeWithText("summary.md").assertIsDisplayed()
    }

    @Test
    fun `content should dispatch history click and render history sheet`() {
        var historyClicked = false

        composeTestRule.setContent {
            K9MailTheme2 {
                TaskSessionDetailContent(
                    state = TaskSessionDetailContract.State(
                        isHistoryVisible = true,
                        detail = replyCapableDetail().copy(
                            recentContext = TaskRecentContextUi(
                                latestAssistantMessage = "Waiting for review.",
                            ),
                            historyPreview = persistentListOf(
                                TaskHistoryRoundUi(
                                    id = "history_001",
                                    title = "Need confirmation",
                                    summary = "Question",
                                    statusLabel = "Question",
                                    messagePreview = "Parser layer is complete. Waiting for the next step.",
                                ),
                            ),
                        ),
                    ),
                    onEvent = { event ->
                        if (event == TaskSessionDetailContract.Event.HistoryClicked) {
                            historyClicked = true
                        }
                    },
                    onPickAttachments = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag("TaskSessionDetailList")
            .performScrollToNode(hasTestTag("TaskSessionDetailHistoryButton"))
        composeTestRule.onNodeWithTag("TaskSessionDetailHistoryButton").performClick()

        assertThat(historyClicked).isEqualTo(true)
        composeTestRule.onNodeWithTag("TaskSessionDetailHistorySheet").assertIsDisplayed()
        composeTestRule.onNodeWithText("Need confirmation").assertIsDisplayed()
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
    fun `content should keep status action visible when reply is unavailable but status is allowed`() {
        composeTestRule.setContent {
            K9MailTheme2 {
                TaskSessionDetailContent(
                    state = TaskSessionDetailContract.State(
                        detail = replyCapableDetail(
                            canReply = false,
                            canQueryStatus = true,
                            replyUnavailableReason = "Direct plain reply is unavailable while the session is paused.",
                        ),
                    ),
                    onEvent = {},
                    onPickAttachments = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag("TaskSessionDetailList")
            .performScrollToNode(hasText("Direct plain reply is unavailable while the session is paused."))

        composeTestRule
            .onNodeWithText("Direct plain reply is unavailable while the session is paused.")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag("TaskSessionDetailList")
            .performScrollToNode(hasTestTag("TaskReplyComposerStatusButton"))
        composeTestRule.onNodeWithTag("TaskReplyComposerStatusButton").assertIsDisplayed()
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
