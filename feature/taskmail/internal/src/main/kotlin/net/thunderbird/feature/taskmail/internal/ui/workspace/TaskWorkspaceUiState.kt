package net.thunderbird.feature.taskmail.internal.ui.workspace

internal data class TaskWorkspaceItemUi(
    val title: String,
    val subtitle: String? = null,
    val sessionCountLabel: String,
    val sessions: List<TaskSessionItemUi>,
)

internal data class TaskSessionItemUi(
    val sessionId: String? = null,
    val threadId: String,
    val sessionName: String,
    val status: String,
    val backend: String,
    val lastSummary: String? = null,
    val pendingQuestion: Boolean,
)
