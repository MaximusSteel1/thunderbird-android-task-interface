package net.thunderbird.feature.taskmail.internal.data.facade

import java.util.concurrent.TimeUnit
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import net.thunderbird.core.logging.Logger
import net.thunderbird.feature.taskmail.internal.data.controlplane.protocol.ControlPlaneDownloadRef
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
import net.thunderbird.feature.taskmail.internal.domain.model.buildRelayArtifactActionTarget
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskQuestionCapsule
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskSessionHistorySnapshotRepository
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskTransportConfigRepository
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

private const val TAG = "TaskSessionHistorySnapshot"
private const val CONFIG_REQUIRED_MESSAGE = "Relay host, port, and Android app token are required."

internal class SessionSnapshotRequestException(
    val errorCode: String? = null,
    override val message: String,
    val retryable: Boolean = false,
) : IllegalStateException(message)

internal class OkHttpTaskSessionHistorySnapshotFacadeRepository(
    private val transportConfigRepository: TaskTransportConfigRepository,
    private val logger: Logger,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    okHttpClient: OkHttpClient? = null,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    },
) : TaskSessionHistorySnapshotRepository {
    private val client = okHttpClient ?: OkHttpClient.Builder()
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    override suspend fun getHistorySnapshot(
        locator: TaskSessionHistorySnapshotLocator,
    ): Result<TaskSessionHistorySnapshot> {
        val config = transportConfigRepository.getRelayTransportConfig().normalized()
        if (!config.isAndroidSessionSnapshotConfigured()) {
            return Result.failure(IllegalStateException(CONFIG_REQUIRED_MESSAGE))
        }

        return withContext(ioDispatcher) {
            runCatching {
                executeRequest(config = config, locator = locator)
            }.onFailure { error ->
                logger.error(TAG, error) {
                    "Failed to fetch Android session history snapshot."
                }
            }
        }
    }

    private fun executeRequest(
        config: RelayTransportConfig,
        locator: TaskSessionHistorySnapshotLocator,
    ): TaskSessionHistorySnapshot {
        val url = config.androidSessionSnapshotUrl().toHttpUrl().newBuilder()
            .addQueryParameter("session_id", locator.sessionId)
            .apply {
                locator.workspaceId?.takeIf(String::isNotBlank)?.let { addQueryParameter("workspace_id", it) }
                locator.threadId?.takeIf(String::isNotBlank)?.let { addQueryParameter("thread_id", it) }
                locator.repoPath?.takeIf(String::isNotBlank)?.let { addQueryParameter("repo_path", it) }
                locator.workdir?.takeIf(String::isNotBlank)?.let { addQueryParameter("workdir", it) }
            }
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${config.androidAppToken}")
            .header("Accept", "application/json")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                val errorPayload = parseErrorPayload(body)
                throw SessionSnapshotRequestException(
                    errorCode = errorPayload.errorCode?.trim()?.takeIf(String::isNotEmpty),
                    message = errorPayload.toUserMessage(response.code),
                    retryable = errorPayload.retryable == true,
                )
            }

            return json.decodeFromString<SessionSnapshotResponsePayload>(body).toDomain()
        }
    }

    private fun parseErrorPayload(body: String): SessionSnapshotErrorPayload {
        return runCatching {
            json.decodeFromString<SessionSnapshotErrorPayload>(body)
        }.getOrElse {
            SessionSnapshotErrorPayload(
                errorMessage = body.trim().takeIf(String::isNotEmpty),
            )
        }
    }

    private fun SessionSnapshotResponsePayload.toDomain(): TaskSessionHistorySnapshot {
        return TaskSessionHistorySnapshot(
            snapshotId = snapshotId,
            generatedAt = generatedAt,
            sessionHeader = sessionSnapshot.toDomainHeader(locator),
            latestSessionAction = sessionSnapshot.latestSessionAction?.toDomain(),
            rounds = sessionSnapshot.historyRounds.map { it.toDomain() }.toImmutableList(),
        )
    }

    private fun SessionSnapshotPayload.toDomainHeader(
        locator: SessionSnapshotLocatorPayload?,
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
            timelineItems = timelineItems.map { timelineItem -> timelineItem.toDomain() }.toImmutableList(),
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

    private fun SessionSnapshotLatestActionPayload.toDomain(): TaskSessionLatestActionSnapshot {
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

    private fun SessionSnapshotRoundPayload.toDomain(): TaskSessionHistorySnapshotRound {
        return TaskSessionHistorySnapshotRound(
            roundId = roundId,
            roundNumber = roundNumber,
            createdAt = createdAt,
            status = status,
            speakerLabel = speakerLabel,
            inputText = input.text,
            inputAttachments = input.attachments.map { it.toDomain() }.toImmutableList(),
            processItems = process.items.map { it.toDomain() }.toImmutableList(),
            resultText = result.text,
            resultAttachments = result.attachments.map { it.toDomain() }.toImmutableList(),
        )
    }

    private fun SessionSnapshotAttachmentPayload.toDomain(): TaskSessionHistorySnapshotAttachment {
        return TaskSessionHistorySnapshotAttachment(
            attachmentId = attachmentId,
            displayName = displayName,
            contentType = contentType,
            sizeBytes = sizeBytes,
            isImage = isImage,
            actionTarget = downloadRef?.let { downloadRef ->
                buildRelayArtifactActionTarget(
                    attachmentId = attachmentId,
                    displayName = displayName,
                    contentType = contentType,
                    kind = downloadRef.kind,
                    fileId = downloadRef.fileId,
                    metadataUrl = downloadRef.metadataUrl,
                    contentUrl = downloadRef.contentUrl,
                    url = downloadRef.url,
                    relayContentType = downloadRef.contentType,
                    encoding = downloadRef.encoding,
                    data = downloadRef.data,
                )
            },
        )
    }

    private fun SessionSnapshotProcessItemPayload.toDomain(): TaskSessionProcessItem {
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

    private fun SessionSnapshotLiveProcessPayload.toDomain(): TaskSessionLiveProcess {
        return TaskSessionLiveProcess(
            status = status.trim(),
            updatedAt = updatedAt.trim(),
            items = items.map { item -> item.toDomain() }.toImmutableList(),
        )
    }

    private fun SessionSnapshotErrorPayload.toUserMessage(statusCode: Int): String {
        return errorMessage?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: "Session snapshot request failed with HTTP $statusCode."
    }

    private companion object {
        const val CALL_TIMEOUT_SECONDS = 15L
    }
}

@Serializable
private data class SessionSnapshotResponsePayload(
    @SerialName("snapshot_id")
    val snapshotId: String,
    @SerialName("generated_at")
    val generatedAt: String,
    val locator: SessionSnapshotLocatorPayload? = null,
    @SerialName("session_snapshot")
    val sessionSnapshot: SessionSnapshotPayload = SessionSnapshotPayload(),
)

@Serializable
private data class SessionSnapshotPayload(
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
    val liveProcess: SessionSnapshotLiveProcessPayload? = null,
    @SerialName("question_state")
    val questionState: RelayQuestionState? = null,
    @SerialName("timeline_items")
    val timelineItems: List<RelayTimelineItem> = emptyList(),
    @SerialName("latest_session_action")
    val latestSessionAction: SessionSnapshotLatestActionPayload? = null,
    @SerialName("history_rounds")
    val historyRounds: List<SessionSnapshotRoundPayload> = emptyList(),
)

@Serializable
private data class SessionSnapshotLiveProcessPayload(
    val status: String,
    @SerialName("updated_at")
    val updatedAt: String,
    val items: List<SessionSnapshotProcessItemPayload> = emptyList(),
)

@Serializable
private data class SessionSnapshotLocatorPayload(
    @SerialName("workspace_id")
    val workspaceId: String? = null,
    @SerialName("session_id")
    val sessionId: String? = null,
    @SerialName("thread_id")
    val threadId: String? = null,
)

@Serializable
private data class SessionSnapshotLatestActionPayload(
    @SerialName("command_id")
    val commandId: String,
    @SerialName("action_type")
    val actionType: String,
    @SerialName("submit_ack")
    val submitAck: SessionSnapshotLatestActionSubmitAckPayload,
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
private data class SessionSnapshotLatestActionSubmitAckPayload(
    @SerialName("ack_status")
    val ackStatus: String,
)

@Serializable
private data class SessionSnapshotRoundPayload(
    @SerialName("round_id")
    val roundId: String,
    @SerialName("round_number")
    val roundNumber: Int,
    @SerialName("created_at")
    val createdAt: String,
    val status: String,
    @SerialName("speaker_label")
    val speakerLabel: String,
    val input: SessionSnapshotInputPayload = SessionSnapshotInputPayload(),
    val process: SessionSnapshotProcessPayload = SessionSnapshotProcessPayload(),
    val result: SessionSnapshotResultPayload = SessionSnapshotResultPayload(),
)

@Serializable
private data class SessionSnapshotInputPayload(
    val text: String? = null,
    val attachments: List<SessionSnapshotAttachmentPayload> = emptyList(),
)

@Serializable
private data class SessionSnapshotProcessPayload(
    val items: List<SessionSnapshotProcessItemPayload> = emptyList(),
)

@Serializable
private data class SessionSnapshotResultPayload(
    val text: String = "",
    val attachments: List<SessionSnapshotAttachmentPayload> = emptyList(),
)

@Serializable
private data class SessionSnapshotAttachmentPayload(
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
    @SerialName("download_ref")
    val downloadRef: ControlPlaneDownloadRef? = null,
)

@Serializable
private data class SessionSnapshotProcessItemPayload(
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
private data class SessionSnapshotErrorPayload(
    val status: String? = null,
    @SerialName("error_code")
    val errorCode: String? = null,
    @SerialName("error_message")
    val errorMessage: String? = null,
    val retryable: Boolean? = null,
)

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
