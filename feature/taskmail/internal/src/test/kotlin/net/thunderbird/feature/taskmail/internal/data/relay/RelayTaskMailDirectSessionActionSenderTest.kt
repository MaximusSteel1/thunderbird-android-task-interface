package net.thunderbird.feature.taskmail.internal.data.relay

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayEvent
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayHelloAck
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayPacket
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayPacketAck
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayResult
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelaySessionUpdate
import net.thunderbird.feature.taskmail.internal.domain.model.RelayConnectionState
import net.thunderbird.feature.taskmail.internal.domain.model.RelayTransportConfig
import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailDirectSessionActionRequest
import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailDirectSessionActionResult
import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailDirectSessionActionTarget
import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailDirectSessionActionType

class RelayTaskMailDirectSessionActionSenderTest {

    @Test
    fun `send should build canonical reply packet and return accepted result`() = runTest {
        val relayConnectionClient = FakeSessionActionRelayConnectionClient(
            sendPacketResult = Result.success(
                RelayPacketAck(
                    packetId = "android-taskmail:session-action:req_001",
                    accepted = true,
                    receiptId = "receipt-1",
                    receivedAt = "2026-03-22T23:00:01Z",
                    transportMessageId = "transport-1",
                ),
            ),
        )
        val testSubject = RelayTaskMailDirectSessionActionSender(
            relayConnectionClient = relayConnectionClient,
            timestampProvider = { "2026-03-22T23:00:00Z" },
            requestIdFactory = { "req_001" },
        )

        val result = testSubject.send(canonicalReplyRequest())

        assertThat(result).isEqualTo(
            TaskMailDirectSessionActionResult.Accepted(
                actionType = TaskMailDirectSessionActionType.Reply,
                requestId = "req_001",
                receiptId = "receipt-1",
                transportMessageId = "transport-1",
            ),
        )
        assertThat(relayConnectionClient.sentPackets.map(RelayPacket::packetId))
            .containsExactly("android-taskmail:session-action:req_001")

        assertCanonicalReplyPacket(relayConnectionClient.sentPackets.single())
    }

    @Test
    fun `send should build canonical status packet and return accepted result`() = runTest {
        val relayConnectionClient = FakeSessionActionRelayConnectionClient(
            sendPacketResult = Result.success(
                RelayPacketAck(
                    packetId = "android-taskmail:session-action:req_002",
                    accepted = true,
                    receiptId = "receipt-2",
                    receivedAt = "2026-03-22T23:00:06Z",
                    transportMessageId = "transport-2",
                ),
            ),
        )
        val testSubject = RelayTaskMailDirectSessionActionSender(
            relayConnectionClient = relayConnectionClient,
            timestampProvider = { "2026-03-22T23:00:05Z" },
            requestIdFactory = { "req_002" },
        )

        val result = testSubject.send(canonicalStatusRequest())

        assertThat(result).isEqualTo(
            TaskMailDirectSessionActionResult.Accepted(
                actionType = TaskMailDirectSessionActionType.Status,
                requestId = "req_002",
                receiptId = "receipt-2",
                transportMessageId = "transport-2",
            ),
        )
        assertCanonicalStatusPacket(relayConnectionClient.sentPackets.single())
    }

    @Test
    fun `send should route capability rejection to mail fallback`() = runTest {
        val testSubject = RelayTaskMailDirectSessionActionSender(
            relayConnectionClient = FakeSessionActionRelayConnectionClient(
                sendPacketResult = Result.failure(
                    RelayServerException(
                        code = "unsupported_action",
                        message = "direct action is not available",
                    ),
                ),
            ),
            timestampProvider = { "2026-03-22T23:00:00Z" },
            requestIdFactory = { "req_001" },
        )

        val result = testSubject.send(canonicalReplyRequest())

        assertThat(result).isEqualTo(
            TaskMailDirectSessionActionResult.FallbackToMail(
                detailMessage = "direct action is not available",
                requestId = "req_001",
            ),
        )
    }

