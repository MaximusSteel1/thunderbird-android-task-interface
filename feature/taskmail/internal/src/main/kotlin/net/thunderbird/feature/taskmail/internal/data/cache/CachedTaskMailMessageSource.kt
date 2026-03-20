package net.thunderbird.feature.taskmail.internal.data.cache

import net.thunderbird.feature.taskmail.internal.data.TaskMailMessage
import net.thunderbird.feature.taskmail.internal.data.TaskMailMessageSource
import net.thunderbird.feature.taskmail.internal.domain.repository.UnifiedMessageRepository
import net.thunderbird.feature.taskmail.internal.sync.DEFAULT_MESSAGE_SYNC_SOURCE

internal class CachedTaskMailMessageSource(
    private val unifiedMessageRepository: UnifiedMessageRepository,
    private val taskMailMessageJsonCodec: TaskMailMessageJsonCodec,
) : TaskMailMessageSource {
    override suspend fun getMessages(): List<TaskMailMessage> {
        return unifiedMessageRepository.getAllMessages()
            .asSequence()
            .filter { message -> message.source == DEFAULT_MESSAGE_SYNC_SOURCE }
            .mapNotNull { message -> taskMailMessageJsonCodec.decode(message.messageJson) }
            .sortedBy(TaskMailMessage::timestamp)
            .toList()
    }
}
