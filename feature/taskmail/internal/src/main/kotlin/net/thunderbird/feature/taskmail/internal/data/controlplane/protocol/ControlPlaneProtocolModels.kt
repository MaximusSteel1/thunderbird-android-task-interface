package net.thunderbird.feature.taskmail.internal.data.controlplane.protocol

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
internal data class ControlPlaneCapabilities(
    val streaming: Boolean = false,
    @SerialName("artifact_manifest")
    val artifactManifest: Boolean = false,
    @SerialName("workspace_snapshot")
    val workspaceSnapshot: Boolean = false,
    @SerialName("supported_backends")
    val supportedBackends: List<String> = emptyList(),
    @SerialName("profile_catalogs")
    val profileCatalogs: Map<String, List<String>> = emptyMap(),
    @SerialName("permission_modes")
    val permissionModes: List<String> = emptyList(),
    @SerialName("backend_transport_modes")
    val backendTransportModes: Map<String, List<String>> = emptyMap(),
)

@Serializable
internal data class ControlPlanePc(
    @SerialName("display_name")
    val displayName: String,
    @SerialName("client_version")
    val clientVersion: String,
    @SerialName("host_fingerprint")
    val hostFingerprint: String,
    @SerialName("runtime_fingerprint")
    val runtimeFingerprint: String,
    val capabilities: ControlPlaneCapabilities = ControlPlaneCapabilities(),
    val status: String? = null,
    @SerialName("last_seen_at")
    val lastSeenAt: String? = null,
    @SerialName("credential_id")
    val credentialId: String? = null,
)

@Serializable
internal data class ControlPlaneWorkspaceSnapshot(
    @SerialName("snapshot_id")
    val snapshotId: String,
    val workspaces: List<ControlPlaneWorkspace> = emptyList(),
)

@Serializable
internal data class ControlPlaneWorkspace(
    @SerialName("workspace_id")
    val workspaceId: String,
    @SerialName("pc_id")
    val pcId: String? = null,
    @SerialName("repo_path")
    val repoPath: String,
    val workdir: String? = null,
    @SerialName("display_name")
    val displayName: String,
    val capabilities: ControlPlaneCapabilities = ControlPlaneCapabilities(),
    @SerialName("last_snapshot_at")
    val lastSnapshotAt: String? = null,
)

@Serializable
internal data class ControlPlaneExecutionPolicy(
    val backend: String? = null,
    val profile: String? = null,
    val permission: String? = null,
    @SerialName("backend_transport")
    val backendTransport: String? = null,
    @SerialName("resolved_model")
    val resolvedModel: String? = null,
)

@Serializable
internal data class ControlPlaneCommand(
    @SerialName("command_id")
    val commandId: String,
    @SerialName("command_type")
    val commandType: String,
    @SerialName("pc_id")
    val pcId: String? = null,
    @SerialName("workspace_id")
    val workspaceId: String? = null,
    @SerialName("session_id")
    val sessionId: String? = null,
    @SerialName("execution_policy")
    val executionPolicy: ControlPlaneExecutionPolicy? = null,
    val payload: JsonObject = EMPTY_JSON_OBJECT,
    @SerialName("issued_at")
    val issuedAt: String,
    @SerialName("issuer_id")
    val issuerId: String? = null,
)

@Serializable
internal data class ControlPlaneCommandAck(
    @SerialName("command_id")
    val commandId: String,
    @SerialName("ack_status")
    val ackStatus: String,
    @SerialName("queue_position")
    val queuePosition: Long? = null,
    val reason: String? = null,
    @SerialName("error_code")
    val errorCode: String? = null,
    @SerialName("error_message")
    val errorMessage: String? = null,
)

@Serializable
internal data class ControlPlaneEvent(
    @SerialName("event_id")
    val eventId: String,
    @SerialName("command_id")
    val commandId: String,
    @SerialName("workspace_id")
    val workspaceId: String? = null,
    @SerialName("session_id")
    val sessionId: String? = null,
    @SerialName("run_id")
    val runId: String? = null,
    @SerialName("event_type")
    val eventType: String,
    val payload: JsonObject = EMPTY_JSON_OBJECT,
    @SerialName("emitted_at")
    val emittedAt: String? = null,
)

