package net.thunderbird.feature.taskmail.internal.domain.usecase

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.thunderbird.feature.taskmail.internal.data.TaskMailMessage
import net.thunderbird.feature.taskmail.internal.data.TaskMailSessionProjector
import net.thunderbird.feature.taskmail.internal.data.cache.TaskMailMessageJsonCodec
import net.thunderbird.feature.taskmail.internal.data.direct.mergeMailCompatibilityProjection
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionDetail
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionKey
import net.thunderbird.feature.taskmail.internal.domain.model.UnifiedMessage
import net.thunderbird.feature.taskmail.internal.domain.model.isCompatibleWith
import net.thunderbird.feature.taskmail.internal.domain.model.merge
import net.thunderbird.feature.taskmail.internal.domain.model.prefersVpsProjection
import net.thunderbird.feature.taskmail.internal.domain.repository.MessageSyncStateRepository
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskSessionDetailRepository
import net.thunderbird.feature.taskmail.internal.domain.repository.UnifiedMessageRepository
import net.thunderbird.feature.taskmail.internal.sync.DEFAULT_MESSAGE_SYNC_SCOPE_KEY
import net.thunderbird.feature.taskmail.internal.sync.DEFAULT_MESSAGE_SYNC_SOURCE
import net.thunderbird.feature.taskmail.internal.sync.DEFAULT_TASKMAIL_BOOTSTRAP_MESSAGE_LIMIT
import net.thunderbird.feature.taskmail.internal.sync.MessageSyncCoordinator
import net.thunderbird.feature.taskmail.internal.sync.MessageSyncMode
import net.thunderbird.feature.taskmail.internal.sync.MessageSyncRequest

