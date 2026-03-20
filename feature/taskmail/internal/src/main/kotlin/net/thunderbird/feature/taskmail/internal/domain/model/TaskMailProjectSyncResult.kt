package net.thunderbird.feature.taskmail.internal.domain.model

internal data class TaskMailProjectSyncResult(
    val receivedAt: Long,
    val scannedAt: String?,
    val roots: List<TaskMailProjectSyncRoot>,
)

internal data class TaskMailProjectSyncRoot(
    val rootPath: String,
    val isAvailable: Boolean,
    val folderCount: Int?,
    val unavailableReason: String?,
    val projects: List<TaskMailProjectSyncProject>,
)

internal data class TaskMailProjectSyncProject(
    val displayName: String,
    val repoPath: String,
)
