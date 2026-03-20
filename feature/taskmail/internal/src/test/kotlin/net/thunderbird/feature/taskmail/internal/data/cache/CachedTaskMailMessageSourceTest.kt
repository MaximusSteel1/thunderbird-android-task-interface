package net.thunderbird.feature.taskmail.internal.data.cache

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import net.thunderbird.feature.taskmail.internal.data.ingress.EmailIngressMessage
import net.thunderbird.feature.taskmail.internal.data.parser.EmailMessageParser
import net.thunderbird.feature.taskmail.internal.domain.model.UnifiedMessage
import net.thunderbird.feature.taskmail.internal.domain.repository.UnifiedMessageRepository
import net.thunderbird.feature.taskmail.internal.sync.DEFAULT_MESSAGE_SYNC_SOURCE

class CachedTaskMailMessageSourceTest {

    @Test
    fun `getMessages should decode cached projections from local cache`() = runTest {
        val payloadJsonCodec = EmailIngressPayloadJsonCodec()
        val taskMailMessageJsonCodec = TaskMailMessageJsonCodec()
        val existingMessage = sampleIngressMessage()
        val testSubject = CachedTaskMailMessageSource(
            unifiedMessageRepository = InMemoryUnifiedMessageRepository(
                initialMessages = listOf(existingMessage.toUnifiedMessage(payloadJsonCodec, taskMailMessageJsonCodec)),
            ),
            taskMailMessageJsonCodec = taskMailMessageJsonCodec,
        )

        val result = testSubject.getMessages()

        assertThat(result).hasSize(1)
        assertThat(result.single().messageServerId).isEqualTo("server-1")
        assertThat(result.single().subject).isEqualTo("[OC] Refactor floor_shear")
    }

    @Test
    fun `getMessages should ignore undecodable cached projections`() = runTest {
        val payloadJsonCodec = EmailIngressPayloadJsonCodec()
        val taskMailMessageJsonCodec = TaskMailMessageJsonCodec()
        val existingMessage = sampleIngressMessage()
        val testSubject = CachedTaskMailMessageSource(
            unifiedMessageRepository = InMemoryUnifiedMessageRepository(
                initialMessages = listOf(
                    existingMessage.toUnifiedMessage(
                        payloadJsonCodec = payloadJsonCodec,
                        taskMailMessageJsonCodec = taskMailMessageJsonCodec,
                        messageJsonOverride = "{invalid",
                    ),
                ),
            ),
            taskMailMessageJsonCodec = taskMailMessageJsonCodec,
        )

        val result = testSubject.getMessages()

        assertThat(result).isEqualTo(emptyList())
    }
}

private class InMemoryUnifiedMessageRepository(
    initialMessages: List<UnifiedMessage> = emptyList(),
) : UnifiedMessageRepository {
    private val state = MutableStateFlow(initialMessages)

    override fun observeMessages(taskId: String): Flow<List<UnifiedMessage>> {
        return state.map { messages ->
            messages.filter { message -> message.taskId == taskId }
        }
    }

    override suspend fun getAllMessages(): List<UnifiedMessage> {
        return state.value
    }

    override suspend fun upsertMessages(messages: List<UnifiedMessage>) {
        state.value = (state.value + messages)
            .associateBy { message -> "${message.source}|${message.sourceMessageId}" }
            .values
            .sortedBy(UnifiedMessage::createdAt)
    }

    override suspend fun findBySourceMessageId(
        source: String,
        sourceMessageId: String,
    ): UnifiedMessage? {
        return state.value.firstOrNull { message ->
            message.source == source && message.sourceMessageId == sourceMessageId
        }
    }
}

private fun EmailIngressMessage.toUnifiedMessage(
    payloadJsonCodec: EmailIngressPayloadJsonCodec,
    taskMailMessageJsonCodec: TaskMailMessageJsonCodec,
    messageJsonOverride: String? = null,
): UnifiedMessage {
    val payloadJson = payloadJsonCodec.encode(this)
    val parsedMessage = checkNotNull(EmailMessageParser().parse(this))
    return UnifiedMessage(
        source = DEFAULT_MESSAGE_SYNC_SOURCE,
        sourceMessageId = "account-1:42:$messageServerId",
        taskId = null,
        createdAt = timestamp,
        contentHash = payloadJson,
        parserVersion = 1,
        payloadJson = payloadJson,
        messageJson = messageJsonOverride ?: taskMailMessageJsonCodec.encode(parsedMessage),
    )
}

private fun sampleIngressMessage(
    messageServerId: String = "server-1",
    timestamp: Long = 123L,
): EmailIngressMessage {
    return EmailIngressMessage(
        accountUuid = "account-1",
        accountEmailAddress = "user@example.com",
        folderId = 42L,
        messageServerId = messageServerId,
        threadRootId = 7L,
        timestamp = timestamp,
        subject = "[OC] Refactor floor_shear",
        fromAddresses = listOf("user@example.com"),
        rawBodyText = "Please refactor floor_shear.",
    )
}