    @Test
    fun `send should surface packet ack hard rejection for unresolved session identity`() = runTest {
        val testSubject = RelayTaskMailDirectSessionActionSender(
            relayConnectionClient = FakeSessionActionRelayConnectionClient(
                sendPacketResult = Result.success(
                    RelayPacketAck(
                        packetId = "android-taskmail:session-action:req_001",
                        accepted = false,
                        receiptId = "receipt-1",
                        receivedAt = "2026-03-22T23:00:01Z",
                        errorCode = "session_identity_unresolved",
                        errorMessage = "workspace_id + session_id did not resolve to a current session",
                    ),
                ),
            ),
            timestampProvider = { "2026-03-22T23:00:00Z" },
            requestIdFactory = { "req_001" },
        )

        val result = testSubject.send(canonicalStatusRequest())

        assertThat(result).isEqualTo(
            TaskMailDirectSessionActionResult.Rejected(
                errorMessage = "workspace_id + session_id did not resolve to a current session",
                requestId = "req_001",
                receiptId = "receipt-1",
            ),
        )
    }

    @Test
    fun `send should keep packet ack temporary direct failure on mail fallback path`() = runTest {
        val testSubject = RelayTaskMailDirectSessionActionSender(
            relayConnectionClient = FakeSessionActionRelayConnectionClient(
                sendPacketResult = Result.success(
                    RelayPacketAck(
                        packetId = "android-taskmail:session-action:req_001",
                        accepted = false,
                        receiptId = "receipt-1",
                        receivedAt = "2026-03-22T23:00:01Z",
                        errorCode = "direct_temporarily_unavailable",
                        errorMessage = "direct lane is temporarily unavailable",
                    ),
                ),
            ),
            timestampProvider = { "2026-03-22T23:00:00Z" },
            requestIdFactory = { "req_001" },
        )

        val result = testSubject.send(canonicalReplyRequest())

        assertThat(result).isEqualTo(
            TaskMailDirectSessionActionResult.FallbackToMail(
                detailMessage = "direct lane is temporarily unavailable",
                requestId = "req_001",
                receiptId = "receipt-1",
            ),
        )
    }
}

private class FakeSessionActionRelayConnectionClient(
    private val connectResult: Result<RelayHelloAck> = Result.success(
        RelayHelloAck(
            messageType = "hello_ack",
            connectionId = "connection-1",
            serverTime = "2026-03-21T12:00:00Z",
            heartbeatSeconds = 30,
        ),
    ),
    private val sendPacketResult: Result<RelayPacketAck> = Result.success(
        RelayPacketAck(
            packetId = "packet-1",
            accepted = true,
            receiptId = "receipt-1",
            receivedAt = "2026-03-21T12:00:01Z",
        ),
    ),
) : RelayConnectionClient {
    private val mutableConnectionState = MutableStateFlow<RelayConnectionState>(RelayConnectionState.Idle)
    private val mutableSessionUpdates = MutableSharedFlow<RelaySessionUpdate>(extraBufferCapacity = 1)
    private val mutableServerEvents = MutableSharedFlow<RelayEvent>(extraBufferCapacity = 1)
    private val mutableServerResults = MutableSharedFlow<RelayResult>(extraBufferCapacity = 1)

    val sentPackets = mutableListOf<RelayPacket>()

    override val connectionState = mutableConnectionState
    override val sessionUpdates: SharedFlow<RelaySessionUpdate> = mutableSessionUpdates
    override val serverEvents: SharedFlow<RelayEvent> = mutableServerEvents
    override val serverResults: SharedFlow<RelayResult> = mutableServerResults

    override suspend fun connect(config: RelayTransportConfig): Result<RelayHelloAck> = connectResult

    override suspend fun sendPacket(
        packet: RelayPacket,
        ackTimeoutMillis: Long,
    ): Result<RelayPacketAck> {
        sentPackets += packet
        return sendPacketResult
    }

    override suspend fun disconnect() {
        mutableConnectionState.value = RelayConnectionState.Idle
    }
}

