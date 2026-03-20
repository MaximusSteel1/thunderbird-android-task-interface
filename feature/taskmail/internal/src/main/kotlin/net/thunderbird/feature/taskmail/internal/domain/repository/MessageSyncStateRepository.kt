package net.thunderbird.feature.taskmail.internal.domain.repository

import net.thunderbird.feature.taskmail.internal.domain.model.MessageSyncState

internal interface MessageSyncStateRepository {
    suspend fun getState(
        source: String,
        scopeKey: String,
    ): MessageSyncState?

    suspend fun upsertState(state: MessageSyncState)
}
