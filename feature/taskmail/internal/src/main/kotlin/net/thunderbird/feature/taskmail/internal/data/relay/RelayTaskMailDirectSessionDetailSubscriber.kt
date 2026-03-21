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

private const val PHASE3_SCHEMA_VERSION = "phase3-direct-inbound-wire-v1"
private const val DIRECT_ACTION_SUBSCRIBE_SESSION_DETAIL = "subscribe_session_detail"
private const val ORIGIN_CLIENT = "android_taskmail"
private const val PACKET_ID_PREFIX = "android-taskmail:detail-subscribe:"
private const val REQUEST_ID_PREFIX = "req_"
private const val DISPATCH_CHANNEL = "taskmail_android_direct"

internal class RelayTaskMailDirectSessionDetailSubscriber(
    private val relayConnectionClient: RelayConnectionClient,
    private val timestampProvider: () -> String = ::currentUtcTimestamp,
    private val requestIdFactory: () -> String = ::nextRequestId,
) {
    suspend fun subscribe(subscription: RelaySessionDetailSubscription): Result<RelayPacketAck> {
        val requestId = requestIdFactory()
        return relayConnectionClient.sendPacket(
            RelayPacket(
                packetId = "$PACKET_ID_PREFIX$requestId",
                clientTraceId = requestId,
                taskRunPacket = buildTaskRunPacket(
                    requestId = requestId,
                    subscription = subscription,
                ),
                dispatchMetadata = buildDispatchMetadata(),
                sentAt = timestampProvider(),
            ),
        )
    }

    private fun buildTaskRunPacket(
        requestId: String,
        subscription: RelaySessionDetailSubscription,
    ) = buildJsonObject {
        put("schema_version", PHASE3_SCHEMA_VERSION)
        put("action", DIRECT_ACTION_SUBSCRIBE_SESSION_DETAIL)
        put("request_id", requestId)
        put(
            "origin",
            buildJsonObject {
                put("client", ORIGIN_CLIENT)
            },
        )
        put(
            "subscription",
            buildJsonObject {
                subscription.workspaceId?.takeIf(String::isNotBlank)?.let { put("workspace_id", it) }
                subscription.repoPath?.takeIf(String::isNotBlank)?.let { put("repo_path", it) }
                subscription.workdir?.takeIf(String::isNotBlank)?.let { put("workdir", it) }
                subscription.sessionId?.takeIf(String::isNotBlank)?.let { put("session_id", it) }
                put("thread_id", subscription.threadId)
                subscription.lastKnownSequence?.takeIf { it > 0 }?.let { put("last_known_sequence", it) }
                put("reason", subscription.reason)
            },
        )
    }

    private fun buildDispatchMetadata() = buildJsonObject {
        put("channel", DISPATCH_CHANNEL)
        put("schema_version", PHASE3_SCHEMA_VERSION)
        put("action", DIRECT_ACTION_SUBSCRIBE_SESSION_DETAIL)
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

internal data class RelaySessionDetailSubscription(
    val workspaceId: String? = null,
    val repoPath: String? = null,
    val workdir: String? = null,
    val sessionId: String? = null,
    val threadId: String,
    val lastKnownSequence: Long? = null,
    val reason: String,
)
