package net.thunderbird.feature.taskmail.internal.data.facade

import com.fsck.k9.message.Attachment
import java.io.File
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit
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
import net.thunderbird.feature.taskmail.internal.data.controlplane.protocol.ControlPlaneCommandAck
import net.thunderbird.feature.taskmail.internal.domain.model.RelayTransportConfig
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionControlPlaneSnapshot
import net.thunderbird.feature.taskmail.internal.domain.newtask.toCanonicalWireValue
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskTransportConfigRepository
import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailDirectSessionActionRequest
import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailDirectSessionActionResult
import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailDirectSessionActionSender
import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailDirectSessionActionType
import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailSessionActionAckStatus
import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailSessionActionTargetIdentity
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private const val TAG = "TaskMailSessionActionFacade"
private const val CONFIG_REQUIRED_MESSAGE = "Relay host, port, and Android app token are required."
private const val DEFAULT_REJECTED_MESSAGE = "Session action request was rejected."
private const val REQUEST_ID_PREFIX = "req_"

internal class OkHttpTaskMailSessionActionFacadeSender(
    private val transportConfigRepository: TaskTransportConfigRepository,
    private val logger: Logger,
    private val replyAttachmentResolver: TaskMailReplyAttachmentResolver,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val requestIdFactory: () -> String = ::nextRequestId,
    okHttpClient: OkHttpClient? = null,
    private val json: Json = Json {
        encodeDefaults = false
        ignoreUnknownKeys = true
        explicitNulls = false
    },
) : TaskMailDirectSessionActionSender {
    private val client = okHttpClient ?: OkHttpClient.Builder()
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    override suspend fun send(
        request: TaskMailDirectSessionActionRequest,
    ): TaskMailDirectSessionActionResult {
        val config = transportConfigRepository.getRelayTransportConfig().normalized()
        if (!config.isAndroidSessionActionConfigured()) {
            return TaskMailDirectSessionActionResult.Rejected(
                errorMessage = CONFIG_REQUIRED_MESSAGE,
            )
        }

        return withContext(ioDispatcher) {
            runCatching {
                executeSessionActionRequest(
                    config = config,
                    request = request,
                )
            }.onFailure { error ->
                logger.error(TAG, error) {
                    "Failed to submit TaskMail session action via Android facade."
                }
            }.getOrElse { error ->
                TaskMailDirectSessionActionResult.Rejected(
                    errorMessage = error.message?.trim().takeUnless(String?::isNullOrEmpty)
                        ?: "Session action request failed.",
                )
            }
        }
    }

    private suspend fun executeSessionActionRequest(
        config: RelayTransportConfig,
        request: TaskMailDirectSessionActionRequest,
    ): TaskMailDirectSessionActionResult {
        val requestId = requestIdFactory()
        val requestPayload = request.toFacadeRequest(requestId)
        val requestBody = json.encodeToString(requestPayload)
            .toRequestBody(JSON_MEDIA_TYPE)
        val httpRequest = Request.Builder()
            .url(config.androidSessionActionUrl())
            .header("Authorization", "Bearer ${config.androidAppToken}")
            .header("Accept", "application/json")
            .post(requestBody)
            .build()

        client.newCall(httpRequest).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                val errorPayload = parseErrorPayload(body)
                return TaskMailDirectSessionActionResult.Rejected(
                    errorMessage = errorPayload.toUserMessage(response.code),
                    errorCode = errorPayload.errorCode?.trim()?.takeIf(String::isNotEmpty),
                    requestId = requestId,
                    receiptId = errorPayload.commandId?.trim()?.takeIf(String::isNotEmpty),
                )
            }

            val payload = json.decodeFromString<AndroidSessionActionResponsePayload>(body)
            return when (val ackStatus = payload.submitAck.ackStatus.toDomainAckStatus()) {
                TaskMailSessionActionAckStatus.Accepted,
                TaskMailSessionActionAckStatus.AcceptedButQueued,
                -> TaskMailDirectSessionActionResult.Accepted(
                    actionType = request.actionType,
                    requestId = requestId,
                    receiptId = payload.commandId,
                    controlPlaneSnapshot = TaskSessionControlPlaneSnapshot(
                        commandAck = payload.toControlPlaneCommandAck(),
                    ),
                    ackStatus = ackStatus,
                    targetIdentity = payload.targetSessionIdentity?.toDomainTargetIdentity(),
                )

                TaskMailSessionActionAckStatus.Rejected -> TaskMailDirectSessionActionResult.Rejected(
                    errorMessage = payload.submitAck.reason
                        ?.trim()
                        .takeUnless(String?::isNullOrEmpty)
                        ?: DEFAULT_REJECTED_MESSAGE,
                    errorCode = payload.submitAck.errorCode?.trim()?.takeIf(String::isNotEmpty),
                    requestId = requestId,
                    receiptId = payload.commandId,
                )
            }
        }
    }

    private fun parseErrorPayload(body: String): AndroidSessionActionErrorPayload {
        return runCatching {
            json.decodeFromString<AndroidSessionActionErrorPayload>(body)
        }.getOrElse {
            AndroidSessionActionErrorPayload(
                errorMessage = body.trim().takeIf(String::isNotEmpty),
            )
        }
    }

    private suspend fun TaskMailDirectSessionActionRequest.toFacadeRequest(
        requestId: String,
    ): AndroidSessionActionRequestPayload {
        val targetPayload = AndroidSessionActionTargetPayload(
            sessionId = requireNormalizedText(target.sessionId, "target.sessionId"),
            workspaceId = target.workspaceId?.trim()?.takeIf(String::isNotEmpty),
            threadId = target.threadId?.trim()?.takeIf(String::isNotEmpty),
        )

        return when (this) {
            is TaskMailDirectSessionActionRequest.Reply -> AndroidSessionActionRequestPayload(
                requestId = requestId,
                action = actionType.wireValue,
                target = targetPayload,
                reply = AndroidSessionActionReplyPayload(
                    replyText = requireNormalizedText(replyText, "reply.replyText"),
                    permission = permission.toCanonicalWireValue(),
                ),
            )

            is TaskMailDirectSessionActionRequest.Status -> AndroidSessionActionRequestPayload(
                requestId = requestId,
                action = actionType.wireValue,
                target = targetPayload,
                status = AndroidSessionActionEmptyPayload(),
            )

            is TaskMailDirectSessionActionRequest.Answers -> AndroidSessionActionRequestPayload(
                requestId = requestId,
                action = actionType.wireValue,
                target = targetPayload,
                answers = AndroidSessionActionAnswersPayload(
                    questionAnswers = questionAnswers.map { answer ->
                        AndroidSessionActionQuestionAnswerPayload(
                            questionId = requireNormalizedText(answer.questionId, "answers.questionAnswers.questionId"),
                            value = requireNormalizedText(answer.value, "answers.questionAnswers.value"),
                        )
                    },
                    permission = permission.toCanonicalWireValue(),
                ),
            )

            is TaskMailDirectSessionActionRequest.Pause -> AndroidSessionActionRequestPayload(
                requestId = requestId,
                action = actionType.wireValue,
                target = targetPayload,
                pause = AndroidSessionActionEmptyPayload(),
            )

            is TaskMailDirectSessionActionRequest.Resume -> AndroidSessionActionRequestPayload(
                requestId = requestId,
                action = actionType.wireValue,
                target = targetPayload,
                resume = AndroidSessionActionEmptyPayload(),
            )

            is TaskMailDirectSessionActionRequest.Kill -> AndroidSessionActionRequestPayload(
                requestId = requestId,
                action = actionType.wireValue,
                target = targetPayload,
                kill = AndroidSessionActionEmptyPayload(),
            )

            is TaskMailDirectSessionActionRequest.End -> AndroidSessionActionRequestPayload(
                requestId = requestId,
                action = actionType.wireValue,
                target = targetPayload,
                end = AndroidSessionActionEmptyPayload(),
            )

            is TaskMailDirectSessionActionRequest.AttachmentContinuation -> AndroidSessionActionRequestPayload(
                requestId = requestId,
                action = actionType.wireValue,
                target = targetPayload,
                attachmentContinuation = AndroidSessionActionAttachmentContinuationPayload(
                    attachments = buildAttachmentPayloads(attachments),
                    replyText = replyText.trim().takeIf(String::isNotEmpty),
                    permission = permission.toCanonicalWireValue(),
                ),
            )
        }
    }

    private suspend fun buildAttachmentPayloads(
        attachments: List<net.thunderbird.feature.taskmail.internal.domain.model.TaskReplyAttachment>,
    ): List<AndroidSessionActionAttachmentPayload> {
        return replyAttachmentResolver.buildOutgoingAttachments(attachments)
            .mapCatching { preparedAttachments ->
                preparedAttachments.map { attachment -> attachment.toFacadeAttachmentPayload() }
            }
            .getOrElse { error ->
                throw IllegalStateException(
                    error.message?.trim().takeUnless(String?::isNullOrEmpty)
                        ?: "Failed to prepare reply attachments.",
                    error,
                )
            }
    }

    private fun Attachment.toFacadeAttachmentPayload(): AndroidSessionActionAttachmentPayload {
        val filePath = fileName?.trim().takeUnless(String?::isNullOrEmpty)
            ?: error("Prepared attachment is missing fileName.")
        val file = File(filePath)
        if (!file.exists() || !file.isFile) {
            error("Prepared attachment file is unavailable: $filePath")
        }
        val contentBytes = file.readBytes()
        return AndroidSessionActionAttachmentPayload(
            name = name?.trim().takeUnless(String?::isNullOrEmpty) ?: file.name,
            contentType = contentType?.trim().takeUnless(String?::isNullOrEmpty)
                ?: "application/octet-stream",
            sizeBytes = size ?: contentBytes.size.toLong(),
            contentBytesBase64 = Base64.getEncoder().encodeToString(contentBytes),
        )
    }

    private fun String.toDomainAckStatus(): TaskMailSessionActionAckStatus {
        return when (trim()) {
            TaskMailSessionActionAckStatus.Accepted.wireValue -> TaskMailSessionActionAckStatus.Accepted
            TaskMailSessionActionAckStatus.AcceptedButQueued.wireValue -> {
                TaskMailSessionActionAckStatus.AcceptedButQueued
            }
            TaskMailSessionActionAckStatus.Rejected.wireValue -> TaskMailSessionActionAckStatus.Rejected
            else -> error("Unknown session-action ack status: $this")
        }
    }

    private fun AndroidSessionActionTargetIdentityPayload.toDomainTargetIdentity():
        TaskMailSessionActionTargetIdentity? {
        val normalizedWorkspaceId = workspaceId?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val normalizedSessionId = sessionId?.trim()?.takeIf(String::isNotEmpty) ?: return null

        return TaskMailSessionActionTargetIdentity(
            pcId = pcId?.trim()?.takeIf(String::isNotEmpty),
            workspaceId = normalizedWorkspaceId,
            sessionId = normalizedSessionId,
            threadId = threadId?.trim()?.takeIf(String::isNotEmpty),
        )
    }

    private fun AndroidSessionActionResponsePayload.toControlPlaneCommandAck(): ControlPlaneCommandAck {
        return ControlPlaneCommandAck(
            commandId = commandId,
            ackStatus = submitAck.ackStatus,
            queuePosition = submitAck.queuePosition?.toLong(),
            reason = submitAck.reason?.trim()?.takeIf(String::isNotEmpty),
            errorCode = submitAck.errorCode?.trim()?.takeIf(String::isNotEmpty),
            errorMessage = submitAck.reason?.trim()?.takeIf(String::isNotEmpty),
        )
    }

    private fun AndroidSessionActionErrorPayload.toUserMessage(statusCode: Int): String {
        val baseMessage = errorMessage?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: "Session action request failed with HTTP $statusCode."
        val normalizedCommandId = commandId?.trim()?.takeIf(String::isNotEmpty)
        return if (normalizedCommandId != null && !baseMessage.contains(normalizedCommandId)) {
            "$baseMessage (command_id=$normalizedCommandId)"
        } else {
            baseMessage
        }
    }

    private companion object {
        const val CALL_TIMEOUT_SECONDS = 30L
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun nextRequestId(): String {
            return REQUEST_ID_PREFIX + UUID.randomUUID()
                .toString()
                .replace("-", "")
        }
    }
}

