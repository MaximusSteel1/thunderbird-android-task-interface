package net.thunderbird.feature.taskmail.internal.data.controlplane.protocol

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayCommandAck
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayEffectiveExecution
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayEvent
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayResult
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayStructuredPayload

// Compatibility bridge while Android still receives relay-era DTOs.
internal fun RelayCommandAck.toControlPlaneCommandAck(): ControlPlaneCommandAck {
    return ControlPlaneCommandAck(
        commandId = requestId.orNonBlank()
            ?: packetId.orNonBlank()
            ?: receiptId,
        ackStatus = ackStatus.orNonBlank()
            ?: if (isAcceptedLike) "accepted" else "rejected",
        queuePosition = related.asJsonObject().longOrNull("queue_position"),
        reason = related.asJsonObject().stringOrNull("reason")
            ?: errorMessage.orNonBlank(),
        errorCode = errorCode.orNonBlank(),
        errorMessage = errorMessage.orNonBlank(),
    )
}

internal fun RelayEvent.toControlPlaneEvent(): ControlPlaneEvent {
    return ControlPlaneEvent(
        eventId = eventId.orNonBlank()
            ?: envelopeId.orNonBlank()
            ?: buildCompatibilityId("event", requestId, packetId, eventType, sentAt),
        commandId = requestId.orNonBlank()
            ?: packetId.orNonBlank()
            ?: buildCompatibilityId("command", eventType, sentAt),
        workspaceId = payload.asJsonObject().stringOrNull("workspace_id")
            ?: related.asJsonObject().stringOrNull("workspace_id"),
        sessionId = payload.asJsonObject().stringOrNull("session_id")
            ?: related.asJsonObject().stringOrNull("session_id"),
        runId = payload.asJsonObject().stringOrNull("run_id")
            ?: related.asJsonObject().stringOrNull("run_id"),
        eventType = eventType,
        payload = payload.asJsonObject(),
        emittedAt = sentAt.orNonBlank(),
    )
}

internal fun RelayResult.toControlPlaneResult(): ControlPlaneResult {
    val commandId = requestId.orNonBlank()
        ?: packetId.orNonBlank()
        ?: resultId.orNonBlank()
        ?: receiptId.orNonBlank()
        ?: "legacy_command"
    val compatibilityPayload = payload.asJsonObject()
    val canonicalStructuredPayload = structuredPayload?.toControlPlaneStructuredPayload()
        ?: ControlPlaneStructuredPayload(
            kind = resultType.orNonBlank() ?: "legacy_result",
            fields = compatibilityPayload,
        )

    return ControlPlaneResult(
        resultId = resultId.orNonBlank()
            ?: receiptId.orNonBlank()
            ?: buildCompatibilityId("result", commandId, terminalStatus, sentAt),
        commandId = commandId,
        workspaceId = compatibilityPayload.stringOrNull("workspace_id")
            ?: related.asJsonObject().stringOrNull("workspace_id"),
        sessionId = compatibilityPayload.stringOrNull("session_id")
            ?: canonicalStructuredPayload.fields.stringOrNull("session_id")
            ?: related.asJsonObject().stringOrNull("session_id"),
        runId = compatibilityPayload.stringOrNull("run_id")
            ?: related.asJsonObject().stringOrNull("run_id"),
        finalStatus = terminalStatus.orNonBlank() ?: "failed",
        summary = compatibilityPayload.stringOrNull("summary")
            ?: canonicalStructuredPayload.fields.stringOrNull("summary")
            ?: resultType.orNonBlank()
            ?: "Legacy relay result",
        effectiveExecution = effectiveExecution?.toControlPlaneExecutionPolicy(),
        structuredPayload = canonicalStructuredPayload,
        generatedAt = sentAt.orNonBlank(),
    )
}

internal fun RelayStructuredPayload.toControlPlaneStructuredPayload(): ControlPlaneStructuredPayload {
    return ControlPlaneStructuredPayload(
        kind = kind.orNonBlank() ?: "legacy_payload",
        fields = payload ?: EMPTY_JSON_OBJECT,
    )
}

internal fun RelayEffectiveExecution.toControlPlaneExecutionPolicy(): ControlPlaneExecutionPolicy {
    return ControlPlaneExecutionPolicy(
        backend = backend.orNonBlank(),
        profile = profile.orNonBlank(),
        permission = permission.orNonBlank(),
        resolvedModel = resolvedModel.orNonBlank(),
    )
}

private fun buildCompatibilityId(
    prefix: String,
    vararg parts: String?,
): String {
    val normalized = parts.mapNotNull { value ->
        value.orNonBlank()
            ?.replace(":", "_")
            ?.replace("/", "_")
    }

    return (listOf(prefix) + normalized)
        .joinToString(separator = "_")
        .ifBlank { prefix }
}

private fun String?.orNonBlank(): String? {
    return this?.trim()?.takeIf(String::isNotEmpty)
}

private fun JsonElement?.asJsonObject(): JsonObject {
    return when (this) {
        is JsonObject -> this
        else -> EMPTY_JSON_OBJECT
    }
}

private fun JsonObject.stringOrNull(key: String): String? {
    return this[key]
        ?.let { element -> runCatching { element.jsonPrimitive.content }.getOrNull() }
        ?.trim()
        ?.takeIf(String::isNotEmpty)
}

private fun JsonObject.longOrNull(key: String): Long? {
    return this[key]
        ?.let { element -> runCatching { element.jsonPrimitive.content }.getOrNull() }
        ?.toLongOrNull()
}
