package net.thunderbird.feature.taskmail.internal.ui.workspace

internal data class TaskWorkspaceItemUi(
    val title: String,
    val subtitle: String? = null,
    val routeTargetLabel: String? = null,
    val sessionCountLabel: String,
    val sessions: List<TaskSessionItemUi>,
)

internal data class TaskWorkspacePcNodeUi(
    val id: String,
    val title: String,
    val supportingText: String,
    val connectionStatus: String,
    val summaryLabel: String,
    val isInitiallyExpanded: Boolean,
    val workspaces: List<TaskWorkspaceTreeNodeUi>,
)

internal data class TaskWorkspaceTreeNodeUi(
    val id: String,
    val title: String,
    val supportingText: String,
    val summaryLabel: String,
    val presenceLabel: String? = null,
    val isMissingBinding: Boolean,
    val isInitiallyExpanded: Boolean,
    val sessions: List<TaskSessionItemUi>,
)

internal data class TaskSessionItemUi(
    val workspaceId: String? = null,
    val sessionId: String? = null,
    val stableId: String,
    val sessionName: String,
    val status: String,
    val backend: String,
    val lastSummary: String? = null,
    val pendingQuestion: Boolean,
    val routeLabel: String? = null,
    val lastUpdatedAt: Long = 0L,
)

internal fun TaskSessionItemUi.requiresAttention(): Boolean {
    return pendingQuestion || status == "WaitingUser" || status == "Paused" || status == "Failed"
}

internal fun TaskSessionItemUi.isActiveSession(): Boolean {
    return status == "Queued" || status == "Running"
}
