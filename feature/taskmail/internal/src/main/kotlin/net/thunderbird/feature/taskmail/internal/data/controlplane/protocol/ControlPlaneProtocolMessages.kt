package net.thunderbird.feature.taskmail.internal.data.controlplane.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal sealed interface ControlPlaneMessage

@Serializable
internal data class ControlPlanePcHelloMessage(
    @SerialName("schema_version")
    val schemaVersion: String = SCHEMA_VERSION,
    val type: String = TYPE,
    @SerialName("message_id")
    val messageId: String,
    @SerialName("trace_id")
    val traceId: String,
    @SerialName("pc_id")
    val pcId: String,
    @SerialName("connection_epoch")
    val connectionEpoch: Long,
    @SerialName("sent_at")
    val sentAt: String,
    val payload: ControlPlanePc,
) : ControlPlaneMessage {
    companion object {
        const val TYPE = "pc_hello"
    }
}

@Serializable
internal data class ControlPlaneWorkspaceSnapshotMessage(
    @SerialName("schema_version")
    val schemaVersion: String = SCHEMA_VERSION,
    val type: String = TYPE,
    @SerialName("message_id")
    val messageId: String,
    @SerialName("trace_id")
    val traceId: String,
    @SerialName("pc_id")
    val pcId: String,
    @SerialName("connection_epoch")
    val connectionEpoch: Long,
    @SerialName("sent_at")
    val sentAt: String,
    val payload: ControlPlaneWorkspaceSnapshot,
) : ControlPlaneMessage {
    companion object {
        const val TYPE = "workspace_snapshot"
    }
}

@Serializable
internal data class ControlPlaneCommandDispatchMessage(
    @SerialName("schema_version")
    val schemaVersion: String = SCHEMA_VERSION,
    val type: String = TYPE,
    @SerialName("message_id")
    val messageId: String,
    @SerialName("trace_id")
    val traceId: String,
    @SerialName("pc_id")
    val pcId: String,
    @SerialName("connection_epoch")
    val connectionEpoch: Long,
    @SerialName("sent_at")
    val sentAt: String,
    val payload: ControlPlaneCommand,
) : ControlPlaneMessage {
    companion object {
        const val TYPE = "command_dispatch"
    }
}

@Serializable
internal data class ControlPlaneCommandAckMessage(
    @SerialName("schema_version")
    val schemaVersion: String = SCHEMA_VERSION,
    val type: String = TYPE,
    @SerialName("message_id")
    val messageId: String,
    @SerialName("trace_id")
    val traceId: String,
    @SerialName("pc_id")
    val pcId: String,
    @SerialName("connection_epoch")
    val connectionEpoch: Long,
    @SerialName("sent_at")
    val sentAt: String,
    val payload: ControlPlaneCommandAck,
) : ControlPlaneMessage {
    companion object {
        const val TYPE = "command_ack"
    }
}

@Serializable
internal data class ControlPlaneEventMessage(
    @SerialName("schema_version")
    val schemaVersion: String = SCHEMA_VERSION,
    val type: String = TYPE,
    @SerialName("message_id")
    val messageId: String,
    @SerialName("trace_id")
    val traceId: String,
    @SerialName("pc_id")
    val pcId: String,
    @SerialName("connection_epoch")
    val connectionEpoch: Long,
    @SerialName("sent_at")
    val sentAt: String,
    val payload: ControlPlaneEvent,
) : ControlPlaneMessage {
    companion object {
        const val TYPE = "event"
    }
}

@Serializable
internal data class ControlPlaneOutputChunkMessage(
    @SerialName("schema_version")
    val schemaVersion: String = SCHEMA_VERSION,
    val type: String = TYPE,
    @SerialName("message_id")
    val messageId: String,
    @SerialName("trace_id")
    val traceId: String,
    @SerialName("pc_id")
    val pcId: String,
    @SerialName("connection_epoch")
    val connectionEpoch: Long,
    @SerialName("sent_at")
    val sentAt: String,
    val payload: ControlPlaneOutputChunk,
) : ControlPlaneMessage {
    companion object {
        const val TYPE = "output_chunk"
    }
}

@Serializable
internal data class ControlPlaneResultMessage(
    @SerialName("schema_version")
    val schemaVersion: String = SCHEMA_VERSION,
    val type: String = TYPE,
    @SerialName("message_id")
    val messageId: String,
    @SerialName("trace_id")
    val traceId: String,
    @SerialName("pc_id")
    val pcId: String,
    @SerialName("connection_epoch")
    val connectionEpoch: Long,
    @SerialName("sent_at")
    val sentAt: String,
    val payload: ControlPlaneResult,
) : ControlPlaneMessage {
    companion object {
        const val TYPE = "result"
    }
}

@Serializable
internal data class ControlPlaneArtifactManifestMessage(
    @SerialName("schema_version")
    val schemaVersion: String = SCHEMA_VERSION,
    val type: String = TYPE,
    @SerialName("message_id")
    val messageId: String,
    @SerialName("trace_id")
    val traceId: String,
    @SerialName("pc_id")
    val pcId: String,
    @SerialName("connection_epoch")
    val connectionEpoch: Long,
    @SerialName("sent_at")
    val sentAt: String,
    val payload: ControlPlaneArtifactManifest,
) : ControlPlaneMessage {
    companion object {
        const val TYPE = "artifact_manifest"
    }
}

internal const val SCHEMA_VERSION = "v1"
