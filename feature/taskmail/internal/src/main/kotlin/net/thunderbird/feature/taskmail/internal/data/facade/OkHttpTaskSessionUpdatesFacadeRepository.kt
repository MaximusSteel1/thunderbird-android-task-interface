package net.thunderbird.feature.taskmail.internal.data.facade

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import net.thunderbird.core.logging.Logger
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayQuestion
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayQuestionState
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayTimelineItem
import net.thunderbird.feature.taskmail.internal.domain.model.RelayTransportConfig
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailBackend
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSessionLifecycle
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSessionStatus
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionHistorySnapshot
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionHistorySnapshotAttachment
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionHistorySnapshotHeader
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionHistorySnapshotLocator
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionHistorySnapshotRound
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionHistorySnapshotTimelineItem
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionLatestActionSnapshot
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionLiveProcess
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionProcessItem
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionProcessItemKind
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskQuestionCapsule
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskSessionUpdatesRepository
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskTransportConfigRepository
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.HttpUrl.Companion.toHttpUrl

private const val TAG = "TaskSessionUpdatesFacade"
private const val CONFIG_REQUIRED_MESSAGE = "Relay host, port, and Android app token are required."
private const val CLOSE_CODE_NORMAL = 1000
private const val CALL_TIMEOUT_SECONDS = 15L
private const val PING_INTERVAL_SECONDS = 30L
private const val MESSAGE_TYPE_SESSION_SNAPSHOT = "session_snapshot"
private const val MESSAGE_TYPE_ERROR = "error"

internal class OkHttpTaskSessionUpdatesFacadeRepository(
    private val transportConfigRepository: TaskTransportConfigRepository,
    private val logger: Logger,
    okHttpClient: OkHttpClient? = null,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    },
) : TaskSessionUpdatesRepository {
    private val client = okHttpClient ?: OkHttpClient.Builder()
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(PING_INTERVAL_SECONDS, TimeUnit.SECONDS)
        .build()

    override fun observeSessionUpdates(
        locator: TaskSessionHistorySnapshotLocator,
    ): Flow<TaskSessionHistorySnapshot> = callbackFlow {
        val config = transportConfigRepository.getRelayTransportConfig().normalized()
        if (!config.isAndroidSessionUpdatesConfigured()) {
            close(IllegalStateException(CONFIG_REQUIRED_MESSAGE))
            return@callbackFlow
        }

        val httpUrl = config.absoluteHttpUrl(RelayTransportConfig.ANDROID_SESSION_UPDATES_PATH).toHttpUrl().newBuilder()
            .addQueryParameter("session_id", locator.sessionId)
            .apply {
                locator.workspaceId?.takeIf(String::isNotBlank)?.let { addQueryParameter("workspace_id", it) }
                locator.threadId?.takeIf(String::isNotBlank)?.let { addQueryParameter("thread_id", it) }
                locator.repoPath?.takeIf(String::isNotBlank)?.let { addQueryParameter("repo_path", it) }
                locator.workdir?.takeIf(String::isNotBlank)?.let { addQueryParameter("workdir", it) }
            }
            .build()
        val url = httpUrl.toString()
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://")

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${config.androidAppToken}")
            .build()

        val webSocket = client.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    runCatching { parseEnvelope(text) }
                        .onSuccess { envelope ->
                            when (envelope.messageType) {
                                MESSAGE_TYPE_SESSION_SNAPSHOT -> {
                                    val snapshot = json.decodeFromString<SessionUpdatesSnapshotResponsePayload>(
                                        envelope.payload.toString(),
                                    ).toDomain()
                                    trySend(snapshot)
                                }

                                MESSAGE_TYPE_ERROR -> {
                                    val errorPayload = json.decodeFromString<SessionUpdatesErrorPayload>(
                                        envelope.payload.toString(),
                                    )
                                    close(
                                        SessionSnapshotRequestException(
                                            errorCode = errorPayload.errorCode?.trim()?.takeIf(String::isNotEmpty),
                                            message = errorPayload.toUserMessage(),
                                            retryable = errorPayload.retryable == true,
                                        ),
                                    )
                                }
                            }
                        }
                        .onFailure { error ->
                            close(
                                IllegalStateException(
                                    error.message?.takeIf(String::isNotBlank)
                                        ?: "Failed to parse Android session-updates websocket message.",
                                    error,
                                ),
                            )
                        }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    logger.warn(TAG, t) { "Android session-updates websocket failed." }
                    close(
                        IllegalStateException(
                            t.message?.takeIf(String::isNotBlank)
                                ?: "Android session-updates websocket failed.",
                            t,
                        ),
                    )
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    close()
                }
            },
        )

        awaitClose {
            webSocket.close(CLOSE_CODE_NORMAL, "detail_closed")
        }
    }

    private fun parseEnvelope(text: String): SessionUpdatesEnvelopePayload {
        return json.decodeFromString(text)
    }
}

@Serializable
private data class SessionUpdatesEnvelopePayload(
    @SerialName("schema_version")
    val schemaVersion: String,
    @SerialName("subscription_id")
    val subscriptionId: String,
    @SerialName("message_type")
    val messageType: String,
    @SerialName("sent_at")
    val sentAt: String,
    val payload: JsonElement,
)

