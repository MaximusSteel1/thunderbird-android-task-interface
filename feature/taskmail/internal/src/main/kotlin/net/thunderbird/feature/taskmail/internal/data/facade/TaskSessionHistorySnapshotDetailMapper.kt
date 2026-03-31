package net.thunderbird.feature.taskmail.internal.data.facade

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSessionStatus
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailStatusLabel
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMessageBody
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionDetail
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionHistorySnapshot
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionHistorySnapshotHeader
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionHistorySnapshotLocator
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionHistorySnapshotTimelineItem
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionKey
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionProjectionDataSource
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionProjectionSubscriptionStatus
import net.thunderbird.feature.taskmail.internal.domain.model.TaskTimelineItem
import net.thunderbird.feature.taskmail.internal.domain.model.TaskTimelineDirection
import net.thunderbird.feature.taskmail.internal.domain.model.TaskWorkspaceKey

internal fun TaskSessionHistorySnapshot.toTaskSessionDetail(
    locator: TaskSessionHistorySnapshotLocator,
    existingDetail: TaskSessionDetail,
    subscriptionStatus: TaskSessionProjectionSubscriptionStatus = TaskSessionProjectionSubscriptionStatus.Idle,
    projectionUpdatedAt: Long = System.currentTimeMillis(),
): TaskSessionDetail? {
    val header = sessionHeader ?: return null
    val sessionId = header.sessionId
        ?.takeIf(String::isNotBlank)
        ?: locator.sessionId
    val workspaceId = header.workspaceId
        ?.takeIf(String::isNotBlank)
        ?: locator.workspaceId
        ?: existingDetail.key.workspaceId
        ?: existingDetail.workspace.workspaceId
    val threadId = header.threadId
        ?.takeIf(String::isNotBlank)
        ?: locator.threadId
        ?: existingDetail.key.threadId
        ?: sessionId
    val repoPath = header.repoPath
        ?.takeIf(String::isNotBlank)
        ?: existingDetail.repoPath
            .takeIf(String::isNotBlank)
        ?: locator.repoPath
            ?.takeIf(String::isNotBlank)
        ?: return null
    val workdir = header.workdir
        ?.takeIf(String::isNotBlank)
        ?: existingDetail.workdir
        ?: locator.workdir
            ?.takeIf(String::isNotBlank)
    val pendingQuestions = header.pendingQuestions
        .takeIf { it.isNotEmpty() }
        ?: existingDetail.pendingQuestions
    val snapshotTimeline = header.timelineItems.map(TaskSessionHistorySnapshotTimelineItem::toTaskTimelineItem)
    val mergedTimeline = mergeProjectionTimeline(
        existingTimeline = existingDetail.timeline,
        snapshotTimeline = snapshotTimeline,
    )
    val existingSyncState = existingDetail.projectionSyncState

    return TaskSessionDetail(
        key = TaskSessionKey(
            workspaceId = workspaceId,
            sessionId = sessionId,
            threadId = threadId,
        ),
        workspace = TaskWorkspaceKey(
            workspaceId = workspaceId,
            repoPath = repoPath,
            workdir = workdir,
        ),
        sessionName = existingDetail.sessionName
            .takeIf(String::isNotBlank)
            ?: header.sessionName
                ?.takeIf(String::isNotBlank)
            ?: sessionId,
        backend = header.backend ?: existingDetail.backend,
        status = header.status ?: existingDetail.status,
        lifecycle = header.lifecycle ?: existingDetail.lifecycle,
        repoPath = repoPath,
        workdir = workdir,
        lastSummary = header.lastSummary ?: existingDetail.lastSummary,
        pausedFromStatus = header.pausedFromStatus ?: existingDetail.pausedFromStatus,
        lastActiveAt = header.lastActiveAt ?: existingDetail.lastActiveAt,
        lastProgressAt = header.lastProgressAt ?: existingDetail.lastProgressAt,
        liveProcess = header.liveProcess,
        question = pendingQuestions.lastOrNull(),
        pendingQuestions = pendingQuestions,
        replyContext = existingDetail.replyContext,
        timeline = mergedTimeline,
        controlPlaneSnapshot = existingDetail.controlPlaneSnapshot,
        pendingSubmissions = existingDetail.pendingSubmissions,
        projectionSyncState = existingSyncState.copy(
            dataSource = when (existingSyncState.dataSource) {
                TaskSessionProjectionDataSource.MixedRepair -> TaskSessionProjectionDataSource.MixedRepair
                else -> TaskSessionProjectionDataSource.VpsNative
            },
            lastProjectionUpdatedAt = projectionUpdatedAt,
            subscriptionStatus = subscriptionStatus,
        ),
    )
}

