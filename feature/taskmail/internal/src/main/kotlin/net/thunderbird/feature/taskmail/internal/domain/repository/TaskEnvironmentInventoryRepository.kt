package net.thunderbird.feature.taskmail.internal.domain.repository

import net.thunderbird.feature.taskmail.internal.domain.model.TaskEnvironmentInventorySnapshot

internal interface TaskEnvironmentInventoryRepository {
    suspend fun getEnvironmentInventory(): Result<TaskEnvironmentInventorySnapshot>
}
