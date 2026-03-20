package net.thunderbird.feature.taskmail.internal.domain.parser

import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailBackend
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSessionLifecycle
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSessionStatus

internal data class TaskStateCapsule(
    val threadId: String,
    val workspaceId: String? = null,
    val sessionId: String? = null,
    val sessionName: String? = null,
    val taskId: String? = null,
    val backend: TaskMailBackend? = null,
    val repoPath: String? = null,
    val workdir: String? = null,
    val mode: String? = null,
    val status: TaskMailSessionStatus? = null,
    val lifecycle: TaskMailSessionLifecycle? = null,
    val pausedFromStatus: TaskMailSessionStatus? = null,
    val lastActiveAt: String? = null,
    val lastProgressAt: String? = null,
    val lastSummary: String? = null,
)
