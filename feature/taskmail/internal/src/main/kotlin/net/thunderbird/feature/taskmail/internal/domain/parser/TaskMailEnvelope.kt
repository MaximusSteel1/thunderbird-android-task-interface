package net.thunderbird.feature.taskmail.internal.domain.parser

internal data class TaskMailEnvelope(
    val messageId: String,
    val subject: String,
    val fromAddress: String,
    val timestamp: Long,
    val inReplyTo: String? = null,
    val references: List<String> = emptyList(),
    val plainTextBody: String,
)
