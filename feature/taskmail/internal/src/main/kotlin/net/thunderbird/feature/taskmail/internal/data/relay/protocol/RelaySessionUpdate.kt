package net.thunderbird.feature.taskmail.internal.data.relay.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class RelaySessionUpdate(
    @SerialName("message_type")
    val messageType: String = MESSAGE_TYPE,
    @SerialName("schema_version")
    val schemaVersion: String,
    @SerialName("subscription_id")
    val subscriptionId: String,
    @SerialName("workspace_id")
    val workspaceId: String,
    @SerialName("session_id")
    val sessionId: String,
    @SerialName("thread_id")
    val threadId: String,
    @SerialName("task_id")
    val taskId: String,
    @SerialName("update_id")
    val updateId: String,
    val sequence: Long,
    @SerialName("sent_at")
    val sentAt: String,
    @SerialName("update_type")
    val updateType: String,
    @SerialName("session_snapshot")
    val sessionSnapshot: RelaySessionSnapshot? = null,
    @SerialName("session_delta")
    val sessionDelta: RelaySessionDelta? = null,
) {
    companion object {
        const val MESSAGE_TYPE = "session_update"
    }
}

@Serializable
internal data class RelaySessionSnapshot(
    @SerialName("session_name")
    val sessionName: String,
    val backend: String? = null,
    @SerialName("repo_path")
    val repoPath: String,
    val workdir: String? = null,
    val status: String? = null,
    val lifecycle: String? = null,
    @SerialName("last_summary")
    val lastSummary: String? = null,
    @SerialName("last_active_at")
    val lastActiveAt: String? = null,
    @SerialName("last_progress_at")
    val lastProgressAt: String? = null,
    @SerialName("paused_from_status")
    val pausedFromStatus: String? = null,
    @SerialName("question_state")
    val questionState: RelayQuestionState? = null,
    @SerialName("timeline_items")
    val timelineItems: List<RelayTimelineItem> = emptyList(),
)

@Serializable
internal data class RelaySessionDelta(
    @SerialName("delta_type")
    val deltaType: String,
    @SerialName("state_transition")
    val stateTransition: RelayStateTransition? = null,
    @SerialName("timeline_items")
    val timelineItems: List<RelayTimelineItem> = emptyList(),
)

@Serializable
internal data class RelayStateTransition(
    val status: String? = null,
    val lifecycle: String? = null,
    @SerialName("last_summary")
    val lastSummary: String? = null,
    @SerialName("last_active_at")
    val lastActiveAt: String? = null,
    @SerialName("last_progress_at")
    val lastProgressAt: String? = null,
    @SerialName("paused_from_status")
    val pausedFromStatus: String? = null,
    @SerialName("question_state")
    val questionState: RelayQuestionState? = null,
)

@Serializable
internal data class RelayQuestionState(
    @SerialName("question_set_id")
    val questionSetId: String? = null,
    @SerialName("question_count")
    val questionCount: Int? = null,
    val questions: List<RelayQuestion> = emptyList(),
)

@Serializable
internal data class RelayQuestion(
    @SerialName("question_id")
    val questionId: String,
    @SerialName("question_text")
    val questionText: String,
    @SerialName("question_type")
    val questionType: String? = null,
    val required: Boolean = true,
    val choices: List<String> = emptyList(),
    @SerialName("choice_labels")
    val choiceLabels: Map<String, String> = emptyMap(),
)

@Serializable
internal data class RelayTimelineItem(
    @SerialName("item_id")
    val itemId: String,
    @SerialName("business_event_key")
    val businessEventKey: String,
    @SerialName("item_type")
    val itemType: String,
    @SerialName("created_at")
    val createdAt: String,
    val status: String? = null,
    val text: String? = null,
    @SerialName("question_set_id")
    val questionSetId: String? = null,
    @SerialName("question_ids")
    val questionIds: List<String> = emptyList(),
    @SerialName("paused_from_status")
    val pausedFromStatus: String? = null,
)
