package net.thunderbird.feature.taskmail.internal.domain.sessionaction

import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionControlPlaneSnapshot

internal sealed interface TaskMailDirectSessionActionResult {
    data class Accepted(
        val actionType: TaskMailDirectSessionActionType,
        val requestId: String,
        val receiptId: String,
        val transportMessageId: String? = null,
        val controlPlaneSnapshot: TaskSessionControlPlaneSnapshot? = null,
        val ackStatus: TaskMailSessionActionAckStatus = TaskMailSessionActionAckStatus.Accepted,
        val targetIdentity: TaskMailSessionActionTargetIdentity? = null,
    ) : TaskMailDirectSessionActionResult

    val commandId: String?
        get() = when (this) {
            is Accepted -> receiptId
            is FallbackToMail -> receiptId
            is Rejected -> receiptId
        }

    data class FallbackToMail(
        val detailMessage: String? = null,
        val requestId: String? = null,
        val receiptId: String? = null,
        val transportMessageId: String? = null,
    ) : TaskMailDirectSessionActionResult

    data class Rejected(
        val errorMessage: String,
        val errorCode: String? = null,
        val requestId: String? = null,
        val receiptId: String? = null,
        val transportMessageId: String? = null,
    ) : TaskMailDirectSessionActionResult
}

internal enum class TaskMailSessionActionAckStatus(
    val wireValue: String,
) {
    Accepted("accepted"),
    AcceptedButQueued("accepted_but_queued"),
    Rejected("rejected"),
}

internal data class TaskMailSessionActionTargetIdentity(
    val pcId: String? = null,
    val workspaceId: String,
    val sessionId: String,
    val threadId: String? = null,
)

internal fun TaskMailDirectSessionActionTarget.toTargetIdentity(
    pcId: String? = null,
): TaskMailSessionActionTargetIdentity {
    return TaskMailSessionActionTargetIdentity(
        pcId = pcId,
        workspaceId = requireNotNull(workspaceId),
        sessionId = sessionId,
        threadId = threadId,
    )
}

internal typealias TaskMailSessionActionResult = TaskMailDirectSessionActionResult
