package net.thunderbird.feature.taskmail.internal.data.facade

import java.io.File
import java.util.Base64
import java.util.concurrent.TimeUnit
import com.fsck.k9.message.Attachment
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.thunderbird.core.logging.Logger
import net.thunderbird.feature.taskmail.internal.data.TaskMailReplyAttachmentResolver
import net.thunderbird.feature.taskmail.internal.data.controlplane.protocol.ControlPlaneExecutionPolicy
import net.thunderbird.feature.taskmail.internal.data.controlplane.protocol.toCanonicalAcceptanceCriteria
import net.thunderbird.feature.taskmail.internal.data.controlplane.protocol.toCanonicalExecutionPolicy
import net.thunderbird.feature.taskmail.internal.domain.model.RelayTransportConfig
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailCreateSessionAckStatus
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailCreateSessionBinding
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailCreateSessionClient
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailCreateSessionResult
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailCreateSessionSubmitAck
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskDraft
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskTransportConfigRepository
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private const val TAG = "TaskMailCreateSessionFacade"
private const val CONFIG_REQUIRED_MESSAGE = "Relay host, port, and Android app token are required."
private const val DEFAULT_REJECTED_MESSAGE = "Create-session request was rejected."

internal class OkHttpTaskMailCreateSessionFacadeClient(
    private val transportConfigRepository: TaskTransportConfigRepository,
    private val logger: Logger,
    private val replyAttachmentResolver: TaskMailReplyAttachmentResolver,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    okHttpClient: OkHttpClient? = null,
    private val json: Json = Json {
        encodeDefaults = false
        ignoreUnknownKeys = true
        explicitNulls = false
    },
) : TaskMailCreateSessionClient {
    private val client = okHttpClient ?: OkHttpClient.Builder()
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    override suspend fun createSession(draft: TaskMailNewTaskDraft): TaskMailCreateSessionResult {
        val config = transportConfigRepository.getRelayTransportConfig().normalized()
        if (!config.isAndroidCreateSessionConfigured()) {
            return TaskMailCreateSessionResult.Failed(
                errorMessage = CONFIG_REQUIRED_MESSAGE,
            )
        }

        return withContext(ioDispatcher) {
            runCatching {
                executeCreateSessionRequest(
                    config = config,
                    draft = draft,
                )
            }.onFailure { error ->
                logger.error(TAG, error) {
                    "Failed to create TaskMail session via Android facade."
                }
            }.getOrElse { error ->
                TaskMailCreateSessionResult.Failed(
                    errorMessage = error.message?.trim().takeUnless(String?::isNullOrEmpty)
                        ?: "Create-session request failed.",
                )
            }
        }
    }

    private suspend fun executeCreateSessionRequest(
        config: RelayTransportConfig,
        draft: TaskMailNewTaskDraft,
    ): TaskMailCreateSessionResult {
        val requestPayload = draft.toFacadeRequest()
        val requestBody = json.encodeToString(requestPayload)
            .toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(config.androidCreateSessionUrl())
            .header("Authorization", "Bearer ${config.androidAppToken}")
            .header("Accept", "application/json")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                val errorPayload = parseErrorPayload(body)
                return TaskMailCreateSessionResult.Failed(
                    errorMessage = errorPayload.toUserMessage(response.code),
                )
            }

            val payload = json.decodeFromString<AndroidCreateSessionResponsePayload>(body)
            val submitAck = payload.submitAck.toDomain()

            return when (submitAck.ackStatus) {
                TaskMailCreateSessionAckStatus.Accepted,
                TaskMailCreateSessionAckStatus.AcceptedButQueued,
                -> TaskMailCreateSessionResult.Submitted(
                    commandId = payload.commandId,
                    submitAck = submitAck,
                    sessionBinding = payload.sessionBinding?.toDomain(),
                )

                TaskMailCreateSessionAckStatus.Rejected -> TaskMailCreateSessionResult.Rejected(
                    commandId = payload.commandId,
                    submitAck = submitAck,
                    errorMessage = submitAck.reason
                        ?.trim()
                        .takeUnless { it.isNullOrEmpty() }
                        ?: DEFAULT_REJECTED_MESSAGE,
                )
            }
        }
    }

    private fun parseErrorPayload(body: String): AndroidCreateSessionErrorPayload {
        return runCatching {
            json.decodeFromString<AndroidCreateSessionErrorPayload>(body)
        }.getOrElse {
            AndroidCreateSessionErrorPayload(
                errorMessage = body.trim().takeIf(String::isNotEmpty),
            )
        }
    }

    private suspend fun TaskMailNewTaskDraft.toFacadeRequest(): AndroidCreateSessionRequestPayload {
        val attachmentPayloads = if (attachments.isEmpty()) {
            emptyList()
        } else {
            replyAttachmentResolver.buildOutgoingAttachments(attachments)
                .mapCatching { preparedAttachments ->
                    preparedAttachments.map(::toFacadeAttachment)
                }
                .getOrElse { error ->
                    throw IllegalStateException(
                        error.message?.trim().takeUnless(String?::isNullOrEmpty)
                            ?: "Failed to prepare input attachments.",
                        error,
                    )
                }
        }
        return AndroidCreateSessionRequestPayload(
            pcId = requireNormalizedText(pcId, "pcId"),
            workspaceId = requireNormalizedText(workspaceId, "workspaceId"),
            prompt = requireNormalizedText(taskText, "taskText"),
            executionPolicy = toCanonicalExecutionPolicy(),
            mode = mode.wireValue.takeUnless { it == DEFAULT_MODE },
            timeoutSeconds = timeoutMinutes?.let { it * SECONDS_PER_MINUTE },
            acceptance = acceptanceCriteria.toCanonicalAcceptanceCriteria().takeIf { it.isNotEmpty() },
            repoPath = repoPath.trim().takeIf(String::isNotEmpty),
            workdir = workdir?.trim()?.takeIf(String::isNotEmpty),
            attachments = attachmentPayloads.takeIf { it.isNotEmpty() },
            source = DEFAULT_SOURCE,
        )
    }

    private fun toFacadeAttachment(attachment: Attachment): AndroidCreateSessionAttachmentPayload {
        val filePath = attachment.fileName?.trim().takeUnless(String?::isNullOrEmpty)
            ?: error("Prepared attachment is missing fileName.")
        val file = File(filePath)
        if (!file.exists() || !file.isFile) {
            error("Prepared attachment file is unavailable: $filePath")
        }
        val contentBytes = file.readBytes()
        return AndroidCreateSessionAttachmentPayload(
            name = attachment.name?.trim().takeUnless(String?::isNullOrEmpty) ?: file.name,
            contentType = attachment.contentType?.trim().takeUnless(String?::isNullOrEmpty)
                ?: "application/octet-stream",
            sizeBytes = attachment.size ?: contentBytes.size.toLong(),
            contentBytesBase64 = Base64.getEncoder().encodeToString(contentBytes),
        )
    }

    private fun AndroidCreateSessionSubmitAckPayload.toDomain(): TaskMailCreateSessionSubmitAck {
        return TaskMailCreateSessionSubmitAck(
            ackStatus = ackStatus.toDomainAckStatus(),
            queuePosition = queuePosition,
            reason = reason?.trim()?.takeIf(String::isNotEmpty),
            errorCode = errorCode?.trim()?.takeIf(String::isNotEmpty),
        )
    }

    private fun String.toDomainAckStatus(): TaskMailCreateSessionAckStatus {
        return when (trim()) {
            TaskMailCreateSessionAckStatus.Accepted.wireValue -> TaskMailCreateSessionAckStatus.Accepted
            TaskMailCreateSessionAckStatus.AcceptedButQueued.wireValue -> {
                TaskMailCreateSessionAckStatus.AcceptedButQueued
            }
            TaskMailCreateSessionAckStatus.Rejected.wireValue -> TaskMailCreateSessionAckStatus.Rejected
            else -> error("Unknown create-session ack status: $this")
        }
    }

    private fun AndroidCreateSessionBindingPayload.toDomain(): TaskMailCreateSessionBinding {
        return TaskMailCreateSessionBinding(
            sessionId = sessionId.trim(),
            pcId = pcId.trim(),
            workspaceId = workspaceId.trim(),
        )
    }

    private fun AndroidCreateSessionErrorPayload.toUserMessage(statusCode: Int): String {
        val baseMessage = errorMessage?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: "Create-session request failed with HTTP $statusCode."
        val normalizedCommandId = commandId?.trim()?.takeIf(String::isNotEmpty)
        return if (normalizedCommandId != null && !baseMessage.contains(normalizedCommandId)) {
            "$baseMessage (command_id=$normalizedCommandId)"
        } else {
            baseMessage
        }
    }

    private companion object {
        const val CALL_TIMEOUT_SECONDS = 30L
        const val DEFAULT_SOURCE = "android"
        const val DEFAULT_MODE = "modify"
        const val SECONDS_PER_MINUTE = 60
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

@Serializable
private data class AndroidCreateSessionRequestPayload(
    @SerialName("pc_id")
    val pcId: String,
    @SerialName("workspace_id")
    val workspaceId: String,
    val prompt: String,
    @SerialName("execution_policy")
    val executionPolicy: ControlPlaneExecutionPolicy,
    val mode: String? = null,
    @SerialName("timeout_seconds")
    val timeoutSeconds: Int? = null,
    val acceptance: List<String>? = null,
    @SerialName("repo_path")
    val repoPath: String? = null,
    val workdir: String? = null,
    val attachments: List<AndroidCreateSessionAttachmentPayload>? = null,
    val source: String? = null,
)

@Serializable
private data class AndroidCreateSessionAttachmentPayload(
    val name: String,
    @SerialName("content_type")
    val contentType: String,
    @SerialName("size_bytes")
    val sizeBytes: Long,
    @SerialName("content_bytes_b64")
    val contentBytesBase64: String,
)

@Serializable
private data class AndroidCreateSessionResponsePayload(
    @SerialName("schema_version")
    val schemaVersion: String,
    val status: String,
    @SerialName("command_id")
    val commandId: String,
    @SerialName("submit_ack")
    val submitAck: AndroidCreateSessionSubmitAckPayload,
    @SerialName("session_binding")
    val sessionBinding: AndroidCreateSessionBindingPayload? = null,
)

@Serializable
private data class AndroidCreateSessionSubmitAckPayload(
    @SerialName("ack_status")
    val ackStatus: String,
    @SerialName("queue_position")
    val queuePosition: Int? = null,
    val reason: String? = null,
    @SerialName("error_code")
    val errorCode: String? = null,
)

@Serializable
private data class AndroidCreateSessionBindingPayload(
    @SerialName("session_id")
    val sessionId: String,
    @SerialName("pc_id")
    val pcId: String,
    @SerialName("workspace_id")
    val workspaceId: String,
)

@Serializable
private data class AndroidCreateSessionErrorPayload(
    val status: String? = null,
    @SerialName("error_code")
    val errorCode: String? = null,
    @SerialName("error_message")
    val errorMessage: String? = null,
    val retryable: Boolean? = null,
    @SerialName("command_id")
    val commandId: String? = null,
)

private fun requireNormalizedText(value: String?, fieldName: String): String {
    return value?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: error("$fieldName is required.")
}
