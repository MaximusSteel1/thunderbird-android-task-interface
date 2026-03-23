package net.thunderbird.feature.taskmail.internal.data.relay.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
internal data class RelayCommand(
    @SerialName("message_type")
    val messageType: String = MESSAGE_TYPE,
    @SerialName("request_id")
    val requestId: String,
    @SerialName("packet_id")
    val packetId: String,
    @SerialName("command_type")
    val commandType: String,
    @SerialName("payload_schema")
    val payloadSchema: String,
    val trace: JsonObject,
    val payload: JsonObject,
    val related: JsonObject,
    @SerialName("sent_at")
    val sentAt: String,
) {
    companion object {
        const val MESSAGE_TYPE = "command"
    }
}
