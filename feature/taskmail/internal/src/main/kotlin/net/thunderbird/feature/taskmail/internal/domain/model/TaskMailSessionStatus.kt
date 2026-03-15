package net.thunderbird.feature.taskmail.internal.domain.model

internal enum class TaskMailSessionStatus {
    Queued,
    Running,
    WaitingUser,
    Done,
    Failed,
    Killed,
    Unknown,
    ;

    companion object {
        fun fromWireValue(value: String?): TaskMailSessionStatus? {
            return when (value?.trim()?.lowercase()) {
                "accepted", "queued" -> Queued
                "running" -> Running
                "awaiting_user_input", "question", "waiting_user", "waiting_user_input" -> WaitingUser
                "done" -> Done
                "failed" -> Failed
                "killed" -> Killed
                "status", "unknown" -> Unknown
                null, "" -> null
                else -> Unknown
            }
        }
    }
}
