package net.thunderbird.feature.taskmail.internal.domain.usecase

import net.thunderbird.feature.taskmail.internal.domain.repository.TaskMailProjectSyncRepository

internal class RequestTaskMailProjectSync(
    private val repository: TaskMailProjectSyncRepository,
) {
    suspend operator fun invoke(accountUuid: String): Result<Unit> {
        return repository.requestSync(accountUuid)
    }
}
