@file:Suppress("TooManyFunctions")

package net.thunderbird.feature.taskmail.internal.data

import java.io.File
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailBackend
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSessionStatus
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailStatusLabel
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMessageBody
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionDetail
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionKey
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionReplyContext
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionSummary
import net.thunderbird.feature.taskmail.internal.domain.model.TaskTimelineDirection
import net.thunderbird.feature.taskmail.internal.domain.model.TaskTimelineItem
import net.thunderbird.feature.taskmail.internal.domain.model.TaskWorkspaceKey
import net.thunderbird.feature.taskmail.internal.domain.model.TaskWorkspaceSummary
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskMailDetection
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskQuestionCapsule
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskStateCapsule
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskMailRepository

@Suppress("TooManyFunctions")
internal class DefaultTaskMailRepository(
    private val messageSource: TaskMailMessageSource,
    private val bodyExtractor: LegacyTaskMailBodyExtractor = LegacyTaskMailBodyExtractor(),
) : TaskMailRepository {

    override suspend fun getTaskWorkspaceSummaries(): List<TaskWorkspaceSummary> {
        return buildSessionRecords()
            .groupBy(SessionRecord::workspace)
            .values
            .map(::toWorkspaceSummary)
            .sortedByDescending { workspace ->
                workspace.sessions.maxOfOrNull(TaskSessionSummary::lastUpdatedAt) ?: Long.MIN_VALUE
            }
    }

    override suspend fun getTaskSessionDetail(key: TaskSessionKey): TaskSessionDetail? {
        return buildSessionRecords().firstOrNull { it.sessionKey == key }?.toDetail()
    }

    private suspend fun buildSessionRecords(): List<SessionRecord> {
        val physicalThreads = messageSource.getMessages()
            .groupBy { message ->
                PhysicalThreadKey(
                    accountUuid = message.accountUuid,
                    threadRootId = message.threadRootId,
                )
            }
            .values

        return physicalThreads
            .mapNotNull(::buildLogicalThreadRecord)
            .groupBy(LogicalThreadRecord::logicalSession)
            .values
            .mapNotNull(::buildSessionRecord)
            .sortedByDescending { it.lastUpdatedAt }
    }

    private fun buildLogicalThreadRecord(messages: List<TaskMailMessage>): LogicalThreadRecord? {
        val sortedMessages = messages.sortedWith(messageComparator)
        val latestState = sortedMessages.lastMappedNotNull { it.detection.stateCapsule }
        val sessionId = latestState?.sessionId
            ?: sortedMessages.lastMappedNotNull { it.detection.parsedSubject.sessionIdFromSubject }
        val threadId = latestState?.threadId?.takeIf { it.isNotBlank() }
        val fallbackThreadRootId = sortedMessages.lastOrNull()?.threadRootId?.toString() ?: return null

        return LogicalThreadRecord(
            logicalSession = LogicalSessionKey(
                identity = sessionId?.let { "session:$it" }
                    ?: threadId?.let { "thread:$it" }
                    ?: "mail:$fallbackThreadRootId",
            ),
            messages = sortedMessages,
        )
    }

    private fun buildSessionRecord(threadRecords: List<LogicalThreadRecord>): SessionRecord? {
        val sortedMessages = threadRecords
            .flatMap(LogicalThreadRecord::messages)
            .sortedWith(messageComparator)
        val latestState = sortedMessages.lastMappedNotNull { it.detection.stateCapsule }
        val representative = resolveRepresentative(
            sortedMessages = sortedMessages,
            latestState = latestState,
        ) ?: return null
        val timeline = buildTimeline(
            messages = sortedMessages,
            bodyExtractor = bodyExtractor,
        )
        val metadata = buildSessionRecordMetadata(
            sortedMessages = sortedMessages,
            latestState = latestState,
            representative = representative,
            timeline = timeline,
        )

        return SessionRecord(
            sessionKey = metadata.sessionKey,
            workspace = metadata.workspace,
            sessionName = metadata.sessionName,
            backend = metadata.backend,
            status = metadata.status,
            repoPath = metadata.repoPath,
            workdir = metadata.workdir,
            lastSummary = metadata.lastSummary,
            pendingQuestions = resolvePendingQuestions(sortedMessages),
            replyContext = buildReplyContext(sortedMessages),
            timeline = timeline,
            lastUpdatedAt = timeline.lastOrNull()?.timestamp ?: 0L,
        )
    }

    private fun toWorkspaceSummary(records: List<SessionRecord>): TaskWorkspaceSummary {
        val sessions = records
            .sortedByDescending(SessionRecord::lastUpdatedAt)
            .map { record ->
                TaskSessionSummary(
                    key = record.sessionKey,
                    sessionName = record.sessionName,
                    status = record.status,
                    backend = record.backend,
                    lastSummary = record.lastSummary,
                    lastUpdatedAt = record.lastUpdatedAt,
                    pendingQuestion = record.pendingQuestions.isNotEmpty(),
                )
            }

        val workspace = records.first().workspace

        return TaskWorkspaceSummary(
            key = workspace,
            title = workspace.workspaceId
                ?: workspace.repoPath.takeIf { it.isNotBlank() }?.let(::deriveWorkspaceTitle)
                ?: "Task workspace",
            subtitle = workspace.workdir,
            backendSet = records.map(SessionRecord::backend).toSet(),
            activeSessionId = sessions.firstOrNull()?.key?.sessionId,
            sessionCount = sessions.size,
            sessions = sessions,
        )
    }

    private fun SessionRecord.toDetail(): TaskSessionDetail {
        return TaskSessionDetail(
            key = sessionKey,
            workspace = workspace,
            sessionName = sessionName,
            backend = backend,
            status = status,
            repoPath = repoPath,
            workdir = workdir,
            lastSummary = lastSummary,
            question = pendingQuestions.lastOrNull(),
            pendingQuestions = pendingQuestions,
            replyContext = replyContext,
            timeline = timeline,
        )
    }

    private fun buildReplyContext(messages: List<TaskMailMessage>): TaskSessionReplyContext? {
        val accountUuid = messages.map(TaskMailMessage::accountUuid).distinct().singleOrNull()
        val anchorMessage = messages.lastOrNull { it.messageServerId.isNotBlank() }

        return if (accountUuid != null && anchorMessage != null) {
            TaskSessionReplyContext(
                accountUuid = accountUuid,
                folderId = anchorMessage.folderId,
                messageServerId = anchorMessage.messageServerId,
                threadRootId = anchorMessage.threadRootId,
                anchorTimestamp = anchorMessage.timestamp,
            )
        } else {
            null
        }
    }

    private data class PhysicalThreadKey(
        val accountUuid: String,
        val threadRootId: Long,
    )

    private data class LogicalSessionKey(
        val identity: String,
    )

    private data class LogicalThreadRecord(
        val logicalSession: LogicalSessionKey,
        val messages: List<TaskMailMessage>,
    )

    private data class SessionRecord(
        val sessionKey: TaskSessionKey,
        val workspace: TaskWorkspaceKey,
        val sessionName: String,
        val backend: TaskMailBackend,
        val status: TaskMailSessionStatus,
        val repoPath: String,
        val workdir: String?,
        val lastSummary: String?,
        val pendingQuestions: List<TaskQuestionCapsule>,
        val replyContext: TaskSessionReplyContext?,
        val timeline: List<TaskTimelineItem>,
        val lastUpdatedAt: Long,
    )

    private companion object {
        val messageComparator = compareBy<TaskMailMessage>({ it.timestamp }, { it.messageServerId })
    }
}

