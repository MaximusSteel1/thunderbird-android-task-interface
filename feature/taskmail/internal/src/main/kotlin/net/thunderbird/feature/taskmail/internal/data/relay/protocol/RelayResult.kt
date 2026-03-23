package net.thunderbird.feature.taskmail.internal.data.relay.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
internal data class RelayResult(
    @SerialName("message_type")
    val messageType: String = MESSAGE_TYPE,
    @SerialName("envelope_id")
    val envelopeId: String? = null,
    @SerialName("sent_at")
    val sentAt: String? = null,
    @SerialName("request_id")
    val requestId: String? = null,
    @SerialName("packet_id")
    val packetId: String? = null,
    @SerialName("command_type")
    val commandType: String? = null,
    @SerialName("receipt_id")
    val receiptId: String? = null,
    @SerialName("result_id")
    val resultId: String? = null,
    @SerialName("result_type")
    val resultType: String? = null,
    val status: String? = null,
    @SerialName("payload_schema")
    val payloadSchema: String? = null,
    val payload: JsonElement? = null,
    val related: JsonElement? = null,
) {
    companion object {
        const val MESSAGE_TYPE = "result"
    }
}
