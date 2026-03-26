package net.thunderbird.feature.taskmail.internal.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import net.thunderbird.feature.taskmail.internal.data.relay.RelayBootstrapManager
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayHelloAck
import net.thunderbird.feature.taskmail.internal.data.transport.TaskMailNewTaskTransport
import net.thunderbird.feature.taskmail.internal.domain.model.RelayBootstrapResult
import net.thunderbird.feature.taskmail.internal.domain.model.RelayBootstrapStatus
import net.thunderbird.feature.taskmail.internal.domain.model.RelayConnectionState
import net.thunderbird.feature.taskmail.internal.domain.model.RelayHealthStatus
import net.thunderbird.feature.taskmail.internal.domain.model.RelayTransportConfig
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskRequest
import net.thunderbird.feature.taskmail.internal.domain.projectsync.TaskMailDirectProjectSyncResult
import net.thunderbird.feature.taskmail.internal.domain.projectsync.TaskMailDirectProjectSyncSender

class TransportBackedTaskMailProjectSyncRequesterTest {

    @Test
    fun `request sync should return success when direct project sync is accepted`() = runTest {
        val transport = RecordingTaskMailNewTaskTransport()
        val directProjectSyncSender = RecordingTaskMailDirectProjectSyncSender(
            result = TaskMailDirectProjectSyncResult.Accepted(
                requestId = "req_001",
                receiptId = "receipt-1",
                transportMessageId = "transport-1",
            ),
        )
        val testSubject = TransportBackedTaskMailProjectSyncRequester(
            transport = transport,
            directProjectSyncSender = directProjectSyncSender,
            relayBootstrapManager = FakeRelayBootstrapManager(
                bootstrapResult = RelayBootstrapResult(
                    status = RelayBootstrapStatus.HelloAck,
                ),
            ),
        )

        val result = testSubject.requestSync("account-1")

        assertThat(result.isSuccess).isEqualTo(true)
        assertThat(directProjectSyncSender.requestedAccounts).isEqualTo(listOf("account-1"))
        assertThat(transport.requests).isEqualTo(emptyList())
    }

    @Test
    fun `request sync should send canonical sync mail request when direct falls back`() = runTest {
        val transport = RecordingTaskMailNewTaskTransport()
        val directProjectSyncSender = RecordingTaskMailDirectProjectSyncSender(
            result = TaskMailDirectProjectSyncResult.FallbackToMail(
                detailMessage = "unsupported_action",
                requestId = "req_001",
                receiptId = "receipt-1",
            ),
        )
        val testSubject = TransportBackedTaskMailProjectSyncRequester(
            transport = transport,
            directProjectSyncSender = directProjectSyncSender,
            relayBootstrapManager = FakeRelayBootstrapManager(
                bootstrapResult = RelayBootstrapResult(
                    status = RelayBootstrapStatus.HelloAck,
                ),
            ),
        )

        val result = testSubject.requestSync("account-1")

        assertThat(result.isSuccess).isEqualTo(true)
        assertThat(transport.requests).isEqualTo(
            listOf(
                TaskMailNewTaskRequest(
                    accountUuid = "account-1",
                    subject = "[SYNC]",
                    body = "",
                ),
            ),
        )
    }

    @Test
    fun `request sync should send canonical sync mail request when relay bootstrap is unavailable`() = runTest {
        val transport = RecordingTaskMailNewTaskTransport()
        val directProjectSyncSender = RecordingTaskMailDirectProjectSyncSender(
            result = TaskMailDirectProjectSyncResult.Accepted(
                requestId = "req_001",
                receiptId = "receipt-1",
            ),
        )
        val testSubject = TransportBackedTaskMailProjectSyncRequester(
            transport = transport,
            directProjectSyncSender = directProjectSyncSender,
            relayBootstrapManager = FakeRelayBootstrapManager(
                bootstrapResult = RelayBootstrapResult(
                    status = RelayBootstrapStatus.NotConfigured,
                ),
            ),
        )

        val result = testSubject.requestSync("account-1")

        assertThat(result.isSuccess).isEqualTo(true)
        assertThat(directProjectSyncSender.requestedAccounts).isEqualTo(emptyList())
        assertThat(transport.requests).isEqualTo(
            listOf(
                TaskMailNewTaskRequest(
                    accountUuid = "account-1",
                    subject = "[SYNC]",
                    body = "",
                ),
            ),
        )
    }

