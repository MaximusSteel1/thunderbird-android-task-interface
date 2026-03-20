package net.thunderbird.feature.taskmail.internal.domain.repository

import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailProjectSyncResult

internal interface TaskMailProjectSyncRepository {
    suspend fun getLatestResult(accountUuid: String): TaskMailProjectSyncResult?

    suspend fun requestSync(accountUuid: String): Result<Unit>
}
