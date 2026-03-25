package net.thunderbird.feature.taskmail.internal.domain.model

internal data class TaskSessionKey(
    val workspaceId: String? = null,
    val sessionId: String? = null,
    val threadId: String? = null,
)

internal fun TaskSessionKey.isCompatibleWith(other: TaskSessionKey): Boolean {
    val sessionIdsMatch = sessionId != null && other.sessionId != null && sessionId == other.sessionId
    val threadIdsMatch = threadId != null && other.threadId != null && threadId == other.threadId
    if (!sessionIdsMatch && !threadIdsMatch) {
        return false
    }

    return workspaceId == null || other.workspaceId == null || workspaceId == other.workspaceId
}

internal fun TaskSessionKey.withWorkspaceIdFallback(workspaceId: String?): TaskSessionKey {
    val normalizedWorkspaceId = workspaceId?.takeIf(String::isNotBlank) ?: return this

    return if (this.workspaceId.isNullOrBlank()) {
        copy(workspaceId = normalizedWorkspaceId)
    } else {
        this
    }
}
