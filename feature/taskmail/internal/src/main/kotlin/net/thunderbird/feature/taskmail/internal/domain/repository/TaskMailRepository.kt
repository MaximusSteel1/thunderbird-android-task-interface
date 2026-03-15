package net.thunderbird.feature.taskmail.internal.domain.repository

import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionDetail
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionKey
import net.thunderbird.feature.taskmail.internal.domain.model.TaskWorkspaceSummary

internal interface TaskMailRepository {
    suspend fun getTaskWorkspaceSummaries(): List<TaskWorkspaceSummary>

    suspend fun getTaskSessionDetail(key: TaskSessionKey): TaskSessionDetail?
}
