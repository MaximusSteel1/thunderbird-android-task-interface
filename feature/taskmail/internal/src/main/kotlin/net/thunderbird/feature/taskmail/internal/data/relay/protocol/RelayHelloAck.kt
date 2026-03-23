package net.thunderbird.feature.taskmail.internal.data.relay.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class RelayHelloAck(
    @SerialName("message_type")
    val messageType: String,
    @SerialName("connection_id")
    val connectionId: String,
    @SerialName("server_time")
    val serverTime: String,
    @SerialName("heartbeat_seconds")
    val heartbeatSeconds: Int,
    @SerialName("transport_token_id")
    val transportTokenId: String? = null,
    @SerialName("accepted_payload_schemas")
    val acceptedPayloadSchemas: List<String> = emptyList(),
)