private fun canonicalReplyRequest(): TaskMailDirectSessionActionRequest.Reply {
    return TaskMailDirectSessionActionRequest.Reply(
        target = canonicalTarget(),
        replyText = "Please continue and keep the current scope.",
    )
}

private fun canonicalStatusRequest(): TaskMailDirectSessionActionRequest.Status {
    return TaskMailDirectSessionActionRequest.Status(
        target = canonicalTarget(),
    )
}

private fun canonicalTarget(): TaskMailDirectSessionActionTarget {
    return TaskMailDirectSessionActionTarget(
        workspaceId = "ws_repo_main",
        sessionId = "thread_041",
        threadId = "thread_041",
    )
}

private fun assertCanonicalReplyPacket(sentPacket: RelayPacket) {
    assertThat(sentPacket.clientTraceId).isEqualTo("req_001")
    assertThat(sentPacket.sentAt).isEqualTo("2026-03-22T23:00:00Z")
    assertThat(sentPacket.dispatchMetadata.string("channel")).isEqualTo("taskmail_android_direct")
    assertThat(sentPacket.dispatchMetadata.string("schema_version"))
        .isEqualTo("post-creation-session-action-contract-v1")
    assertThat(sentPacket.dispatchMetadata.string("action")).isEqualTo("reply")
    assertThat(sentPacket.dispatchMetadata.string("fallback_policy")).isEqualTo("mail")
    assertThat(sentPacket.taskRunPacket.string("schema_version"))
        .isEqualTo("post-creation-session-action-contract-v1")
    assertThat(sentPacket.taskRunPacket.string("action")).isEqualTo("reply")
    assertThat(sentPacket.taskRunPacket.string("request_id")).isEqualTo("req_001")

    val origin = sentPacket.taskRunPacket.jsonObject("origin")
    assertThat(origin.string("client")).isEqualTo("android_taskmail")

    val target = sentPacket.taskRunPacket.jsonObject("target")
    assertThat(target.string("scope")).isEqualTo("current_session")
    assertThat(target.string("workspace_id")).isEqualTo("ws_repo_main")
    assertThat(target.string("session_id")).isEqualTo("thread_041")
    assertThat(target.string("thread_id")).isEqualTo("thread_041")

    val reply = sentPacket.taskRunPacket.jsonObject("reply")
    assertThat(reply.string("reply_text")).isEqualTo("Please continue and keep the current scope.")
}

private fun assertCanonicalStatusPacket(sentPacket: RelayPacket) {
    assertThat(sentPacket.clientTraceId).isEqualTo("req_002")
    assertThat(sentPacket.sentAt).isEqualTo("2026-03-22T23:00:05Z")
    assertThat(sentPacket.dispatchMetadata.string("channel")).isEqualTo("taskmail_android_direct")
    assertThat(sentPacket.dispatchMetadata.string("schema_version"))
        .isEqualTo("post-creation-session-action-contract-v1")
    assertThat(sentPacket.dispatchMetadata.string("action")).isEqualTo("status")
    assertThat(sentPacket.dispatchMetadata.string("fallback_policy")).isEqualTo("mail")
    assertThat(sentPacket.taskRunPacket.string("schema_version"))
        .isEqualTo("post-creation-session-action-contract-v1")
    assertThat(sentPacket.taskRunPacket.string("action")).isEqualTo("status")
    assertThat(sentPacket.taskRunPacket.string("request_id")).isEqualTo("req_002")

    val target = sentPacket.taskRunPacket.jsonObject("target")
    assertThat(target.string("scope")).isEqualTo("current_session")
    assertThat(target.string("workspace_id")).isEqualTo("ws_repo_main")
    assertThat(target.string("session_id")).isEqualTo("thread_041")
    assertThat(target.string("thread_id")).isEqualTo("thread_041")
    assertThat(sentPacket.taskRunPacket.jsonObject("status").keys).isEqualTo(emptySet())
}

private fun JsonObject.string(key: String): String {
    return getValue(key).jsonPrimitive.content
}

private fun JsonObject.jsonObject(key: String): JsonObject {
    return getValue(key).jsonObject
}
