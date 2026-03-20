package net.thunderbird.feature.taskmail.internal.domain.usecase

import net.thunderbird.feature.taskmail.internal.data.TaskMailSyncRequester

internal class RefreshTaskMail(
    private val syncRequester: TaskMailSyncRequester,
    private val syncTaskMailCache: SyncTaskMailCache? = null,
) {
    suspend operator fun invoke(accountUuid: String? = null): Result<Unit> {
        val transportResult = syncRequester.requestSync(accountUuid)
        val cacheResult = syncTaskMailCache?.invoke(force = true) ?: Result.success(Unit)

        return when {
            transportResult.isFailure -> transportResult
            cacheResult.isFailure -> cacheResult
            else -> Result.success(Unit)
        }
    }
}