    @Test
    fun `request sync should surface hard direct rejection and skip mail fallback`() = runTest {
        val transport = RecordingTaskMailNewTaskTransport()
        val directProjectSyncSender = RecordingTaskMailDirectProjectSyncSender(
            result = TaskMailDirectProjectSyncResult.Rejected(
                errorMessage = "invalid_payload",
                requestId = "req_001",
                receiptId = "receipt-1",
            ),
        )
        val testSubject = TransportBackedTaskMailProjectSyncRequester(
            transport = transport,
            directProjectSyncSender = directProjectSyncSender,
            relayBootstrapManager = FakeRelayBootstrapManager(
                bootstrapResult = RelayBootstrapResult(
                    status = RelayBootstrapStatus.HelloAck,
                ),
            ),
        )

        val result = testSubject.requestSync("account-1")

        assertThat(result.isFailure).isEqualTo(true)
        assertThat(result.exceptionOrNull()?.message).isEqualTo("invalid_payload")
        assertThat(transport.requests).isEqualTo(emptyList())
    }

    @Test
    fun `request sync via mail should skip direct and send canonical sync mail request`() = runTest {
        val transport = RecordingTaskMailNewTaskTransport()
        val directProjectSyncSender = RecordingTaskMailDirectProjectSyncSender(
            result = TaskMailDirectProjectSyncResult.Accepted(
                requestId = "req_001",
                receiptId = "receipt-1",
            ),
        )
        val testSubject = TransportBackedTaskMailProjectSyncRequester(
            transport = transport,
            directProjectSyncSender = directProjectSyncSender,
            relayBootstrapManager = FakeRelayBootstrapManager(
                bootstrapResult = RelayBootstrapResult(
                    status = RelayBootstrapStatus.HelloAck,
                ),
            ),
        )

        val result = testSubject.requestSyncViaMail("account-1")

        assertThat(result.isSuccess).isEqualTo(true)
        assertThat(directProjectSyncSender.requestedAccounts).isEqualTo(emptyList())
        assertThat(transport.requests).isEqualTo(
            listOf(
                TaskMailNewTaskRequest(
                    accountUuid = "account-1",
                    subject = "[SYNC]",
                    body = "",
                ),
            ),
        )
    }
}

private class RecordingTaskMailNewTaskTransport : TaskMailNewTaskTransport {
    val requests = mutableListOf<TaskMailNewTaskRequest>()

    override suspend fun send(request: TaskMailNewTaskRequest): Result<Unit> {
        requests += request
        return Result.success(Unit)
    }
}

private class RecordingTaskMailDirectProjectSyncSender(
    private val result: TaskMailDirectProjectSyncResult,
) : TaskMailDirectProjectSyncSender {
    val requestedAccounts = mutableListOf<String>()

    override suspend fun send(accountUuid: String): TaskMailDirectProjectSyncResult {
        requestedAccounts += accountUuid
        return result
    }
}

private class FakeRelayBootstrapManager(
    private val bootstrapResult: RelayBootstrapResult,
) : RelayBootstrapManager {
    private val mutableConnectionState = MutableStateFlow<RelayConnectionState>(RelayConnectionState.Idle)

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
                serverTime = "2026-03-23T10:00:00Z",
                heartbeatSeconds = 30,
            ),
        )
    }

    override suspend fun disconnect() {
        mutableConnectionState.value = RelayConnectionState.Idle
    }

    override suspend fun bootstrap(config: RelayTransportConfig): RelayBootstrapResult {
        return bootstrapResult
    }
}
