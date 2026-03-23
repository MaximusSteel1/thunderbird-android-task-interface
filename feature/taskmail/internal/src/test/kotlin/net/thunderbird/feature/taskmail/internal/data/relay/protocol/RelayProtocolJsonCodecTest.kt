package net.thunderbird.feature.taskmail.internal.data.relay.protocol

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayQuestion
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayQuestionState
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelaySessionSnapshot
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelaySessionUpdate
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayTimelineItem

class RelayProtocolJsonCodecTest {

    private val testSubject = RelayProtocolJsonCodec()

    @Test
    fun `encodeHello should encode canonical hello payload`() {
        val payload = testSubject.encodeHello(
            RelayHello(
                clientId = "android-taskmail",
                clientVersion = "0.1.0-dev",
                transportTokenId = "abc123def456",
                sentAt = "2026-03-20T19:00:00Z",
            ),
        )

        assertThat(payload).isEqualTo(
            "{" +
                "\"message_type\":\"hello\"," +
                "\"client_id\":\"android-taskmail\"," +
                "\"client_version\":\"0.1.0-dev\"," +
                "\"transport_token_id\":\"abc123def456\"," +
                "\"sent_at\":\"2026-03-20T19:00:00Z\"" +
                "}",
        )
    }

    @Test
    fun `decodeServerMessage should decode hello_ack`() {
        val message = testSubject.decodeServerMessage(
            """
            {
              "message_type": "hello_ack",
              "connection_id": "connection-1",
              "server_time": "2026-03-20T19:00:00Z",
              "heartbeat_seconds": 30
            }
            """.trimIndent(),
        )

        assertThat(message).isEqualTo(
            RelayServerMessage.HelloAck(
                RelayHelloAck(
                    messageType = "hello_ack",
                    connectionId = "connection-1",
                    serverTime = "2026-03-20T19:00:00Z",
                    heartbeatSeconds = 30,
                ),
            ),
        )
    }

    @Test
    fun `decodeServerMessage should decode error`() {
        val message = testSubject.decodeServerMessage(
            """
            {
              "message_type": "error",
              "code": "unauthorized",
              "message": "invalid token",
              "sent_at": "2026-03-20T19:00:00Z"
            }
            """.trimIndent(),
        )

        assertThat(message).isEqualTo(
            RelayServerMessage.Error(
                RelayError(
                    messageType = "error",
                    code = "unauthorized",
                    message = "invalid token",
                    sentAt = "2026-03-20T19:00:00Z",
                ),
            ),
        )
    }

