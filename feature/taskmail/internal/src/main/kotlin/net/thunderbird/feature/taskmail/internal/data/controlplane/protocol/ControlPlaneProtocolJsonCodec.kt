package net.thunderbird.feature.taskmail.internal.data.controlplane.protocol

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal class ControlPlaneProtocolJsonCodec(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    },
) {

    fun encodePcHello(message: ControlPlanePcHelloMessage): String {
        return json.encodeToString(ControlPlanePcHelloMessage.serializer(), message)
    }

    fun encodeWorkspaceSnapshot(message: ControlPlaneWorkspaceSnapshotMessage): String {
        return json.encodeToString(ControlPlaneWorkspaceSnapshotMessage.serializer(), message)
    }

    fun encodeCommandDispatch(message: ControlPlaneCommandDispatchMessage): String {
        return json.encodeToString(ControlPlaneCommandDispatchMessage.serializer(), message)
    }

    fun encodeCommandAck(message: ControlPlaneCommandAckMessage): String {
        return json.encodeToString(ControlPlaneCommandAckMessage.serializer(), message)
    }

    fun encodeEvent(message: ControlPlaneEventMessage): String {
        return json.encodeToString(ControlPlaneEventMessage.serializer(), message)
    }

    fun encodeOutputChunk(message: ControlPlaneOutputChunkMessage): String {
        return json.encodeToString(ControlPlaneOutputChunkMessage.serializer(), message)
    }

    fun encodeResult(message: ControlPlaneResultMessage): String {
        return json.encodeToString(ControlPlaneResultMessage.serializer(), message)
    }

    fun encodeArtifactManifest(message: ControlPlaneArtifactManifestMessage): String {
        return json.encodeToString(ControlPlaneArtifactManifestMessage.serializer(), message)
    }

    fun decodeMessage(payload: String): ControlPlaneMessage {
        val type = json.parseToJsonElement(payload)
            .jsonObject["type"]
            ?.jsonPrimitive
            ?.content
            .orEmpty()

        return when (type) {
            ControlPlanePcHelloMessage.TYPE -> json.decodeFromString(
                ControlPlanePcHelloMessage.serializer(),
                payload,
            )

            ControlPlaneWorkspaceSnapshotMessage.TYPE -> json.decodeFromString(
                ControlPlaneWorkspaceSnapshotMessage.serializer(),
                payload,
            )

            ControlPlaneCommandDispatchMessage.TYPE -> json.decodeFromString(
                ControlPlaneCommandDispatchMessage.serializer(),
                payload,
            )

            ControlPlaneCommandAckMessage.TYPE -> json.decodeFromString(
                ControlPlaneCommandAckMessage.serializer(),
                payload,
            )

            ControlPlaneEventMessage.TYPE -> json.decodeFromString(
                ControlPlaneEventMessage.serializer(),
                payload,
            )

            ControlPlaneOutputChunkMessage.TYPE -> json.decodeFromString(
                ControlPlaneOutputChunkMessage.serializer(),
                payload,
            )

            ControlPlaneResultMessage.TYPE -> json.decodeFromString(
                ControlPlaneResultMessage.serializer(),
                payload,
            )

            ControlPlaneArtifactManifestMessage.TYPE -> json.decodeFromString(
                ControlPlaneArtifactManifestMessage.serializer(),
                payload,
            )

            else -> throw SerializationException("Unsupported control-plane message type: $type")
        }
    }
}
