package net.thunderbird.feature.taskmail.internal.domain.model

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
internal data class TaskSessionLiveProcess(
    val status: String,
    val updatedAt: String,
    val items: ImmutableList<TaskSessionProcessItem> = persistentListOf(),
)

@Immutable
internal data class TaskSessionProcessItem(
    val itemId: String,
    val kind: TaskSessionProcessItemKind,
    val createdAt: String,
    val updatedAt: String,
    val status: String? = null,
    val text: String,
)

internal enum class TaskSessionProcessItemKind {
    Assistant,
    Tool,
    System,
    Unknown,
    ;

    companion object {
        fun fromWireValue(value: String?): TaskSessionProcessItemKind {
            return when (value?.trim()?.lowercase()) {
                "assistant" -> Assistant
                "tool" -> Tool
                "system" -> System
                null,
                "",
                -> Unknown

                else -> Unknown
            }
        }
    }
}
