package net.thunderbird.feature.taskmail.internal.domain.repository

import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionHistorySnapshot
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionHistorySnapshotLocator

internal interface TaskSessionHistorySnapshotRepository {
    suspend fun getHistorySnapshot(locator: TaskSessionHistorySnapshotLocator): Result<TaskSessionHistorySnapshot>
}
