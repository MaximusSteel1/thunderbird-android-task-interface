package net.thunderbird.feature.taskmail.internal.data.relay

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.thunderbird.feature.taskmail.internal.data.debug.NoOpTaskMailProjectSyncDebugRecorder
import net.thunderbird.feature.taskmail.internal.data.debug.TaskMailProjectSyncDebugRecorder
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayPacket
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayPacketAck
import net.thunderbird.feature.taskmail.internal.domain.projectsync.TaskMailDirectProjectSyncResult
import net.thunderbird.feature.taskmail.internal.domain.projectsync.TaskMailDirectProjectSyncSender

private const val PROJECT_SYNC_SCHEMA_VERSION = "taskmail-bootstrap-control-contract-v1"
private const val DIRECT_ACTION_SYNC_PROJECT_FOLDERS = "sync_project_folders"
private const val ORIGIN_CLIENT = "android_taskmail"
private const val PACKET_ID_PREFIX = "android-taskmail:project-sync:"
private const val REQUEST_ID_PREFIX = "req_"
private const val DISPATCH_CHANNEL = "taskmail_android_direct"
private const val FALLBACK_POLICY_MAIL = "mail"
private const val DEFAULT_DIRECT_REJECTION_MESSAGE = "Relay rejected direct TaskMail project sync request."
private const val PROJECT_SYNC_PACKET_ACK_TIMEOUT_MILLIS = 30_000L
private val HARD_REJECTION_CODES = setOf(
    "invalid_payload",
    "validation_failed",
    "unauthorized",
)

internal class RelayTaskMailDirectProjectSyncSender(
    private val relayConnectionClient: RelayConnectionClient,
    private val debugRecorder: TaskMailProjectSyncDebugRecorder = NoOpTaskMailProjectSyncDebugRecorder,
    private val timestampProvider: () -> String = ::currentUtcTimestamp,
    private val requestIdFactory: () -> String = ::nextRequestId,
) : TaskMailDirectProjectSyncSender {

    override suspend fun send(accountUuid: String): TaskMailDirectProjectSyncResult {
        val requestId = requestIdFactory()
        debugRecorder.record(
            event = "project_sync_direct_packet_send_started",
            "requestId" to requestId,
        )

        return relayConnectionClient.sendPacket(
            packet = buildPacket(accountUuid, requestId),
            ackTimeoutMillis = PROJECT_SYNC_PACKET_ACK_TIMEOUT_MILLIS,
        ).fold(
            onSuccess = { packetAck ->
                debugRecorder.record(
                    event = "project_sync_direct_packet_ack_received",
                    "requestId" to requestId,
                    "packetId" to packetAck.packetId,
                    "accepted" to packetAck.accepted,
                    "receiptId" to packetAck.receiptId,
                    "transportMessageId" to packetAck.transportMessageId,
                    "errorCode" to packetAck.errorCode,
                    "errorMessage" to packetAck.errorMessage,
                )
                packetAck.toDirectProjectSyncResult(requestId)
            },
            onFailure = { error ->
                debugRecorder.record(
                    event = "project_sync_direct_packet_send_failed",
                    "requestId" to requestId,
                    "errorType" to error::class.simpleName,
                    "errorMessage" to error.message,
                )
                error.toDirectProjectSyncResult(requestId)
            },
        )
    }

    private fun buildPacket(
        accountUuid: String,
        requestId: String,
    ): RelayPacket {
        return RelayPacket(
            packetId = "$PACKET_ID_PREFIX$requestId",
            clientTraceId = requestId,
            taskRunPacket = buildTaskRunPacket(
                accountUuid = accountUuid,
                requestId = requestId,
            ),
            dispatchMetadata = buildDispatchMetadata(),
            sentAt = timestampProvider(),
        )
    }

    private fun buildTaskRunPacket(
        accountUuid: String,
        requestId: String,
    ) = buildJsonObject {
        put("schema_version", PROJECT_SYNC_SCHEMA_VERSION)
        put("action", DIRECT_ACTION_SYNC_PROJECT_FOLDERS)
        put("request_id", requestId)
        put(
            "origin",
            buildJsonObject {
                put("client", ORIGIN_CLIENT)
                put("sender_account_uuid", accountUuid)
            },
        )
        put(DIRECT_ACTION_SYNC_PROJECT_FOLDERS, buildJsonObject {})
    }

    private fun buildDispatchMetadata() = buildJsonObject {
        put("channel", DISPATCH_CHANNEL)
        put("schema_version", PROJECT_SYNC_SCHEMA_VERSION)
        put("action", DIRECT_ACTION_SYNC_PROJECT_FOLDERS)
        put("fallback_policy", FALLBACK_POLICY_MAIL)
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

private fun Throwable.toDirectProjectSyncResult(
    requestId: String,
): TaskMailDirectProjectSyncResult {
    return when (this) {
        is RelayServerException -> {
            if (code in HARD_REJECTION_CODES) {
                TaskMailDirectProjectSyncResult.Rejected(
                    errorMessage = message,
                    requestId = requestId,
                )
            } else {
                TaskMailDirectProjectSyncResult.FallbackToMail(
                    detailMessage = message,
                    requestId = requestId,
                )
            }
        }

        else -> TaskMailDirectProjectSyncResult.FallbackToMail(
            detailMessage = message,
            requestId = requestId,
        )
    }
}

private fun RelayPacketAck.toDirectProjectSyncResult(
    requestId: String,
): TaskMailDirectProjectSyncResult {
    return if (accepted) {
        TaskMailDirectProjectSyncResult.Accepted(
            requestId = requestId,
            receiptId = receiptId,
            transportMessageId = transportMessageId,
        )
    } else {
        val relayErrorCode = errorCode.normalizedRelayErrorCode() ?: errorMessage.extractRelayErrorCode()
        if (relayErrorCode in HARD_REJECTION_CODES) {
            TaskMailDirectProjectSyncResult.Rejected(
                errorMessage = errorMessage?.takeIf(String::isNotBlank) ?: DEFAULT_DIRECT_REJECTION_MESSAGE,
                requestId = requestId,
                receiptId = receiptId,
                transportMessageId = transportMessageId,
            )
        } else {
            TaskMailDirectProjectSyncResult.FallbackToMail(
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
