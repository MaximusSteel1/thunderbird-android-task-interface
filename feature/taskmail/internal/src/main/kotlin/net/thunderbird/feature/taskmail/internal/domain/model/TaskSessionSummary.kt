package net.thunderbird.feature.taskmail.internal.domain.model

internal data class TaskSessionSummary(
    val key: TaskSessionKey,
    val sessionName: String,
    val status: TaskMailSessionStatus,
    val lifecycle: TaskMailSessionLifecycle? = null,
    val backend: TaskMailBackend,
    val lastSummary: String? = null,
    val lastActiveAt: String? = null,
    val lastProgressAt: String? = null,
    val lastUpdatedAt: Long,
    val pendingQuestion: Boolean,
)
