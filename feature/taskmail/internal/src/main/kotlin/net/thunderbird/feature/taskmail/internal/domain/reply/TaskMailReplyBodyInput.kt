package net.thunderbird.feature.taskmail.internal.domain.reply

internal data class TaskMailReplyBodyInput(
    val mode: TaskMailReplyMode,
    val userText: String = "",
)
