package net.thunderbird.feature.taskmail.internal.data.controlplane

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import kotlin.test.Test
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import net.thunderbird.feature.taskmail.internal.data.relay.RelayConnectionClient
import net.thunderbird.feature.taskmail.internal.data.relay.RelayServerException
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayCommand
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayCommandAck
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayEvent
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayHelloAck
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayPacket
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayPacketAck
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayResult
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelaySessionUpdate
import net.thunderbird.feature.taskmail.internal.domain.model.RelayConnectionState
import net.thunderbird.feature.taskmail.internal.domain.model.RelayTransportConfig
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskPermission
import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailDirectSessionActionRequest
import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailDirectSessionActionResult
import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailDirectSessionActionTarget
import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailDirectSessionActionType

class RelayControlTaskMailSessionActionSenderTest {

    @Test
    fun `send should build canonical reply control command and attach observed result snapshot`() = runTest {
        val relayConnectionClient = FakeControlSessionActionRelayConnectionClient(
            sendCommandResult = Result.success(
                RelayCommandAck(
                    accepted = true,
                    ackStatus = "accepted",
                    receiptId = "receipt-1",
                    receivedAt = "2026-03-26T11:00:01Z",
                    transportMessageId = "transport-1",
                ),
            ),
            resultToEmit = RelayResult(
                requestId = "req_001",
                packetId = "android-control:session-action:req_001",
                receiptId = "receipt-1",
                resultId = "result-1",
                resultType = "session_action_result",
                finalStatus = "completed",
                payload = buildJsonObject {
                    put("workspace_id", "ws_repo_main")
                    put("session_id", "session_041")
                    put("summary", "Canonical mail ingress accepted.")
                },
            ),
        )
        val testSubject = RelayControlTaskMailSessionActionSender(
            relayConnectionClient = relayConnectionClient,
            timestampProvider = { "2026-03-26T11:00:00Z" },
            requestIdFactory = { "req_001" },
            resultObserveWindowMillis = 10L,
        )

        val result = testSubject.send(canonicalReplyRequest())

        val acceptedResult = result as TaskMailDirectSessionActionResult.Accepted
        assertThat(acceptedResult.actionType).isEqualTo(TaskMailDirectSessionActionType.Reply)
        assertThat(acceptedResult.requestId).isEqualTo("req_001")
        assertThat(acceptedResult.receiptId).isEqualTo("receipt-1")
        assertThat(acceptedResult.transportMessageId).isEqualTo("transport-1")
        assertThat(acceptedResult.controlPlaneSnapshot).isNotNull()
        assertThat(acceptedResult.controlPlaneSnapshot?.commandAck?.ackStatus).isEqualTo("accepted")
        assertThat(acceptedResult.controlPlaneSnapshot?.result?.summary)
            .isEqualTo("Canonical mail ingress accepted.")
        assertThat(relayConnectionClient.sentCommands.map(RelayCommand::packetId))
            .containsExactly("android-control:session-action:req_001")

        assertCanonicalReplyCommand(relayConnectionClient.sentCommands.single())
    }

    @Test
    fun `send should build canonical status control command and return accepted snapshot without result`() = runTest {
        val relayConnectionClient = FakeControlSessionActionRelayConnectionClient(
            sendCommandResult = Result.success(
                RelayCommandAck(
                    accepted = true,
                    ackStatus = "accepted_but_queued",
                    receiptId = "receipt-2",
                    receivedAt = "2026-03-26T11:00:06Z",
                ),
            ),
        )
        val testSubject = RelayControlTaskMailSessionActionSender(
            relayConnectionClient = relayConnectionClient,
            timestampProvider = { "2026-03-26T11:00:05Z" },
            requestIdFactory = { "req_002" },
            resultObserveWindowMillis = 10L,
        )

        val result = testSubject.send(canonicalStatusRequest())

        val acceptedResult = result as TaskMailDirectSessionActionResult.Accepted
        assertThat(acceptedResult.actionType).isEqualTo(TaskMailDirectSessionActionType.Status)
        assertThat(acceptedResult.requestId).isEqualTo("req_002")
        assertThat(acceptedResult.controlPlaneSnapshot?.commandAck?.ackStatus).isEqualTo("accepted_but_queued")
        assertThat(acceptedResult.controlPlaneSnapshot?.result).isEqualTo(null)
        assertCanonicalStatusCommand(relayConnectionClient.sentCommands.single())
    }

    @Test
    fun `send should surface hard rejection from control send failure`() = runTest {
        val testSubject = RelayControlTaskMailSessionActionSender(
            relayConnectionClient = FakeControlSessionActionRelayConnectionClient(
                sendCommandResult = Result.failure(
                    RelayServerException(
                        code = "session_identity_unresolved",
                        message = "workspace_id + session_id did not resolve to a current session",
                    ),
                ),
            ),
            timestampProvider = { "2026-03-26T11:00:00Z" },
            requestIdFactory = { "req_003" },
            resultObserveWindowMillis = 10L,
        )

        val result = testSubject.send(canonicalStatusRequest())

        assertThat(result).isEqualTo(
            TaskMailDirectSessionActionResult.Rejected(
                errorMessage = "workspace_id + session_id did not resolve to a current session",
                errorCode = "session_identity_unresolved",
                requestId = "req_003",
            ),
        )
    }
}

