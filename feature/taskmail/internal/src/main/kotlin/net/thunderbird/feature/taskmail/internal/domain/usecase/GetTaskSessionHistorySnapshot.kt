package net.thunderbird.feature.taskmail.internal.domain.usecase

import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionHistorySnapshot
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionHistorySnapshotLocator
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskSessionHistorySnapshotRepository

internal class GetTaskSessionHistorySnapshot(
    private val repository: TaskSessionHistorySnapshotRepository,
) {
    suspend operator fun invoke(locator: TaskSessionHistorySnapshotLocator): Result<TaskSessionHistorySnapshot> {
        return repository.getHistorySnapshot(locator)
    }
}
