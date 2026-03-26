package net.thunderbird.feature.taskmail.internal.domain.usecase

import kotlinx.coroutines.flow.Flow
import net.thunderbird.feature.taskmail.internal.data.TaskSessionDetailStoreChangeObserver

internal class ObserveTaskSessionDetailStoreChanges(
    private val observer: TaskSessionDetailStoreChangeObserver,
) {
    operator fun invoke(): Flow<Unit> = observer.changes()
}

