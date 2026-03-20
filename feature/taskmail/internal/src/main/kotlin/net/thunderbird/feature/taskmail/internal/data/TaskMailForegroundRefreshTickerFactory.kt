package net.thunderbird.feature.taskmail.internal.data

import kotlinx.coroutines.flow.Flow

internal fun interface TaskMailForegroundRefreshTickerFactory {
    fun createTicker(intervalMs: Long): Flow<Unit>
}
