package net.thunderbird.feature.taskmail.internal.data

internal interface TaskMailProjectSyncRequester {
    suspend fun requestSync(accountUuid: String): Result<Unit>
}
