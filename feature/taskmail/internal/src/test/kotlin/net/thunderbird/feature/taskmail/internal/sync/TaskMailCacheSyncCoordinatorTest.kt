package net.thunderbird.feature.taskmail.internal.sync

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import net.thunderbird.feature.taskmail.internal.data.cache.EmailIngressPayloadJsonCodec
import net.thunderbird.feature.taskmail.internal.data.cache.TaskMailMessageJsonCodec
import net.thunderbird.feature.taskmail.internal.data.ingress.EmailIngressMessage
import net.thunderbird.feature.taskmail.internal.data.ingress.MessageIngress
import net.thunderbird.feature.taskmail.internal.data.parser.EmailMessageParser
import net.thunderbird.feature.taskmail.internal.domain.model.MessageSyncState
import net.thunderbird.feature.taskmail.internal.domain.model.UnifiedMessage
import net.thunderbird.feature.taskmail.internal.domain.repository.MessageSyncStateRepository
import net.thunderbird.feature.taskmail.internal.domain.repository.UnifiedMessageRepository

class TaskMailCacheSyncCoordinatorTest {

    @Test
    fun `sync should bootstrap from a full local scan when no cursor state exists`() = runTest {
        // Arrange
        val ingress = FakeEmailIngress(
            latestMessages = listOf(sampleIngressMessage()),
        )
        val unifiedMessageRepository = InMemoryUnifiedMessageRepository()
        val syncStateRepository = InMemoryMessageSyncStateRepository()
        val testSubject = TaskMailCacheSyncCoordinator(
            messageIngress = ingress,
            messageParser = EmailMessageParser(),
            unifiedMessageRepository = unifiedMessageRepository,
            messageSyncStateRepository = syncStateRepository,
            payloadJsonCodec = EmailIngressPayloadJsonCodec(),
            taskMailMessageJsonCodec = TaskMailMessageJsonCodec(),
            clock = { 999L },
        )

        // Act
        val result = testSubject.sync()

        // Assert
        assertThat(result.isSuccess).isEqualTo(true)
        assertThat(ingress.latestRequestLimits).isEqualTo(listOf(Int.MAX_VALUE))
        assertThat(unifiedMessageRepository.getAllMessages()).hasSize(1)
        assertThat(unifiedMessageRepository.getAllMessages().single().messageJson.isNotBlank()).isEqualTo(true)
        assertThat(
            syncStateRepository.getState(
                source = DEFAULT_MESSAGE_SYNC_SOURCE,
                scopeKey = DEFAULT_MESSAGE_SYNC_SCOPE_KEY,
            ),
        ).isEqualTo(
            MessageSyncState(
                source = DEFAULT_MESSAGE_SYNC_SOURCE,
                scopeKey = DEFAULT_MESSAGE_SYNC_SCOPE_KEY,
                lastCursor = "123:account-1:42:server-1",
                lastSyncAt = 999L,
            ),
        )
    }

    @Test
    fun `sync should use incremental cursor fetch once sync state exists`() = runTest {
        // Arrange
        val ingress = FakeEmailIngress(
            sinceMessages = listOf(sampleIngressMessage(messageServerId = "server-2", timestamp = 456L)),
        )
        val unifiedMessageRepository = InMemoryUnifiedMessageRepository()
        val syncStateRepository = InMemoryMessageSyncStateRepository(
            MessageSyncState(
                source = DEFAULT_MESSAGE_SYNC_SOURCE,
                scopeKey = DEFAULT_MESSAGE_SYNC_SCOPE_KEY,
                lastCursor = "cursor-1",
                lastSyncAt = 111L,
            ),
        )
        val testSubject = TaskMailCacheSyncCoordinator(
            messageIngress = ingress,
            messageParser = EmailMessageParser(),
            unifiedMessageRepository = unifiedMessageRepository,
            messageSyncStateRepository = syncStateRepository,
            payloadJsonCodec = EmailIngressPayloadJsonCodec(),
            taskMailMessageJsonCodec = TaskMailMessageJsonCodec(),
            clock = { 222L },
        )

        // Act
        val result = testSubject.sync()

        // Assert
        assertThat(result.isSuccess).isEqualTo(true)
        assertThat(ingress.sinceRequests).isEqualTo(
            listOf(
                SinceRequest(
                    cursor = "cursor-1",
                    recentMessageLimit = 10,
                ),
            ),
        )
        assertThat(unifiedMessageRepository.getAllMessages().single().sourceMessageId).isEqualTo(
            "account-1:42:server-2",
        )
        assertThat(
            syncStateRepository.getState(
                source = DEFAULT_MESSAGE_SYNC_SOURCE,
                scopeKey = DEFAULT_MESSAGE_SYNC_SCOPE_KEY,
            )?.lastCursor,
        ).isEqualTo("456:account-1:42:server-2")
    }
}

private class FakeEmailIngress(
    private val latestMessages: List<EmailIngressMessage> = emptyList(),
    private val sinceMessages: List<EmailIngressMessage> = emptyList(),
) : MessageIngress<EmailIngressMessage> {
    val latestRequestLimits = mutableListOf<Int>()
    val sinceRequests = mutableListOf<SinceRequest>()

    override suspend fun fetchLatest(recentMessageLimit: Int): List<EmailIngressMessage> {
        latestRequestLimits += recentMessageLimit
        return latestMessages
    }

    override suspend fun fetchSince(
        cursor: String,
        recentMessageLimit: Int,
    ): List<EmailIngressMessage> {
        sinceRequests += SinceRequest(
            cursor = cursor,
            recentMessageLimit = recentMessageLimit,
        )
        return sinceMessages
    }
}

private data class SinceRequest(
    val cursor: String,
    val recentMessageLimit: Int,
)

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

private class InMemoryMessageSyncStateRepository(
    initialState: MessageSyncState? = null,
) : MessageSyncStateRepository {
    private var state: MessageSyncState? = initialState

    override suspend fun getState(
        source: String,
        scopeKey: String,
    ): MessageSyncState? {
        return state?.takeIf { currentState ->
            currentState.source == source && currentState.scopeKey == scopeKey
        }
    }

    override suspend fun upsertState(state: MessageSyncState) {
        this.state = state
    }
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