private data class SessionRecordMetadata(
    val sessionKey: TaskSessionKey,
    val workspace: TaskWorkspaceKey,
    val sessionName: String,
    val backend: TaskMailBackend,
    val status: TaskMailSessionStatus,
    val repoPath: String,
    val workdir: String?,
    val lastSummary: String?,
)

private fun deriveWorkspaceTitle(repoPath: String): String {
    return File(repoPath).name.takeIf { it.isNotBlank() } ?: repoPath
}

private fun resolvePendingQuestions(messages: List<TaskMailMessage>): List<TaskQuestionCapsule> {
    return messages.lastMappedNotNull { message ->
        message.detection.effectiveQuestionCapsules().takeIf { it.isNotEmpty() }
    }.orEmpty()
}

private fun resolveRepresentative(
    sortedMessages: List<TaskMailMessage>,
    latestState: TaskStateCapsule?,
): String? {
    return latestState?.threadId?.takeIf { it.isNotBlank() }
        ?: sortedMessages.lastMappedNotNull { it.detection.parsedSubject.sessionIdFromSubject }
        ?: sortedMessages.lastOrNull()?.threadRootId?.toString()
}

private fun buildTimeline(
    messages: List<TaskMailMessage>,
    bodyExtractor: LegacyTaskMailBodyExtractor,
): List<TaskTimelineItem> {
    return messages.map { message ->
        val plainText = extractTimelinePlainText(
            message = message,
            bodyExtractor = bodyExtractor,
        )

        TaskTimelineItem(
            id = "${message.accountUuid}:${message.folderId}:${message.messageServerId}",
            timestamp = message.timestamp,
            direction = message.toTimelineDirection(),
            statusLabel = message.detection.parsedSubject.statusLabel,
            summary = message.detection.stateCapsule?.lastSummary
                ?: plainText.takeIf { it.isNotBlank() },
            body = TaskMessageBody(
                plainText = plainText,
                markdownCandidate = false,
            ),
            attachments = message.attachments,
        )
    }
}

