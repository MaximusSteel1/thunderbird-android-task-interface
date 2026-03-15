package net.thunderbird.feature.taskmail.internal.domain.model

internal data class TaskWorkspaceSummary(
    val key: TaskWorkspaceKey,
    val title: String,
    val subtitle: String? = null,
    val backendSet: Set<TaskMailBackend>,
    val activeSessionId: String? = null,
    val sessionCount: Int,
    val sessions: List<TaskSessionSummary>,
)
