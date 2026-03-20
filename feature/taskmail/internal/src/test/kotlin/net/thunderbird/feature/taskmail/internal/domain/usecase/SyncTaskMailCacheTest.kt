package net.thunderbird.feature.taskmail.internal.domain.usecase

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import net.thunderbird.feature.taskmail.internal.data.TaskMailMessage
import net.thunderbird.feature.taskmail.internal.data.TaskMailSessionProjector
import net.thunderbird.feature.taskmail.internal.data.cache.TaskMailMessageJsonCodec
import net.thunderbird.feature.taskmail.internal.domain.model.MessageSyncState
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailBackend
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSessionStatus
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionDetail
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionKey
import net.thunderbird.feature.taskmail.internal.domain.model.UnifiedMessage
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskMailDetection
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskMailParsedSubject
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskStateCapsule
import net.thunderbird.feature.taskmail.internal.domain.repository.MessageSyncStateRepository
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskSessionDetailRepository
import net.thunderbird.feature.taskmail.internal.domain.repository.UnifiedMessageRepository
import net.thunderbird.feature.taskmail.internal.sync.DEFAULT_MESSAGE_SYNC_SCOPE_KEY
import net.thunderbird.feature.taskmail.internal.sync.DEFAULT_MESSAGE_SYNC_SOURCE
import net.thunderbird.feature.taskmail.internal.sync.MessageSyncCoordinator
import net.thunderbird.feature.taskmail.internal.sync.MessageSyncMode
import net.thunderbird.feature.taskmail.internal.sync.MessageSyncRequest

class SyncTaskMailCacheTest {

    @Test
    fun `invoke should bootstrap when cache and sync state are both missing`() = runTest {
        val syncCoordinator = RecordingMessageSyncCoordinator()
        val sessionDetailRepository = InMemoryTaskSessionDetailRepository()
        val testSubject = SyncTaskMailCache(
            unifiedMessageRepository = InMemoryUnifiedMessageRepository(),
            messageSyncStateRepository = InMemoryMessageSyncStateRepository(),
            syncCoordinator = syncCoordinator,
            taskMailMessageJsonCodec = TaskMailMessageJsonCodec(),
            sessionProjector = TaskMailSessionProjector(),
            taskSessionDetailRepository = sessionDetailRepository,
            clock = { 2_000L },
        )

        val result = testSubject()

        assertThat(result.isSuccess).isEqualTo(true)
        assertThat(syncCoordinator.requests).isEqualTo(
            listOf(
                MessageSyncRequest(
                    mode = MessageSyncMode.Bootstrap,
                    recentMessageLimit = Int.MAX_VALUE,
                ),
            ),
        )
        assertThat(sessionDetailRepository.sessionDetails).isEqualTo(emptyList())
        assertThat(sessionDetailRepository.replaceAllCalls).isEqualTo(1)
    }

    @Test
    fun `invoke should recover when cache is empty but cursor state exists`() = runTest {
        val syncCoordinator = RecordingMessageSyncCoordinator()
        val sessionDetailRepository = InMemoryTaskSessionDetailRepository()
        val testSubject = SyncTaskMailCache(
            unifiedMessageRepository = InMemoryUnifiedMessageRepository(),
            messageSyncStateRepository = InMemoryMessageSyncStateRepository(
                MessageSyncState(
                    source = DEFAULT_MESSAGE_SYNC_SOURCE,
                    scopeKey = DEFAULT_MESSAGE_SYNC_SCOPE_KEY,
                    lastCursor = "cursor-1",
                    lastSyncAt = 123L,
                ),
            ),
            syncCoordinator = syncCoordinator,
            taskMailMessageJsonCodec = TaskMailMessageJsonCodec(),
            sessionProjector = TaskMailSessionProjector(),
            taskSessionDetailRepository = sessionDetailRepository,
            clock = { 2_000L },
        )

        val result = testSubject()

        assertThat(result.isSuccess).isEqualTo(true)
        assertThat(syncCoordinator.requests).isEqualTo(
            listOf(
                MessageSyncRequest(
                    mode = MessageSyncMode.Recovery,
                    recentMessageLimit = Int.MAX_VALUE,
                ),
            ),
        )
        assertThat(sessionDetailRepository.sessionDetails).isEqualTo(emptyList())
        assertThat(sessionDetailRepository.replaceAllCalls).isEqualTo(1)
    }

