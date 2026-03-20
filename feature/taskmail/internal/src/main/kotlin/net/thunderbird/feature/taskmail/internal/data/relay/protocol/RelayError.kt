package net.thunderbird.feature.taskmail.internal.data.relay.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class RelayError(
    @SerialName("message_type")
    val messageType: String,
    val code: String,
    val message: String,
    @SerialName("sent_at")
    val sentAt: String,
) {
    companion object {
        const val MESSAGE_TYPE = "error"
    }
}
