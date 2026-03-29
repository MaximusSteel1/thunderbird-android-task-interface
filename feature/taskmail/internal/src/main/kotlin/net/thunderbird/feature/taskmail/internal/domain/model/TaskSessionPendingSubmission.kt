package net.thunderbird.feature.taskmail.internal.domain.model

import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailSessionActionType
import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailSessionActionAckStatus
import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailSessionActionTargetIdentity

internal data class TaskSessionPendingSubmission(
    val commandId: String,
    val requestId: String,
    val actionType: TaskMailSessionActionType,
    val submittedAt: Long,
    val ackStatus: TaskMailSessionActionAckStatus,
    val targetIdentity: TaskMailSessionActionTargetIdentity,
)

internal fun TaskSessionDetail.withPendingSubmission(
    submission: TaskSessionPendingSubmission,
): TaskSessionDetail {
    return copy(
        pendingSubmissions = pendingSubmissions.upsertPendingSubmission(submission),
    )
}

internal fun TaskSessionDetail.withPendingSubmissionsRemoved(
    commandIds: Set<String>,
): TaskSessionDetail {
    if (commandIds.isEmpty()) return this

    val updatedPendingSubmissions = pendingSubmissions.filterNot { submission ->
        submission.commandId in commandIds
    }
    return if (updatedPendingSubmissions == pendingSubmissions) {
        this
    } else {
        copy(pendingSubmissions = updatedPendingSubmissions)
    }
}

private fun List<TaskSessionPendingSubmission>.upsertPendingSubmission(
    submission: TaskSessionPendingSubmission,
): List<TaskSessionPendingSubmission> {
    val withoutConflicts = filterNot { existing ->
        existing.commandId == submission.commandId ||
            (
                existing.requestId == submission.requestId &&
                    existing.actionType == submission.actionType &&
                    existing.targetIdentity.sessionId == submission.targetIdentity.sessionId
                )
    }

    return (listOf(submission) + withoutConflicts)
        .sortedByDescending(TaskSessionPendingSubmission::submittedAt)
        .take(MAX_PENDING_SUBMISSIONS)
}

private const val MAX_PENDING_SUBMISSIONS = 20
