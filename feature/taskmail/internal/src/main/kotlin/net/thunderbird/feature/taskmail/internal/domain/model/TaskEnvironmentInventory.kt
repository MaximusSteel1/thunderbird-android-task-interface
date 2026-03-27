package net.thunderbird.feature.taskmail.internal.domain.model

internal data class TaskEnvironmentInventorySnapshot(
    val snapshotId: String,
    val generatedAt: String,
    val inventoryState: String,
    val refreshAfterSeconds: Int,
    val pcs: List<TaskEnvironmentPc>,
) {
    companion object {
        fun empty(
            inventoryState: String = "partial",
        ): TaskEnvironmentInventorySnapshot {
            return TaskEnvironmentInventorySnapshot(
                snapshotId = "env_snap_empty",
                generatedAt = "",
                inventoryState = inventoryState,
                refreshAfterSeconds = 15,
                pcs = emptyList(),
            )
        }
    }
}

internal data class TaskEnvironmentPc(
    val pcId: String,
    val displayName: String,
    val status: String,
    val lastSeenAt: String? = null,
    val workspaceInventoryState: String,
    val workspaceCount: Int,
    val pcCapabilities: TaskEnvironmentCapabilities = TaskEnvironmentCapabilities(),
    val routeAdmission: TaskEnvironmentRouteAdmission = TaskEnvironmentRouteAdmission(),
    val workspaces: List<TaskEnvironmentWorkspace> = emptyList(),
)

internal data class TaskEnvironmentWorkspace(
    val workspaceId: String,
    val pcId: String,
    val displayName: String,
    val repoPath: String,
    val workdir: String? = null,
    val presence: String,
    val lastSnapshotAt: String? = null,
    val effectiveExecutionCapabilities: TaskEnvironmentCapabilities = TaskEnvironmentCapabilities(),
    val routeAdmission: TaskEnvironmentRouteAdmission = TaskEnvironmentRouteAdmission(),
)

internal data class TaskEnvironmentCapabilities(
    val supportedBackends: List<String> = emptyList(),
    val profileCatalogs: Map<String, List<String>> = emptyMap(),
    val permissionModes: List<String> = emptyList(),
    val backendTransportModes: Map<String, List<String>> = emptyMap(),
)

internal data class TaskEnvironmentRouteAdmission(
    val allowed: Boolean = false,
    val reasonCode: String? = null,
    val reason: String? = null,
)
