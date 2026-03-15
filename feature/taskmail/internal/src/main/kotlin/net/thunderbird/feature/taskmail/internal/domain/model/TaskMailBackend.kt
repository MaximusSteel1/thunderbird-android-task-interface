package net.thunderbird.feature.taskmail.internal.domain.model

internal enum class TaskMailBackend(
    val wireValue: String,
    val subjectPrefix: String,
) {
    OpenCode(
        wireValue = "opencode",
        subjectPrefix = "[OC]",
    ),
    Codex(
        wireValue = "codex",
        subjectPrefix = "[CX]",
    ),
    ;

    companion object {
        fun fromSubjectPrefix(value: String): TaskMailBackend? {
            return entries.firstOrNull { it.subjectPrefix.equals(value, ignoreCase = true) }
        }

        fun fromWireValue(value: String?): TaskMailBackend? {
            return entries.firstOrNull { it.wireValue.equals(value, ignoreCase = true) }
        }
    }
}
