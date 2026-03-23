package net.thunderbird.feature.taskmail.internal.data

import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailProjectSyncResult
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskMailProjectSyncRepository

internal class DefaultTaskMailProjectSyncRepository(
    private val requester: TaskMailProjectSyncRequester,
    private val resultReader: TaskMailProjectSyncResultReader,
) : TaskMailProjectSyncRepository {

    override suspend fun getLatestResult(accountUuid: String): TaskMailProjectSyncResult? {
        return resultReader.getLatestResult(accountUuid)
    }

    override suspend fun requestSync(accountUuid: String): Result<Unit> {
        return requester.requestSync(accountUuid)
    }

    override suspend fun requestSyncViaMail(accountUuid: String): Result<Unit> {
        return requester.requestSyncViaMail(accountUuid)
    }
}
