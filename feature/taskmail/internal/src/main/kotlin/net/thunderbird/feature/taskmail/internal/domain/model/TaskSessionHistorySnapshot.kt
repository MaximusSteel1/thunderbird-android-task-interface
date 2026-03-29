package net.thunderbird.feature.taskmail.internal.domain.model

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
internal data class TaskSessionHistorySnapshot(
    val snapshotId: String,
    val generatedAt: String,
    val latestSessionAction: TaskSessionLatestActionSnapshot? = null,
    val rounds: ImmutableList<TaskSessionHistorySnapshotRound> = persistentListOf(),
)

@Immutable
internal data class TaskSessionLatestActionSnapshot(
    val commandId: String,
    val actionType: String,
    val ackStatus: String,
    val createdAt: String? = null,
    val ackedAt: String? = null,
    val pcId: String? = null,
    val resultStatus: String? = null,
)

@Immutable
internal data class TaskSessionHistorySnapshotRound(
    val roundId: String,
    val roundNumber: Int,
    val createdAt: String,
    val status: String,
    val speakerLabel: String,
    val inputText: String? = null,
    val inputAttachments: ImmutableList<TaskSessionHistorySnapshotAttachment> = persistentListOf(),
    val processItems: ImmutableList<TaskSessionHistorySnapshotProcessItem> = persistentListOf(),
    val resultText: String,
    val resultAttachments: ImmutableList<TaskSessionHistorySnapshotAttachment> = persistentListOf(),
)

@Immutable
internal data class TaskSessionHistorySnapshotAttachment(
    val attachmentId: String,
    val displayName: String,
    val contentType: String? = null,
    val sizeBytes: Long? = null,
    val isImage: Boolean = false,
)

@Immutable
internal data class TaskSessionHistorySnapshotProcessItem(
    val itemId: String,
    val createdAt: String,
    val status: String? = null,
    val text: String,
)

@Immutable
internal data class TaskSessionHistorySnapshotLocator(
    val workspaceId: String? = null,
    val sessionId: String,
    val threadId: String? = null,
    val repoPath: String? = null,
    val workdir: String? = null,
)