@Serializable
internal data class ControlPlaneOutputChunk(
    @SerialName("stream_id")
    val streamId: String,
    @SerialName("command_id")
    val commandId: String? = null,
    @SerialName("run_id")
    val runId: String,
    val seq: Long,
    val channel: String,
    val text: String,
    @SerialName("emitted_at")
    val emittedAt: String? = null,
)

@Serializable(with = ControlPlaneStructuredPayloadSerializer::class)
internal data class ControlPlaneStructuredPayload(
    val kind: String,
    val fields: JsonObject = EMPTY_JSON_OBJECT,
)

@Serializable
internal data class ControlPlaneResult(
    @SerialName("result_id")
    val resultId: String,
    @SerialName("command_id")
    val commandId: String,
    @SerialName("workspace_id")
    val workspaceId: String? = null,
    @SerialName("session_id")
    val sessionId: String? = null,
    @SerialName("run_id")
    val runId: String? = null,
    @SerialName("final_status")
    val finalStatus: String,
    val summary: String,
    @SerialName("effective_execution")
    val effectiveExecution: ControlPlaneExecutionPolicy? = null,
    @SerialName("structured_payload")
    val structuredPayload: ControlPlaneStructuredPayload,
    @SerialName("generated_at")
    val generatedAt: String? = null,
)

@Serializable
internal data class ControlPlaneArtifactManifest(
    @SerialName("command_id")
    val commandId: String? = null,
    @SerialName("workspace_id")
    val workspaceId: String? = null,
    @SerialName("session_id")
    val sessionId: String? = null,
    @SerialName("run_id")
    val runId: String? = null,
    val artifacts: List<ControlPlaneArtifact> = emptyList(),
)

@Serializable
internal data class ControlPlaneArtifact(
    @SerialName("artifact_id")
    val artifactId: String,
    val name: String,
    val kind: String,
    val role: String? = null,
    @SerialName("content_type")
    val contentType: String,
    val size: Long,
    val sha256: String? = null,
    @SerialName("download_ref")
    val downloadRef: ControlPlaneDownloadRef? = null,
    @SerialName("inline_preview")
    val inlinePreview: JsonElement? = null,
    val caption: String? = null,
)

@Serializable
internal data class ControlPlaneDownloadRef(
    val kind: String,
    @SerialName("file_id")
    val fileId: String? = null,
    @SerialName("metadata_url")
    val metadataUrl: String? = null,
    @SerialName("content_url")
    val contentUrl: String? = null,
    val url: String? = null,
    @SerialName("content_type")
    val contentType: String? = null,
    val encoding: String? = null,
    val data: String? = null,
)

internal object ControlPlaneStructuredPayloadSerializer : KSerializer<ControlPlaneStructuredPayload> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(
        "ControlPlaneStructuredPayload",
        PrimitiveKind.STRING,
    )

    override fun serialize(
        encoder: Encoder,
        value: ControlPlaneStructuredPayload,
    ) {
        require(encoder is JsonEncoder) {
            "ControlPlaneStructuredPayload can only be serialized by JSON"
        }

        encoder.encodeJsonElement(
            buildJsonObject {
                put("kind", JsonPrimitive(value.kind))
                value.fields.forEach { (key, fieldValue) ->
                    if (key != "kind") {
                        put(key, fieldValue)
                    }
                }
            },
        )
    }

    override fun deserialize(decoder: Decoder): ControlPlaneStructuredPayload {
        require(decoder is JsonDecoder) {
            "ControlPlaneStructuredPayload can only be deserialized by JSON"
        }

        val jsonObject = decoder.decodeJsonElement().jsonObject
        val kind = jsonObject["kind"]
            ?.jsonPrimitive
            ?.content
            ?.takeIf(String::isNotBlank)
            ?: throw SerializationException("structured_payload.kind is required")

        return ControlPlaneStructuredPayload(
            kind = kind,
            fields = buildJsonObject {
                jsonObject.forEach { (key, value) ->
                    if (key != "kind") {
                        put(key, value)
                    }
                }
            },
        )
    }
}

internal val EMPTY_JSON_OBJECT = JsonObject(emptyMap())
