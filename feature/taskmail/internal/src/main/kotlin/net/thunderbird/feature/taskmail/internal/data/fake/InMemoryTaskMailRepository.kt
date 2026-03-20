package net.thunderbird.feature.taskmail.internal.data.fake

import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionDetail
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionKey
import net.thunderbird.feature.taskmail.internal.domain.model.TaskWorkspaceSummary
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskMailRepository
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskSessionDetailRepository
import net.thunderbird.feature.taskmail.internal.preview.TaskMailPreviewData

internal class InMemoryTaskMailRepository(
    private val workspaceSummaries: List<TaskWorkspaceSummary> = TaskMailPreviewData.workspaceSummaries,
    private val sessionDetails: List<TaskSessionDetail> = TaskMailPreviewData.sessionDetails,
) : TaskMailRepository,
    TaskSessionDetailRepository {

    override suspend fun getTaskWorkspaceSummaries(): List<TaskWorkspaceSummary> {
        return workspaceSummaries
    }

    override suspend fun getTaskSessionDetail(key: TaskSessionKey): TaskSessionDetail? {
        return sessionDetails.firstOrNull { detail ->
            detail.key.threadId == key.threadId && detail.key.sessionId == key.sessionId
        }
    }

    override suspend fun getTaskSessionDetails(): List<TaskSessionDetail> = sessionDetails

    override suspend fun replaceAllSessionDetails(details: List<TaskSessionDetail>) = Unit

    override suspend fun upsertSessionDetails(details: List<TaskSessionDetail>) = Unit

    override suspend fun removeSessionDetails(keys: List<TaskSessionKey>) = Unit
}
