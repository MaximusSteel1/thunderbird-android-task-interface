package net.thunderbird.feature.taskmail.internal.domain.usecase

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import net.thunderbird.feature.taskmail.internal.data.relay.RelayBootstrapManager
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayHelloAck
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
    fun `execute should return direct accepted after successful bootstrap`() = runTest {
        val relayBootstrapManager = DispatchFakeRelayBootstrapManager(
            bootstrapResult = RelayBootstrapResult(status = RelayBootstrapStatus.HelloAck),
        )
        val testSubject = RunTaskMailDirectDispatch(relayBootstrapManager)

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
        assertThat(relayBootstrapManager.bootstrapCallCount).isEqualTo(1)
        assertThat(relayBootstrapManager.disconnectCallCount).isEqualTo(1)
    }

    @Test
    fun `execute should reject when bootstrap is unavailable`() = runTest {
        val relayBootstrapManager = DispatchFakeRelayBootstrapManager(
            bootstrapResult = RelayBootstrapResult(
                status = RelayBootstrapStatus.NotConfigured,
                detailMessage = "Relay host, port, and transport token are required.",
            ),
        )
        val testSubject = RunTaskMailDirectDispatch(relayBootstrapManager)

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
        assertThat(relayBootstrapManager.disconnectCallCount).isEqualTo(0)
    }

    @Test
    fun `execute should keep fallback-classified direct result local`() = runTest {
        val relayBootstrapManager = DispatchFakeRelayBootstrapManager(
            bootstrapResult = RelayBootstrapResult(status = RelayBootstrapStatus.HelloAck),
        )
        val testSubject = RunTaskMailDirectDispatch(relayBootstrapManager)

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
        assertThat(relayBootstrapManager.disconnectCallCount).isEqualTo(1)
    }

    @Test
    fun `execute should use default message when fallback-classified direct result is blank`() = runTest {
        val relayBootstrapManager = DispatchFakeRelayBootstrapManager(
            bootstrapResult = RelayBootstrapResult(status = RelayBootstrapStatus.HelloAck),
        )
        val testSubject = RunTaskMailDirectDispatch(relayBootstrapManager)

        val result = testSubject.execute<String>(
            directSend = { TaskMailDirectAttemptResult.FallbackToMail() },
        )

        assertThat(result).isEqualTo(
            TaskMailDirectDispatchResult.DirectRejected(
                errorMessage = "Relay dispatch failed.",
                evidence = TaskMailDirectSendEvidence(
                    bootstrapStatus = RelayBootstrapStatus.HelloAck,
                    outcome = TaskMailDirectOutcome.DirectRejected,
                    switchGate = TaskMailDirectSwitchGate.SwitchBlocker,
                    errorMessage = "Relay dispatch failed.",
                ),
            ),
        )
        assertThat(relayBootstrapManager.disconnectCallCount).isEqualTo(1)
    }
}

private class DispatchFakeRelayBootstrapManager(
    private val bootstrapResult: RelayBootstrapResult,
) : RelayBootstrapManager {
    private val mutableConnectionState = MutableStateFlow<RelayConnectionState>(RelayConnectionState.Idle)

    var bootstrapCallCount: Int = 0
    var disconnectCallCount: Int = 0

    override val connectionState = mutableConnectionState

    override fun loadConfig(): RelayTransportConfig = RelayTransportConfig()

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
                connectionId = "connection-1",
                serverTime = "2026-03-21T12:00:00Z",
                heartbeatSeconds = 30,
            ),
        )
    }

    override suspend fun disconnect() {
        disconnectCallCount += 1
        mutableConnectionState.value = RelayConnectionState.Idle
    }

    override suspend fun bootstrap(config: RelayTransportConfig): RelayBootstrapResult {
        bootstrapCallCount += 1
        return bootstrapResult
    }
}