    @Test
    fun `invoke should rebuild detail snapshots during incremental sync`() = runTest {
        val syncCoordinator = RecordingMessageSyncCoordinator()
        val sessionDetailRepository = InMemoryTaskSessionDetailRepository()
        val messageCodec = TaskMailMessageJsonCodec()
        val testSubject = SyncTaskMailCache(
            unifiedMessageRepository = InMemoryUnifiedMessageRepository(
                initialMessages = listOf(
                    sampleUnifiedMessage(messageJson = messageCodec.encode(sampleTaskMailMessage())),
                ),
            ),
            messageSyncStateRepository = InMemoryMessageSyncStateRepository(
                MessageSyncState(
                    source = DEFAULT_MESSAGE_SYNC_SOURCE,
                    scopeKey = DEFAULT_MESSAGE_SYNC_SCOPE_KEY,
                    lastCursor = "cursor-1",
                    lastSyncAt = 123L,
                ),
            ),
            syncCoordinator = syncCoordinator,
            taskMailMessageJsonCodec = messageCodec,
            sessionProjector = TaskMailSessionProjector(),
            taskSessionDetailRepository = sessionDetailRepository,
            clock = { 2_000L },
        )

        val result = testSubject()

        assertThat(result.isSuccess).isEqualTo(true)
        assertThat(syncCoordinator.requests).isEqualTo(listOf(MessageSyncRequest()))
        assertThat(sessionDetailRepository.sessionDetails.map { it.sessionName }).isEqualTo(listOf("Implement parser"))
        assertThat(sessionDetailRepository.replaceAllCalls).isEqualTo(1)
        assertThat(sessionDetailRepository.upsertCalls).isEqualTo(0)
    }

    @Test
    fun `invoke should skip repeated incremental sync inside throttle window`() = runTest {
        var now = 2_000L
        val syncCoordinator = RecordingMessageSyncCoordinator()
        val sessionDetailRepository = InMemoryTaskSessionDetailRepository()
        val messageCodec = TaskMailMessageJsonCodec()
        val testSubject = SyncTaskMailCache(
            unifiedMessageRepository = InMemoryUnifiedMessageRepository(
                initialMessages = listOf(
                    sampleUnifiedMessage(messageJson = messageCodec.encode(sampleTaskMailMessage())),
                ),
            ),
            messageSyncStateRepository = InMemoryMessageSyncStateRepository(
                MessageSyncState(
                    source = DEFAULT_MESSAGE_SYNC_SOURCE,
                    scopeKey = DEFAULT_MESSAGE_SYNC_SCOPE_KEY,
                    lastCursor = "cursor-1",
                    lastSyncAt = 123L,
                ),
            ),
            syncCoordinator = syncCoordinator,
            taskMailMessageJsonCodec = messageCodec,
            sessionProjector = TaskMailSessionProjector(),
            taskSessionDetailRepository = sessionDetailRepository,
            clock = { now },
        )

        testSubject()
        now = 2_500L
        val secondResult = testSubject()

        assertThat(secondResult.isSuccess).isEqualTo(true)
        assertThat(syncCoordinator.requests).isEqualTo(listOf(MessageSyncRequest()))
        assertThat(sessionDetailRepository.sessionDetails.map { it.sessionName }).isEqualTo(listOf("Implement parser"))
        assertThat(sessionDetailRepository.replaceAllCalls).isEqualTo(1)
        assertThat(sessionDetailRepository.upsertCalls).isEqualTo(0)
    }

