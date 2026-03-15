package net.thunderbird.feature.taskmail.internal.domain.reply

import net.thunderbird.feature.taskmail.internal.domain.model.TaskReplyAttachment
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionReplyContext

internal data class TaskMailReplyRequest(
    val context: TaskSessionReplyContext,
    val body: String,
    val kind: TaskMailReplyKind,
    val attachments: List<TaskReplyAttachment> = emptyList(),
)
