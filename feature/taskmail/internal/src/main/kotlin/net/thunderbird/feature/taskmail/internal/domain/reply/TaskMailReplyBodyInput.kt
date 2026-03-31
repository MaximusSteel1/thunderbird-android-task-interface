package net.thunderbird.feature.taskmail.internal.domain.reply

import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskPermission

internal data class TaskMailReplyBodyInput(
    val mode: TaskMailReplyMode,
    val userText: String = "",
    val permission: TaskMailNewTaskPermission? = null,
)
