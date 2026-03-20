@file:Suppress("TooManyFunctions")

package net.thunderbird.feature.taskmail.internal.data

import java.io.File
import kotlin.math.abs
import net.thunderbird.feature.taskmail.internal.domain.model.TaskBodyRenderMode
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailBackend
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSessionLifecycle
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSessionStatus
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailStatusLabel
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMessageAttachment
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

internal class DefaultTaskMailRepository(
    private val messageSource: TaskMailMessageSource,
    private val sessionProjector: TaskMailSessionProjector = TaskMailSessionProjector(),
) : TaskMailRepository {

    override suspend fun getTaskWorkspaceSummaries(): List<TaskWorkspaceSummary> {
        return sessionProjector.projectWorkspaceSummaries(messageSource.getMessages())
    }

    override suspend fun getTaskSessionDetail(key: TaskSessionKey): TaskSessionDetail? {
        return sessionProjector.projectSessionDetail(
            messages = messageSource.getMessages(),
            key = key,
        )
    }
}

@Suppress("TooManyFunctions")
internal class TaskMailSessionProjector(
    private val bodyExtractor: LegacyTaskMailBodyExtractor = LegacyTaskMailBodyExtractor(),
    private val richTextProjector: TaskMailRichTextProjector = TaskMailRichTextProjector(),
) {
    fun projectWorkspaceSummaries(messages: List<TaskMailMessage>): List<TaskWorkspaceSummary> {
        return buildSessionRecords(messages)
            .groupBy(SessionRecord::workspace)
            .values
            .map(::toWorkspaceSummary)
            .sortedByDescending { workspace ->
                workspace.sessions.maxOfOrNull(TaskSessionSummary::lastUpdatedAt) ?: Long.MIN_VALUE
            }
    }

    fun projectSessionDetail(
        messages: List<TaskMailMessage>,
        key: TaskSessionKey,
    ): TaskSessionDetail? {
        return buildSessionRecords(messages).firstOrNull { it.sessionKey == key }?.toDetail()
    }

    fun projectSessionDetails(messages: List<TaskMailMessage>): List<TaskSessionDetail> {
        return buildSessionRecords(messages).map { sessionRecord -> sessionRecord.toDetail() }
    }

    fun projectSessionDetails(
        messages: List<TaskMailMessage>,
        keys: Set<TaskSessionKey>,
    ): List<TaskSessionDetail> {
        if (keys.isEmpty()) return emptyList()

        return buildSessionRecords(messages)
            .filter { sessionRecord -> sessionRecord.sessionKey in keys }
            .map { sessionRecord -> sessionRecord.toDetail() }
    }

    private fun buildSessionRecords(messages: List<TaskMailMessage>): List<SessionRecord> {
        val physicalThreads = preferredMailboxMessages(messages)
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
        val sortedMessages = messages.sortedWith(TASK_MAIL_MESSAGE_COMPARATOR)
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
        val sortedMessages = dedupeMessages(
            threadRecords.flatMap(LogicalThreadRecord::messages),
        )
        val latestState = sortedMessages.lastMappedNotNull { it.detection.stateCapsule }
        val representative = resolveRepresentative(
            sortedMessages = sortedMessages,
            latestState = latestState,
        ) ?: return null
        val timeline = buildTimeline(
            messages = sortedMessages,
            bodyExtractor = bodyExtractor,
            richTextProjector = richTextProjector,
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
            lifecycle = metadata.lifecycle,
            repoPath = metadata.repoPath,
            workdir = metadata.workdir,
            lastSummary = metadata.lastSummary,
            pausedFromStatus = metadata.pausedFromStatus,
            lastActiveAt = metadata.lastActiveAt,
            lastProgressAt = metadata.lastProgressAt,
            pendingQuestions = resolvePendingQuestions(sortedMessages),
            replyContext = buildReplyContext(sortedMessages),
            timeline = timeline,
            lastUpdatedAt = timeline.lastOrNull()?.timestamp ?: 0L,
        )
    }

    private fun toWorkspaceSummary(records: List<SessionRecord>): TaskWorkspaceSummary {
        val primaryRecord = records.maxByOrNull(SessionRecord::lastUpdatedAt) ?: records.first()
        val sessions = records
            .sortedByDescending(SessionRecord::lastUpdatedAt)
            .map { record ->
                TaskSessionSummary(
                    key = record.sessionKey,
                    sessionName = record.sessionName,
                    status = record.status,
                    lifecycle = record.lifecycle,
                    backend = record.backend,
                    lastSummary = record.lastSummary,
                    lastActiveAt = record.lastActiveAt,
                    lastProgressAt = record.lastProgressAt,
                    lastUpdatedAt = record.lastUpdatedAt,
                    pendingQuestion = record.pendingQuestions.isNotEmpty(),
                )
            }

        val workspace = primaryRecord.workspace

        return TaskWorkspaceSummary(
            key = workspace,
            title = workspace.workspaceId
                ?: workspace.repoPath.takeIf { it.isNotBlank() }?.let(::deriveWorkspaceTitle)
                ?: "Task workspace",
            subtitle = primaryRecord.workdir,
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
            lifecycle = lifecycle,
            repoPath = repoPath,
            workdir = workdir,
            lastSummary = lastSummary,
            pausedFromStatus = pausedFromStatus,
            lastActiveAt = lastActiveAt,
            lastProgressAt = lastProgressAt,
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
        val lifecycle: TaskMailSessionLifecycle?,
        val repoPath: String,
        val workdir: String?,
        val lastSummary: String?,
        val pausedFromStatus: TaskMailSessionStatus?,
        val lastActiveAt: String?,
        val lastProgressAt: String?,
        val pendingQuestions: List<TaskQuestionCapsule>,
        val replyContext: TaskSessionReplyContext?,
        val timeline: List<TaskTimelineItem>,
        val lastUpdatedAt: Long,
    )
}

private data class SessionRecordMetadata(
    val sessionKey: TaskSessionKey,
    val workspace: TaskWorkspaceKey,
    val sessionName: String,
    val backend: TaskMailBackend,
    val status: TaskMailSessionStatus,
    val lifecycle: TaskMailSessionLifecycle?,
    val repoPath: String,
    val workdir: String?,
    val lastSummary: String?,
    val pausedFromStatus: TaskMailSessionStatus?,
    val lastActiveAt: String?,
    val lastProgressAt: String?,
)

private fun deriveWorkspaceTitle(repoPath: String): String {
    return File(repoPath).name.takeIf { it.isNotBlank() } ?: repoPath
}

private fun resolvePendingQuestions(messages: List<TaskMailMessage>): List<TaskQuestionCapsule> {
    return messages.lastOrNull { it.detection.isSystemMessage }
        ?.detection
        ?.effectiveQuestionCapsules()
        .orEmpty()
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
    richTextProjector: TaskMailRichTextProjector,
): List<TaskTimelineItem> {
    return messages.map { message ->
        val timelineAttachments = message.attachments
            .filter(TaskMessageAttachment::shouldDisplayInTaskTimeline)
            .deduplicateForTaskTimeline()
        val plainText = extractTimelinePlainText(
            message = message,
            bodyExtractor = bodyExtractor,
        )
        val richDocument = message.htmlBody?.let { htmlBody ->
            richTextProjector.project(
                html = htmlBody,
                attachments = timelineAttachments,
            )
        }
        val displaySummary = sanitizeDisplaySummary(message.detection.stateCapsule?.lastSummary)
            ?: plainText.takeIf { it.isNotBlank() }

        TaskTimelineItem(
            id = "${message.accountUuid}:${message.folderId}:${message.messageServerId}",
            timestamp = message.timestamp,
            direction = message.toTimelineDirection(),
            statusLabel = message.timelineStatusLabel(),
            summary = displaySummary,
            body = TaskMessageBody(
                plainTextFallback = plainText,
                renderMode = if (richDocument != null) {
                    TaskBodyRenderMode.RichText
                } else {
                    TaskBodyRenderMode.PlainTextOnly
                },
                richDocument = richDocument,
                sourceHtml = message.htmlBody,
            ),
            attachments = timelineAttachments,
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
    val workspaceId = latestState?.workspaceId?.trim()?.takeIf { it.isNotBlank() }
    val repoPath = latestState?.repoPath.orEmpty()
    val normalizedRepoPath = normalizeWorkspacePath(repoPath)
    val workdir = latestState?.workdir?.trim()?.takeIf { it.isNotBlank() }
    val normalizedWorkdir = normalizeWorkspaceWorkdir(workdir)
    val displayLastSummary = sanitizeDisplaySummary(latestState?.lastSummary)
        ?: timeline.lastMappedNotNull(TaskTimelineItem::summary)
    val workspace = buildWorkspaceKey(
        workspaceId = workspaceId,
        repoPath = normalizedRepoPath,
        representative = representative,
        workdir = normalizedWorkdir,
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
            ?: sortedMessages.lastMappedNotNull { it.timelineStatusLabel()?.toSessionStatus() }
            ?: TaskMailSessionStatus.Unknown,
        lifecycle = latestState?.lifecycle,
        repoPath = repoPath.ifBlank { representative },
        workdir = workdir,
        pausedFromStatus = latestState?.pausedFromStatus,
        lastSummary = displayLastSummary,
        lastActiveAt = latestState?.lastActiveAt?.trim()?.takeIf(String::isNotBlank),
        lastProgressAt = latestState?.lastProgressAt?.trim()?.takeIf(String::isNotBlank),
    )
}

private fun buildWorkspaceKey(
    workspaceId: String?,
    repoPath: String?,
    representative: String,
    workdir: String?,
): TaskWorkspaceKey {
    return when {
        repoPath != null -> TaskWorkspaceKey(
            workspaceId = null,
            repoPath = repoPath,
            workdir = workdir,
        )

        workspaceId != null -> TaskWorkspaceKey(
            workspaceId = workspaceId,
            repoPath = workspaceId,
            workdir = null,
        )

        else -> TaskWorkspaceKey(
            workspaceId = null,
            repoPath = representative,
            workdir = null,
        )
    }
}

private fun normalizeWorkspacePath(path: String?): String? {
    val normalized = path
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.replace('\\', '/')
        ?.trimEnd('/')

    return normalized?.takeIf { it.isNotBlank() }
}

private fun normalizeWorkspaceWorkdir(workdir: String?): String? {
    return normalizeWorkspacePath(workdir)
        ?.takeUnless { it == "." }
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

private fun TaskMailMessage.timelineStatusLabel(): TaskMailStatusLabel? {
    return detection.parsedSubject.statusLabel?.takeIf { detection.isSystemMessage }
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
        TaskMailStatusLabel.Paused -> TaskMailSessionStatus.Paused
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

private fun preferredMailboxMessages(messages: List<TaskMailMessage>): List<TaskMailMessage> {
    val messagesByAccount = messages.groupBy(TaskMailMessage::accountUuid)
    val botLikeAccounts = messagesByAccount
        .takeIf { it.size > 1 }
        ?.filterValues(::looksLikeBotMailboxAccount)
        ?.keys
        .orEmpty()
    val preferredMessages = messages.takeUnless { botLikeAccounts.isNotEmpty() }
        ?: messages.filterNot { it.accountUuid in botLikeAccounts }

    // Preserve the legacy single-account fallback if every configured TaskMail account looks service-side.
    return if (preferredMessages.isNotEmpty()) preferredMessages else messages
}

private fun looksLikeBotMailboxAccount(messages: List<TaskMailMessage>): Boolean {
    return messages.any(TaskMailMessage::looksLikeBotMailboxTraffic)
}

private fun TaskMailMessage.looksLikeBotMailboxTraffic(): Boolean {
    return when {
        detection.isSystemMessage && isFromCurrentUser -> true
        !detection.isSystemMessage && !detection.parsedSubject.isReplyLike && !isFromCurrentUser -> true
        else -> false
    }
}

private fun dedupeMessages(messages: List<TaskMailMessage>): List<TaskMailMessage> {
    val deduped = mutableListOf<TaskMailMessage>()

    messages.sortedWith(TASK_MAIL_MESSAGE_COMPARATOR).forEach { candidate ->
        val existingIndex = deduped.indexOfFirst { existing ->
            existing.isEquivalentTimelineMessage(candidate)
        }

        if (existingIndex >= 0) {
            deduped[existingIndex] = preferredDuplicateMessage(
                first = deduped[existingIndex],
                second = candidate,
            )
        } else {
            deduped += candidate
        }
    }

    return deduped.sortedWith(TASK_MAIL_MESSAGE_COMPARATOR)
}

private fun TaskMailMessage.isEquivalentTimelineMessage(other: TaskMailMessage): Boolean {
    val normalizedBody = rawBodyText.normalizeForDuplicateComparison()
    val otherNormalizedBody = other.rawBodyText.normalizeForDuplicateComparison()
    val matchesMessageIdentity = accountUuid == other.accountUuid &&
        isFromCurrentUser == other.isFromCurrentUser &&
        detection.isSystemMessage == other.detection.isSystemMessage
    val sharesInternetMessageId = hasEquivalentInternetMessageId(other)
    val hasComparableBodies = normalizedBody.isNotBlank() && otherNormalizedBody.isNotBlank()
    val matchesBodyContent = normalizedBody == otherNormalizedBody ||
        normalizedBody.startsWith(otherNormalizedBody) ||
        otherNormalizedBody.startsWith(normalizedBody)
    val matchesFallbackDuplicateSignals = hasEquivalentTimestamp(other) &&
        subject.trim() == other.subject.trim() &&
        hasComparableBodies &&
        matchesBodyContent

    return matchesMessageIdentity && (sharesInternetMessageId || matchesFallbackDuplicateSignals)
}

private fun TaskMailMessage.hasEquivalentInternetMessageId(other: TaskMailMessage): Boolean {
    val normalizedInternetMessageId = internetMessageId.normalizeInternetMessageId()

    return normalizedInternetMessageId != null &&
        normalizedInternetMessageId == other.internetMessageId.normalizeInternetMessageId()
}

private fun TaskMailMessage.hasEquivalentTimestamp(other: TaskMailMessage): Boolean {
    val timestampsMatchExactly = timestamp == other.timestamp
    val isWithinDuplicateWindow = abs(timestamp - other.timestamp) <= DUPLICATE_MESSAGE_TIMESTAMP_WINDOW_MS
    val hasRelaxedDuplicateSignal = hasEquivalentAttachmentSignature(other) || hasEquivalentTaskContext(other)

    return timestampsMatchExactly || (isWithinDuplicateWindow && hasRelaxedDuplicateSignal)
}

private fun TaskMailMessage.hasEquivalentAttachmentSignature(other: TaskMailMessage): Boolean {
    return attachments.isNotEmpty() &&
        attachments.toDuplicateComparableAttachments() == other.attachments.toDuplicateComparableAttachments()
}

private fun TaskMailMessage.hasEquivalentTaskContext(other: TaskMailMessage): Boolean {
    val taskId = detection.stateCapsule?.taskId

    return taskId != null && taskId == other.detection.stateCapsule?.taskId
}

private fun List<TaskMessageAttachment>.toDuplicateComparableAttachments(): List<DuplicateComparableAttachment> {
    return map { attachment ->
        DuplicateComparableAttachment(
            displayName = attachment.displayName,
            contentType = attachment.contentType,
            sizeBytes = attachment.sizeBytes,
            isInline = attachment.isInline,
            isImage = attachment.isImage,
            partId = attachment.partId,
        )
    }
}

private fun preferredDuplicateMessage(
    first: TaskMailMessage,
    second: TaskMailMessage,
): TaskMailMessage {
    return listOf(first, second).maxWithOrNull(
        compareBy<TaskMailMessage>(
            { duplicateMessageScore(it) },
            { it.messageServerId },
        ),
    ) ?: first
}

private fun duplicateMessageScore(message: TaskMailMessage): Int {
    return buildList {
        add(if (message.detection.stateCapsule != null) DUPLICATE_MESSAGE_STATE_CAPSULE_SCORE else 0)
        add(message.attachments.size * DUPLICATE_MESSAGE_ATTACHMENT_WEIGHT)
        add(message.rawBodyText.normalizeForDuplicateComparison().length)
    }.sum()
}

private fun String.normalizeForDuplicateComparison(): String {
    return replace("\r\n", "\n")
        .replace("\r", "\n")
        .replace(Regex("\\s+"), " ")
        .trim()
        .removeSuffix("...")
        .removeSuffix("..")
        .removeSuffix("\u2026")
        .trim()
}

private fun String?.normalizeInternetMessageId(): String? {
    return this
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.lowercase()
}

private val TASK_MAIL_MESSAGE_COMPARATOR = compareBy<TaskMailMessage>({ it.timestamp }, { it.messageServerId })

private const val DUPLICATE_MESSAGE_STATE_CAPSULE_SCORE = 1_000_000
private const val DUPLICATE_MESSAGE_ATTACHMENT_WEIGHT = 10_000
private const val DUPLICATE_MESSAGE_TIMESTAMP_WINDOW_MS = 60_000L

private fun sanitizeDisplaySummary(summary: String?): String? {
    val normalizedSummary = summary
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: return null

    val structuredTokenMatches = DISPLAY_SUMMARY_STRUCTURED_TOKENS.count { token ->
        normalizedSummary.contains(token, ignoreCase = true)
    }

    return normalizedSummary.takeUnless {
        normalizedSummary.contains("---TASK-STATE-BEGIN---", ignoreCase = true) ||
            normalizedSummary.contains("---TASK-QUESTION-BEGIN---", ignoreCase = true) ||
            structuredTokenMatches >= DISPLAY_SUMMARY_STRUCTURED_TOKEN_THRESHOLD
    }
}

private val DISPLAY_SUMMARY_STRUCTURED_TOKENS = listOf(
    "Status:",
    "Session ID:",
    "Thread ID:",
    "Task ID:",
    "Backend:",
    "Repo:",
    "Workdir:",
    "thread_id:",
    "workspace_id:",
    "session_id:",
    "session_name:",
    "task_id:",
    "backend:",
    "repo_path:",
    "workdir:",
    "mode:",
    "status:",
    "last_summary:",
)

private const val DISPLAY_SUMMARY_STRUCTURED_TOKEN_THRESHOLD = 3

private data class DuplicateComparableAttachment(
    val displayName: String,
    val contentType: String?,
    val sizeBytes: Long?,
    val isInline: Boolean,
    val isImage: Boolean,
    val partId: Long?,
)
