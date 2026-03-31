package net.thunderbird.feature.taskmail.internal.domain.model

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskQuestionCapsule

@Immutable
internal data class TaskSessionHistorySnapshot(
    val snapshotId: String,
    val generatedAt: String,
    val sessionHeader: TaskSessionHistorySnapshotHeader? = null,
    val latestSessionAction: TaskSessionLatestActionSnapshot? = null,
    val rounds: ImmutableList<TaskSessionHistorySnapshotRound> = persistentListOf(),
)

@Immutable
internal data class TaskSessionHistorySnapshotHeader(
    val workspaceId: String? = null,
    val sessionId: String? = null,
    val threadId: String? = null,
    val sessionName: String? = null,
    val backend: TaskMailBackend? = null,
    val repoPath: String? = null,
    val workdir: String? = null,
    val status: TaskMailSessionStatus? = null,
    val lifecycle: TaskMailSessionLifecycle? = null,
    val lastSummary: String? = null,
    val lastActiveAt: String? = null,
    val lastProgressAt: String? = null,
    val pausedFromStatus: TaskMailSessionStatus? = null,
    val liveProcess: TaskSessionLiveProcess? = null,
    val pendingQuestions: ImmutableList<TaskQuestionCapsule> = persistentListOf(),
    val timelineItems: ImmutableList<TaskSessionHistorySnapshotTimelineItem> = persistentListOf(),
)

@Immutable
internal data class TaskSessionHistorySnapshotTimelineItem(
    val itemId: String,
    val businessEventKey: String,
    val itemType: String,
    val createdAt: String,
    val status: String? = null,
    val text: String? = null,
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
    val processItems: ImmutableList<TaskSessionProcessItem> = persistentListOf(),
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
    val actionTarget: TaskAttachmentActionTarget? = null,
)

@Immutable
internal data class TaskSessionHistorySnapshotLocator(
    val workspaceId: String? = null,
    val sessionId: String,
    val threadId: String? = null,
    val repoPath: String? = null,
    val workdir: String? = null,
)