private fun mergeProjectionTimeline(
    existingTimeline: List<TaskTimelineItem>,
    snapshotTimeline: List<TaskTimelineItem>,
): List<TaskTimelineItem> {
    if (snapshotTimeline.isEmpty()) return existingTimeline

    return (existingTimeline + snapshotTimeline)
        .distinctBy { item ->
            item.businessEventKeys.firstOrNull()
                ?.takeIf(String::isNotBlank)
                ?: item.id
        }
        .sortedWith(compareBy(TaskTimelineItem::timestamp, TaskTimelineItem::id))
}

private fun TaskSessionHistorySnapshotTimelineItem.toTaskTimelineItem(): TaskTimelineItem {
    val plainText = text
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?: fallbackPlainText()

    return TaskTimelineItem(
        id = "snapshot:$itemId",
        timestamp = createdAt.toTimelineTimestamp(),
        direction = TaskTimelineDirection.System,
        statusLabel = toTimelineStatusLabel(),
        summary = plainText.takeIf(String::isNotBlank),
        body = TaskMessageBody(plainTextFallback = plainText),
        businessEventKeys = listOfNotNull(businessEventKey.takeIf(String::isNotBlank)),
    )
}

private fun TaskSessionHistorySnapshotTimelineItem.toTimelineStatusLabel(): TaskMailStatusLabel? {
    return when (itemType.lowercase()) {
        "question_prompt" -> TaskMailStatusLabel.Question
        "paused_hint" -> TaskMailStatusLabel.Paused
        "terminal_summary",
        "status_transition",
        -> TaskMailSessionStatus.fromWireValue(status)?.toTimelineStatusLabel()

        else -> null
    }
}

private fun TaskSessionHistorySnapshotTimelineItem.fallbackPlainText(): String {
    return when (itemType.lowercase()) {
        "question_prompt" -> "Question pending."
        "paused_hint" -> "Session is paused."
        "terminal_summary",
        "status_transition",
        -> status
            ?.let(TaskMailSessionStatus::fromWireValue)
            ?.let { taskStatus -> "Status: ${taskStatus.name}" }
            .orEmpty()

        else -> ""
    }
}

private fun TaskMailSessionStatus.toTimelineStatusLabel(): TaskMailStatusLabel? {
    return when (this) {
        TaskMailSessionStatus.Queued -> TaskMailStatusLabel.Accepted
        TaskMailSessionStatus.Running -> TaskMailStatusLabel.Running
        TaskMailSessionStatus.WaitingUser -> TaskMailStatusLabel.Question
        TaskMailSessionStatus.Paused -> TaskMailStatusLabel.Paused
        TaskMailSessionStatus.Done -> TaskMailStatusLabel.Done
        TaskMailSessionStatus.Failed -> TaskMailStatusLabel.Failed
        TaskMailSessionStatus.Killed -> TaskMailStatusLabel.Killed
        TaskMailSessionStatus.Unknown -> TaskMailStatusLabel.Status
    }
}

private fun String.toTimelineTimestamp(): Long {
    return runCatching {
        OffsetDateTime.parse(this).toInstant().toEpochMilli()
    }.recoverCatching {
        LocalDateTime.parse(this, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }.getOrDefault(0L)
}