private fun extractTimelinePlainText(
    message: TaskMailMessage,
    bodyExtractor: LegacyTaskMailBodyExtractor,
): String {
    return if (message.detection.isSystemMessage) {
        bodyExtractor.extractSystemMessageText(message.rawBodyText)
    } else {
        bodyExtractor.extractUserMessageText(message.rawBodyText)
    }
}

private fun buildSessionRecordMetadata(
    sortedMessages: List<TaskMailMessage>,
    latestState: TaskStateCapsule?,
    representative: String,
    timeline: List<TaskTimelineItem>,
): SessionRecordMetadata {
    val sessionId = latestState?.sessionId
        ?: sortedMessages.lastMappedNotNull { it.detection.parsedSubject.sessionIdFromSubject }
    val repoPath = latestState?.repoPath.orEmpty()
    val workdir = latestState?.workdir
    val workspace = TaskWorkspaceKey(
        workspaceId = latestState?.workspaceId,
        repoPath = repoPath.ifBlank { representative },
        workdir = workdir,
    )

    return SessionRecordMetadata(
        sessionKey = TaskSessionKey(
            sessionId = sessionId,
            threadId = representative,
        ),
        workspace = workspace,
        sessionName = resolveSessionName(
            sortedMessages = sortedMessages,
            latestState = latestState,
            sessionId = sessionId,
            representative = representative,
        ),
        backend = latestState?.backend
            ?: sortedMessages.lastMappedNotNull { it.detection.parsedSubject.backend }
            ?: TaskMailBackend.Codex,
        status = latestState?.status
            ?: sortedMessages.lastMappedNotNull { it.detection.parsedSubject.statusLabel?.toSessionStatus() }
            ?: TaskMailSessionStatus.Unknown,
        repoPath = repoPath.ifBlank { workspace.repoPath },
        workdir = workdir,
        lastSummary = latestState?.lastSummary
            ?: timeline.lastMappedNotNull(TaskTimelineItem::summary),
    )
}

private fun resolveSessionName(
    sortedMessages: List<TaskMailMessage>,
    latestState: TaskStateCapsule?,
    sessionId: String?,
    representative: String,
): String {
    return latestState?.sessionName
        ?.takeIf { it.isNotBlank() }
        ?: sortedMessages.firstMappedNotNull { message ->
            message.detection.parsedSubject.subjectText.takeIf { it.isNotBlank() }
        }
        ?: sessionId
        ?: representative
}

private fun TaskMailMessage.toTimelineDirection(): TaskTimelineDirection {
    return when {
        detection.isSystemMessage -> TaskTimelineDirection.System
        isFromCurrentUser -> TaskTimelineDirection.Outgoing
        else -> TaskTimelineDirection.Incoming
    }
}

private fun TaskMailDetection.effectiveQuestionCapsules(): List<TaskQuestionCapsule> {
    return if (questionCapsules.isNotEmpty()) {
        questionCapsules
    } else {
        listOfNotNull(questionCapsule)
    }
}

private fun TaskMailStatusLabel.toSessionStatus(): TaskMailSessionStatus {
    return when (this) {
        TaskMailStatusLabel.Accepted -> TaskMailSessionStatus.Queued
        TaskMailStatusLabel.Running -> TaskMailSessionStatus.Running
        TaskMailStatusLabel.Done -> TaskMailSessionStatus.Done
        TaskMailStatusLabel.Failed -> TaskMailSessionStatus.Failed
        TaskMailStatusLabel.Status -> TaskMailSessionStatus.Unknown
        TaskMailStatusLabel.Killed -> TaskMailSessionStatus.Killed
        TaskMailStatusLabel.Question -> TaskMailSessionStatus.WaitingUser
    }
}

private inline fun <T, R : Any> List<T>.lastMappedNotNull(transform: (T) -> R?): R? {
    for (index in indices.reversed()) {
        transform(this[index])?.let { return it }
    }

    return null
}

private inline fun <T, R : Any> List<T>.firstMappedNotNull(transform: (T) -> R?): R? {
    for (item in this) {
        transform(item)?.let { return it }
    }

    return null
}
