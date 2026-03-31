package net.thunderbird.feature.taskmail.internal.ui.detail

import androidx.compose.runtime.Immutable
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import net.thunderbird.feature.taskmail.internal.domain.model.TaskAttachmentActionTarget
import net.thunderbird.feature.taskmail.internal.domain.model.TaskBodyRenderMode
import net.thunderbird.feature.taskmail.internal.domain.model.TaskRichTextDocument
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionHistorySnapshotAttachment
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionHistorySnapshotRound
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionLiveProcess
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionProcessItem
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionProcessItemKind
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionReplyContext

@Immutable
internal data class TaskSessionDetailUiState(
    val sessionId: String? = null,
    val workspaceId: String? = null,
    val sessionName: String,
    val backend: String,
    val status: String,
    val pageMode: TaskSessionPageMode = TaskSessionPageMode.AwaitingReply,
    val repoPath: String,
    val workdir: String? = null,
    val lastSummary: String? = null,
    val lastActiveAt: String? = null,
    val lastProgressAt: String? = null,
    val processSection: TaskProcessSectionUi? = null,
    val pendingSubmission: TaskPendingSubmissionUi? = null,
    val recentContext: TaskRecentContextUi? = null,
    val resultSummary: TaskResultSummaryUi? = null,
    val resultBody: TaskTimelineItemUi? = null,
    val artifacts: ImmutableList<TaskTimelineAttachmentUi> = persistentListOf(),
    val historyPreview: ImmutableList<TaskHistoryRoundUi> = persistentListOf(),
    val pendingQuestions: ImmutableList<TaskPendingQuestionUi> = persistentListOf(),
    val quickAnswerChoices: ImmutableList<TaskPendingQuestionChoiceUi> = persistentListOf(),
    val requiresStructuredReply: Boolean = false,
    val requiresResumeBeforeReply: Boolean = false,
    val structuredReplyTemplate: String? = null,
    val replyLabel: String = "Reply to this task",
    val replySupportingText: String = "Send a plain-text reply or use a quick TaskMail action.",
    val replyContext: TaskSessionReplyContext? = null,
    val canReply: Boolean = false,
    val canQueryStatus: Boolean = false,
    val replyUnavailableReason: String? = null,
    val timeline: ImmutableList<TaskTimelineItemUi> = persistentListOf(),
) {
    fun canSendReply(
        draftText: String,
        attachmentCount: Int = 0,
    ): Boolean {
        return if (!canReply) {
            false
        } else if (requiresStructuredReply) {
            validateStructuredReply(
                draftText = draftText,
                pendingQuestions = pendingQuestions,
            ) == StructuredReplyValidationResult.Valid
        } else {
            draftText.isNotBlank() || attachmentCount > 0
        }
    }

    fun canUseQuickAnswer(choice: String): Boolean {
        return !requiresStructuredReply && quickAnswerChoices.any { it.value == choice }
    }
}

@Immutable
internal data class TaskRecentContextUi(
    val latestUserMessage: String? = null,
    val latestAssistantMessage: String? = null,
    val waitingForUserText: String? = null,
)

@Immutable
internal data class TaskResultSummaryUi(
    val headline: String,
    val supportingText: String? = null,
    val statusLabel: String,
    val effectiveExecutionSummary: String? = null,
)

@Immutable
internal data class TaskPendingSubmissionUi(
    val message: String,
    val submittedAt: Long,
)

@Immutable
internal data class TaskProcessSectionUi(
    val title: String,
    val supportingText: String? = null,
    val visibleItems: ImmutableList<TaskTimelineItemUi> = persistentListOf(),
    val rawItemCount: Int = 0,
    val previewText: String? = null,
    val emptyText: String = "No assistant output is available yet.",
    val defaultExpanded: Boolean = false,
) {
    val canExpand: Boolean
        get() = visibleItems.isNotEmpty()
}

@Immutable
internal data class TaskHistoryRoundUi(
    val id: String,
    val title: String,
    val summary: String? = null,
    val statusLabel: String? = null,
    val messagePreview: String? = null,
)