private class FakeControlSessionActionRelayConnectionClient(
    private val sendCommandResult: Result<RelayCommandAck>,
    private val resultToEmit: RelayResult? = null,
) : RelayConnectionClient {
    private val mutableConnectionState = MutableStateFlow<RelayConnectionState>(RelayConnectionState.Idle)
    private val mutableSessionUpdates = MutableSharedFlow<RelaySessionUpdate>(extraBufferCapacity = 1)
    private val mutableServerEvents = MutableSharedFlow<RelayEvent>(extraBufferCapacity = 1)
    private val mutableServerResults = MutableSharedFlow<RelayResult>(replay = 1, extraBufferCapacity = 1)

    val sentCommands = mutableListOf<RelayCommand>()

    override val connectionState = mutableConnectionState
    override val sessionUpdates: SharedFlow<RelaySessionUpdate> = mutableSessionUpdates
    override val serverEvents: SharedFlow<RelayEvent> = mutableServerEvents
    override val serverResults: SharedFlow<RelayResult> = mutableServerResults

    override suspend fun connect(config: RelayTransportConfig): Result<RelayHelloAck> = error("Not used by this test")

    override suspend fun sendPacket(
        packet: RelayPacket,
        ackTimeoutMillis: Long,
    ): Result<RelayPacketAck> = error("Not used by this test")

    override suspend fun sendCommand(
        command: RelayCommand,
        ackTimeoutMillis: Long,
    ): Result<RelayCommandAck> {
        sentCommands += command
        resultToEmit?.let(mutableServerResults::tryEmit)
        return sendCommandResult
    }

    override suspend fun disconnect() = Unit
}

private fun canonicalReplyRequest(): TaskMailDirectSessionActionRequest.Reply {
    return TaskMailDirectSessionActionRequest.Reply(
        target = canonicalTarget(),
        replyText = "Please continue and keep the current scope.",
        permission = TaskMailNewTaskPermission.Highest,
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
        sessionId = "session_041",
        threadId = "thread_041",
    )
}

private fun assertCanonicalReplyCommand(sentCommand: RelayCommand) {
    assertThat(sentCommand.requestId).isEqualTo("req_001")
    assertThat(sentCommand.packetId).isEqualTo("android-control:session-action:req_001")
    assertThat(sentCommand.commandType).isEqualTo("reply")
    assertThat(sentCommand.payloadSchema).isEqualTo("post-creation-session-action-contract-v1")
    assertThat(sentCommand.sentAt).isEqualTo("2026-03-26T11:00:00Z")
    assertThat(sentCommand.trace.string("trace_id")).isEqualTo("trace_session_action_req_001")

    val origin = sentCommand.payload.jsonObject("origin")
    assertThat(origin.string("client")).isEqualTo("android_taskmail")

    val target = sentCommand.payload.jsonObject("target")
    assertThat(target.string("scope")).isEqualTo("current_session")
    assertThat(target.string("workspace_id")).isEqualTo("ws_repo_main")
    assertThat(target.string("session_id")).isEqualTo("session_041")
    assertThat(target.string("thread_id")).isEqualTo("thread_041")

    val reply = sentCommand.payload.jsonObject("reply")
    assertThat(reply.string("reply_text")).isEqualTo("Please continue and keep the current scope.")
    assertThat(reply.string("permission")).isEqualTo("highest")
}

private fun assertCanonicalStatusCommand(sentCommand: RelayCommand) {
    assertThat(sentCommand.requestId).isEqualTo("req_002")
    assertThat(sentCommand.packetId).isEqualTo("android-control:session-action:req_002")
    assertThat(sentCommand.commandType).isEqualTo("status")
    assertThat(sentCommand.payloadSchema).isEqualTo("post-creation-session-action-contract-v1")
    assertThat(sentCommand.sentAt).isEqualTo("2026-03-26T11:00:05Z")

    val target = sentCommand.payload.jsonObject("target")
    assertThat(target.string("scope")).isEqualTo("current_session")
    assertThat(target.string("workspace_id")).isEqualTo("ws_repo_main")
    assertThat(target.string("session_id")).isEqualTo("session_041")
    assertThat(target.string("thread_id")).isEqualTo("thread_041")
    assertThat(sentCommand.payload.jsonObject("status").keys).isEqualTo(emptySet())
}

private fun JsonObject.string(key: String): String {
    return getValue(key).jsonPrimitive.content
}

private fun JsonObject.jsonObject(key: String): JsonObject {
    return getValue(key).jsonObject
}
