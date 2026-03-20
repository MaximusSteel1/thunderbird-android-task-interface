package net.thunderbird.feature.taskmail.internal.data

import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

internal class DefaultTaskMailForegroundRefreshTickerFactory : TaskMailForegroundRefreshTickerFactory {
    override fun createTicker(intervalMs: Long): Flow<Unit> {
        require(intervalMs > 0) { "Foreground refresh interval must be greater than zero." }

        return flow {
            while (coroutineContext.isActive) {
                delay(intervalMs)
                emit(Unit)
            }
        }
    }
}
