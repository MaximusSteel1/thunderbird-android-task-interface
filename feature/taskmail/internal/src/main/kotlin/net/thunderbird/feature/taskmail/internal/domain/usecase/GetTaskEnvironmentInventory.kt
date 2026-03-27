package net.thunderbird.feature.taskmail.internal.domain.usecase

import net.thunderbird.feature.taskmail.internal.domain.model.TaskEnvironmentInventorySnapshot
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskEnvironmentInventoryRepository

internal class GetTaskEnvironmentInventory(
    private val repository: TaskEnvironmentInventoryRepository,
) {
    suspend operator fun invoke(): Result<TaskEnvironmentInventorySnapshot> {
        return repository.getEnvironmentInventory()
    }
}
