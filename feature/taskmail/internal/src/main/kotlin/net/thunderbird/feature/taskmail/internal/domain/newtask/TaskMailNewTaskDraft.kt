package net.thunderbird.feature.taskmail.internal.domain.newtask

import net.thunderbird.feature.taskmail.internal.data.controlplane.protocol.ControlPlaneExecutionPolicy
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailBackend

internal data class TaskMailNewTaskDraft(
    val senderAccountId: String,
    val backend: TaskMailBackend,
    val repoPath: String,
    val taskText: String,
    val subjectTitle: String,
    val workdir: String? = null,
    val mode: TaskMailNewTaskMode = TaskMailNewTaskMode.Modify,
    val timeoutMinutes: Int? = null,
    val permission: TaskMailNewTaskPermission = TaskMailNewTaskPermission.Default,
    val profile: String? = null,
    val acceptanceCriteria: List<String> = emptyList(),
    val pcId: String? = null,
    val workspaceId: String? = null,
    val executionPolicy: ControlPlaneExecutionPolicy? = null,
)

internal enum class TaskMailNewTaskMode(
    val wireValue: String,
) {
    Modify("modify"),
    AnalysisOnly("analysis_only"),
}

internal enum class TaskMailNewTaskPermission(
    val wireValue: String?,
) {
    Default(null),
    Highest("highest"),
}
