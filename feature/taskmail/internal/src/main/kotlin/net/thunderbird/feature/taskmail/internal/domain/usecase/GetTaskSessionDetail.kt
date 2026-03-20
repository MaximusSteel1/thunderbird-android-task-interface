package net.thunderbird.feature.taskmail.internal.domain.usecase

import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionDetail
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionKey
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskSessionDetailRepository

internal class GetTaskSessionDetail(
    private val repository: TaskSessionDetailRepository,
) {
    suspend operator fun invoke(key: TaskSessionKey): TaskSessionDetail? {
        return repository.getTaskSessionDetail(key)
    }
}
