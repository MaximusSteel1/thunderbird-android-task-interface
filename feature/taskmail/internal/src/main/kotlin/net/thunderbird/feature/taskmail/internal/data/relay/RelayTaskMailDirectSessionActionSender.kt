package net.thunderbird.feature.taskmail.internal.data.relay

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayPacket
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayPacketAck
import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailDirectSessionActionRequest
import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailDirectSessionActionResult
import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailDirectSessionActionSender
import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailDirectSessionActionType

private const val SESSION_ACTION_SCHEMA_VERSION = "post-creation-session-action-contract-v1"
private const val ORIGIN_CLIENT = "android_taskmail"
private const val PACKET_ID_PREFIX = "android-taskmail:session-action:"
private const val REQUEST_ID_PREFIX = "req_"
private const val CURRENT_SESSION_SCOPE = "current_session"
private const val DISPATCH_CHANNEL = "taskmail_android_direct"
private const val FALLBACK_POLICY_NONE = "none"
private const val DEFAULT_DIRECT_REJECTION_MESSAGE = "Relay rejected direct session action request."
private val HARD_REJECTION_CODES = setOf(
    "invalid_payload",
    "validation_failed",
    "unauthorized",
    "session_identity_unresolved",
    "session_identity_mismatch",
    "current_session_only_violation",
    "paused_resume_not_supported",
    "answer_flow_not_supported",
)

internal class RelayTaskMailDirectSessionActionSender(
    private val relayConnectionClient: RelayConnectionClient,
    private val timestampProvider: () -> String = ::currentUtcTimestamp,
    private val requestIdFactory: () -> String = ::nextRequestId,
) : TaskMailDirectSessionActionSender {

    override suspend fun send(
        request: TaskMailDirectSessionActionRequest,
    ): TaskMailDirectSessionActionResult {
        val requestId = requestIdFactory()
        return relayConnectionClient.sendPacket(buildPacket(request, requestId)).fold(
            onSuccess = { packetAck ->
                packetAck.toDirectSessionActionResult(
                    requestId = requestId,
                    actionType = request.actionType,
                )
            },
            onFailure = { error ->
                error.toDirectSessionActionResult(requestId = requestId)
            },
        )
    }

    private fun buildPacket(
        request: TaskMailDirectSessionActionRequest,
        requestId: String,
    ): RelayPacket {
        return RelayPacket(
            packetId = "$PACKET_ID_PREFIX$requestId",
            clientTraceId = requestId,
            taskRunPacket = buildTaskRunPacket(request, requestId),
            dispatchMetadata = buildDispatchMetadata(request.actionType),
            sentAt = timestampProvider(),
        )
    }

    private fun buildTaskRunPacket(
        request: TaskMailDirectSessionActionRequest,
        requestId: String,
    ) = buildJsonObject {
        put("schema_version", SESSION_ACTION_SCHEMA_VERSION)
        put("action", request.actionType.wireValue)
        put("request_id", requestId)
        put(
            "origin",
            buildJsonObject {
                put("client", ORIGIN_CLIENT)
            },
        )
        put(
            "target",
            buildJsonObject {
                put("scope", CURRENT_SESSION_SCOPE)
                put("workspace_id", request.target.workspaceId)
                put("session_id", request.target.sessionId)
                request.target.threadId?.takeIf(String::isNotBlank)?.let { put("thread_id", it) }
            },
        )
        when (request) {
            is TaskMailDirectSessionActionRequest.Reply -> {
                put(
                    "reply",
                    buildJsonObject {
                        put("reply_text", request.replyText)
                    },
                )
            }

            is TaskMailDirectSessionActionRequest.Status -> {
                put("status", buildJsonObject {})
            }
        }
    }

    private fun buildDispatchMetadata(
        actionType: TaskMailDirectSessionActionType,
    ) = buildJsonObject {
        put("channel", DISPATCH_CHANNEL)
        put("schema_version", SESSION_ACTION_SCHEMA_VERSION)
        put("action", actionType.wireValue)
        put("fallback_policy", FALLBACK_POLICY_NONE)
    }

    private companion object {
        fun currentUtcTimestamp(): String {
            return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date())
        }

        fun nextRequestId(): String {
            return REQUEST_ID_PREFIX + UUID.randomUUID()
                .toString()
                .replace("-", "")
        }
    }
}

private fun Throwable.toDirectSessionActionResult(
    requestId: String,
): TaskMailDirectSessionActionResult {
    return when (this) {
        is RelayServerException -> {
            if (code in HARD_REJECTION_CODES) {
                TaskMailDirectSessionActionResult.Rejected(
                    errorMessage = message,
                    requestId = requestId,
                )
            } else {
                TaskMailDirectSessionActionResult.FallbackToMail(
                    detailMessage = message,
                    requestId = requestId,
                )
            }
        }

        else -> TaskMailDirectSessionActionResult.FallbackToMail(
            detailMessage = message,
            requestId = requestId,
        )
    }
}

private fun RelayPacketAck.toDirectSessionActionResult(
    requestId: String,
    actionType: TaskMailDirectSessionActionType,
): TaskMailDirectSessionActionResult {
    return if (accepted) {
        TaskMailDirectSessionActionResult.Accepted(
            actionType = actionType,
            requestId = requestId,
            receiptId = receiptId,
            transportMessageId = transportMessageId,
        )
    } else {
        val relayErrorCode = errorCode.normalizedRelayErrorCode() ?: errorMessage.extractRelayErrorCode()
        if (relayErrorCode in HARD_REJECTION_CODES) {
            TaskMailDirectSessionActionResult.Rejected(
                errorMessage = errorMessage?.takeIf(String::isNotBlank) ?: DEFAULT_DIRECT_REJECTION_MESSAGE,
                requestId = requestId,
                receiptId = receiptId,
                transportMessageId = transportMessageId,
            )
        } else {
            TaskMailDirectSessionActionResult.FallbackToMail(
                detailMessage = errorMessage,
                requestId = requestId,
                receiptId = receiptId,
                transportMessageId = transportMessageId,
            )
        }
    }
}

private fun String?.normalizedRelayErrorCode(): String? {
    return this?.trim()
        ?.lowercase(Locale.US)
        ?.takeIf(String::isNotEmpty)
}

private fun String?.extractRelayErrorCode(): String? {
    val message = this?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: return null

    return message
        .substringBefore(':')
        .trim()
        .lowercase(Locale.US)
        .takeIf { it in HARD_REJECTION_CODES }
}
