package net.thunderbird.feature.taskmail.internal.data.relay.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class RelayPacketAck(
    @SerialName("message_type")
    val messageType: String = MESSAGE_TYPE,
    @SerialName("packet_id")
    val packetId: String,
    val accepted: Boolean,
    @SerialName("receipt_id")
    val receiptId: String,
    @SerialName("received_at")
    val receivedAt: String,
    @SerialName("transport_message_id")
    val transportMessageId: String? = null,
    @SerialName("error_code")
    val errorCode: String? = null,
    @SerialName("error_message")
    val errorMessage: String? = null,
) {
    companion object {
        const val MESSAGE_TYPE = "packet_ack"
    }
}