@Suppress("TooManyFunctions")
internal class SyncTaskMailCache(
    private val unifiedMessageRepository: UnifiedMessageRepository,
    private val messageSyncStateRepository: MessageSyncStateRepository,
    private val syncCoordinator: MessageSyncCoordinator,
    private val taskMailMessageJsonCodec: TaskMailMessageJsonCodec,
    private val sessionProjector: TaskMailSessionProjector,
    private val taskSessionDetailRepository: TaskSessionDetailRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val syncMutex = Mutex()
    private var lastSyncAttemptAt: Long = 0L

    suspend operator fun invoke(force: Boolean = false): Result<Unit> {
        return withContext(ioDispatcher) {
            syncMutex.withLock {
                val cachedMessages = unifiedMessageRepository.getAllMessages()
                val syncState = messageSyncStateRepository.getState(
                    source = DEFAULT_MESSAGE_SYNC_SOURCE,
                    scopeKey = DEFAULT_MESSAGE_SYNC_SCOPE_KEY,
                )
                val hasStoredSessionDetails = taskSessionDetailRepository.getTaskSessionDetails().isNotEmpty()
                val syncRequest = resolveSyncRequest(
                    cachedMessages = cachedMessages,
                    syncStateExists = syncState != null,
                )
                val rebuildMode = resolveSnapshotRebuildMode(
                    syncMode = syncRequest.mode,
                    hasCachedMessages = cachedMessages.isNotEmpty(),
                    hasStoredSessionDetails = hasStoredSessionDetails,
                )

                if (!force && shouldSkipIncrementalSync(cachedMessages.isNotEmpty(), syncState != null)) {
                    return@withLock rebuildSessionDetails(
                        previousMessages = cachedMessages,
                        currentMessages = cachedMessages,
                        rebuildMode = rebuildMode,
                    )
                }

                val result = syncCoordinator.sync(request = syncRequest)
                lastSyncAttemptAt = clock()
                result.fold(
                    onSuccess = {
                        rebuildSessionDetails(
                            previousMessages = cachedMessages,
                            currentMessages = unifiedMessageRepository.getAllMessages(),
                            rebuildMode = rebuildMode,
                        )
                    },
                    onFailure = { throwable ->
                        Result.failure(throwable)
                    },
                )
            }
        }
    }

    private suspend fun rebuildSessionDetails(
        previousMessages: List<UnifiedMessage>,
        currentMessages: List<UnifiedMessage>,
        rebuildMode: SnapshotRebuildMode,
    ): Result<Unit> {
        return runCatching {
            when (rebuildMode) {
                SnapshotRebuildMode.Full -> rebuildAllSessionDetails(currentMessages)
                SnapshotRebuildMode.Incremental -> rebuildSessionDetailsIncrementally(
                    previousMessages = previousMessages,
                    currentMessages = currentMessages,
                )
            }
        }
    }

    private suspend fun rebuildSessionDetailsIncrementally(
        previousMessages: List<UnifiedMessage>,
        currentMessages: List<UnifiedMessage>,
    ) {
        val affectedKeys = resolveAffectedSessionKeys(
            previousMessages = previousMessages,
            currentMessages = currentMessages,
        ) ?: run {
            rebuildAllSessionDetails(currentMessages)
            return
        }

        if (affectedKeys.isEmpty()) return

        val taskMailMessages = decodeTaskMailMessages(currentMessages)
        val existingDetails = taskSessionDetailRepository.getTaskSessionDetails()
        val sessionDetails = sessionProjector.projectSessionDetails(
            messages = taskMailMessages,
            keys = affectedKeys,
        ).mergeExistingProjectionData(existingDetails)
        val projectedKeys = sessionDetails.map { detail -> detail.key }.toSet()

        taskSessionDetailRepository.upsertSessionDetails(sessionDetails)
        taskSessionDetailRepository.removeSessionDetails((affectedKeys - projectedKeys).toList())
    }

    private suspend fun rebuildAllSessionDetails(cachedMessages: List<UnifiedMessage>) {
        val taskMailMessages = decodeTaskMailMessages(cachedMessages)
        val existingDetails = taskSessionDetailRepository.getTaskSessionDetails()
        val sessionDetails = sessionProjector.projectSessionDetails(taskMailMessages)
            .mergeExistingProjectionData(existingDetails)
        taskSessionDetailRepository.replaceAllSessionDetails(sessionDetails)
    }

    private fun decodeTaskMailMessages(cachedMessages: List<UnifiedMessage>): List<TaskMailMessage> {
        return cachedMessages.mapNotNull { cachedMessage ->
            taskMailMessageJsonCodec.decode(cachedMessage.messageJson)
        }
    }

    private fun resolveAffectedSessionKeys(
        previousMessages: List<UnifiedMessage>,
        currentMessages: List<UnifiedMessage>,
    ): Set<TaskSessionKey>? {
        val previousMessagesByStorageKey = previousMessages.associateBy(UnifiedMessage::storageKey)
        val currentMessagesByStorageKey = currentMessages.associateBy(UnifiedMessage::storageKey)
        val affectedKeys = linkedSetOf<TaskSessionKey>()

        for (storageKey in previousMessagesByStorageKey.keys + currentMessagesByStorageKey.keys) {
            val changedKeys = resolveChangedMessageSessionKeys(
                previousMessage = previousMessagesByStorageKey[storageKey],
                currentMessage = currentMessagesByStorageKey[storageKey],
            ) ?: return null

            affectedKeys += changedKeys
        }

        return affectedKeys
    }

    private fun resolveSyncRequest(
        cachedMessages: List<UnifiedMessage>,
        syncStateExists: Boolean,
    ): MessageSyncRequest {
        return when {
            cachedMessages.isEmpty() -> {
                MessageSyncRequest(
                    mode = if (syncStateExists) {
                        MessageSyncMode.Recovery
                    } else {
                        MessageSyncMode.Bootstrap
                    },
                    recentMessageLimit = DEFAULT_TASKMAIL_BOOTSTRAP_MESSAGE_LIMIT,
                )
            }

            !syncStateExists -> {
                MessageSyncRequest(
                    mode = MessageSyncMode.Recovery,
                    recentMessageLimit = DEFAULT_TASKMAIL_BOOTSTRAP_MESSAGE_LIMIT,
                )
            }

            else -> MessageSyncRequest()
        }
    }

    private fun resolveSnapshotRebuildMode(
        syncMode: MessageSyncMode,
        hasCachedMessages: Boolean,
        hasStoredSessionDetails: Boolean,
    ): SnapshotRebuildMode {
        return if (
            syncMode != MessageSyncMode.Incremental ||
            (hasCachedMessages && !hasStoredSessionDetails)
        ) {
            SnapshotRebuildMode.Full
        } else {
            SnapshotRebuildMode.Incremental
        }
    }

    private fun shouldSkipIncrementalSync(
        hasCachedMessages: Boolean,
        hasSyncState: Boolean,
    ): Boolean {
        return hasCachedMessages &&
            hasSyncState &&
            clock() - lastSyncAttemptAt < MIN_INCREMENTAL_SYNC_INTERVAL_MS
    }

    private fun decodeTaskSessionKey(cachedMessage: UnifiedMessage): TaskSessionKey? {
        val message = taskMailMessageJsonCodec.decode(cachedMessage.messageJson) ?: return null
        return message.toTaskSessionKey()
    }

    private fun resolveChangedMessageSessionKeys(
        previousMessage: UnifiedMessage?,
        currentMessage: UnifiedMessage?,
    ): Set<TaskSessionKey>? {
        if (previousMessage == currentMessage) return emptySet()

        val previousKey = previousMessage?.let(::decodeTaskSessionKey)
        val currentKey = currentMessage?.let(::decodeTaskSessionKey)
        val hasDecodeFailure = (previousMessage != null && previousKey == null) ||
            (currentMessage != null && currentKey == null)

        return if (hasDecodeFailure) {
            null
        } else {
            buildSet {
                previousKey?.let(::add)
                currentKey?.let(::add)
            }
        }
    }

    private companion object {
        const val MIN_INCREMENTAL_SYNC_INTERVAL_MS = 1_000L
    }
}

private fun UnifiedMessage.storageKey(): String = "$source|$sourceMessageId"

private fun TaskMailMessage.toTaskSessionKey(): TaskSessionKey {
    val subjectSessionId = detection.parsedSubject.sessionIdFromSubject
        ?.takeIf(String::isNotBlank)
    val stateThreadId = detection.stateCapsule?.threadId
        ?.takeIf(String::isNotBlank)

    return TaskSessionKey(
        workspaceId = detection.stateCapsule?.workspaceId
            ?.takeIf(String::isNotBlank),
        sessionId = detection.stateCapsule?.sessionId
            ?.takeIf(String::isNotBlank)
            ?: subjectSessionId,
        threadId = stateThreadId
            ?: subjectSessionId
            ?: threadRootId.toString(),
    )
}

private enum class SnapshotRebuildMode {
    Full,
    Incremental,
}

private fun List<TaskSessionDetail>.mergeExistingProjectionData(
    existingDetails: List<TaskSessionDetail>,
): List<TaskSessionDetail> {
    if (isEmpty() || existingDetails.isEmpty()) return this

    return map { detail ->
        val existingDetail = existingDetails
            .firstOrNull { existingDetail ->
                existingDetail.key == detail.key ||
                    existingDetail.key.isCompatibleWith(detail.key)
            }

        when {
            existingDetail == null -> detail
            existingDetail.prefersVpsProjection() -> existingDetail.mergeMailCompatibilityProjection(detail)
            else -> detail.copy(
                controlPlaneSnapshot = existingDetail.controlPlaneSnapshot.merge(detail.controlPlaneSnapshot),
            )
        }
    }
}