@Immutable
internal data class TaskPendingQuestionUi(
    val questionId: String,
    val questionText: String,
    val choices: ImmutableList<TaskPendingQuestionChoiceUi> = persistentListOf(),
    val isRequired: Boolean = true,
)

@Immutable
internal data class TaskPendingQuestionChoiceUi(
    val value: String,
    val label: String = value,
)

@Immutable
internal data class TaskTimelineItemUi(
    val id: String,
    val timestamp: Long,
    val direction: String,
    val statusLabel: String? = null,
    val summary: String? = null,
    val plainText: String,
    val renderMode: TaskBodyRenderMode = TaskBodyRenderMode.PlainTextOnly,
    val richDocument: TaskRichTextDocument? = null,
    val attachments: ImmutableList<TaskTimelineAttachmentUi> = persistentListOf(),
)

@Immutable
internal data class TaskTimelineAttachmentUi(
    val id: String,
    val displayName: String,
    val contentType: String? = null,
    val sizeBytes: Long? = null,
    val isInline: Boolean = false,
    val isImage: Boolean = false,
    val internalUriString: String? = null,
    val actionTarget: TaskAttachmentActionTarget? = null,
    val isActionAvailable: Boolean = actionTarget != null,
)

internal enum class TaskSessionPageMode {
    ActiveRun,
    AwaitingReply,
    Terminal,
}

internal fun ImmutableList<TaskTimelineItemUi>.findLatestResultBodyCandidate(
    summary: String?,
    statusLabel: String? = null,
): TaskTimelineItemUi? {
    val normalizedSummary = summary
        ?.normalizeForResultMatching()
        ?.takeIf(String::isNotEmpty)
    val normalizedStatusLabel = statusLabel
        ?.trim()
        ?.takeIf(String::isNotEmpty)

    return firstOrNull { item ->
        item.isAssistantFacingResultCandidate() &&
            when {
                normalizedSummary != null -> item.matchesResultSummary(normalizedSummary)
                normalizedStatusLabel != null -> item.statusLabel.equals(normalizedStatusLabel, ignoreCase = true)
                else -> true
            }
    }
        ?.takeUnless { item ->
            normalizedSummary != null &&
                item.richDocument == null &&
                item.plainText.normalizeForResultMatching() == normalizedSummary
        }
}

internal fun ImmutableList<TaskSessionHistorySnapshotRound>.findLatestResultBodyCandidate(
    fallbackItem: TaskTimelineItemUi? = null,
    statusLabel: String? = null,
): TaskTimelineItemUi? {
    val latestRound = maxByOrNull(TaskSessionHistorySnapshotRound::roundNumber) ?: return null
    val resultText = latestRound.resultText
        .trim()
        .takeIf(String::isNotBlank)
        ?: return null
    val snapshotAttachments = latestRound.resultAttachments
        .map(TaskSessionHistorySnapshotAttachment::toTimelineAttachmentUi)
        .toImmutableList()

    fallbackItem
        ?.takeIf { it.plainText.trim() == resultText }
        ?.let { fallback ->
            return if (fallback.attachments.isNotEmpty() || snapshotAttachments.isEmpty()) {
                fallback
            } else {
                fallback.copy(attachments = snapshotAttachments)
            }
        }

    return TaskTimelineItemUi(
        id = "history_result:${latestRound.roundId}",
        timestamp = fallbackItem?.timestamp ?: 0L,
        direction = fallbackItem?.direction ?: "System",
        statusLabel = fallbackItem?.statusLabel ?: statusLabel,
        summary = fallbackItem?.summary,
        plainText = resultText,
        attachments = fallbackItem?.attachments?.takeIf { it.isNotEmpty() } ?: snapshotAttachments,
    )
}

private fun TaskTimelineItemUi.isAssistantFacingResultCandidate(): Boolean {
    return (direction.equals("Incoming", ignoreCase = true) ||
        direction.equals("System", ignoreCase = true)) &&
        (plainText.isNotBlank() || richDocument != null)
}

