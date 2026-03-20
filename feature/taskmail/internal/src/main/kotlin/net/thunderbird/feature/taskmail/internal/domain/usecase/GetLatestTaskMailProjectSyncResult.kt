package net.thunderbird.feature.taskmail.internal.domain.usecase

import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailProjectSyncResult
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskMailProjectSyncRepository

internal class GetLatestTaskMailProjectSyncResult(
    private val repository: TaskMailProjectSyncRepository,
) {
    suspend operator fun invoke(accountUuid: String): TaskMailProjectSyncResult? {
        return repository.getLatestResult(accountUuid)
    }
}
