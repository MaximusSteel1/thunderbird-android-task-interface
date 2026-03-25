package net.thunderbird.feature.taskmail.internal.data.controlplane.protocol

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayCommandAck
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayEffectiveExecution
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayEvent
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayResult
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayStructuredPayload

class ControlPlaneRelayMapperTest {

    @Test
    fun `RelayCommandAck should map to control-plane ack`() {
        val relayAck = RelayCommandAck(
            requestId = "cmd_01",
            commandType = "new_task",
            accepted = true,
            ackStatus = "accepted_but_queued",
            receiptId = "receipt_01",
            receivedAt = "2026-03-25T10:01:00Z",
            related = Json.parseToJsonElement("""{"queue_position":2}"""),
        )

        assertThat(relayAck.toControlPlaneCommandAck()).isEqualTo(
            ControlPlaneCommandAck(
                commandId = "cmd_01",
                ackStatus = "accepted_but_queued",
                queuePosition = 2,
            ),
        )
    }

    @Test
    fun `RelayEvent should map to control-plane event`() {
        val relayEvent = RelayEvent(
            envelopeId = "env_01",
            sentAt = "2026-03-25T10:01:20Z",
            requestId = "cmd_01",
            eventType = "running",
            payload = buildJsonObject {
                put("workspace_id", "ws_01")
                put("session_id", "sess_01")
                put("run_id", "run_01")
                put(
                    "effective_execution",
                    Json.parseToJsonElement(
                        """
                        {
                          "backend": "codex",
                          "profile": "strong",
                          "permission": "highest"
                        }
                        """.trimIndent(),
                    ),
                )
            },
        )

        assertThat(relayEvent.toControlPlaneEvent()).isEqualTo(
            ControlPlaneEvent(
                eventId = "env_01",
                commandId = "cmd_01",
                workspaceId = "ws_01",
                sessionId = "sess_01",
                runId = "run_01",
                eventType = "running",
                payload = buildJsonObject {
                    put("workspace_id", "ws_01")
                    put("session_id", "sess_01")
                    put("run_id", "run_01")
                    put(
                        "effective_execution",
                        Json.parseToJsonElement(
                            """
                            {
                              "backend": "codex",
                              "profile": "strong",
                              "permission": "highest"
                            }
                            """.trimIndent(),
                        ),
                    )
                },
                emittedAt = "2026-03-25T10:01:20Z",
            ),
        )
    }

    @Test
    fun `RelayResult should map to control-plane result`() {
        val relayResult = RelayResult(
            requestId = "cmd_01",
            receiptId = "receipt_01",
            resultId = "res_01",
            resultType = "task_outcome",
            status = "completed",
            finalStatus = "done",
            payload = buildJsonObject {
                put("summary", "完成重构并保留原有输出。")
                put("workspace_id", "ws_01")
                put("run_id", "run_01")
            },
            structuredPayload = RelayStructuredPayload(
                kind = "task_outcome",
                payload = buildJsonObject {
                    put("changed_files", Json.parseToJsonElement("""["floor_shear.py"]"""))
                    put("session_id", "sess_01")
                },
            ),
            effectiveExecution = RelayEffectiveExecution(
                backend = "codex",
                profile = "strong",
                permission = "highest",
                resolvedModel = "gpt-5-codex",
            ),
            sentAt = "2026-03-25T10:02:00Z",
        )

        assertThat(relayResult.toControlPlaneResult()).isEqualTo(
            ControlPlaneResult(
                resultId = "res_01",
                commandId = "cmd_01",
                workspaceId = "ws_01",
                sessionId = "sess_01",
                runId = "run_01",
                finalStatus = "done",
                summary = "完成重构并保留原有输出。",
                effectiveExecution = ControlPlaneExecutionPolicy(
                    backend = "codex",
                    profile = "strong",
                    permission = "highest",
                    resolvedModel = "gpt-5-codex",
                ),
                structuredPayload = ControlPlaneStructuredPayload(
                    kind = "task_outcome",
                    fields = buildJsonObject {
                        put("changed_files", Json.parseToJsonElement("""["floor_shear.py"]"""))
                        put("session_id", "sess_01")
                    },
                ),
                generatedAt = "2026-03-25T10:02:00Z",
            ),
        )
    }
}
