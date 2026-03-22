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

class RunTaskMailDirectOrFallbackTest {

    @Test
    fun `execute should return direct accepted after successful bootstrap`() = runTest {
        val relayBootstrapManager = FakeRelayBootstrapManager(
            bootstrapResult = RelayBootstrapResult(status = RelayBootstrapStatus.HelloAck),
        )
        val testSubject = RunTaskMailDirectOrFallback(relayBootstrapManager)

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
            mailFallback = { Result.success(Unit) },
        )

        assertThat(result).isEqualTo(
            TaskMailDirectOrFallbackResult.DirectAccepted(
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
    fun `execute should use mail fallback when bootstrap is unavailable`() = runTest {
        val relayBootstrapManager = FakeRelayBootstrapManager(
            bootstrapResult = RelayBootstrapResult(status = RelayBootstrapStatus.NotConfigured),
        )
        val testSubject = RunTaskMailDirectOrFallback(relayBootstrapManager)
        var mailFallbackCallCount = 0

        val result = testSubject.execute(
            directSend = { TaskMailDirectAttemptResult.Accepted("receipt-1") },
            mailFallback = {
                mailFallbackCallCount += 1
                Result.success(Unit)
            },
        )

        assertThat(result).isEqualTo(
            TaskMailDirectOrFallbackResult.MailFallbackSucceeded(
                evidence = TaskMailDirectSendEvidence(
                    bootstrapStatus = RelayBootstrapStatus.NotConfigured,
                    outcome = TaskMailDirectOutcome.MailFallbackSucceeded,
                    switchGate = TaskMailDirectSwitchGate.FallbackRequired,
                ),
            ),
        )
        assertThat(mailFallbackCallCount).isEqualTo(1)
        assertThat(relayBootstrapManager.disconnectCallCount).isEqualTo(0)
    }

    @Test
    fun `execute should route direct fallback result to mail fallback`() = runTest {
        val relayBootstrapManager = FakeRelayBootstrapManager(
            bootstrapResult = RelayBootstrapResult(status = RelayBootstrapStatus.HelloAck),
        )
        val testSubject = RunTaskMailDirectOrFallback(relayBootstrapManager)
        var mailFallbackCallCount = 0

        val result = testSubject.execute(
            directSend = { TaskMailDirectAttemptResult.FallbackToMail("unsupported_action") },
            mailFallback = {
                mailFallbackCallCount += 1
                Result.success(Unit)
            },
        )

        assertThat(result).isEqualTo(
            TaskMailDirectOrFallbackResult.MailFallbackSucceeded(
                evidence = TaskMailDirectSendEvidence(
                    bootstrapStatus = RelayBootstrapStatus.HelloAck,
                    outcome = TaskMailDirectOutcome.MailFallbackSucceeded,
                    switchGate = TaskMailDirectSwitchGate.FallbackRequired,
                    fallbackReason = "unsupported_action",
                ),
            ),
        )
        assertThat(mailFallbackCallCount).isEqualTo(1)
        assertThat(relayBootstrapManager.disconnectCallCount).isEqualTo(1)
    }

    @Test
    fun `execute should keep hard rejection local and skip mail fallback`() = runTest {
        val relayBootstrapManager = FakeRelayBootstrapManager(
            bootstrapResult = RelayBootstrapResult(status = RelayBootstrapStatus.HelloAck),
        )
        val testSubject = RunTaskMailDirectOrFallback(relayBootstrapManager)
        var mailFallbackCallCount = 0

        val result = testSubject.execute(
            directSend = { TaskMailDirectAttemptResult.Rejected("invalid_payload") },
            mailFallback = {
                mailFallbackCallCount += 1
                Result.success(Unit)
            },
        )

        assertThat(result).isEqualTo(
            TaskMailDirectOrFallbackResult.DirectRejected(
                errorMessage = "invalid_payload",
                evidence = TaskMailDirectSendEvidence(
                    bootstrapStatus = RelayBootstrapStatus.HelloAck,
                    outcome = TaskMailDirectOutcome.DirectRejected,
                    switchGate = TaskMailDirectSwitchGate.SwitchBlocker,
                    errorMessage = "invalid_payload",
                ),
            ),
        )
        assertThat(mailFallbackCallCount).isEqualTo(0)
        assertThat(relayBootstrapManager.disconnectCallCount).isEqualTo(1)
    }

    @Test
    fun `execute should fall back to mail when direct send throws`() = runTest {
        val relayBootstrapManager = FakeRelayBootstrapManager(
            bootstrapResult = RelayBootstrapResult(status = RelayBootstrapStatus.HelloAck),
        )
        val testSubject = RunTaskMailDirectOrFallback(relayBootstrapManager)
        var mailFallbackCallCount = 0

        val result = testSubject.execute<String>(
            directSend = { error("send failed") },
            mailFallback = {
                mailFallbackCallCount += 1
                Result.failure(IllegalStateException("mail failed"))
            },
        )

        assertThat(result).isEqualTo(
            TaskMailDirectOrFallbackResult.MailFallbackFailed(
                errorMessage = "mail failed",
                evidence = TaskMailDirectSendEvidence(
                    bootstrapStatus = RelayBootstrapStatus.HelloAck,
                    outcome = TaskMailDirectOutcome.MailFallbackFailed,
                    switchGate = TaskMailDirectSwitchGate.FallbackRequired,
                    fallbackReason = "send failed",
                    errorMessage = "mail failed",
                ),
            ),
        )
        assertThat(mailFallbackCallCount).isEqualTo(1)
        assertThat(relayBootstrapManager.disconnectCallCount).isEqualTo(1)
    }
}

private class FakeRelayBootstrapManager(
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
