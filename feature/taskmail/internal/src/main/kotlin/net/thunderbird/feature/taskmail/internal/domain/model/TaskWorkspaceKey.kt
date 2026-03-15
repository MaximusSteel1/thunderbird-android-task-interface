package net.thunderbird.feature.taskmail.internal.domain.model

internal data class TaskWorkspaceKey(
    val workspaceId: String? = null,
    val repoPath: String,
    val workdir: String? = null,
)
