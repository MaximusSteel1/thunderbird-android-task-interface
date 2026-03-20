package net.thunderbird.feature.taskmail.internal.data

import kotlinx.coroutines.flow.Flow

internal interface TaskMailStoreChangeObserver {
    fun changes(): Flow<Unit>
}
