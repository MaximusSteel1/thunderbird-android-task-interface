package net.thunderbird.feature.taskmail.internal.data.direct

import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionControlPlaneSnapshot
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionDetail
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionKey
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionProjectionDataSource
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionProjectionSubscriptionStatus
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionProjectionSyncState
import net.thunderbird.feature.taskmail.internal.domain.model.TaskTimelineItem
import net.thunderbird.feature.taskmail.internal.domain.model.TaskWorkspaceKey
import net.thunderbird.feature.taskmail.internal.domain.model.merge
import net.thunderbird.feature.taskmail.internal.domain.model.prefersVpsProjection

internal fun TaskMailDirectSessionProjection.toTaskSessionDetail(
    existingDetail: TaskSessionDetail? = null,
    projectionUpdatedAt: Long = System.currentTimeMillis(),
): TaskSessionDetail {
    val existingSyncState = existingDetail?.projectionSyncState
    return TaskSessionDetail(
        key = TaskSessionKey(
            workspaceId = canonicalWorkspaceId,
            sessionId = canonicalSessionId,
            threadId = canonicalThreadId,
        ),
        workspace = TaskWorkspaceKey(
            workspaceId = canonicalWorkspaceId,
            repoPath = repoPath,
            workdir = workdir,
        ),
        sessionName = sessionName,
        backend = backend,
        status = headerStatus,
        lifecycle = headerLifecycle,
        repoPath = repoPath,
        workdir = workdir,
        lastSummary = lastSummary,
        pausedFromStatus = pausedFromStatus,
        lastActiveAt = lastActiveAt,
        lastProgressAt = lastProgressAt,
        question = pendingQuestions.lastOrNull(),
        pendingQuestions = pendingQuestions,
        replyContext = existingDetail?.replyContext,
        timeline = provisionalTimeline,
        controlPlaneSnapshot = existingDetail?.controlPlaneSnapshot.merge(controlPlaneSnapshot),
        projectionSyncState = TaskSessionProjectionSyncState(
            dataSource = when (existingSyncState?.dataSource) {
                TaskSessionProjectionDataSource.MixedRepair -> TaskSessionProjectionDataSource.MixedRepair
                else -> TaskSessionProjectionDataSource.VpsNative
            },
            lastSequence = lastSequence,
            lastEventId = controlPlaneSnapshot?.events?.lastOrNull()?.eventId
                ?: existingSyncState?.lastEventId,
            lastResultId = controlPlaneSnapshot?.result?.resultId
                ?: existingSyncState?.lastResultId,
            lastProjectionUpdatedAt = projectionUpdatedAt,
            subscriptionStatus = TaskSessionProjectionSubscriptionStatus.Active,
        ),
    )
}

internal fun TaskSessionDetail.mergeMailCompatibilityProjection(
    mailDetail: TaskSessionDetail,
): TaskSessionDetail {
    if (!prefersVpsProjection()) return mailDetail

    return copy(
        replyContext = replyContext ?: mailDetail.replyContext,
        timeline = mergeProjectionTimeline(
            mailTimeline = mailDetail.timeline,
            vpsTimeline = timeline,
        ),
        controlPlaneSnapshot = controlPlaneSnapshot.merge(mailDetail.controlPlaneSnapshot),
        projectionSyncState = projectionSyncState.copy(
            dataSource = TaskSessionProjectionDataSource.MixedRepair,
        ),
    )
}

private fun mergeProjectionTimeline(
    mailTimeline: List<TaskTimelineItem>,
    vpsTimeline: List<TaskTimelineItem>,
): List<TaskTimelineItem> {
    val visibleVpsTimeline = vpsTimeline
        .takeIf(List<TaskTimelineItem>::isNotEmpty)
        ?.let { items ->
            val mailBusinessEventKeys = mailTimeline
                .asSequence()
                .flatMap { item -> item.businessEventKeys.asSequence() }
                .filter(String::isNotBlank)
                .toSet()

            items.filterNot { directItem ->
                directItem.businessEventKeys.any { businessEventKey -> businessEventKey in mailBusinessEventKeys }
            }
        }
        .orEmpty()

    return if (visibleVpsTimeline.isEmpty()) {
        mailTimeline
    } else {
        (mailTimeline + visibleVpsTimeline)
            .sortedWith(compareBy(TaskTimelineItem::timestamp, TaskTimelineItem::id))
    }
}