@Serializable
private data class SessionUpdatesSnapshotResponsePayload(
    @SerialName("snapshot_id")
    val snapshotId: String,
    @SerialName("generated_at")
    val generatedAt: String,
    val locator: SessionUpdatesSnapshotLocatorPayload? = null,
    @SerialName("session_snapshot")
    val sessionSnapshot: SessionUpdatesSnapshotPayload = SessionUpdatesSnapshotPayload(),
)

@Serializable
private data class SessionUpdatesSnapshotPayload(
    @SerialName("session_name")
    val sessionName: String? = null,
    val backend: String? = null,
    @SerialName("repo_path")
    val repoPath: String? = null,
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
    @SerialName("live_process")
    val liveProcess: SessionUpdatesLiveProcessPayload? = null,
    @SerialName("question_state")
    val questionState: RelayQuestionState? = null,
    @SerialName("timeline_items")
    val timelineItems: List<RelayTimelineItem> = emptyList(),
    @SerialName("latest_session_action")
    val latestSessionAction: SessionUpdatesLatestActionPayload? = null,
    @SerialName("history_rounds")
    val historyRounds: List<SessionUpdatesRoundPayload> = emptyList(),
)

@Serializable
private data class SessionUpdatesLiveProcessPayload(
    val status: String,
    @SerialName("updated_at")
    val updatedAt: String,
    val items: List<SessionUpdatesProcessItemPayload> = emptyList(),
)

@Serializable
private data class SessionUpdatesSnapshotLocatorPayload(
    @SerialName("workspace_id")
    val workspaceId: String? = null,
    @SerialName("session_id")
    val sessionId: String? = null,
    @SerialName("thread_id")
    val threadId: String? = null,
)

@Serializable
private data class SessionUpdatesLatestActionPayload(
    @SerialName("command_id")
    val commandId: String,
    @SerialName("action_type")
    val actionType: String,
    @SerialName("submit_ack")
    val submitAck: SessionUpdatesLatestActionSubmitAckPayload,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("acked_at")
    val ackedAt: String? = null,
    @SerialName("pc_id")
    val pcId: String? = null,
    @SerialName("result_status")
    val resultStatus: String? = null,
)

@Serializable
private data class SessionUpdatesLatestActionSubmitAckPayload(
    @SerialName("ack_status")
    val ackStatus: String,
)

@Serializable
private data class SessionUpdatesRoundPayload(
    @SerialName("round_id")
    val roundId: String,
    @SerialName("round_number")
    val roundNumber: Int,
    @SerialName("created_at")
    val createdAt: String,
    val status: String,
    @SerialName("speaker_label")
    val speakerLabel: String,
    val input: SessionUpdatesInputPayload = SessionUpdatesInputPayload(),
    val process: SessionUpdatesProcessPayload = SessionUpdatesProcessPayload(),
    val result: SessionUpdatesResultPayload = SessionUpdatesResultPayload(),
)

@Serializable
private data class SessionUpdatesInputPayload(
    val text: String? = null,
    val attachments: List<SessionUpdatesAttachmentPayload> = emptyList(),
)

@Serializable
private data class SessionUpdatesProcessPayload(
    val items: List<SessionUpdatesProcessItemPayload> = emptyList(),
)

@Serializable
private data class SessionUpdatesResultPayload(
    val text: String = "",
    val attachments: List<SessionUpdatesAttachmentPayload> = emptyList(),
)

@Serializable
private data class SessionUpdatesAttachmentPayload(
    @SerialName("attachment_id")
    val attachmentId: String,
    @SerialName("display_name")
    val displayName: String,
    @SerialName("content_type")
    val contentType: String? = null,
    @SerialName("size_bytes")
    val sizeBytes: Long? = null,
    @SerialName("is_image")
    val isImage: Boolean = false,
)

@Serializable
private data class SessionUpdatesProcessItemPayload(
    @SerialName("item_id")
    val itemId: String,
    val kind: String? = null,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String,
    val status: String? = null,
    val text: String,
)

@Serializable
private data class SessionUpdatesErrorPayload(
    val status: String? = null,
    @SerialName("error_code")
    val errorCode: String? = null,
    @SerialName("error_message")
    val errorMessage: String? = null,
    val retryable: Boolean? = null,
)

private fun SessionUpdatesSnapshotResponsePayload.toDomain(): TaskSessionHistorySnapshot {
    return TaskSessionHistorySnapshot(
        snapshotId = snapshotId,
        generatedAt = generatedAt,
        sessionHeader = sessionSnapshot.toDomainHeader(locator),
        latestSessionAction = sessionSnapshot.latestSessionAction?.toDomain(),
        rounds = sessionSnapshot.historyRounds.map(SessionUpdatesRoundPayload::toDomain)
            .toImmutableList(),
    )
}

