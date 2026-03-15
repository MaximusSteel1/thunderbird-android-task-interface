package net.thunderbird.feature.taskmail.internal.domain.model

internal data class TaskMessageBody(
    val plainText: String,
    val markdownCandidate: Boolean,
)
