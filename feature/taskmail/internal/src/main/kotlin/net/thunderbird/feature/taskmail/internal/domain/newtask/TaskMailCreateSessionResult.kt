package net.thunderbird.feature.taskmail.internal.domain.newtask

import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailDirectSendEvidence

internal sealed interface TaskMailCreateSessionResult {
    val evidence: TaskMailDirectSendEvidence?

    data class Submitted(
        val commandId: String,
        val submitAck: TaskMailCreateSessionSubmitAck,
        val sessionBinding: TaskMailCreateSessionBinding?,
        override val evidence: TaskMailDirectSendEvidence? = null,
    ) : TaskMailCreateSessionResult

    data class Rejected(
        val commandId: String?,
        val submitAck: TaskMailCreateSessionSubmitAck?,
        val errorMessage: String,
        override val evidence: TaskMailDirectSendEvidence? = null,
    ) : TaskMailCreateSessionResult

    data class Failed(
        val errorMessage: String,
        override val evidence: TaskMailDirectSendEvidence? = null,
    ) : TaskMailCreateSessionResult
}

internal data class TaskMailCreateSessionSubmitAck(
    val ackStatus: TaskMailCreateSessionAckStatus,
    val queuePosition: Int? = null,
    val reason: String? = null,
    val errorCode: String? = null,
)

internal data class TaskMailCreateSessionBinding(
    val sessionId: String,
    val pcId: String,
    val workspaceId: String,
)

internal enum class TaskMailCreateSessionAckStatus(
    val wireValue: String,
) {
    Accepted("accepted"),
    AcceptedButQueued("accepted_but_queued"),
    Rejected("rejected"),
}
