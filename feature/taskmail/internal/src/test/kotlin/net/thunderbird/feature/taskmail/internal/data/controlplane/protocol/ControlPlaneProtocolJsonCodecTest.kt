package net.thunderbird.feature.taskmail.internal.data.controlplane.protocol

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ControlPlaneProtocolJsonCodecTest {

    private val testSubject = ControlPlaneProtocolJsonCodec()

    @Test
    fun `encodePcHello should encode canonical pc hello payload`() {
        val payload = testSubject.encodePcHello(
            ControlPlanePcHelloMessage(
                messageId = "msg_hello_01",
                traceId = "trace_boot_01",
                pcId = "pc_home",
                connectionEpoch = 0,
                sentAt = "2026-03-25T10:00:00Z",
                payload = ControlPlanePc(
                    displayName = "Home PC",
                    clientVersion = "0.1.0",
                    hostFingerprint = "host_abc123",
                    runtimeFingerprint = "runtime_def456",
                    capabilities = ControlPlaneCapabilities(
                        streaming = true,
                        artifactManifest = true,
                        workspaceSnapshot = true,
                        supportedBackends = listOf("codex", "opencode"),
                        profileCatalogs = mapOf(
                            "codex" to listOf("fast", "strong", "vision"),
                            "opencode" to listOf("fast", "strong", "vision"),
                        ),
                        permissionModes = listOf("default", "highest"),
                        backendTransportModes = mapOf(
                            "codex" to listOf("cli", "sdk"),
                            "opencode" to listOf("cli"),
                        ),
                    ),
                ),
            ),
        )

        assertThat(Json.parseToJsonElement(payload)).isEqualTo(
            Json.parseToJsonElement(
                """
                {
                  "schema_version": "v1",
                  "type": "pc_hello",
                  "message_id": "msg_hello_01",
                  "trace_id": "trace_boot_01",
                  "pc_id": "pc_home",
                  "connection_epoch": 0,
                  "sent_at": "2026-03-25T10:00:00Z",
                  "payload": {
                    "display_name": "Home PC",
                    "client_version": "0.1.0",
                    "host_fingerprint": "host_abc123",
                    "runtime_fingerprint": "runtime_def456",
                    "capabilities": {
                      "streaming": true,
                      "artifact_manifest": true,
                      "workspace_snapshot": true,
                      "supported_backends": ["codex", "opencode"],
                      "profile_catalogs": {
                        "codex": ["fast", "strong", "vision"],
                        "opencode": ["fast", "strong", "vision"]
                      },
                      "permission_modes": ["default", "highest"],
                      "backend_transport_modes": {
                        "codex": ["cli", "sdk"],
                        "opencode": ["cli"]
                      }
                    }
                  }
                }
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `encodeCommandDispatch should encode canonical command payload`() {
        val payload = testSubject.encodeCommandDispatch(
            ControlPlaneCommandDispatchMessage(
                messageId = "msg_cmd_01",
                traceId = "trace_cmd_01",
                pcId = "pc_home",
                connectionEpoch = 12,
                sentAt = "2026-03-25T10:00:30Z",
                payload = ControlPlaneCommand(
                    commandId = "cmd_01",
                    commandType = "new_task",
                    pcId = "pc_home",
                    workspaceId = "ws_pc_home_repo_a_src",
                    executionPolicy = ControlPlaneExecutionPolicy(
                        backend = "codex",
                        profile = "strong",
                        permission = "highest",
                        backendTransport = "sdk",
                    ),
                    payload = buildJsonObject {
                        put("repo_path", "E:\\projects\\repo_a")
                        put("workdir", "src")
                        put("task_text", "整理日志并提交修复。")
                    },
                    issuedAt = "2026-03-25T10:00:29Z",
                    issuerId = "android_user",
                ),
            ),
        )

        assertThat(Json.parseToJsonElement(payload)).isEqualTo(
            Json.parseToJsonElement(
                """
                {
                  "schema_version": "v1",
                  "type": "command_dispatch",
                  "message_id": "msg_cmd_01",
                  "trace_id": "trace_cmd_01",
                  "pc_id": "pc_home",
                  "connection_epoch": 12,
                  "sent_at": "2026-03-25T10:00:30Z",
                  "payload": {
                    "command_id": "cmd_01",
                    "command_type": "new_task",
                    "pc_id": "pc_home",
                    "workspace_id": "ws_pc_home_repo_a_src",
                    "execution_policy": {
                      "backend": "codex",
                      "profile": "strong",
                      "permission": "highest",
                      "backend_transport": "sdk"
                    },
                    "payload": {
                      "repo_path": "E:\\projects\\repo_a",
                      "workdir": "src",
                      "task_text": "整理日志并提交修复。"
                    },
                    "issued_at": "2026-03-25T10:00:29Z",
                    "issuer_id": "android_user"
                  }
                }
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `decodeMessage should decode command_ack`() {
        val message = testSubject.decodeMessage(
            """
            {
              "schema_version": "v1",
              "type": "command_ack",
              "message_id": "msg_ack_01",
              "trace_id": "trace_cmd_01",
              "pc_id": "pc_home",
              "connection_epoch": 12,
              "sent_at": "2026-03-25T10:01:00Z",
              "payload": {
                "command_id": "cmd_01",
                "ack_status": "accepted_but_queued",
                "queue_position": 1,
                "reason": null,
                "error_code": null,
                "error_message": null
              }
            }
            """.trimIndent(),
        )

        assertThat(message).isEqualTo(
            ControlPlaneCommandAckMessage(
                messageId = "msg_ack_01",
                traceId = "trace_cmd_01",
                pcId = "pc_home",
                connectionEpoch = 12,
                sentAt = "2026-03-25T10:01:00Z",
                payload = ControlPlaneCommandAck(
                    commandId = "cmd_01",
                    ackStatus = "accepted_but_queued",
                    queuePosition = 1,
                ),
            ),
        )
    }

    @Test
    fun `decodeMessage should decode result with structured payload`() {
        val message = testSubject.decodeMessage(
            """
            {
              "schema_version": "v1",
              "type": "result",
              "message_id": "msg_res_01",
              "trace_id": "trace_cmd_01",
              "pc_id": "pc_home",
              "connection_epoch": 12,
              "sent_at": "2026-03-25T10:02:00Z",
              "payload": {
                "result_id": "res_01",
                "command_id": "cmd_01",
                "workspace_id": "ws_pc_home_repo_a_src",
                "session_id": "sess_01",
                "run_id": "run_01",
                "final_status": "done",
                "summary": "完成重构并保留原有输出。",
                "effective_execution": {
                  "backend": "codex",
                  "profile": "strong",
                  "permission": "highest",
                  "backend_transport": "sdk",
                  "resolved_model": "gpt-5-codex"
                },
                "structured_payload": {
                  "kind": "task_outcome",
                  "changed_files": ["floor_shear.py"],
                  "tests_passed": true
                },
                "generated_at": "2026-03-25T10:01:59Z"
              }
            }
            """.trimIndent(),
        )

        assertThat(message).isEqualTo(
            ControlPlaneResultMessage(
                messageId = "msg_res_01",
                traceId = "trace_cmd_01",
                pcId = "pc_home",
                connectionEpoch = 12,
                sentAt = "2026-03-25T10:02:00Z",
                payload = ControlPlaneResult(
                    resultId = "res_01",
                    commandId = "cmd_01",
                    workspaceId = "ws_pc_home_repo_a_src",
                    sessionId = "sess_01",
                    runId = "run_01",
                    finalStatus = "done",
                    summary = "完成重构并保留原有输出。",
                    effectiveExecution = ControlPlaneExecutionPolicy(
                        backend = "codex",
                        profile = "strong",
                        permission = "highest",
                        backendTransport = "sdk",
                        resolvedModel = "gpt-5-codex",
                    ),
                    structuredPayload = ControlPlaneStructuredPayload(
                        kind = "task_outcome",
                        fields = buildJsonObject {
                            put("changed_files", Json.parseToJsonElement("""["floor_shear.py"]"""))
                            put("tests_passed", true)
                        },
                    ),
                    generatedAt = "2026-03-25T10:01:59Z",
                ),
            ),
        )
    }

    @Test
    fun `decodeMessage should decode artifact_manifest`() {
        val message = testSubject.decodeMessage(
            """
            {
              "schema_version": "v1",
              "type": "artifact_manifest",
              "message_id": "msg_art_01",
              "trace_id": "trace_cmd_01",
              "pc_id": "pc_home",
              "connection_epoch": 12,
              "sent_at": "2026-03-25T10:02:05Z",
              "payload": {
                "run_id": "run_01",
                "artifacts": [
                  {
                    "artifact_id": "art_01",
                    "name": "summary.md",
                    "kind": "file",
                    "role": "output",
                    "content_type": "text/markdown",
                    "size": 1024,
                    "download_ref": {
                      "kind": "vps_file",
                      "file_id": "file_01",
                      "metadata_url": "/v1/files/file_01",
                      "content_url": "/v1/files/file_01/content"
                    }
                  }
                ]
              }
            }
            """.trimIndent(),
        )

        assertThat(message).isEqualTo(
            ControlPlaneArtifactManifestMessage(
                messageId = "msg_art_01",
                traceId = "trace_cmd_01",
                pcId = "pc_home",
                connectionEpoch = 12,
                sentAt = "2026-03-25T10:02:05Z",
                payload = ControlPlaneArtifactManifest(
                    runId = "run_01",
                    artifacts = listOf(
                        ControlPlaneArtifact(
                            artifactId = "art_01",
                            name = "summary.md",
                            kind = "file",
                            role = "output",
                            contentType = "text/markdown",
                            size = 1024,
                            downloadRef = ControlPlaneDownloadRef(
                                kind = "vps_file",
                                fileId = "file_01",
                                metadataUrl = "/v1/files/file_01",
                                contentUrl = "/v1/files/file_01/content",
                            ),
                        ),
                    ),
                ),
            ),
        )
    }
}
