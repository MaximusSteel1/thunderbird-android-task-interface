package net.thunderbird.feature.taskmail.internal.data.relay

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.thunderbird.core.logging.LogTag
import net.thunderbird.core.logging.Logger
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayCommand
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayPacket
import net.thunderbird.feature.taskmail.internal.domain.model.RelayConnectionState
import net.thunderbird.feature.taskmail.internal.domain.model.RelayTransportConfig
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class OkHttpRelayConnectionClientTest {
    private val server = MockWebServer()

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `connect should send hello with bearer auth and update connected state`() = runTest {
        val capturedMessages = mutableListOf<String>()
        val config = RelayTransportConfig(
            host = server.hostName,
            port = server.port,
            useTls = false,
            transportToken = "secret-token",
        )
        server.enqueue(
            MockResponse().withWebSocketUpgrade(
                object : WebSocketListener() {
                    override fun onMessage(webSocket: WebSocket, text: String) {
                        capturedMessages += text
                        if (text.contains("\"message_type\":\"hello\"")) {
                            webSocket.send(
                                """
                                {
                                  "message_type": "hello_ack",
                                  "connection_id": "connection-1",
                                  "server_time": "2026-03-20T19:00:00Z",
                                  "heartbeat_seconds": 30
                                }
                                """.trimIndent(),
                            )
                        } else if (text.contains("\"message_type\":\"packet\"")) {
                            webSocket.send(
                                """
                                {
                                  "message_type": "packet_ack",
                                  "packet_id": "android-taskmail:new-task:req_001",
                                  "accepted": true,
                                  "receipt_id": "receipt-1",
                                  "received_at": "2026-03-20T19:00:01Z",
                                  "transport_message_id": "transport-1"
                                }
                                """.trimIndent(),
                            )
                        }
                    }
                },
            ),
        )
        server.start()
        val testSubject = OkHttpRelayConnectionClient(
            codec = net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayProtocolJsonCodec(),
            logger = RelayFakeLogger(),
        )

        val result = testSubject.connect(config)
        val packetAck = testSubject.sendPacket(
            RelayPacket(
                packetId = "android-taskmail:new-task:req_001",
                clientTraceId = "req_001",
                taskRunPacket = buildJsonObject {
                    put("schema_version", "phase2-direct-outbound-contract-v1")
                    put("action", "new_task")
                },
                dispatchMetadata = buildJsonObject {
                    put("channel", "taskmail_android_direct")
                },
                sentAt = "2026-03-20T19:00:00Z",
            ),
            ackTimeoutMillis = DEFAULT_PACKET_ACK_TIMEOUT_MILLIS,
        )

        val request = server.takeRequest()
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer secret-token")
        assertThat(result.isSuccess).isEqualTo(true)
        assertThat(packetAck.isSuccess).isEqualTo(true)
        assertThat((testSubject.connectionState.value as RelayConnectionState.Connected).connectionId)
            .isEqualTo("connection-1")
        assertThat(capturedMessages.firstOrNull()?.contains("\"message_type\":\"hello\"")).isEqualTo(true)
        assertThat(
            capturedMessages.firstOrNull()?.contains("\"transport_token_id\":\"${config.tokenFingerprint()}\""),
        ).isEqualTo(true)
        assertThat(capturedMessages.lastOrNull()?.contains("\"message_type\":\"packet\"")).isEqualTo(true)
    }

    @Test
    fun `sendPacket should fail with relay server error when packet is rejected before acceptance`() = runTest {
        val config = RelayTransportConfig(
            host = server.hostName,
            port = server.port,
            useTls = false,
            transportToken = "secret-token",
        )
        server.enqueue(
            MockResponse().withWebSocketUpgrade(
                object : WebSocketListener() {
                    override fun onMessage(webSocket: WebSocket, text: String) {
                        if (text.contains("\"message_type\":\"hello\"")) {
                            webSocket.send(
                                """
                                {
                                  "message_type": "hello_ack",
                                  "connection_id": "connection-1",
                                  "server_time": "2026-03-20T19:00:00Z",
                                  "heartbeat_seconds": 30
                                }
                                """.trimIndent(),
                            )
                        } else if (text.contains("\"message_type\":\"packet\"")) {
                            webSocket.send(
                                """
                                {
                                  "message_type": "error",
                                  "code": "invalid_payload",
                                  "message": "new_task.task_text must be a non-empty string",
                                  "sent_at": "2026-03-20T19:00:01Z"
                                }
                                """.trimIndent(),
                            )
                        }
                    }
                },
            ),
        )
        server.start()
        val testSubject = OkHttpRelayConnectionClient(
            codec = net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayProtocolJsonCodec(),
            logger = RelayFakeLogger(),
        )

        val connectResult = testSubject.connect(config)
        val packetResult = testSubject.sendPacket(
            RelayPacket(
                packetId = "android-taskmail:new-task:req_001",
                clientTraceId = "req_001",
                taskRunPacket = buildJsonObject {
                    put("schema_version", "phase2-direct-outbound-contract-v1")
                    put("action", "new_task")
                },
                dispatchMetadata = buildJsonObject {
                    put("channel", "taskmail_android_direct")
                },
                sentAt = "2026-03-20T19:00:00Z",
            ),
            ackTimeoutMillis = DEFAULT_PACKET_ACK_TIMEOUT_MILLIS,
        )

        assertThat(connectResult.isSuccess).isEqualTo(true)
        assertThat(packetResult.isFailure).isEqualTo(true)
        assertThat(packetResult.exceptionOrNull()?.message)
            .isEqualTo("new_task.task_text must be a non-empty string")
        assertThat(testSubject.connectionState.value)
            .isEqualTo(RelayConnectionState.Failed("new_task.task_text must be a non-empty string"))
    }

    @Test
    fun `connect should emit session_update without failing connection`() = runTest {
        val config = RelayTransportConfig(
            host = server.hostName,
            port = server.port,
            useTls = false,
            transportToken = "secret-token",
        )
        server.enqueue(
            MockResponse().withWebSocketUpgrade(
                object : WebSocketListener() {
                    override fun onMessage(webSocket: WebSocket, text: String) {
                        if (text.contains("\"message_type\":\"hello\"")) {
                            webSocket.send(
                                """
                                {
                                  "message_type": "hello_ack",
                                  "connection_id": "connection-1",
                                  "server_time": "2026-03-20T19:00:00Z",
                                  "heartbeat_seconds": 30
                                }
                                """.trimIndent(),
                            )
                            webSocket.send(
                                """
                                {
                                  "message_type": "session_update",
                                  "schema_version": "phase3-direct-inbound-wire-v1",
                                  "subscription_id": "sub_001",
                                  "workspace_id": "workspace_a13f92d1c0ef",
                                  "session_id": "session_001",
                                  "thread_id": "thread_001",
                                  "task_id": "task_001",
                                  "update_id": "sessupd:session_001:9",
                                  "sequence": 9,
                                  "sent_at": "2026-03-21T22:38:03",
                                  "update_type": "session_snapshot",
                                  "session_snapshot": {
                                    "session_name": "Phase 3 detail bridge",
                                    "backend": "codex",
                                    "repo_path": "E:\\projects\\android_task_manager",
                                    "workdir": "feature/taskmail/internal",
                                    "status": "running",
                                    "lifecycle": "active",
                                    "last_summary": "Running.",
                                    "last_active_at": "2026-03-21T22:38:03",
                                    "last_progress_at": "2026-03-21T22:38:03",
                                    "paused_from_status": null,
                                    "question_state": null,
                                    "timeline_items": []
                                  }
                                }
                                """.trimIndent(),
                            )
                        }
                    }
                },
            ),
        )
        server.start()
        val testSubject = OkHttpRelayConnectionClient(
            codec = net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayProtocolJsonCodec(),
            logger = RelayFakeLogger(),
        )
        val sessionUpdate = async { testSubject.sessionUpdates.first() }

        val result = testSubject.connect(config)

        assertThat(result.isSuccess).isEqualTo(true)
        assertThat(sessionUpdate.await().updateId).isEqualTo("sessupd:session_001:9")
        assertThat((testSubject.connectionState.value as RelayConnectionState.Connected).connectionId)
            .isEqualTo("connection-1")
    }

    @Test
    fun `connect should emit generic event and result without failing connection`() = runTest {
        val config = RelayTransportConfig(
            host = server.hostName,
            port = server.port,
            useTls = false,
            transportToken = "secret-token",
        )
        server.enqueue(
            MockResponse().withWebSocketUpgrade(
                object : WebSocketListener() {
                    override fun onMessage(webSocket: WebSocket, text: String) {
                        if (text.contains("\"message_type\":\"hello\"")) {
                            webSocket.send(
                                """
                                {
                                  "message_type": "hello_ack",
                                  "connection_id": "connection-1",
                                  "server_time": "2026-03-20T19:00:00Z",
                                  "heartbeat_seconds": 30
                                }
                                """.trimIndent(),
                            )
                            webSocket.send(
                                """
                                {
                                  "message_type": "event",
                                  "request_id": "req_001",
                                  "packet_id": "pkt_001",
                                  "event_type": "vps_probe_bridge_finished",
                                  "payload_schema": "taskmail-transport-probe-payload-v1",
                                  "payload": {
                                    "probe_id": "probe_001"
                                  }
                                }
                                """.trimIndent(),
                            )
                            webSocket.send(
                                """
                                {
                                  "message_type": "result",
                                  "request_id": "req_001",
                                  "packet_id": "pkt_001",
                                  "receipt_id": "receipt_001",
                                  "result_id": "result_001",
                                  "result_type": "transport_probe_result",
                                  "status": "completed",
                                  "payload_schema": "taskmail-transport-probe-payload-v1",
                                  "payload": {
                                    "probe_id": "probe_001"
                                  }
                                }
                                """.trimIndent(),
                            )
                        }
                    }
                },
            ),
        )
        server.start()
        val testSubject = OkHttpRelayConnectionClient(
            codec = net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayProtocolJsonCodec(),
            logger = RelayFakeLogger(),
        )
        val serverEvent = async { testSubject.serverEvents.first() }
        val serverResult = async { testSubject.serverResults.first() }

        val result = testSubject.connect(config)

        assertThat(result.isSuccess).isEqualTo(true)
        assertThat(serverEvent.await().eventType).isEqualTo("vps_probe_bridge_finished")
        assertThat(serverResult.await().resultId).isEqualTo("result_001")
        assertThat((testSubject.connectionState.value as RelayConnectionState.Connected).connectionId)
            .isEqualTo("connection-1")
    }

    @Test
    fun `connect with supported schemas and sendCommand should use control path and surface command ack`() = runTest {
        val capturedMessages = mutableListOf<String>()
        val config = RelayTransportConfig(
            host = server.hostName,
            port = server.port,
            useTls = false,
            transportToken = "secret-token",
        )
        server.enqueue(
            MockResponse().withWebSocketUpgrade(
                object : WebSocketListener() {
                    override fun onMessage(webSocket: WebSocket, text: String) {
                        capturedMessages += text
                        if (text.contains("\"message_type\":\"hello\"")) {
                            webSocket.send(
                                """
                                {
                                  "message_type": "hello_ack",
                                  "connection_id": "connection-control-1",
                                  "server_time": "2026-03-24T10:00:00Z",
                                  "heartbeat_seconds": 30,
                                  "transport_token_id": "${config.tokenFingerprint()}",
                                  "accepted_payload_schemas": [
                                    "taskmail-bootstrap-control-contract-v2",
                                    "taskmail-transport-probe-payload-v1"
                                  ]
                                }
                                """.trimIndent(),
                            )
                        } else if (text.contains("\"message_type\":\"command\"")) {
                            webSocket.send(
                                """
                                {
                                  "message_type": "command_ack",
                                  "request_id": "probe_req_001",
                                  "packet_id": "android-control:transport-probe:probe_req_001",
                                  "command_type": "transport_probe",
                                  "payload_schema": "taskmail-transport-probe-payload-v1",
                                  "accepted": true,
                                  "receipt_id": "receipt-control-1",
                                  "received_at": "2026-03-24T10:00:01Z",
                                  "transport_message_id": "<transport-probe-1@example.com>"
                                }
                                """.trimIndent(),
                            )
                        }
                    }
                },
            ),
        )
        server.start()
        val testSubject = OkHttpRelayConnectionClient(
            codec = net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayProtocolJsonCodec(),
            logger = RelayFakeLogger(),
        )

        val connectResult = testSubject.connect(
            config = config,
            supportedPayloadSchemas = listOf(
                "taskmail-bootstrap-control-contract-v2",
                "taskmail-transport-probe-payload-v1",
            ),
        )
        val commandResult = testSubject.sendCommand(
            RelayCommand(
                requestId = "probe_req_001",
                packetId = "android-control:transport-probe:probe_req_001",
                commandType = "transport_probe",
                payloadSchema = "taskmail-transport-probe-payload-v1",
                trace = buildJsonObject {
                    put("trace_id", "trace-transport-001")
                    put("probe_id", "probe-transport-001")
                },
                payload = buildJsonObject {
                    put("probe_id", "probe-transport-001")
                    put("scenario", "android_direct_ping_to_vps_to_pc")
                    put("direction", "android_to_pc")
                    put("transport_kind", "mail")
                    put("payload_text", "PING transport probe")
                    put("timeout_seconds", 180)
                },
                related = buildJsonObject {
                    put("ui_surface", "transport_probe_sheet")
                },
                sentAt = "2026-03-24T10:00:00Z",
            ),
        )

        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/control")
        assertThat(connectResult.isSuccess).isEqualTo(true)
        assertThat(commandResult.isSuccess).isEqualTo(true)
        assertThat(connectResult.getOrThrow().acceptedPayloadSchemas).isEqualTo(
            listOf(
                "taskmail-bootstrap-control-contract-v2",
                "taskmail-transport-probe-payload-v1",
            ),
        )
        assertThat(commandResult.getOrThrow().receiptId).isEqualTo("receipt-control-1")
        assertThat(commandResult.getOrThrow().transportMessageId).isEqualTo("<transport-probe-1@example.com>")
        assertThat(
            Json.parseToJsonElement(capturedMessages.first()).toString().contains("supported_payload_schemas"),
        ).isEqualTo(true)
        assertThat(capturedMessages.last().contains("\"message_type\":\"command\"")).isEqualTo(true)
    }
}

internal class RelayFakeLogger : Logger {
    override fun verbose(tag: LogTag?, throwable: Throwable?, message: () -> String) = Unit
    override fun debug(tag: LogTag?, throwable: Throwable?, message: () -> String) = Unit
    override fun info(tag: LogTag?, throwable: Throwable?, message: () -> String) = Unit
    override fun warn(tag: LogTag?, throwable: Throwable?, message: () -> String) = Unit
    override fun error(tag: LogTag?, throwable: Throwable?, message: () -> String) = Unit
}
