package net.thunderbird.feature.taskmail.internal.data

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMessageBody
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionDetail
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionKey
import net.thunderbird.feature.taskmail.internal.domain.model.TaskTimelineDirection
import net.thunderbird.feature.taskmail.internal.domain.model.TaskTimelineItem
import net.thunderbird.feature.taskmail.internal.domain.model.TaskWorkspaceKey
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskSessionDetailRepository
import net.thunderbird.feature.taskmail.internal.preview.TaskMailPreviewData

class SnapshotBackedTaskMailRepositoryTest {

    @Test
    fun `getTaskWorkspaceSummaries should group snapshot details by workspace and sort by latest update`() = runTest {
        val repository = SnapshotBackedTaskMailRepository(
            taskSessionDetailRepository = InMemoryTaskSessionDetailRepository(
                sessionDetails = listOf(
                    sampleDetail(
                        sessionId = "session-1",
                        threadId = "thread-1",
                        sessionName = "Implement parser",
                        summary = "Parser is running.",
                        timestamp = 100L,
                    ),
                    sampleDetail(
                        sessionId = "session-2",
                        threadId = "thread-2",
                        sessionName = "Wire repository",
                        summary = "Repository wiring is waiting for input.",
                        timestamp = 200L,
                    ),
                ),
            ),
        )

        val result = repository.getTaskWorkspaceSummaries()

        assertThat(result.size).isEqualTo(1)
        assertThat(result.single().sessionCount).isEqualTo(2)
        assertThat(result.single().activeSessionId).isEqualTo("session-2")
        assertThat(result.single().sessions.map { it.sessionName }).containsExactly(
            "Wire repository",
            "Implement parser",
        )
    }

    @Test
    fun `getTaskWorkspaceSummaries should prefer repo folder name over workspace id when both are present`() = runTest {
        val repository = SnapshotBackedTaskMailRepository(
            taskSessionDetailRepository = InMemoryTaskSessionDetailRepository(
                sessionDetails = listOf(
                    sampleDetail(
                        sessionId = "session-1",
                        threadId = "thread-1",
                        sessionName = "Wire repository",
                        summary = "Repository wiring is waiting for input.",
                        timestamp = 200L,
                    ).copy(
                        workspace = TaskWorkspaceKey(
                            workspaceId = "workspace_cb2404bf828c",
                            repoPath = "E:/projects/android_task_manager",
                        ),
                    ),
                ),
            ),
        )

        val result = repository.getTaskWorkspaceSummaries()

        assertThat(result.single().title).isEqualTo("android_task_manager")
    }

    @Test
    fun `getTaskWorkspaceSummaries should propagate workspace id into session summary key`() = runTest {
        val repository = SnapshotBackedTaskMailRepository(
            taskSessionDetailRepository = InMemoryTaskSessionDetailRepository(
                sessionDetails = listOf(
                    sampleDetail(
                        sessionId = "session-1",
                        threadId = "thread-1",
                        sessionName = "Wire repository",
                        summary = "Repository wiring is waiting for input.",
                        timestamp = 200L,
                    ).copy(
                        key = TaskSessionKey(
                            sessionId = "session-1",
                            threadId = "thread-1",
                        ),
                        workspace = TaskWorkspaceKey(
                            workspaceId = "workspace_cb2404bf828c",
                            repoPath = "E:/projects/android_task_manager",
                        ),
                    ),
                ),
            ),
        )

        val result = repository.getTaskWorkspaceSummaries()

        assertThat(result.single().sessions.single().key.workspaceId).isEqualTo("workspace_cb2404bf828c")
    }

    @Test
    fun `getTaskSessionDetail should delegate to snapshot repository`() = runTest {
        val detail = sampleDetail(
            sessionId = "session-1",
            threadId = "thread-1",
            sessionName = "Implement parser",
            summary = "Parser is running.",
            timestamp = 100L,
        )
        val repository = SnapshotBackedTaskMailRepository(
            taskSessionDetailRepository = InMemoryTaskSessionDetailRepository(
                sessionDetails = listOf(detail),
            ),
        )

        val result = repository.getTaskSessionDetail(detail.key)

        assertThat(result).isNotNull()
        assertThat(result).isEqualTo(detail)
    }
}

private class InMemoryTaskSessionDetailRepository(
    private val sessionDetails: List<TaskSessionDetail>,
) : TaskSessionDetailRepository {
    override suspend fun getTaskSessionDetail(key: TaskSessionKey): TaskSessionDetail? {
        return sessionDetails.firstOrNull { detail -> detail.key == key }
    }

    override suspend fun getTaskSessionDetails(): List<TaskSessionDetail> = sessionDetails

    override suspend fun replaceAllSessionDetails(details: List<TaskSessionDetail>) = Unit

    override suspend fun upsertSessionDetails(details: List<TaskSessionDetail>) = Unit

    override suspend fun removeSessionDetails(keys: List<TaskSessionKey>) = Unit
}

private fun sampleDetail(
    sessionId: String,
    threadId: String,
    sessionName: String,
    summary: String,
    timestamp: Long,
): TaskSessionDetail {
    return TaskMailPreviewData.sessionDetails.first().copy(
        key = TaskSessionKey(
            sessionId = sessionId,
            threadId = threadId,
        ),
        sessionName = sessionName,
        lastSummary = summary,
        timeline = listOf(
            TaskTimelineItem(
                id = "timeline-$threadId",
                timestamp = timestamp,
                direction = TaskTimelineDirection.System,
                summary = summary,
                body = TaskMessageBody(
                    plainTextFallback = summary,
                ),
            ),
        ),
    )
}
