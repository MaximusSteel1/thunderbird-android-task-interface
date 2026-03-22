package net.thunderbird.feature.taskmail.internal.domain.model

import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailDirectSessionActionTarget
import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailDirectSessionActionType

internal data class TaskMailSessionActionSendRecord(
    val recordedAt: Long,
    val actionType: TaskMailDirectSessionActionType,
    val target: TaskMailDirectSessionActionTarget,
    val evidence: TaskMailDirectSendEvidence,
)