    @Suppress("LongMethod")
    @Test
    fun `invoke should update only affected session detail snapshots during incremental sync`() = runTest {
        val messageCodec = TaskMailMessageJsonCodec()
        val parserMessage = sampleTaskMailMessage(
            sessionId = "session-1",
            threadId = "thread-100",
            sessionName = "Implement parser",
            messageServerId = "server-1",
            summary = "Parser is running.",
        )
        val repositoryMessage = sampleTaskMailMessage(
            sessionId = "session-2",
            threadId = "thread-200",
            sessionName = "Wire repository",
            messageServerId = "server-2",
            summary = "Repository wiring is queued.",
            timestamp = 456L,
            threadRootId = 200L,
        )
        val updatedParserMessage = parserMessage.copy(
            rawBodyText = """
                Summary: Parser completed.

                ---TASK-STATE-BEGIN---
                thread_id: thread-100
                workspace_id: workspace-1
                session_id: session-1
                session_name: Implement parser
                repo_path: E:/projects/android_task_manager
                workdir: feature/taskmail
                backend: codex
                status: done
                last_summary: Parser completed.
                ---TASK-STATE-END---
            """.trimIndent(),
            detection = parserMessage.detection.copy(
                stateCapsule = parserMessage.detection.stateCapsule?.copy(
                    status = TaskMailSessionStatus.Done,
                    lastSummary = "Parser completed.",
                ),
            ),
        )
        val unifiedMessageRepository = InMemoryUnifiedMessageRepository(
            initialMessages = listOf(
                sampleUnifiedMessage(
                    sourceMessageId = "account-1:42:server-1",
                    messageJson = messageCodec.encode(parserMessage),
                ),
                sampleUnifiedMessage(
                    sourceMessageId = "account-1:42:server-2",
                    messageJson = messageCodec.encode(repositoryMessage),
                ),
            ),
        )
        val syncCoordinator = RecordingMessageSyncCoordinator {
            unifiedMessageRepository.upsertMessages(
                listOf(
                    sampleUnifiedMessage(
                        sourceMessageId = "account-1:42:server-1",
                        messageJson = messageCodec.encode(updatedParserMessage),
                    ),
                ),
            )
            Result.success(Unit)
        }
        val sessionDetailRepository = InMemoryTaskSessionDetailRepository(
            sessionDetails = projectSessionDetails(parserMessage, repositoryMessage),
        )
        val testSubject = SyncTaskMailCache(
            unifiedMessageRepository = unifiedMessageRepository,
            messageSyncStateRepository = InMemoryMessageSyncStateRepository(
                MessageSyncState(
                    source = DEFAULT_MESSAGE_SYNC_SOURCE,
                    scopeKey = DEFAULT_MESSAGE_SYNC_SCOPE_KEY,
                    lastCursor = "cursor-1",
                    lastSyncAt = 123L,
                ),
            ),
            syncCoordinator = syncCoordinator,
            taskMailMessageJsonCodec = messageCodec,
            sessionProjector = TaskMailSessionProjector(),
            taskSessionDetailRepository = sessionDetailRepository,
            clock = { 2_000L },
        )

        val result = testSubject()

        assertThat(result.isSuccess).isEqualTo(true)
        assertThat(syncCoordinator.requests).isEqualTo(listOf(MessageSyncRequest()))
        assertThat(sessionDetailRepository.replaceAllCalls).isEqualTo(0)
        assertThat(sessionDetailRepository.upsertCalls).isEqualTo(1)
        assertThat(sessionDetailRepository.removeCalls).isEqualTo(0)
        assertThat(
            sessionDetailRepository.sessionDetails.first { detail -> detail.key.sessionId == "session-1" }.lastSummary,
        ).isEqualTo("Parser completed.")
        assertThat(
            sessionDetailRepository.sessionDetails.first { detail -> detail.key.sessionId == "session-2" }.lastSummary,
        ).isEqualTo("Repository wiring is queued.")
    }
}

