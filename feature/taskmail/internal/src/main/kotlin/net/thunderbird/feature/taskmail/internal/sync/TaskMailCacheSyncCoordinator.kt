package net.thunderbird.feature.taskmail.internal.sync

import java.security.MessageDigest
import net.thunderbird.feature.taskmail.internal.data.TaskMailMessage
import net.thunderbird.feature.taskmail.internal.data.cache.EmailIngressPayloadJsonCodec
import net.thunderbird.feature.taskmail.internal.data.cache.TaskMailMessageJsonCodec
import net.thunderbird.feature.taskmail.internal.data.ingress.DEFAULT_TASKMAIL_RECENT_MESSAGE_LIMIT
import net.thunderbird.feature.taskmail.internal.data.ingress.EmailIngressMessage
import net.thunderbird.feature.taskmail.internal.data.ingress.MessageIngress
import net.thunderbird.feature.taskmail.internal.data.parser.IncomingMessageParser
import net.thunderbird.feature.taskmail.internal.domain.model.MessageSyncState
import net.thunderbird.feature.taskmail.internal.domain.model.UnifiedMessage
import net.thunderbird.feature.taskmail.internal.domain.repository.MessageSyncStateRepository
import net.thunderbird.feature.taskmail.internal.domain.repository.UnifiedMessageRepository

internal class TaskMailCacheSyncCoordinator(
    private val messageIngress: MessageIngress<EmailIngressMessage>,
    private val messageParser: IncomingMessageParser<EmailIngressMessage, TaskMailMessage>,
    private val unifiedMessageRepository: UnifiedMessageRepository,
    private val messageSyncStateRepository: MessageSyncStateRepository,
    private val payloadJsonCodec: EmailIngressPayloadJsonCodec,
    private val taskMailMessageJsonCodec: TaskMailMessageJsonCodec,
    private val clock: () -> Long = System::currentTimeMillis,
) : MessageSyncCoordinator {
    override suspend fun sync(request: MessageSyncRequest): Result<Unit> = runCatching {
        val currentState = messageSyncStateRepository.getState(
            source = request.source,
            scopeKey = request.scopeKey,
        )
        val ingressMessages = loadIngressMessages(
            request = request,
            currentState = currentState,
        )
        val unifiedMessages = ingressMessages.mapNotNull { message ->
            toUnifiedMessage(
                source = request.source,
                message = message,
            )
        }

        if (unifiedMessages.isNotEmpty()) {
            unifiedMessageRepository.upsertMessages(unifiedMessages)
        }

        messageSyncStateRepository.upsertState(
            MessageSyncState(
                source = request.source,
                scopeKey = request.scopeKey,
                lastCursor = unifiedMessages.lastOrNull()?.toCursor() ?: currentState?.lastCursor,
                lastSyncAt = clock(),
            ),
        )
    }

    private suspend fun loadIngressMessages(
        request: MessageSyncRequest,
        currentState: MessageSyncState?,
    ): List<EmailIngressMessage> {
        return when {
            request.mode == MessageSyncMode.Bootstrap || request.mode == MessageSyncMode.Recovery -> {
                messageIngress.fetchLatest(
                    recentMessageLimit = request.bootstrapRecentMessageLimit(),
                )
            }

            currentState?.lastCursor.isNullOrBlank() -> {
                messageIngress.fetchLatest(
                    recentMessageLimit = request.bootstrapRecentMessageLimit(),
                )
            }

            else -> {
                messageIngress.fetchSince(
                    cursor = currentState.lastCursor.orEmpty(),
                    recentMessageLimit = request.recentMessageLimit,
                )
            }
        }
    }

    private fun toUnifiedMessage(
        source: String,
        message: EmailIngressMessage,
    ): UnifiedMessage? {
        val parsedMessage = messageParser.parse(message)
        val shouldPersist = parsedMessage?.detection?.isTaskMail == true

        return if (shouldPersist) {
            val payloadJson = payloadJsonCodec.encode(message)
            UnifiedMessage(
                source = source,
                sourceMessageId = message.toSourceMessageId(),
                taskId = parsedMessage.resolveTaskId(),
                createdAt = message.timestamp,
                contentHash = payloadJson.sha256(),
                parserVersion = TASKMAIL_EMAIL_PARSER_VERSION,
                payloadJson = payloadJson,
                messageJson = taskMailMessageJsonCodec.encode(parsedMessage),
            )
        } else {
            null
        }
    }

    private fun MessageSyncRequest.bootstrapRecentMessageLimit(): Int {
        return if (recentMessageLimit == DEFAULT_TASKMAIL_RECENT_MESSAGE_LIMIT) {
            DEFAULT_TASKMAIL_BOOTSTRAP_MESSAGE_LIMIT
        } else {
            recentMessageLimit
        }
    }

    private fun UnifiedMessage.toCursor(): String {
        return "$createdAt:$sourceMessageId"
    }

    private fun TaskMailMessage.resolveTaskId(): String? {
        return detection.stateCapsule?.sessionId
            ?.takeIf(String::isNotBlank)
            ?.let { sessionId -> "session:$sessionId" }
            ?: detection.stateCapsule?.threadId
                ?.takeIf(String::isNotBlank)
                ?.let { threadId -> "thread:$threadId" }
            ?: detection.parsedSubject.sessionIdFromSubject
                ?.takeIf(String::isNotBlank)
                ?.let { sessionId -> "session:$sessionId" }
    }

    private fun EmailIngressMessage.toSourceMessageId(): String {
        return "$accountUuid:$folderId:$messageServerId"
    }

    private fun String.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray())
        return digest.joinToString(separator = "") { byte ->
            "%02x".format(byte)
        }
    }

    private companion object {
        const val TASKMAIL_EMAIL_PARSER_VERSION = 1
    }
}
