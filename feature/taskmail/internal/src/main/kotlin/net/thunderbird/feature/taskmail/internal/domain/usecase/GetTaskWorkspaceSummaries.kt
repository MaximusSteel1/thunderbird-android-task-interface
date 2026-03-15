package net.thunderbird.feature.taskmail.internal.domain.usecase

import net.thunderbird.feature.taskmail.internal.domain.model.TaskWorkspaceSummary
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskMailRepository

internal class GetTaskWorkspaceSummaries(
    private val repository: TaskMailRepository,
) {
    suspend operator fun invoke(): List<TaskWorkspaceSummary> {
        return repository.getTaskWorkspaceSummaries()
    }
}
