package net.thunderbird.feature.taskmail.internal.data.relay.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

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
    @SerialName("final_status")
    val finalStatus: String? = null,
    @SerialName("result_scope")
    val resultScope: String? = null,
    @SerialName("payload_schema")
    val payloadSchema: String? = null,
    val payload: JsonElement? = null,
    @SerialName("structured_payload")
    val structuredPayload: RelayStructuredPayload? = null,
    @SerialName("effective_execution")
    val effectiveExecution: RelayEffectiveExecution? = null,
    val related: JsonElement? = null,
) {
    val terminalStatus: String?
        get() = finalStatus ?: status

    companion object {
        const val MESSAGE_TYPE = "result"
    }
}

@Serializable
internal data class RelayStructuredPayload(
    val kind: String? = null,
    val payload: JsonObject? = null,
)

@Serializable
internal data class RelayEffectiveExecution(
    val backend: String? = null,
    val profile: String? = null,
    val permission: String? = null,
    @SerialName("resolved_model")
    val resolvedModel: String? = null,
)
