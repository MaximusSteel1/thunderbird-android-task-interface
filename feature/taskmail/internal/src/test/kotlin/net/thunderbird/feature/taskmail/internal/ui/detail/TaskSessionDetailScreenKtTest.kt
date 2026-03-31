package net.thunderbird.feature.taskmail.internal.ui.detail

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
import net.thunderbird.feature.taskmail.internal.domain.model.TaskReplyAttachment
import net.thunderbird.feature.taskmail.internal.domain.model.TaskRichTextBlock
import net.thunderbird.feature.taskmail.internal.domain.model.TaskRichTextDocument
import net.thunderbird.feature.taskmail.internal.domain.model.TaskRichTextInline
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
                            processSection = TaskProcessSectionUi(
                                title = "Process",
                                visibleItems = persistentListOf(
                                    TaskTimelineItemUi(
                                        id = "timeline_001",
                                        timestamp = 1L,
                                        direction = "Assistant",
                                        statusLabel = "Completed",
                                        plainText = "Parser layer is complete. Waiting for the next step.",
                                    ),
                                ),
                                rawItemCount = 1,
                                previewText = "Need confirmation",
                                defaultExpanded = false,
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

        composeTestRule.onAllNodesWithText("Should I proceed?").assertCountEquals(1)
        composeTestRule
            .onNodeWithTag("TaskSessionDetailList")
            .performScrollToNode(hasText("yes"))
        composeTestRule.onNodeWithText("yes").assertIsDisplayed()
        composeTestRule
            .onNodeWithTag("TaskSessionDetailList")
            .performScrollToNode(hasText("no"))
        composeTestRule.onNodeWithText("no").assertIsDisplayed()
        expandProcessRecords()
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
                                supportingText = "Cleanup finished",
                                statusLabel = "WaitingUser",
                            ),
                            artifacts = persistentListOf(
                                TaskTimelineAttachmentUi(
                                    id = "artifact_001",
                                    displayName = "cleanup_report.md",
                                    contentType = "text/markdown",
                                    sizeBytes = 4_096L,
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
            .performScrollToNode(hasTestTag("TaskSessionDetailResultSummary"))
        composeTestRule.onNodeWithTag("TaskSessionDetailResultSummary").assertIsDisplayed()
        composeTestRule.onNodeWithText("Waiting for your reply").assertIsDisplayed()
        composeTestRule.onNodeWithText("cleanup_report.md").assertIsDisplayed()
    }

    @Test
    fun `content should normalize markdown code reference in latest session output and reveal it on tap`() {
        val reference = "[README.md#L34](/E:/projects/mail_based_task_manager/README.md#L34)"

        composeTestRule.setContent {
            K9MailTheme2 {
                TaskSessionDetailContent(
                    state = TaskSessionDetailContract.State(
                        detail = replyCapableDetail().copy(
                            status = "Running",
                            pageMode = TaskSessionPageMode.ActiveRun,
                            processSection = TaskProcessSectionUi(
                                title = "Process",
                                visibleItems = persistentListOf(
                                    TaskTimelineItemUi(
                                        id = "process_reference_001",
                                        timestamp = 1L,
                                        direction = "Assistant",
                                        statusLabel = "Streaming",
                                        plainText = reference,
                                    ),
                                ),
                                rawItemCount = 1,
                                previewText = null,
                                defaultExpanded = true,
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
            .performScrollToNode(hasTestTag("TaskProcessSection:Process"))
        composeTestRule.onAllNodesWithText("README.md#L34", substring = true).assertCountEquals(2)
        composeTestRule.onAllNodesWithText(reference, substring = true).assertCountEquals(0)

        composeTestRule.onNodeWithTag("TaskCodeLocatorToken:$reference").performClick()

        composeTestRule.onNodeWithText(reference, substring = true).assertIsDisplayed()
    }

    @Test
    fun `content should render latest result body when preserved assistant output is available`() {
        composeTestRule.setContent {
            K9MailTheme2 {
                TaskSessionDetailContent(
                    state = TaskSessionDetailContract.State(
                        detail = replyCapableDetail().copy(
                            resultSummary = TaskResultSummaryUi(
                                headline = "Latest run completed",
                                supportingText = "Cleanup finished.",
                                statusLabel = "Done",
                            ),
                            resultBody = TaskTimelineItemUi(
                                id = "result_body_001",
                                timestamp = 2L,
                                direction = "System",
                                statusLabel = "Done",
                                summary = "Cleanup finished.",
                                plainText = "Cleanup finished.\n\nChanged files:\n- TaskNewTaskViewModel.kt",
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
            .performScrollToNode(hasTestTag("TaskSessionDetailResultBody"))
        composeTestRule.onNodeWithTag("TaskSessionDetailResultBody").assertIsDisplayed()
        composeTestRule
            .onNodeWithTag("TaskSessionDetailList")
            .performScrollToNode(hasTestTag("TaskSessionDetailResultConclusion"))
        composeTestRule.onNodeWithTag("TaskSessionDetailResultConclusion").assertIsDisplayed()
    }

    @Test
    fun `content should render current round input and assistant context without duplicating outgoing text`() {
        composeTestRule.setContent {
            K9MailTheme2 {
                TaskSessionDetailContent(
                    state = TaskSessionDetailContract.State(
                        detail = replyCapableDetail(status = "Running").copy(
                            recentContext = TaskRecentContextUi(
                                latestUserMessage = "Please continue with the cleanup.",
                            ),
                            processSection = TaskProcessSectionUi(
                                title = "Process",
                                visibleItems = persistentListOf(
                                    TaskTimelineItemUi(
                                        id = "process_cleanup_001",
                                        timestamp = 2L,
                                        direction = "Assistant",
                                        statusLabel = "Streaming",
                                        plainText = "Cleanup is running on the Android branch.",
                                    ),
                                ),
                                rawItemCount = 1,
                                previewText = "Cleanup is running on the Android branch.",
                                defaultExpanded = true,
                            ),
                            resultSummary = TaskResultSummaryUi(
                                headline = "Run in progress",
                                supportingText = "Previous stable result · 1 file",
                                statusLabel = "Running",
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
            .performScrollToNode(hasText("Current round"))
        composeTestRule.onNodeWithText("Current round").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Please continue with the cleanup.").assertCountEquals(1)
        composeTestRule
            .onNodeWithTag("TaskSessionDetailList")
            .performScrollToNode(hasTestTag("TaskProcessSection:Process"))
        composeTestRule.onAllNodesWithText("Cleanup is running on the Android branch.").assertCountEquals(2)
        composeTestRule.onAllNodesWithText("You last said").assertCountEquals(0)
    }

    @Test
    fun `content should prefer live output over stale recent assistant context in active run`() {
        composeTestRule.setContent {
            K9MailTheme2 {
                TaskSessionDetailContent(
                    state = TaskSessionDetailContract.State(
                        detail = replyCapableDetail(status = "Running").copy(
                            processSection = TaskProcessSectionUi(
                                title = "Process",
                                visibleItems = persistentListOf(
                                    TaskTimelineItemUi(
                                        id = "process_streaming_001",
                                        timestamp = 2L,
                                        direction = "Assistant",
                                        statusLabel = "Streaming",
                                        plainText = "Streaming assistant output.",
                                    ),
                                ),
                                rawItemCount = 1,
                                previewText = "Streaming assistant output.",
                                defaultExpanded = true,
                            ),
                            recentContext = TaskRecentContextUi(
                                latestUserMessage = "Please continue with the cleanup.",
                                latestAssistantMessage = "Older assistant output.",
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
            .performScrollToNode(hasTestTag("TaskSessionDetailStatusCard"))
        composeTestRule
            .onNodeWithTag("TaskSessionDetailList")
            .performScrollToNode(hasTestTag("TaskProcessSection:Process"))
        composeTestRule.onAllNodesWithText("Streaming assistant output.").assertCountEquals(2)
        composeTestRule.onAllNodesWithText("Older assistant output.").assertCountEquals(0)
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
            .performScrollToNode(hasTestTag("TaskSessionDetailResultSummary"))
        composeTestRule.onAllNodesWithText(
            "Completed the Android VPS-first readiness slice.",
            substring = true,
        ).assertCountEquals(1)
        composeTestRule
            .onNodeWithTag("TaskSessionDetailList")
            .performScrollToNode(hasTestTag("TaskSessionDetailResultSummary"))
        composeTestRule.onNodeWithText("Done").assertIsDisplayed()
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

        composeTestRule.onNodeWithTag("TaskSessionDetailHistoryButton").performClick()

        assertThat(historyClicked).isEqualTo(true)
        composeTestRule.onNodeWithTag("TaskSessionDetailHistorySheet").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Need confirmation").assertCountEquals(1)
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

        expandProcessRecords()
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
            .performScrollToNode(hasTestTag("TaskReplyComposerStructuredInput_question_001"))
        composeTestRule.onNodeWithTag("TaskReplyComposerStructuredInput_question_001").assertIsDisplayed()
        composeTestRule
            .onNodeWithTag("TaskSessionDetailList")
            .performScrollToNode(hasTestTag("TaskReplyComposerStructuredInput_question_002"))
        composeTestRule.onNodeWithTag("TaskReplyComposerStructuredInput_question_002").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("TaskReplyComposerInput").assertCountEquals(0)
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
                            replyUnavailableReason = "Plain-text reply is unavailable while the session is paused.",
                        ),
                    ),
                    onEvent = {},
                    onPickAttachments = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag("TaskSessionDetailList")
            .performScrollToNode(hasText("Plain-text reply is unavailable while the session is paused."))

        composeTestRule
            .onNodeWithText("Plain-text reply is unavailable while the session is paused.")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag("TaskSessionDetailList")
            .performScrollToNode(hasText("View status"))
        composeTestRule.onNodeWithText("View status").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Send reply").assertCountEquals(0)
    }

    @Test
    fun `content should render guide composer in current input led mode and dispatch dismiss`() {
        var guideDismissed = false

        composeTestRule.setContent {
            K9MailTheme2 {
                TaskSessionDetailContent(
                    state = TaskSessionDetailContract.State(
                        isGuideComposerVisible = true,
                        draftText = "Keep the current plan, but skip the cleanup.",
                        detail = replyCapableDetail(
                            status = "Running",
                            pendingQuestions = persistentListOf(),
                            quickAnswerChoices = persistentListOf(),
                        ),
                    ),
                    onEvent = { event ->
                        if (event == TaskSessionDetailContract.Event.GuideDismissed) {
                            guideDismissed = true
                        }
                    },
                    onPickAttachments = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag("TaskSessionDetailList")
            .performScrollToNode(hasTestTag("TaskGuideComposerInput"))

        composeTestRule.onNodeWithTag("TaskGuideComposerInput").assertIsDisplayed()
        composeTestRule
            .onNodeWithTag("TaskSessionDetailList")
            .performScrollToNode(hasTestTag("TaskGuideComposerSendButton"))
        composeTestRule.onNodeWithTag("TaskGuideComposerSendButton").assertIsDisplayed()
        composeTestRule
            .onNodeWithTag("TaskSessionDetailList")
            .performScrollToNode(hasText("Cancel"))
        composeTestRule.onNodeWithText("Cancel").performClick()

        assertThat(guideDismissed).isEqualTo(true)
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
        expandProcessRecords()
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
            .performScrollToNode(hasText("Should I proceed?"))
        composeTestRule.onNodeWithText("Should I proceed?").assertIsDisplayed()
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

        expandProcessRecords()
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

        expandProcessRecords()
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
        composeTestRule.onAllNodesWithText("View status").assertCountEquals(0)
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
        composeTestRule
            .onNodeWithTag("TaskSessionDetailList")
            .performScrollToNode(hasTestTag("TaskReplyComposerChoice_approve"))
        composeTestRule.onNodeWithTag("TaskReplyComposerChoice_approve").assertIsDisplayed()
        composeTestRule.onNodeWithTag("TaskReplyComposerChoice_decline").assertIsDisplayed()
    }

    @Test
    fun `content should show resume action when paused reply is unavailable`() {
        composeTestRule.setContent {
            K9MailTheme2 {
                TaskSessionDetailContent(
                    state = TaskSessionDetailContract.State(
                        detail = replyCapableDetail(
                            pendingQuestions = persistentListOf(),
                            quickAnswerChoices = persistentListOf(),
                            requiresResumeBeforeReply = true,
                            canReply = false,
                            replyUnavailableReason = "Plain-text reply is unavailable while the session is paused.",
                            replySupportingText =
                            "This session is paused. Plain-text reply is currently unavailable in this screen.",
                        ),
                    ),
                    onEvent = {},
                    onPickAttachments = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag("TaskSessionDetailList")
            .performScrollToNode(hasText("Plain-text reply is unavailable while the session is paused."))

        composeTestRule.onNodeWithText("Plain-text reply is unavailable while the session is paused.").assertIsDisplayed()
        composeTestRule
            .onNodeWithTag("TaskSessionDetailList")
            .performScrollToNode(hasText("Resume"))
        composeTestRule.onNodeWithText("Resume").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Resume and send").assertCountEquals(0)
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

        expandProcessRecords()
        composeTestRule
            .onNodeWithTag("TaskSessionDetailList")
            .performScrollToNode(hasTestTag("TimelineAttachmentOpen:content://taskmail/result-chart"))
        composeTestRule.onNodeWithTag("TimelineAttachmentOpen:content://taskmail/result-chart").performClick()
        composeTestRule.onNodeWithTag("TimelineAttachmentSave:content://taskmail/result-chart").performClick()

        assertThat(openedAttachmentId).isEqualTo("content://taskmail/result-chart")
        assertThat(savedAttachmentId).isEqualTo("content://taskmail/result-chart")
    }

    private fun replyCapableDetail(
        status: String = "WaitingUser",
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
        processSection: TaskProcessSectionUi? = null,
    ): TaskSessionDetailUiState {
        val effectiveProcessSection = processSection ?: timeline
            .takeIf { it.isNotEmpty() }
            ?.let { processTimeline ->
                TaskProcessSectionUi(
                    title = "Process",
                    visibleItems = processTimeline,
                    rawItemCount = processTimeline.size,
                    previewText = processTimeline.firstOrNull()
                        ?.summary
                        ?.takeIf { summary ->
                            summary.trim() != processTimeline.firstOrNull()?.plainText?.trim()
                        },
                    defaultExpanded = status == "Queued" || status == "Running",
                )
            }

        return TaskSessionDetailUiState(
            sessionName = "Build TaskMail Phase 1",
            backend = "Codex",
            status = status,
            pageMode = when (status) {
                "Queued", "Running" -> TaskSessionPageMode.ActiveRun
                "WaitingUser", "Paused" -> TaskSessionPageMode.AwaitingReply
                else -> TaskSessionPageMode.Terminal
            },
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
            processSection = effectiveProcessSection,
            timeline = timeline,
        )
    }

    private fun expandProcessRecords(sectionTitle: String = "Process") {
        composeTestRule
            .onNodeWithTag("TaskSessionDetailList")
            .performScrollToNode(hasText(sectionTitle))
        composeTestRule.onNodeWithText("Expand").performClick()
    }
}

private const val MULTI_QUESTION_REPLY_SUPPORTING_TEXT =
    "Answer each pending question below. Your answers will be sent as a structured TaskMail payload."
