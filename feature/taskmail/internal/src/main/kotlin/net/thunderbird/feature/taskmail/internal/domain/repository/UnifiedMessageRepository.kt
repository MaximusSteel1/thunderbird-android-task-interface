package net.thunderbird.feature.taskmail.internal.domain.repository

import kotlinx.coroutines.flow.Flow
import net.thunderbird.feature.taskmail.internal.domain.model.UnifiedMessage

internal interface UnifiedMessageRepository {
    fun observeMessages(taskId: String): Flow<List<UnifiedMessage>>

    suspend fun getAllMessages(): List<UnifiedMessage>

    suspend fun upsertMessages(messages: List<UnifiedMessage>)

    suspend fun findBySourceMessageId(
        source: String,
        sourceMessageId: String,
    ): UnifiedMessage?
}
