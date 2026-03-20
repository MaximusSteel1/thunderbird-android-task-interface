package net.thunderbird.feature.taskmail.internal.domain.model

internal enum class TaskMailSessionLifecycle {
    Active,
    Ended,
    Unknown,
    ;

    companion object {
        fun fromWireValue(value: String?): TaskMailSessionLifecycle? {
            return when (value?.trim()?.lowercase()) {
                "active" -> Active
                "ended" -> Ended
                "unknown" -> Unknown
                null, "" -> null
                else -> Unknown
            }
        }
    }
}
