package net.thunderbird.feature.taskmail.internal.domain.usecase

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.test.runTest
import net.thunderbird.feature.taskmail.internal.data.relay.RelayBootstrapManager
import net.thunderbird.feature.taskmail.internal.data.relay.RelayConnectionClient
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayCommand
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayCommandAck
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayEvent
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayHelloAck
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayPacket
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayPacketAck
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayResult
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelaySessionUpdate
import net.thunderbird.feature.taskmail.internal.domain.model.RelayBootstrapResult
import net.thunderbird.feature.taskmail.internal.domain.model.RelayBootstrapStatus
import net.thunderbird.feature.taskmail.internal.domain.model.RelayConnectionState
import net.thunderbird.feature.taskmail.internal.domain.model.RelayHealthStatus
import net.thunderbird.feature.taskmail.internal.domain.model.RelayTransportConfig
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailDirectAcceptedEvidence
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailDirectOutcome
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailDirectSendEvidence
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailDirectSwitchGate

class RunTaskMailDirectDispatchTest {

    @Test
    fun `execute should return direct accepted after successful control bootstrap`() = runTest {
        val relayConnectionClient = DispatchFakeRelayConnectionClient()
        val testSubject = RunTaskMailDirectDispatch(
            relayBootstrapManager = DispatchFakeRelayBootstrapManager(),
            relayConnectionClient = relayConnectionClient,
        )

        val result = testSubject.execute(
            directSend = {
                TaskMailDirectAttemptResult.Accepted(
                    payload = "receipt-1",
                    acceptedEvidence = TaskMailDirectAcceptedEvidence(
                        requestId = "req_001",
                        receiptId = "receipt-1",
                        transportMessageId = "transport-1",
                    ),
                )
            },
        )

        assertThat(result).isEqualTo(
            TaskMailDirectDispatchResult.DirectAccepted(
                payload = "receipt-1",
                evidence = TaskMailDirectSendEvidence(
                    bootstrapStatus = RelayBootstrapStatus.HelloAck,
                    outcome = TaskMailDirectOutcome.DirectAccepted,
                    switchGate = TaskMailDirectSwitchGate.KeepDirectDefault,
                    requestId = "req_001",
                    receiptId = "receipt-1",
                    transportMessageId = "transport-1",
                ),
            ),
        )
        assertThat(relayConnectionClient.connectedConfig?.path).isEqualTo("/control")
        assertThat(relayConnectionClient.disconnectCallCount).isEqualTo(1)
    }

    @Test
    fun `execute should reject when relay transport config is missing`() = runTest {
        val relayConnectionClient = DispatchFakeRelayConnectionClient()
        val testSubject = RunTaskMailDirectDispatch(
            relayBootstrapManager = DispatchFakeRelayBootstrapManager(
                config = RelayTransportConfig(),
            ),
            relayConnectionClient = relayConnectionClient,
        )

        val result = testSubject.execute(
            directSend = { TaskMailDirectAttemptResult.Accepted("receipt-1") },
        )

        assertThat(result).isEqualTo(
            TaskMailDirectDispatchResult.DirectRejected(
                errorMessage = "Relay host, port, and transport token are required.",
                evidence = TaskMailDirectSendEvidence(
                    bootstrapStatus = RelayBootstrapStatus.NotConfigured,
                    outcome = TaskMailDirectOutcome.DirectRejected,
                    switchGate = TaskMailDirectSwitchGate.SwitchBlocker,
                    errorMessage = "Relay host, port, and transport token are required.",
                ),
            ),
        )
        assertThat(relayConnectionClient.disconnectCallCount).isEqualTo(0)
    }

    @Test
    fun `execute should reject when control hello ack misses session action schema`() = runTest {
        val relayConnectionClient = DispatchFakeRelayConnectionClient(
            connectResult = Result.success(
                RelayHelloAck(
                    messageType = "hello_ack",
                    connectionId = "connection-1",
                    serverTime = "2026-03-26T10:00:00Z",
                    heartbeatSeconds = 30,
                    acceptedPayloadSchemas = listOf("taskmail-bootstrap-control-contract-v2"),
                ),
            ),
        )
        val testSubject = RunTaskMailDirectDispatch(
            relayBootstrapManager = DispatchFakeRelayBootstrapManager(),
            relayConnectionClient = relayConnectionClient,
        )

        val result = testSubject.execute(
            directSend = { TaskMailDirectAttemptResult.Accepted("receipt-1") },
        )

        assertThat(result).isEqualTo(
            TaskMailDirectDispatchResult.DirectRejected(
                errorMessage = "Relay /control hello_ack did not advertise post-creation session actions.",
                evidence = TaskMailDirectSendEvidence(
                    bootstrapStatus = RelayBootstrapStatus.UnexpectedResponse,
                    outcome = TaskMailDirectOutcome.DirectRejected,
                    switchGate = TaskMailDirectSwitchGate.SwitchBlocker,
                    errorMessage = "Relay /control hello_ack did not advertise post-creation session actions.",
                ),
            ),
        )
    }

