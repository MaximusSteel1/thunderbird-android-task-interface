package net.thunderbird.feature.taskmail.internal.domain.usecase

import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionDetail
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskSessionDetailRepository

internal class GetTaskSessionDetails(
    private val repository: TaskSessionDetailRepository,
) {
    suspend operator fun invoke(): List<TaskSessionDetail> {
        return repository.getTaskSessionDetails()
    }
}