    @Test
    fun `encodePacket should encode canonical packet payload`() {
        val payload = testSubject.encodePacket(
            RelayPacket(
                packetId = "android-taskmail:new-task:req_001",
                clientTraceId = "req_001",
                taskRunPacket = buildJsonObject {
                    put("schema_version", "phase2-direct-outbound-contract-v1")
                    put("action", "new_task")
                },
                dispatchMetadata = buildJsonObject {
                    put("channel", "taskmail_android_direct")
                    put("fallback_policy", "mail")
                },
                sentAt = "2026-03-21T12:30:00Z",
            ),
        )

        assertThat(Json.parseToJsonElement(payload)).isEqualTo(
            Json.parseToJsonElement(
                """
                {
                  "message_type": "packet",
                  "packet_id": "android-taskmail:new-task:req_001",
                  "client_trace_id": "req_001",
                  "task_run_packet": {
                    "schema_version": "phase2-direct-outbound-contract-v1",
                    "action": "new_task"
                  },
                  "dispatch_metadata": {
                    "channel": "taskmail_android_direct",
                    "fallback_policy": "mail"
                  },
                  "sent_at": "2026-03-21T12:30:00Z"
                }
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `decodeServerMessage should decode packet_ack`() {
        val message = testSubject.decodeServerMessage(
            """
            {
              "message_type": "packet_ack",
              "packet_id": "android-taskmail:new-task:req_001",
              "accepted": true,
              "receipt_id": "receipt-1",
              "received_at": "2026-03-21T12:30:01Z",
              "transport_message_id": "transport-1"
            }
            """.trimIndent(),
        )

        assertThat(message).isEqualTo(
            RelayServerMessage.PacketAck(
                RelayPacketAck(
                    messageType = "packet_ack",
                    packetId = "android-taskmail:new-task:req_001",
                    accepted = true,
                    receiptId = "receipt-1",
                    receivedAt = "2026-03-21T12:30:01Z",
                    transportMessageId = "transport-1",
                ),
            ),
        )
    }

    @Test
    fun `decodeServerMessage should decode packet_ack rejection with optional error code`() {
        val message = testSubject.decodeServerMessage(
            """
            {
              "message_type": "packet_ack",
              "packet_id": "android-taskmail:new-task:req_001",
              "accepted": false,
              "receipt_id": "receipt-1",
              "received_at": "2026-03-21T12:30:01Z",
              "error_code": "invalid_payload",
              "error_message": "new_task.task_text must be a non-empty string"
            }
            """.trimIndent(),
        )

        assertThat(message).isEqualTo(
            RelayServerMessage.PacketAck(
                RelayPacketAck(
                    messageType = "packet_ack",
                    packetId = "android-taskmail:new-task:req_001",
                    accepted = false,
                    receiptId = "receipt-1",
                    receivedAt = "2026-03-21T12:30:01Z",
                    errorCode = "invalid_payload",
                    errorMessage = "new_task.task_text must be a non-empty string",
                ),
            ),
        )
    }

    @Test
    fun `decodeServerMessage should decode event`() {
        val message = testSubject.decodeServerMessage(
            """
            {
              "message_type": "event",
              "envelope_id": "env_001",
              "sent_at": "2026-03-23T20:00:01Z",
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

        assertThat(message).isEqualTo(
            RelayServerMessage.Event(
                RelayEvent(
                    envelopeId = "env_001",
                    sentAt = "2026-03-23T20:00:01Z",
                    requestId = "req_001",
                    packetId = "pkt_001",
                    eventType = "vps_probe_bridge_finished",
                    payloadSchema = "taskmail-transport-probe-payload-v1",
                    payload = Json.parseToJsonElement("""{"probe_id":"probe_001"}"""),
                ),
            ),
        )
    }

    @Test
    fun `decodeServerMessage should decode result`() {
        val message = testSubject.decodeServerMessage(
            """
            {
              "message_type": "result",
              "envelope_id": "env_002",
              "sent_at": "2026-03-23T20:00:03Z",
              "request_id": "req_001",
              "packet_id": "pkt_001",
              "receipt_id": "receipt_001",
              "result_id": "result_001",
              "result_type": "transport_probe_result",
              "status": "completed",
              "payload_schema": "taskmail-transport-probe-payload-v1",
              "payload": {
                "probe_id": "probe_001",
                "outcome": "observed"
              }
            }
            """.trimIndent(),
        )

        assertThat(message).isEqualTo(
            RelayServerMessage.Result(
                RelayResult(
                    envelopeId = "env_002",
                    sentAt = "2026-03-23T20:00:03Z",
                    requestId = "req_001",
                    packetId = "pkt_001",
                    receiptId = "receipt_001",
                    resultId = "result_001",
                    resultType = "transport_probe_result",
                    status = "completed",
                    payloadSchema = "taskmail-transport-probe-payload-v1",
                    payload = Json.parseToJsonElement(
                        """{"probe_id":"probe_001","outcome":"observed"}""",
                    ),
                ),
            ),
        )
    }

    @Test
    @Suppress("LongMethod")
    fun `decodeServerMessage should decode session_update snapshot`() {
        val message = testSubject.decodeServerMessage(
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
                "status": "awaiting_user_input",
                "lifecycle": "active",
                "last_summary": "Need one answer before continuing.",
                "last_active_at": "2026-03-21T22:38:03",
                "last_progress_at": "2026-03-21T22:38:03",
                "paused_from_status": null,
                "question_state": {
                  "question_set_id": "qset_branch_choice",
                  "question_count": 1,
                  "questions": [
                    {
                      "question_id": "q_branch",
                      "question_text": "Which branch should I use?",
                      "question_type": "single_choice",
                      "required": true,
                      "choices": ["main", "release"],
                      "choice_labels": {
                        "main": "Main branch",
                        "release": "Release branch"
                      }
                    }
                  ]
                },
                "timeline_items": [
                  {
                    "item_id": "tl_question_009",
                    "business_event_key": "question/qset_branch_choice/2026-03-21T22:38:03",
                    "item_type": "question_prompt",
                    "created_at": "2026-03-21T22:38:03",
                    "status": null,
                    "text": "Which branch should I use?",
                    "question_set_id": "qset_branch_choice",
                    "question_ids": ["q_branch"],
                    "paused_from_status": null
                  }
                ]
              }
            }
            """.trimIndent(),
        )

        assertThat(message).isEqualTo(
            RelayServerMessage.SessionUpdate(
                RelaySessionUpdate(
                    schemaVersion = "phase3-direct-inbound-wire-v1",
                    subscriptionId = "sub_001",
                    workspaceId = "workspace_a13f92d1c0ef",
                    sessionId = "session_001",
                    threadId = "thread_001",
                    taskId = "task_001",
                    updateId = "sessupd:session_001:9",
                    sequence = 9,
                    sentAt = "2026-03-21T22:38:03",
                    updateType = "session_snapshot",
                    sessionSnapshot = RelaySessionSnapshot(
                        sessionName = "Phase 3 detail bridge",
                        backend = "codex",
                        repoPath = "E:\\projects\\android_task_manager",
                        workdir = "feature/taskmail/internal",
                        status = "awaiting_user_input",
                        lifecycle = "active",
                        lastSummary = "Need one answer before continuing.",
                        lastActiveAt = "2026-03-21T22:38:03",
                        lastProgressAt = "2026-03-21T22:38:03",
                        questionState = RelayQuestionState(
                            questionSetId = "qset_branch_choice",
                            questionCount = 1,
                            questions = listOf(
                                RelayQuestion(
                                    questionId = "q_branch",
                                    questionText = "Which branch should I use?",
                                    questionType = "single_choice",
                                    required = true,
                                    choices = listOf("main", "release"),
                                    choiceLabels = mapOf(
                                        "main" to "Main branch",
                                        "release" to "Release branch",
                                    ),
                                ),
                            ),
                        ),
                        timelineItems = listOf(
                            RelayTimelineItem(
                                itemId = "tl_question_009",
                                businessEventKey = "question/qset_branch_choice/2026-03-21T22:38:03",
                                itemType = "question_prompt",
                                createdAt = "2026-03-21T22:38:03",
                                text = "Which branch should I use?",
                                questionSetId = "qset_branch_choice",
                                questionIds = listOf("q_branch"),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }
}
