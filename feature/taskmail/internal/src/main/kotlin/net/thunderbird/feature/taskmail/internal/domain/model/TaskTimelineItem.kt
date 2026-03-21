package net.thunderbird.feature.taskmail.internal.domain.model

internal data class TaskTimelineItem(
    val id: String,
    val timestamp: Long,
    val direction: TaskTimelineDirection,
    val statusLabel: TaskMailStatusLabel? = null,
    val summary: String? = null,
    val body: TaskMessageBody,
    val attachments: List<TaskMessageAttachment> = emptyList(),
    val businessEventKeys: List<String> = emptyList(),
)
