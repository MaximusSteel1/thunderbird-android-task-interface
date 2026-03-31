package net.thunderbird.feature.taskmail.internal.domain.usecase

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import net.thunderbird.feature.taskmail.internal.data.facade.SessionSnapshotRequestException
import net.thunderbird.feature.taskmail.internal.data.relay.RelayFakeLogger
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailBackend
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSessionLifecycle
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSessionStatus
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMessageBody
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionDetail
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionHistorySnapshot
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionHistorySnapshotHeader
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionHistorySnapshotLocator
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionKey
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionProjectionDataSource
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionProjectionSubscriptionStatus
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionProjectionSyncState
import net.thunderbird.feature.taskmail.internal.domain.model.TaskTimelineDirection
import net.thunderbird.feature.taskmail.internal.domain.model.TaskTimelineItem
import net.thunderbird.feature.taskmail.internal.domain.model.TaskWorkspaceKey
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskSessionUpdatesRepository

class ObserveTaskMailSessionUpdatesTest {

    @Test
    fun `invoke should retry after session not found and emit recovered snapshot`() = runTest {
        val repository = FakeTaskSessionUpdatesRepository(
            scriptedFlows = mutableListOf(
                flow {
                    throw SessionSnapshotRequestException(
                        errorCode = "session_not_found",
                        message = "could not resolve a session for the requested session_id",
                        retryable = true,
                    )
                },
                flowOf(sampleSessionUpdatesSnapshot(status = TaskMailSessionStatus.Done)),
            ),
        )
        val testSubject = DefaultObserveTaskMailSessionUpdates(
            repository = repository,
            logger = RelayFakeLogger(),
            reconnectDelayMillis = 0L,
        )

        val snapshot = testSubject(sampleVpsDetail()).first()

        assertThat(snapshot.sessionHeader?.status).isEqualTo(TaskMailSessionStatus.Done)
        assertThat(repository.requestedLocators.size).isEqualTo(2)
    }

    @Test
    fun `invoke should reconnect when updates flow completes without snapshot`() = runTest {
        val repository = FakeTaskSessionUpdatesRepository(
            scriptedFlows = mutableListOf(
                emptyFlow(),
                flowOf(sampleSessionUpdatesSnapshot(status = TaskMailSessionStatus.Running)),
            ),
        )
        val testSubject = DefaultObserveTaskMailSessionUpdates(
            repository = repository,
            logger = RelayFakeLogger(),
            reconnectDelayMillis = 0L,
        )

        val snapshot = testSubject(sampleVpsDetail()).first()

        assertThat(snapshot.sessionHeader?.status).isEqualTo(TaskMailSessionStatus.Running)
        assertThat(repository.requestedLocators.size).isEqualTo(2)
    }
}

private class FakeTaskSessionUpdatesRepository(
    private val scriptedFlows: MutableList<Flow<TaskSessionHistorySnapshot>>,
) : TaskSessionUpdatesRepository {
    val requestedLocators = mutableListOf<TaskSessionHistorySnapshotLocator>()

    override fun observeSessionUpdates(locator: TaskSessionHistorySnapshotLocator): Flow<TaskSessionHistorySnapshot> {
        requestedLocators += locator
        return scriptedFlows.removeAt(0)
    }
}

private fun sampleSessionUpdatesSnapshot(
    status: TaskMailSessionStatus,
): TaskSessionHistorySnapshot {
    return TaskSessionHistorySnapshot(
        snapshotId = "snapshot_001",
        generatedAt = "2026-03-29T18:31:00Z",
        sessionHeader = TaskSessionHistorySnapshotHeader(
            workspaceId = "workspace_001",
            sessionId = "session_001",
            threadId = "thread_001",
            sessionName = "say hi",
            backend = TaskMailBackend.Codex,
            repoPath = "E:/projects/android_task_manager",
            workdir = "feature/taskmail/internal",
            status = status,
            lifecycle = TaskMailSessionLifecycle.Active,
        ),
    )
}

private fun sampleVpsDetail(): TaskSessionDetail {
    return TaskSessionDetail(
        key = TaskSessionKey(
            workspaceId = "workspace_001",
            sessionId = "session_001",
        ),
        workspace = TaskWorkspaceKey(
            workspaceId = "workspace_001",
            repoPath = "E:/projects/android_task_manager",
            workdir = "feature/taskmail/internal",
        ),
        sessionName = "say hi",
        backend = TaskMailBackend.Codex,
        status = TaskMailSessionStatus.Queued,
        lifecycle = TaskMailSessionLifecycle.Active,
        repoPath = "E:/projects/android_task_manager",
        workdir = "feature/taskmail/internal",
        timeline = listOf(
            TaskTimelineItem(
                id = "android-create-session:session_001",
                timestamp = 1_000L,
                direction = TaskTimelineDirection.System,
                summary = "Queued",
                body = TaskMessageBody(plainTextFallback = "Queued"),
            ),
        ),
        projectionSyncState = TaskSessionProjectionSyncState(
            dataSource = TaskSessionProjectionDataSource.VpsNative,
            subscriptionStatus = TaskSessionProjectionSubscriptionStatus.Idle,
        ),
        pendingQuestions = persistentListOf(),
    )
}
