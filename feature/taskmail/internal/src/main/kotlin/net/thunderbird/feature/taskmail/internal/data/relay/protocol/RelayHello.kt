package net.thunderbird.feature.taskmail.internal.data.relay.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class RelayHello(
    @SerialName("message_type")
    val messageType: String = MESSAGE_TYPE,
    @SerialName("client_id")
    val clientId: String,
    @SerialName("client_version")
    val clientVersion: String,
    @SerialName("transport_token_id")
    val transportTokenId: String,
    @SerialName("sent_at")
    val sentAt: String,
) {
    companion object {
        const val MESSAGE_TYPE = "hello"
    }
}
