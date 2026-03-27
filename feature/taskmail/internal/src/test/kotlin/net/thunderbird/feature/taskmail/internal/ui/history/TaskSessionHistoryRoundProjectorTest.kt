package net.thunderbird.feature.taskmail.internal.ui.history

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlinx.collections.immutable.persistentListOf
import net.thunderbird.feature.taskmail.internal.ui.detail.TaskSessionDetailUiState
import net.thunderbird.feature.taskmail.internal.ui.detail.TaskTimelineAttachmentUi
import net.thunderbird.feature.taskmail.internal.ui.detail.TaskTimelineItemUi
import org.junit.Test

class TaskSessionHistoryRoundProjectorTest {

    @Test
    fun `project should keep newest round first and preserve input process result structure`() {
        val testSubject = historyDetail(
            chronologicalTimeline = listOf(
                timelineItem(
                    id = "outgoing_1",
                    timestamp = 10L,
                    direction = "Outgoing",
                    plainText = "Build the first Session page skeleton.",
                    attachments = persistentListOf(
                        attachment(
                            id = "input_doc_1",
                            displayName = "session-notes.md",
                            contentType = "text/markdown",
                        ),
                    ),
                ),
                timelineItem(
                    id = "system_progress_1",
                    timestamp = 20L,
                    direction = "System",
                    statusLabel = "Running",
                    summary = "Applying the session mode layout.",
                    plainText = "Reordering the current round, process fold, and previous result sections.",
                ),
                timelineItem(
                    id = "incoming_result_1",
                    timestamp = 30L,
                    direction = "Incoming",
                    statusLabel = "Done",
                    summary = "Session skeleton is ready.",
                    plainText = "Session skeleton is ready for the first VPS-only cutover pass.",
                    attachments = persistentListOf(
                        attachment(
                            id = "result_doc_1",
                            displayName = "session-skeleton.png",
                            contentType = "image/png",
                            isImage = true,
                        ),
                    ),
                ),
                timelineItem(
                    id = "outgoing_2",
                    timestamp = 40L,
                    direction = "Outgoing",
                    plainText = "Review the latest homepage sketch and keep the task tree.",
                    attachments = persistentListOf(
                        attachment(
                            id = "input_doc_2",
                            displayName = "homepage-brief.md",
                            contentType = "text/markdown",
                        ),
                    ),
                ),
                timelineItem(
                    id = "system_progress_2",
                    timestamp = 50L,
                    direction = "System",
                    statusLabel = "Running",
                    summary = "Rebuilding the homepage tree rows.",
                    plainText = "Compressing the left gutter and adding PC online/offline state.",
                ),
                timelineItem(
                    id = "incoming_result_2",
                    timestamp = 60L,
                    direction = "Incoming",
                    statusLabel = "WaitingUser",
                    summary = "Homepage draft ready for review.",
                    plainText = "Added the tree-based homepage draft and updated the history card layout.",
                    attachments = persistentListOf(
                        attachment(
                            id = "result_image_2",
                            displayName = "home-tree.png",
                            contentType = "image/png",
                            isImage = true,
                        ),
                        attachment(
                            id = "result_doc_2",
                            displayName = "home-tree-notes.md",
                            contentType = "text/markdown",
                        ),
                    ),
                ),
            ),
        )

        val rounds = TaskSessionHistoryRoundProjector.project(testSubject)

        assertThat(rounds).hasSize(2)
        assertThat(rounds.map { it.roundNumber }).containsExactly(2, 1)
        assertThat(rounds.first().inputText).isEqualTo("Review the latest homepage sketch and keep the task tree.")
        assertThat(rounds.first().resultText).isEqualTo(
            "Added the tree-based homepage draft and updated the history card layout.",
        )
        assertThat(rounds.first().processItems.map { it.id }).containsExactly("system_progress_2")
        assertThat(rounds.first().inputAttachments.map { it.id }).containsExactly("input_doc_2")
        assertThat(rounds.first().resultAttachments.map { it.id }).containsExactly("result_image_2", "result_doc_2")
        assertThat(rounds.first().previewAttachments.map { it.attachmentId })
            .containsExactly("input_doc_2", "result_image_2", "result_doc_2")
    }

    @Test
    fun `project should preserve bootstrap round before first outgoing message`() {
        val testSubject = historyDetail(
            chronologicalTimeline = listOf(
                timelineItem(
                    id = "bootstrap_system",
                    timestamp = 10L,
                    direction = "System",
                    statusLabel = "Queued",
                    plainText = "Session restored from cache.",
                ),
                timelineItem(
                    id = "bootstrap_result",
                    timestamp = 20L,
                    direction = "Incoming",
                    statusLabel = "WaitingUser",
                    summary = "Need confirmation before continuing.",
                    plainText = "Please confirm whether to continue on the homepage tree branch.",
                ),
                timelineItem(
                    id = "outgoing_1",
                    timestamp = 30L,
                    direction = "Outgoing",
                    plainText = "Continue on the homepage tree branch.",
                ),
                timelineItem(
                    id = "system_progress_1",
                    timestamp = 40L,
                    direction = "System",
                    statusLabel = "Running",
                    summary = "Applying the latest homepage updates.",
                    plainText = "Compressing the tree gutter and refreshing the session chips.",
                ),
            ),
        )

        val rounds = TaskSessionHistoryRoundProjector.project(testSubject)

        assertThat(rounds).hasSize(2)
        assertThat(rounds.first().roundNumber).isEqualTo(2)
        assertThat(rounds.first().inputText).isEqualTo("Continue on the homepage tree branch.")
        assertThat(rounds.first().resultText).isEqualTo("Compressing the tree gutter and refreshing the session chips.")
        assertThat(rounds.first().processItems).hasSize(0)
        assertThat(rounds.last().roundNumber).isEqualTo(1)
        assertThat(rounds.last().inputText).isNull()
        assertThat(rounds.last().resultText).isEqualTo(
            "Please confirm whether to continue on the homepage tree branch.",
        )
        assertThat(rounds.last().statusLabel).isEqualTo("WaitingUser")
    }
}

private fun historyDetail(
    chronologicalTimeline: List<TaskTimelineItemUi>,
): TaskSessionDetailUiState {
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

private fun timelineItem(
    id: String,
    timestamp: Long,
    direction: String,
    plainText: String,
    statusLabel: String? = null,
    summary: String? = null,
    attachments: kotlinx.collections.immutable.ImmutableList<TaskTimelineAttachmentUi> = persistentListOf(),
): TaskTimelineItemUi {
    return TaskTimelineItemUi(
        id = id,
        timestamp = timestamp,
        direction = direction,
        statusLabel = statusLabel,
        summary = summary,
        plainText = plainText,
        attachments = attachments,
    )
}

private fun attachment(
    id: String,
    displayName: String,
    contentType: String,
    isImage: Boolean = false,
): TaskTimelineAttachmentUi {
    return TaskTimelineAttachmentUi(
        id = id,
        displayName = displayName,
        contentType = contentType,
        isImage = isImage,
        isActionAvailable = true,
    )
}

private fun <T> List<T>.toPersistentList() = persistentListOf<T>().addAll(this)
