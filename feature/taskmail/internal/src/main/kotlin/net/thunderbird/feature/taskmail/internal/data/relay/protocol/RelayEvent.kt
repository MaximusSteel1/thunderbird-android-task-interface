package net.thunderbird.feature.taskmail.internal.data.relay.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
internal data class RelayEvent(
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
    @SerialName("subscription_id")
    val subscriptionId: String? = null,
    @SerialName("event_type")
    val eventType: String,
    @SerialName("payload_schema")
    val payloadSchema: String? = null,
    val payload: JsonElement? = null,
) {
    companion object {
        const val MESSAGE_TYPE = "event"
    }
}
