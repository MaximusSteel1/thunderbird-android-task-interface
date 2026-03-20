package net.thunderbird.feature.taskmail.internal.domain.model

internal data class TaskMailBotMailboxSettings(
    val address: String?,
    val source: Source,
) {
    enum class Source {
        Saved,
        BuildDefault,
        Missing,
    }
}
