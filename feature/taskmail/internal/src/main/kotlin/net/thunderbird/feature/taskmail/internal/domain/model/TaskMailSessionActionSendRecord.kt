package net.thunderbird.feature.taskmail.internal.domain.model

import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailSessionActionTarget
import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailSessionActionType

internal data class TaskMailSessionActionSendRecord(
    val recordedAt: Long,
    val actionType: TaskMailSessionActionType,
    val target: TaskMailSessionActionTarget,
    val evidence: TaskMailDirectSendEvidence,
)
