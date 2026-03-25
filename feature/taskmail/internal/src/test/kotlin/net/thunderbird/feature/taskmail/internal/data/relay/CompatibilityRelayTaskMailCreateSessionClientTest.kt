package net.thunderbird.feature.taskmail.internal.data.relay

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.coroutines.flow.MutableStateFlow
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayHelloAck
import net.thunderbird.feature.taskmail.internal.domain.model.RelayBootstrapResult
import net.thunderbird.feature.taskmail.internal.domain.model.RelayBootstrapStatus
import net.thunderbird.feature.taskmail.internal.domain.model.RelayConnectionState
import net.thunderbird.feature.taskmail.internal.domain.model.RelayHealthStatus
import net.thunderbird.feature.taskmail.internal.domain.model.RelayTransportConfig
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailBackend
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailDirectOutcome
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailDirectSendEvidence
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailDirectSwitchGate
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailCreateSessionAckStatus
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailCreateSessionResult
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailCreateSessionSubmitAck
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailDirectNewTaskResult
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailDirectNewTaskSender
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskDraft

class CompatibilityRelayTaskMailCreateSessionClientTest {

    @Test
    fun `createSession should map accepted legacy direct result into submitted compatibility result`() =
        kotlinx.coroutines.test.runTest {
            val relayBootstrapManager = FakeRelayBootstrapManager(
                bootstrapResult = RelayBootstrapResult(
                    status = RelayBootstrapStatus.HelloAck,
                ),
            )
            val directNewTaskSender = FakeTaskMailDirectNewTaskSender(
                result = TaskMailDirectNewTaskResult.Accepted(
                    requestId = "req_001",
                    receiptId = "receipt_001",
                    transportMessageId = "transport_001",
                ),
            )
            val testSubject = CompatibilityRelayTaskMailCreateSessionClient(
                relayBootstrapManager = relayBootstrapManager,
                directNewTaskSender = directNewTaskSender,
                commandIdFactory = { "cmd_001" },
            )

            val result = testSubject.createSession(canonicalDraft())

            assertThat(result).isEqualTo(
                TaskMailCreateSessionResult.Submitted(
                    commandId = "cmd_001",
                    submitAck = TaskMailCreateSessionSubmitAck(
                        ackStatus = TaskMailCreateSessionAckStatus.Accepted,
                    ),
                    sessionBinding = null,
                    evidence = TaskMailDirectSendEvidence(
                        bootstrapStatus = RelayBootstrapStatus.HelloAck,
                        outcome = TaskMailDirectOutcome.DirectAccepted,
                        switchGate = TaskMailDirectSwitchGate.KeepDirectDefault,
                        requestId = "req_001",
                        receiptId = "receipt_001",
                        transportMessageId = "transport_001",
                    ),
                ),
            )
            assertThat(directNewTaskSender.sendCallCount).isEqualTo(1)
            assertThat(relayBootstrapManager.disconnectCallCount).isEqualTo(1)
        }

    @Test
    fun `createSession should fail early when relay bootstrap does not reach hello ack`() =
        kotlinx.coroutines.test.runTest {
            val relayBootstrapManager = FakeRelayBootstrapManager(
                bootstrapResult = RelayBootstrapResult(
                    status = RelayBootstrapStatus.NotConfigured,
                    detailMessage = "Relay host, port, and transport token are required.",
                ),
            )
            val directNewTaskSender = FakeTaskMailDirectNewTaskSender(
                result = TaskMailDirectNewTaskResult.Accepted(
                    requestId = "req_001",
                    receiptId = "receipt_001",
                ),
            )
            val testSubject = CompatibilityRelayTaskMailCreateSessionClient(
                relayBootstrapManager = relayBootstrapManager,
                directNewTaskSender = directNewTaskSender,
                commandIdFactory = { "cmd_001" },
            )

            val result = testSubject.createSession(canonicalDraft())

            assertThat(result).isEqualTo(
                TaskMailCreateSessionResult.Failed(
                    errorMessage = "Relay host, port, and transport token are required.",
                    evidence = TaskMailDirectSendEvidence(
                        bootstrapStatus = RelayBootstrapStatus.NotConfigured,
                        outcome = TaskMailDirectOutcome.DirectRejected,
                        switchGate = TaskMailDirectSwitchGate.SwitchBlocker,
                        errorMessage = "Relay host, port, and transport token are required.",
                    ),
                ),
            )
            assertThat(directNewTaskSender.sendCallCount).isEqualTo(0)
            assertThat(relayBootstrapManager.disconnectCallCount).isEqualTo(0)
        }

    @Test
    fun `createSession should map hard relay rejection into rejected create-session result`() =
        kotlinx.coroutines.test.runTest {
            val relayBootstrapManager = FakeRelayBootstrapManager(
                bootstrapResult = RelayBootstrapResult(
                    status = RelayBootstrapStatus.HelloAck,
                ),
            )
            val directNewTaskSender = FakeTaskMailDirectNewTaskSender(
                result = TaskMailDirectNewTaskResult.Rejected(
                    errorMessage = "invalid_payload: task_text is required",
                ),
            )
            val testSubject = CompatibilityRelayTaskMailCreateSessionClient(
                relayBootstrapManager = relayBootstrapManager,
                directNewTaskSender = directNewTaskSender,
                commandIdFactory = { "cmd_001" },
            )

            val result = testSubject.createSession(canonicalDraft())

            assertThat(result).isEqualTo(
                TaskMailCreateSessionResult.Rejected(
                    commandId = "cmd_001",
                    submitAck = TaskMailCreateSessionSubmitAck(
                        ackStatus = TaskMailCreateSessionAckStatus.Rejected,
                        reason = "invalid_payload: task_text is required",
                    ),
                    errorMessage = "invalid_payload: task_text is required",
                    evidence = TaskMailDirectSendEvidence(
                        bootstrapStatus = RelayBootstrapStatus.HelloAck,
                        outcome = TaskMailDirectOutcome.DirectRejected,
                        switchGate = TaskMailDirectSwitchGate.SwitchBlocker,
                        errorMessage = "invalid_payload: task_text is required",
                    ),
                ),
            )
            assertThat(directNewTaskSender.sendCallCount).isEqualTo(1)
            assertThat(relayBootstrapManager.disconnectCallCount).isEqualTo(1)
        }
}

private class FakeRelayBootstrapManager(
    private val bootstrapResult: RelayBootstrapResult,
) : RelayBootstrapManager {
    private val mutableConnectionState = MutableStateFlow<RelayConnectionState>(RelayConnectionState.Idle)

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
        return bootstrapResult
    }
}

private class FakeTaskMailDirectNewTaskSender(
    private val result: TaskMailDirectNewTaskResult,
) : TaskMailDirectNewTaskSender {
    var sendCallCount: Int = 0

    override suspend fun send(draft: TaskMailNewTaskDraft): TaskMailDirectNewTaskResult {
        sendCallCount += 1
        return result
    }
}

private fun canonicalDraft(): TaskMailNewTaskDraft {
    return TaskMailNewTaskDraft(
        senderAccountId = "account_primary",
        backend = TaskMailBackend.Codex,
        repoPath = "E:/projects/android_task_manager",
        taskText = "Audit the VPS-first handshake slice.",
        subjectTitle = "Audit the VPS-first handshake slice.",
        workdir = "feature/taskmail/internal",
        pcId = "pc_workstation_01",
        workspaceId = "workspace_android_app",
    )
}