private fun TaskTimelineItemUi.matchesResultSummary(normalizedSummary: String): Boolean {
    val normalizedItemSummary = summary
        ?.normalizeForResultMatching()
        ?.takeIf(String::isNotEmpty)
    val normalizedBody = plainText
        .normalizeForResultMatching()
        .takeIf(String::isNotEmpty)

    return normalizedItemSummary == normalizedSummary ||
        normalizedBody == normalizedSummary ||
        normalizedBody?.contains(normalizedSummary) == true
}

private fun String.normalizeForResultMatching(): String {
    return trim()
        .replace(Regex("\\s+"), " ")
        .lowercase()
}

internal fun TaskSessionLiveProcess.toProcessSectionUi(
    title: String,
    supportingText: String? = null,
    defaultExpanded: Boolean = false,
): TaskProcessSectionUi? {
    return items.toProcessSectionUi(
        title = title,
        supportingText = supportingText,
        defaultExpanded = defaultExpanded,
    )
}

internal fun Iterable<TaskSessionProcessItem>.toProcessSectionUi(
    title: String,
    supportingText: String? = null,
    defaultExpanded: Boolean = false,
): TaskProcessSectionUi? {
    val normalizedItems = mapNotNull { item ->
        item.text.trim()
            .takeIf(String::isNotBlank)
            ?.let { normalizedText -> item.copy(text = normalizedText) }
    }
    if (normalizedItems.isEmpty()) return null

    val assistantItems = normalizedItems
        .filter { item -> item.kind == TaskSessionProcessItemKind.Assistant }
        .map(TaskSessionProcessItem::toUiTimelineItem)
        .toImmutableList()

    return TaskProcessSectionUi(
        title = title,
        supportingText = supportingText,
        visibleItems = assistantItems,
        rawItemCount = normalizedItems.size,
        previewText = normalizedItems.lastAssistantPreviewText(),
        defaultExpanded = defaultExpanded,
    )
}

internal fun Iterable<TaskTimelineItemUi>.toLocalProcessSectionUi(
    title: String,
    supportingText: String? = null,
    defaultExpanded: Boolean = false,
): TaskProcessSectionUi? {
    val normalizedItems = mapNotNull { item ->
        item.plainText.trim()
            .takeIf(String::isNotBlank)
            ?.let { normalizedText -> item.copy(plainText = normalizedText) }
    }
    if (normalizedItems.isEmpty()) return null

    val assistantItems = normalizedItems
        .filter(TaskTimelineItemUi::isAssistantProcessFallbackItem)
        .map { item ->
            item.copy(
                direction = "Assistant",
                summary = null,
            )
        }
        .toImmutableList()

    return TaskProcessSectionUi(
        title = title,
        supportingText = supportingText,
        visibleItems = assistantItems,
        rawItemCount = normalizedItems.size,
        previewText = assistantItems.lastOrNull()?.plainText?.toProcessPreviewText(),
        defaultExpanded = defaultExpanded,
    )
}

private fun TaskSessionProcessItem.toUiTimelineItem(): TaskTimelineItemUi {
    val timestamp = updatedAt.toProcessEpochMillis().takeIf { it > 0L } ?: createdAt.toProcessEpochMillis()
    return TaskTimelineItemUi(
        id = itemId,
        timestamp = timestamp,
        direction = "Assistant",
        statusLabel = status.toProcessStatusLabel(),
        summary = null,
        plainText = text,
    )
}

private fun Iterable<TaskSessionProcessItem>.lastAssistantPreviewText(): String? {
    return lastOrNull { item -> item.kind == TaskSessionProcessItemKind.Assistant }
        ?.text
        ?.toProcessPreviewText()
}

private fun TaskTimelineItemUi.isAssistantProcessFallbackItem(): Boolean {
    return direction.equals("Incoming", ignoreCase = true) ||
        direction.equals("Assistant", ignoreCase = true)
}

private fun String?.toProcessStatusLabel(): String? {
    return this
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?.replaceFirstChar { char -> char.titlecase() }
}

private fun String.toProcessPreviewText(): String {
    return replace(Regex("\\s+"), " ")
        .trim()
        .take(120)
}

private fun String.toProcessEpochMillis(): Long {
    return runCatching {
        OffsetDateTime.parse(this).toInstant().toEpochMilli()
    }.recoverCatching {
        LocalDateTime.parse(this, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }.getOrDefault(0L)
}
