package net.thunderbird.feature.taskmail.internal.domain.repository

import kotlinx.coroutines.flow.Flow
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionHistorySnapshot
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionHistorySnapshotLocator

internal interface TaskSessionUpdatesRepository {
    fun observeSessionUpdates(locator: TaskSessionHistorySnapshotLocator): Flow<TaskSessionHistorySnapshot>
}