private fun SessionUpdatesSnapshotPayload.toDomainHeader(
    locator: SessionUpdatesSnapshotLocatorPayload?,
): TaskSessionHistorySnapshotHeader? {
    return TaskSessionHistorySnapshotHeader(
        workspaceId = locator?.workspaceId?.trim()?.takeIf(String::isNotEmpty),
        sessionId = locator?.sessionId?.trim()?.takeIf(String::isNotEmpty),
        threadId = locator?.threadId?.trim()?.takeIf(String::isNotEmpty),
        sessionName = sessionName?.trim()?.takeIf(String::isNotEmpty),
        backend = TaskMailBackend.fromWireValue(backend),
        repoPath = repoPath?.trim()?.takeIf(String::isNotEmpty),
        workdir = workdir?.trim()?.takeIf(String::isNotEmpty),
        status = TaskMailSessionStatus.fromWireValue(status),
        lifecycle = TaskMailSessionLifecycle.fromWireValue(lifecycle),
        lastSummary = lastSummary?.trim()?.takeIf(String::isNotEmpty),
        lastActiveAt = lastActiveAt?.trim()?.takeIf(String::isNotEmpty),
        lastProgressAt = lastProgressAt?.trim()?.takeIf(String::isNotEmpty),
        pausedFromStatus = TaskMailSessionStatus.fromWireValue(pausedFromStatus),
        liveProcess = liveProcess?.toDomain(),
        pendingQuestions = questionState.toQuestionCapsules().toImmutableList(),
        timelineItems = timelineItems.map(RelayTimelineItem::toDomain).toImmutableList(),
    ).takeIf { header ->
        header.workspaceId != null ||
            header.sessionId != null ||
            header.threadId != null ||
            header.sessionName != null ||
            header.backend != null ||
            header.repoPath != null ||
            header.workdir != null ||
            header.status != null ||
            header.lifecycle != null ||
            header.lastSummary != null ||
            header.lastActiveAt != null ||
            header.lastProgressAt != null ||
            header.pausedFromStatus != null ||
            header.liveProcess != null ||
            header.pendingQuestions.isNotEmpty() ||
            header.timelineItems.isNotEmpty()
    }
}

private fun SessionUpdatesLatestActionPayload.toDomain(): TaskSessionLatestActionSnapshot {
    return TaskSessionLatestActionSnapshot(
        commandId = commandId,
        actionType = actionType,
        ackStatus = submitAck.ackStatus,
        createdAt = createdAt,
        ackedAt = ackedAt,
        pcId = pcId,
        resultStatus = resultStatus,
    )
}

private fun SessionUpdatesRoundPayload.toDomain(): TaskSessionHistorySnapshotRound {
    return TaskSessionHistorySnapshotRound(
        roundId = roundId,
        roundNumber = roundNumber,
        createdAt = createdAt,
        status = status,
        speakerLabel = speakerLabel,
        inputText = input.text,
        inputAttachments = input.attachments.map(SessionUpdatesAttachmentPayload::toDomain).toImmutableList(),
        processItems = process.items.map(SessionUpdatesProcessItemPayload::toDomain).toImmutableList(),
        resultText = result.text,
        resultAttachments = result.attachments.map(SessionUpdatesAttachmentPayload::toDomain).toImmutableList(),
    )
}

private fun SessionUpdatesAttachmentPayload.toDomain(): TaskSessionHistorySnapshotAttachment {
    return TaskSessionHistorySnapshotAttachment(
        attachmentId = attachmentId,
        displayName = displayName,
        contentType = contentType,
        sizeBytes = sizeBytes,
        isImage = isImage,
    )
}

private fun SessionUpdatesProcessItemPayload.toDomain(): TaskSessionProcessItem {
    return TaskSessionProcessItem(
        itemId = itemId,
        kind = TaskSessionProcessItemKind.fromWireValue(kind),
        createdAt = createdAt,
        updatedAt = updatedAt,
        status = status,
        text = text,
    )
}

private fun RelayTimelineItem.toDomain(): TaskSessionHistorySnapshotTimelineItem {
    return TaskSessionHistorySnapshotTimelineItem(
        itemId = itemId,
        businessEventKey = businessEventKey,
        itemType = itemType,
        createdAt = createdAt,
        status = status,
        text = text,
    )
}

private fun SessionUpdatesLiveProcessPayload.toDomain(): TaskSessionLiveProcess {
    return TaskSessionLiveProcess(
        status = status.trim(),
        updatedAt = updatedAt.trim(),
        items = items.map { item -> item.toDomain() }.toImmutableList(),
    )
}

private fun SessionUpdatesErrorPayload.toUserMessage(): String {
    return errorMessage?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: "Android session-updates request failed."
}

private fun RelayQuestionState?.toQuestionCapsules(): List<TaskQuestionCapsule> {
    return this?.questions
        ?.map { question -> question.toTaskQuestionCapsule(questionSetId) }
        .orEmpty()
}

private fun RelayQuestion.toTaskQuestionCapsule(questionSetId: String?): TaskQuestionCapsule {
    return TaskQuestionCapsule(
        questionId = questionId,
        questionText = questionText,
        choices = choices,
        questionSetId = questionSetId,
        questionType = questionType,
        required = required,
        choiceLabels = choiceLabels,
    )
}
