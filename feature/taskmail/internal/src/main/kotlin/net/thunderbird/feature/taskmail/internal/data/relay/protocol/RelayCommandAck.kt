package net.thunderbird.feature.taskmail.internal.data.relay.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
internal data class RelayCommandAck(
    @SerialName("message_type")
    val messageType: String = MESSAGE_TYPE,
    @SerialName("request_id")
    val requestId: String? = null,
    @SerialName("packet_id")
    val packetId: String? = null,
    @SerialName("command_type")
    val commandType: String? = null,
    @SerialName("payload_schema")
    val payloadSchema: String? = null,
    val accepted: Boolean,
    @SerialName("ack_status")
    val ackStatus: String? = null,
    @SerialName("receipt_id")
    val receiptId: String,
    @SerialName("received_at")
    val receivedAt: String,
    @SerialName("transport_message_id")
    val transportMessageId: String? = null,
    val related: JsonElement? = null,
    @SerialName("error_code")
    val errorCode: String? = null,
    @SerialName("error_message")
    val errorMessage: String? = null,
) {
    val isAcceptedLike: Boolean
        get() = when (ackStatus?.trim()?.lowercase()) {
            "accepted",
            "accepted_but_queued",
            -> true

            "rejected" -> false
            else -> accepted
        }

    companion object {
        const val MESSAGE_TYPE = "command_ack"
    }
}
