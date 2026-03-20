package net.thunderbird.feature.taskmail.internal.domain.model

internal enum class TaskMailStatusLabel(
    val subjectToken: String,
) {
    Accepted("[ACCEPTED]"),
    Running("[RUNNING]"),
    Done("[DONE]"),
    Failed("[FAILED]"),
    Status("[STATUS]"),
    Killed("[KILLED]"),
    Question("[QUESTION]"),
    Paused("[PAUSED]"),
    ;

    companion object {
        fun fromSubjectToken(value: String): TaskMailStatusLabel? {
            return entries.firstOrNull { it.subjectToken.equals(value, ignoreCase = true) }
        }
    }
}
