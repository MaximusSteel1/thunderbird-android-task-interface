package net.thunderbird.feature.taskmail.internal.domain.usecase

import kotlinx.coroutines.flow.Flow
import net.thunderbird.feature.taskmail.internal.data.TaskMailStoreChangeObserver

internal class ObserveTaskMailStoreChanges(
    private val observer: TaskMailStoreChangeObserver,
) {
    operator fun invoke(): Flow<Unit> = observer.changes()
}
