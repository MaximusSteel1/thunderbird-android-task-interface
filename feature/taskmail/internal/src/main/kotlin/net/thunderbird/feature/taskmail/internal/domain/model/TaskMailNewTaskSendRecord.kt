package net.thunderbird.feature.taskmail.internal.domain.model

internal data class TaskMailNewTaskSendRecord(
    val recordedAt: Long,
    val senderAccountId: String,
    val backend: TaskMailBackend,
    val repoPath: String,
    val workdir: String? = null,
    val evidence: TaskMailDirectSendEvidence,
)