    @Test
    fun `execute should keep fallback-classified direct result local`() = runTest {
        val relayConnectionClient = DispatchFakeRelayConnectionClient()
        val testSubject = RunTaskMailDirectDispatch(
            relayBootstrapManager = DispatchFakeRelayBootstrapManager(),
            relayConnectionClient = relayConnectionClient,
        )

        val result = testSubject.execute(
            directSend = {
                TaskMailDirectAttemptResult.FallbackToMail(
                    detailMessage = "unsupported_action",
                    requestId = "req_101",
                    receiptId = "receipt-101",
                    transportMessageId = "transport-101",
                )
            },
        )

        assertThat(result).isEqualTo(
            TaskMailDirectDispatchResult.DirectRejected(
                errorMessage = "unsupported_action",
                evidence = TaskMailDirectSendEvidence(
                    bootstrapStatus = RelayBootstrapStatus.HelloAck,
                    outcome = TaskMailDirectOutcome.DirectRejected,
                    switchGate = TaskMailDirectSwitchGate.SwitchBlocker,
                    requestId = "req_101",
                    receiptId = "receipt-101",
                    transportMessageId = "transport-101",
                    fallbackReason = "unsupported_action",
                    errorMessage = "unsupported_action",
                ),
            ),
        )
        assertThat(relayConnectionClient.disconnectCallCount).isEqualTo(1)
    }
}

private class DispatchFakeRelayBootstrapManager(
    private val config: RelayTransportConfig = RelayTransportConfig(
        host = "relay.example.test",
        port = 8787,
        transportToken = "transport-token",
    ),
) : RelayBootstrapManager {
    private val mutableConnectionState = MutableStateFlow<RelayConnectionState>(RelayConnectionState.Idle)

    override val connectionState = mutableConnectionState

    override fun loadConfig(): RelayTransportConfig = config

    override fun saveConfig(config: RelayTransportConfig): Boolean = true

    override suspend fun probeHealth(config: RelayTransportConfig): Result<RelayHealthStatus> {
        return Result.success(
            RelayHealthStatus(
                status = "ok",
                service = "mail-runner-relay",
                listenHost = "0.0.0.0",
                listenPort = 8787,
                sessionCount = 0,
                packetCount = 0,
                tlsEnabled = false,
                transportTokenId = "token-id",
            ),
        )
    }

    override suspend fun connect(config: RelayTransportConfig): Result<RelayHelloAck> {
        return Result.success(
            RelayHelloAck(
                messageType = "hello_ack",
                connectionId = "connection-legacy",
                serverTime = "2026-03-26T10:00:00Z",
                heartbeatSeconds = 30,
            ),
        )
    }

    override suspend fun disconnect() {
        mutableConnectionState.value = RelayConnectionState.Idle
    }

    override suspend fun bootstrap(config: RelayTransportConfig): RelayBootstrapResult {
        return RelayBootstrapResult(status = RelayBootstrapStatus.HelloAck)
    }
}

private class DispatchFakeRelayConnectionClient(
    private val connectResult: Result<RelayHelloAck> = Result.success(
        RelayHelloAck(
            messageType = "hello_ack",
            connectionId = "connection-1",
            serverTime = "2026-03-26T10:00:00Z",
            heartbeatSeconds = 30,
            acceptedPayloadSchemas = listOf(
                "taskmail-bootstrap-control-contract-v2",
                "post-creation-session-action-contract-v1",
            ),
        ),
    ),
) : RelayConnectionClient {
    private val mutableConnectionState = MutableStateFlow<RelayConnectionState>(RelayConnectionState.Idle)
    private val mutableSessionUpdates = MutableSharedFlow<RelaySessionUpdate>(extraBufferCapacity = 1)
    private val mutableServerEvents = MutableSharedFlow<RelayEvent>(extraBufferCapacity = 1)
    private val mutableServerResults = MutableSharedFlow<RelayResult>(extraBufferCapacity = 1)

    var connectedConfig: RelayTransportConfig? = null
    var disconnectCallCount: Int = 0

    override val connectionState = mutableConnectionState
    override val sessionUpdates: SharedFlow<RelaySessionUpdate> = mutableSessionUpdates
    override val serverEvents: SharedFlow<RelayEvent> = mutableServerEvents
    override val serverResults: SharedFlow<RelayResult> = mutableServerResults

    override suspend fun connect(config: RelayTransportConfig): Result<RelayHelloAck> {
        connectedConfig = config
        return connectResult
    }

    override suspend fun connect(
        config: RelayTransportConfig,
        supportedPayloadSchemas: List<String>,
    ): Result<RelayHelloAck> {
        connectedConfig = config
        return connectResult
    }

    override suspend fun sendPacket(
        packet: RelayPacket,
        ackTimeoutMillis: Long,
    ): Result<RelayPacketAck> = error("Not used by this test")

    override suspend fun sendCommand(
        command: RelayCommand,
        ackTimeoutMillis: Long,
    ): Result<RelayCommandAck> = error("Not used by this test")

    override suspend fun disconnect() {
        disconnectCallCount += 1
        mutableConnectionState.value = RelayConnectionState.Idle
    }
}
