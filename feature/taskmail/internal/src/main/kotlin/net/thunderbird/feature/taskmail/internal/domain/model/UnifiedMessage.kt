package net.thunderbird.feature.taskmail.internal.domain.model

internal data class UnifiedMessage(
    val source: String,
    val sourceMessageId: String,
    val taskId: String?,
    val createdAt: Long,
    val contentHash: String,
    val parserVersion: Int,
    val payloadJson: String,
    val messageJson: String,
)
