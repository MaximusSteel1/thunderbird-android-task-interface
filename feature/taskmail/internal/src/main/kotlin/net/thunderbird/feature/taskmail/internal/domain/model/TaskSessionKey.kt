package net.thunderbird.feature.taskmail.internal.domain.model

internal data class TaskSessionKey(
    val workspaceId: String? = null,
    val sessionId: String? = null,
    val threadId: String,
)

internal fun TaskSessionKey.isCompatibleWith(other: TaskSessionKey): Boolean {
    if (sessionId != other.sessionId || threadId != other.threadId) {
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
