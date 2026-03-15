package net.thunderbird.feature.taskmail.internal.domain.model

internal data class TaskSessionKey(
    val sessionId: String? = null,
    val threadId: String,
)
