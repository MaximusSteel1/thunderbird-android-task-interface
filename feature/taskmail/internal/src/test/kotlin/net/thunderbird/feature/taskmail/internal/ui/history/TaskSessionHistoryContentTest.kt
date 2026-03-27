package net.thunderbird.feature.taskmail.internal.ui.history

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlinx.collections.immutable.persistentListOf
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionHistorySnapshotAttachment
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionHistorySnapshotProcessItem
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionHistorySnapshotRound
import net.thunderbird.core.ui.compose.theme2.k9mail.K9MailTheme2
import net.thunderbird.feature.taskmail.internal.ui.detail.TaskSessionDetailContract
import net.thunderbird.feature.taskmail.internal.ui.detail.TaskSessionDetailUiState
import net.thunderbird.feature.taskmail.internal.ui.detail.TaskTimelineAttachmentUi
import net.thunderbird.feature.taskmail.internal.ui.detail.TaskTimelineItemUi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TaskSessionHistoryContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `content should expand multiple rounds and open attachment previews`() {
        var openedAttachmentId: String? = null

        composeTestRule.setContent {
            K9MailTheme2 {
                TaskSessionHistoryContent(
                    state = TaskSessionDetailContract.State(
                        detail = historyDetail(),
                    ),
                    onBack = {},
                    onEvent = {},
                    onOpenTimelineAttachment = { openedAttachmentId = it },
                    onSaveTimelineAttachment = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag("TaskSessionHistoryList")
            .performScrollToNode(hasTestTag("TaskSessionHistoryRoundCard:round_2_incoming_result_2"))

        composeTestRule.onNodeWithTag("TaskSessionHistoryRoundCard:round_2_incoming_result_2").performClick()
        composeTestRule.onNodeWithText("Review the latest homepage sketch and keep the task tree.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Added the tree-based homepage draft and updated the history card layout.").assertIsDisplayed()

        composeTestRule.onNodeWithTag("TaskSessionHistoryAttachmentPreview:result_image_2").performClick()
        assertThat(openedAttachmentId).isEqualTo("result_image_2")

        composeTestRule
            .onNodeWithTag("TaskSessionHistoryList")
            .performScrollToNode(hasTestTag("TaskSessionHistoryRoundCard:round_1_incoming_result_1"))
        composeTestRule.onNodeWithTag("TaskSessionHistoryRoundCard:round_1_incoming_result_1").performClick()

        composeTestRule.onNodeWithText("Build the first Session page skeleton.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Session skeleton is ready for the first VPS-only cutover pass.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Review the latest homepage sketch and keep the task tree.").assertIsDisplayed()
    }

    @Test
    fun `content should prefer server projected rounds when available`() {
        composeTestRule.setContent {
            K9MailTheme2 {
                TaskSessionHistoryContent(
                    state = TaskSessionDetailContract.State(
                        detail = historyDetail(),
                        historySnapshotRounds = persistentListOf(
                            TaskSessionHistorySnapshotRound(
                                roundId = "hist_round_task_002",
                                roundNumber = 2,
                                createdAt = "2026-03-27T12:00:00",
                                status = "running",
                                speakerLabel = "Codex",
                                inputText = "Server round: keep the homepage tree and tighten the gutter.",
                                processItems = persistentListOf(
                                    TaskSessionHistorySnapshotProcessItem(
                                        itemId = "hist_process_task_002_running",
                                        createdAt = "2026-03-27T12:00:00",
                                        status = "running",
                                        text = "Server round: rebuilding the homepage rows.",
                                    ),
                                ),
                                resultText = "Server round: still processing the latest homepage follow-up.",
                                inputAttachments = persistentListOf(
                                    TaskSessionHistorySnapshotAttachment(
                                        attachmentId = "hist_input_task_002_1",
                                        displayName = "homepage-followup.md",
                                        contentType = "text/markdown",
                                    ),
                                ),
                            ),
                        ),
                    ),
                    onBack = {},
                    onEvent = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("TaskSessionHistoryRoundCard:hist_round_task_002").performClick()
        composeTestRule.onNodeWithText("Server round: keep the homepage tree and tighten the gutter.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Server round: still processing the latest homepage follow-up.")
            .assertIsDisplayed()
    }
}

private fun historyDetail(): TaskSessionDetailUiState {
    val chronologicalTimeline = listOf(
        TaskTimelineItemUi(
            id = "outgoing_1",
            timestamp = 1_742_000_000_000,
            direction = "Outgoing",
            plainText = "Build the first Session page skeleton.",
            attachments = persistentListOf(
                TaskTimelineAttachmentUi(
                    id = "input_doc_1",
                    displayName = "session-notes.md",
                    contentType = "text/markdown",
                    isActionAvailable = true,
                ),
            ),
        ),
        TaskTimelineItemUi(
            id = "system_progress_1",
            timestamp = 1_742_000_060_000,
            direction = "System",
            statusLabel = "Running",
            summary = "Applying the session mode layout.",
            plainText = "Reordering the current round, process fold, and previous result sections.",
        ),
        TaskTimelineItemUi(
            id = "incoming_result_1",
            timestamp = 1_742_000_120_000,
            direction = "Incoming",
            statusLabel = "Done",
            summary = "Session skeleton is ready.",
            plainText = "Session skeleton is ready for the first VPS-only cutover pass.",
            attachments = persistentListOf(
                TaskTimelineAttachmentUi(
                    id = "result_doc_1",
                    displayName = "session-skeleton.png",
                    contentType = "image/png",
                    isImage = true,
                    isActionAvailable = true,
                ),
            ),
        ),
        TaskTimelineItemUi(
            id = "outgoing_2",
            timestamp = 1_742_000_180_000,
            direction = "Outgoing",
            plainText = "Review the latest homepage sketch and keep the task tree.",
            attachments = persistentListOf(
                TaskTimelineAttachmentUi(
                    id = "input_doc_2",
                    displayName = "homepage-brief.md",
                    contentType = "text/markdown",
                    isActionAvailable = true,
                ),
            ),
        ),
        TaskTimelineItemUi(
            id = "system_progress_2",
            timestamp = 1_742_000_240_000,
            direction = "System",
            statusLabel = "Running",
            summary = "Rebuilding the homepage tree rows.",
            plainText = "Compressing the left gutter and adding PC online/offline state.",
        ),
        TaskTimelineItemUi(
            id = "incoming_result_2",
            timestamp = 1_742_000_300_000,
            direction = "Incoming",
            statusLabel = "WaitingUser",
            summary = "Homepage draft ready for review.",
            plainText = "Added the tree-based homepage draft and updated the history card layout.",
            attachments = persistentListOf(
                TaskTimelineAttachmentUi(
                    id = "result_image_2",
                    displayName = "home-tree.png",
                    contentType = "image/png",
                    isImage = true,
                    isActionAvailable = true,
                ),
                TaskTimelineAttachmentUi(
                    id = "result_doc_2",
                    displayName = "home-tree-notes.md",
                    contentType = "text/markdown",
                    isActionAvailable = true,
                ),
            ),
        ),
    )

    return TaskSessionDetailUiState(
        sessionId = "session_001",
        workspaceId = "workspace_001",
        sessionName = "Build TaskMail Phase 1",
        backend = "Codex",
        status = "WaitingUser",
        repoPath = "E:/projects/android_task_manager",
        workdir = "feature/taskmail",
        timeline = chronologicalTimeline.asReversed().toPersistentList(),
    )
}

private fun <T> List<T>.toPersistentList() = persistentListOf<T>().addAll(this)