@Serializable
private data class AndroidSessionActionRequestPayload(
    @SerialName("request_id")
    val requestId: String,
    val action: String,
    val target: AndroidSessionActionTargetPayload,
    val reply: AndroidSessionActionReplyPayload? = null,
    val status: AndroidSessionActionEmptyPayload? = null,
    val answers: AndroidSessionActionAnswersPayload? = null,
    val pause: AndroidSessionActionEmptyPayload? = null,
    val resume: AndroidSessionActionEmptyPayload? = null,
    val kill: AndroidSessionActionEmptyPayload? = null,
    val end: AndroidSessionActionEmptyPayload? = null,
    @SerialName("attachment_continuation")
    val attachmentContinuation: AndroidSessionActionAttachmentContinuationPayload? = null,
)

@Serializable
private data class AndroidSessionActionTargetPayload(
    @SerialName("session_id")
    val sessionId: String,
    @SerialName("workspace_id")
    val workspaceId: String? = null,
    @SerialName("thread_id")
    val threadId: String? = null,
)

@Serializable
private data class AndroidSessionActionReplyPayload(
    @SerialName("reply_text")
    val replyText: String,
    val permission: String,
)

@Serializable
private class AndroidSessionActionEmptyPayload

@Serializable
private data class AndroidSessionActionAnswersPayload(
    @SerialName("question_answers")
    val questionAnswers: List<AndroidSessionActionQuestionAnswerPayload>,
    val permission: String,
)