private class RecordingMessageSyncCoordinator(
    private val onSync: suspend (MessageSyncRequest) -> Result<Unit> = { Result.success(Unit) },
) : MessageSyncCoordinator {
    val requests = mutableListOf<MessageSyncRequest>()

    override suspend fun sync(request: MessageSyncRequest): Result<Unit> {
        requests += request
        return onSync(request)
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
        val mergedMessages = state.value
            .associateBy(UnifiedMessage::storageKey)
            .toMutableMap()

        messages.forEach { message ->
            mergedMessages[message.storageKey()] = message
        }

        state.value = mergedMessages.values.sortedBy(UnifiedMessage::sourceMessageId)
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
    private var state: MessageSyncState? = null,
) : MessageSyncStateRepository {
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

private class InMemoryTaskSessionDetailRepository : TaskSessionDetailRepository {
    constructor(sessionDetails: List<TaskSessionDetail> = emptyList()) {
        this.sessionDetails = sessionDetails
    }

    var sessionDetails: List<TaskSessionDetail> = emptyList()
    var replaceAllCalls: Int = 0
    var upsertCalls: Int = 0
    var removeCalls: Int = 0

    override suspend fun getTaskSessionDetail(key: TaskSessionKey): TaskSessionDetail? {
        return sessionDetails.firstOrNull { detail -> detail.key == key }
    }

    override suspend fun getTaskSessionDetails(): List<TaskSessionDetail> = sessionDetails

    override suspend fun replaceAllSessionDetails(details: List<TaskSessionDetail>) {
        replaceAllCalls += 1
        sessionDetails = details
    }

    override suspend fun upsertSessionDetails(details: List<TaskSessionDetail>) {
        if (details.isEmpty()) return

        upsertCalls += 1
        val mergedDetails = sessionDetails
            .associateBy(TaskSessionDetail::key)
            .toMutableMap()

        details.forEach { detail ->
            mergedDetails[detail.key] = detail
        }

        sessionDetails = mergedDetails.values.toList()
    }

    override suspend fun removeSessionDetails(keys: List<TaskSessionKey>) {
        if (keys.isEmpty()) return

        removeCalls += 1
        val keysToRemove = keys.toSet()
        sessionDetails = sessionDetails.filterNot { detail -> detail.key in keysToRemove }
    }
}

private fun sampleUnifiedMessage(
    sourceMessageId: String = "account-1:42:server-1",
    messageJson: String = "{}",
): UnifiedMessage {
    return UnifiedMessage(
        source = DEFAULT_MESSAGE_SYNC_SOURCE,
        sourceMessageId = sourceMessageId,
        taskId = null,
        createdAt = 123L,
        contentHash = "hash",
        parserVersion = 1,
        payloadJson = "{}",
        messageJson = messageJson,
    )
}

private fun sampleTaskMailMessage(
    sessionId: String = "session-1",
    threadId: String = "thread-100",
    sessionName: String = "Implement parser",
    messageServerId: String = "server-1",
    summary: String = "Parser is running.",
    timestamp: Long = 123L,
    threadRootId: Long = 100L,
): TaskMailMessage {
    return TaskMailMessage(
        accountUuid = "account-1",
        folderId = 1L,
        messageServerId = messageServerId,
        threadRootId = threadRootId,
        timestamp = timestamp,
        subject = "[RUNNING] [CX] [S:$sessionId] $sessionName",
        rawBodyText = """
            Summary: $summary

            ---TASK-STATE-BEGIN---
            thread_id: $threadId
            workspace_id: workspace-1
            session_id: $sessionId
            session_name: $sessionName
            repo_path: E:/projects/android_task_manager
            workdir: feature/taskmail
            backend: codex
            status: running
            last_summary: $summary
            ---TASK-STATE-END---
        """.trimIndent(),
        detection = TaskMailDetection(
            isTaskMail = true,
            isSystemMessage = true,
            parsedSubject = TaskMailParsedSubject(
                backend = TaskMailBackend.Codex,
                statusLabel = null,
                sessionIdFromSubject = sessionId,
                subjectText = sessionName,
                isReplyLike = false,
            ),
            stateCapsule = TaskStateCapsule(
                threadId = threadId,
                workspaceId = "workspace-1",
                sessionId = sessionId,
                sessionName = sessionName,
                backend = TaskMailBackend.Codex,
                repoPath = "E:/projects/android_task_manager",
                workdir = "feature/taskmail",
                status = TaskMailSessionStatus.Running,
                lastSummary = summary,
            ),
        ),
        isFromCurrentUser = false,
    )
}

private fun projectSessionDetails(vararg messages: TaskMailMessage): List<TaskSessionDetail> {
    return TaskMailSessionProjector().projectSessionDetails(messages.toList())
}

private fun UnifiedMessage.storageKey(): String = "$source|$sourceMessageId"
