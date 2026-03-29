package net.thunderbird.feature.taskmail.internal.domain.sessionaction

import net.thunderbird.feature.taskmail.internal.domain.model.TaskReplyAttachment

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

    data class Answers(
        override val target: TaskMailDirectSessionActionTarget,
        val questionAnswers: List<TaskMailSessionQuestionAnswer>,
    ) : TaskMailDirectSessionActionRequest {
        override val actionType: TaskMailDirectSessionActionType = TaskMailDirectSessionActionType.Answers
    }

    data class Pause(
        override val target: TaskMailDirectSessionActionTarget,
    ) : TaskMailDirectSessionActionRequest {
        override val actionType: TaskMailDirectSessionActionType = TaskMailDirectSessionActionType.Pause
    }

    data class Resume(
        override val target: TaskMailDirectSessionActionTarget,
    ) : TaskMailDirectSessionActionRequest {
        override val actionType: TaskMailDirectSessionActionType = TaskMailDirectSessionActionType.Resume
    }

    data class Kill(
        override val target: TaskMailDirectSessionActionTarget,
    ) : TaskMailDirectSessionActionRequest {
        override val actionType: TaskMailDirectSessionActionType = TaskMailDirectSessionActionType.Kill
    }

    data class End(
        override val target: TaskMailDirectSessionActionTarget,
    ) : TaskMailDirectSessionActionRequest {
        override val actionType: TaskMailDirectSessionActionType = TaskMailDirectSessionActionType.End
    }

    data class AttachmentContinuation(
        override val target: TaskMailDirectSessionActionTarget,
        val replyText: String = "",
        val attachments: List<TaskReplyAttachment>,
    ) : TaskMailDirectSessionActionRequest {
        override val actionType: TaskMailDirectSessionActionType =
            TaskMailDirectSessionActionType.AttachmentContinuation
    }
}

internal data class TaskMailDirectSessionActionTarget(
    val workspaceId: String? = null,
    val sessionId: String,
    val threadId: String? = null,
)

internal data class TaskMailSessionQuestionAnswer(
    val questionId: String,
    val value: String,
)

internal enum class TaskMailDirectSessionActionType(
    val wireValue: String,
) {
    Reply("reply"),
    Status("status"),
    Answers("answers"),
    Pause("pause"),
    Resume("resume"),
    Kill("kill"),
    End("end"),
    AttachmentContinuation("attachment_continuation"),
}

internal typealias TaskMailSessionActionRequest = TaskMailDirectSessionActionRequest
internal typealias TaskMailSessionActionTarget = TaskMailDirectSessionActionTarget
internal typealias TaskMailSessionActionType = TaskMailDirectSessionActionType