@Serializable
private data class AndroidSessionActionQuestionAnswerPayload(
    @SerialName("question_id")
    val questionId: String,
    val value: String,
)

@Serializable
private data class AndroidSessionActionAttachmentContinuationPayload(
    val attachments: List<AndroidSessionActionAttachmentPayload>,
    @SerialName("reply_text")
    val replyText: String? = null,
    val permission: String,
)

@Serializable
private data class AndroidSessionActionAttachmentPayload(
    val name: String,
    @SerialName("content_type")
    val contentType: String,
    @SerialName("size_bytes")
    val sizeBytes: Long,
    @SerialName("content_bytes_b64")
    val contentBytesBase64: String,
)

@Serializable
private data class AndroidSessionActionResponsePayload(
    @SerialName("schema_version")
    val schemaVersion: String,
    val status: String,
    @SerialName("command_id")
    val commandId: String,
    @SerialName("submit_ack")
    val submitAck: AndroidSessionActionSubmitAckPayload,
    @SerialName("target_session_identity")
    val targetSessionIdentity: AndroidSessionActionTargetIdentityPayload? = null,
)

@Serializable
private data class AndroidSessionActionSubmitAckPayload(
    @SerialName("ack_status")
    val ackStatus: String,
    @SerialName("queue_position")
    val queuePosition: Int? = null,
    val reason: String? = null,
    @SerialName("error_code")
    val errorCode: String? = null,
)

@Serializable
private data class AndroidSessionActionTargetIdentityPayload(
    @SerialName("pc_id")
    val pcId: String? = null,
    @SerialName("workspace_id")
    val workspaceId: String? = null,
    @SerialName("session_id")
    val sessionId: String? = null,
    @SerialName("thread_id")
    val threadId: String? = null,
)

@Serializable
private data class AndroidSessionActionErrorPayload(
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
