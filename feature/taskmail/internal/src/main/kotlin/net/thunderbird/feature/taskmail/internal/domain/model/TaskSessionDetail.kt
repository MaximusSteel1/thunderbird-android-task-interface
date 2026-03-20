package net.thunderbird.feature.taskmail.internal.domain.model

import net.thunderbird.feature.taskmail.internal.domain.parser.TaskQuestionCapsule

internal data class TaskSessionDetail(
    val key: TaskSessionKey,
    val workspace: TaskWorkspaceKey,
    val sessionName: String,
    val backend: TaskMailBackend,
    val status: TaskMailSessionStatus,
    val lifecycle: TaskMailSessionLifecycle? = null,
    val repoPath: String,
    val workdir: String? = null,
    val lastSummary: String? = null,
    val pausedFromStatus: TaskMailSessionStatus? = null,
    val lastActiveAt: String? = null,
    val lastProgressAt: String? = null,
    val question: TaskQuestionCapsule? = null,
    val pendingQuestions: List<TaskQuestionCapsule> = question?.let { listOf(it) } ?: emptyList(),
    val replyContext: TaskSessionReplyContext? = null,
    val timeline: List<TaskTimelineItem>,
)
