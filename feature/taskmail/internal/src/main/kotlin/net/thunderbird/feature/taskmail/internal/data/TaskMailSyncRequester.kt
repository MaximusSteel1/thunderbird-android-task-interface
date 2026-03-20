package net.thunderbird.feature.taskmail.internal.data

internal interface TaskMailSyncRequester {
    suspend fun requestSync(): Result<Unit>

    suspend fun requestSync(accountUuid: String?): Result<Unit> {
        return requestSync()
    }
}
