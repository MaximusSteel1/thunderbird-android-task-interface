package net.thunderbird.feature.taskmail.internal.domain.repository

import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionDetail
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionKey

internal interface TaskSessionDetailRepository {
    suspend fun getTaskSessionDetail(key: TaskSessionKey): TaskSessionDetail?

    suspend fun getTaskSessionDetails(): List<TaskSessionDetail>

    suspend fun replaceAllSessionDetails(details: List<TaskSessionDetail>)

    suspend fun upsertSessionDetails(details: List<TaskSessionDetail>)

    suspend fun removeSessionDetails(keys: List<TaskSessionKey>)
}
