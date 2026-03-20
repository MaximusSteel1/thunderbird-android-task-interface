package net.thunderbird.feature.taskmail.internal.data

import java.io.File
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionDetail
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionKey
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionSummary
import net.thunderbird.feature.taskmail.internal.domain.model.TaskWorkspaceSummary
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskMailRepository
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskSessionDetailRepository

internal class SnapshotBackedTaskMailRepository(
    private val taskSessionDetailRepository: TaskSessionDetailRepository,
    private val workspaceSummaryProjector: TaskWorkspaceSummaryProjector = TaskWorkspaceSummaryProjector(),
) : TaskMailRepository {
    override suspend fun getTaskWorkspaceSummaries(): List<TaskWorkspaceSummary> {
        return workspaceSummaryProjector.project(
            taskSessionDetailRepository.getTaskSessionDetails(),
        )
    }

    override suspend fun getTaskSessionDetail(key: TaskSessionKey): TaskSessionDetail? {
        return taskSessionDetailRepository.getTaskSessionDetail(key)
    }
}

internal class TaskWorkspaceSummaryProjector {
    fun project(details: List<TaskSessionDetail>): List<TaskWorkspaceSummary> {
        return details
            .groupBy(TaskSessionDetail::workspace)
            .values
            .map(::toWorkspaceSummary)
            .sortedByDescending { workspace ->
                workspace.sessions.maxOfOrNull(TaskSessionSummary::lastUpdatedAt) ?: Long.MIN_VALUE
            }
    }

    private fun toWorkspaceSummary(details: List<TaskSessionDetail>): TaskWorkspaceSummary {
        val primaryDetail = details.maxByOrNull { detail -> detail.lastUpdatedAt() } ?: details.first()
        val sessions = details
            .sortedByDescending { detail -> detail.lastUpdatedAt() }
            .map { detail -> detail.toSessionSummary() }
        val workspace = primaryDetail.workspace

        return TaskWorkspaceSummary(
            key = workspace,
            title = workspace.workspaceId
                ?: workspace.repoPath.takeIf { it.isNotBlank() }?.let(::deriveWorkspaceTitle)
                ?: "Task workspace",
            subtitle = primaryDetail.workdir,
            backendSet = details.map(TaskSessionDetail::backend).toSet(),
            activeSessionId = sessions.firstOrNull()?.key?.sessionId,
            sessionCount = sessions.size,
            sessions = sessions,
        )
    }
}

private fun TaskSessionDetail.toSessionSummary(): TaskSessionSummary {
    return TaskSessionSummary(
        key = key,
        sessionName = sessionName,
        status = status,
        lifecycle = lifecycle,
        backend = backend,
        lastSummary = lastSummary,
        lastActiveAt = lastActiveAt,
        lastProgressAt = lastProgressAt,
        lastUpdatedAt = lastUpdatedAt(),
        pendingQuestion = pendingQuestions.isNotEmpty(),
    )
}

private fun TaskSessionDetail.lastUpdatedAt(): Long {
    return timeline.lastOrNull()?.timestamp ?: 0L
}

private fun deriveWorkspaceTitle(repoPath: String): String {
    return File(repoPath).name.takeIf { it.isNotBlank() } ?: repoPath
}
