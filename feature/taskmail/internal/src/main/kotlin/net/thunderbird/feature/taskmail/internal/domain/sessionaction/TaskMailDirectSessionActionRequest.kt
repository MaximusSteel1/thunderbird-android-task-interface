package net.thunderbird.feature.taskmail.internal.domain.sessionaction

internal sealed interface TaskMailDirectSessionActionRequest {
    val actionType: TaskMailDirectSessionActionType
    val target: TaskMailDirectSessionActionTarget

    data class Reply(
        override val target: TaskMailDirectSessionActionTarget,
        val replyText: String,
    ) : TaskMailDirectSessionActionRequest {
        override val actionType: TaskMailDirectSessionActionType = TaskMailDirectSessionActionType.Reply
    }

    data class Status(
        override val target: TaskMailDirectSessionActionTarget,
    ) : TaskMailDirectSessionActionRequest {
        override val actionType: TaskMailDirectSessionActionType = TaskMailDirectSessionActionType.Status
    }
}

internal data class TaskMailDirectSessionActionTarget(
    val workspaceId: String,
    val sessionId: String,
    val threadId: String? = null,
)

internal enum class TaskMailDirectSessionActionType(
    val wireValue: String,
) {
    Reply("reply"),
    Status("status"),
}
