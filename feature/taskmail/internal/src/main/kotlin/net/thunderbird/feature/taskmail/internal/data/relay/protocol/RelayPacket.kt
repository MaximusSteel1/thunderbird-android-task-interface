package net.thunderbird.feature.taskmail.internal.data.relay.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
internal data class RelayPacket(
    @SerialName("message_type")
    val messageType: String = MESSAGE_TYPE,
    @SerialName("packet_id")
    val packetId: String,
    @SerialName("client_trace_id")
    val clientTraceId: String,
    @SerialName("task_run_packet")
    val taskRunPacket: JsonObject,
    @SerialName("dispatch_metadata")
    val dispatchMetadata: JsonObject,
    @SerialName("sent_at")
    val sentAt: String,
) {
    companion object {
        const val MESSAGE_TYPE = "packet"
    }
}
