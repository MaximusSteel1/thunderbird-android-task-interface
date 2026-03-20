package net.thunderbird.feature.taskmail.internal.data

import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailProjectSyncResult

internal interface TaskMailProjectSyncResultReader {
    suspend fun getLatestResult(accountUuid: String): TaskMailProjectSyncResult?
}
