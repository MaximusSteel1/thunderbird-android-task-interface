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
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
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
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailBackend
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailDirectNewTaskResult
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskDraft
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskMode
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskPermission

class RelayTaskMailDirectNewTaskSenderTest {

    @Test
    fun `send should build canonical phase2 packet and return accepted result`() = runTest {
        val relayConnectionClient = FakeDirectRelayConnectionClient(
            sendPacketResult = Result.success(
                RelayPacketAck(
                    packetId = "android-taskmail:new-task:req_001",
                    accepted = true,
                    receiptId = "receipt-1",
                    receivedAt = "2026-03-21T12:30:01Z",
                    transportMessageId = "transport-1",
                ),
            ),
        )
        val testSubject = RelayTaskMailDirectNewTaskSender(
            relayConnectionClient = relayConnectionClient,
            timestampProvider = { "2026-03-21T12:30:00Z" },
            requestIdFactory = { "req_001" },
        )

        val result = testSubject.send(canonicalDraft())

        assertThat(result).isEqualTo(
            TaskMailDirectNewTaskResult.Accepted(
                requestId = "req_001",
                receiptId = "receipt-1",
                transportMessageId = "transport-1",
            ),
        )
        assertThat(relayConnectionClient.sentPackets.map(RelayPacket::packetId))
            .containsExactly("android-taskmail:new-task:req_001")

        assertCanonicalPacket(relayConnectionClient.sentPackets.single())
    }

    @Test
    fun `send should route capability rejection to fallback-classified result`() = runTest {
        val testSubject = RelayTaskMailDirectNewTaskSender(
            relayConnectionClient = FakeDirectRelayConnectionClient(
                sendPacketResult = Result.failure(
                    RelayServerException(
                        code = "unsupported_action",
                        message = "direct action is not available",
                    ),
                ),
            ),
            timestampProvider = { "2026-03-21T12:30:00Z" },
            requestIdFactory = { "req_001" },
        )

        val result = testSubject.send(minimalDraft())

        assertThat(result).isEqualTo(
            TaskMailDirectNewTaskResult.FallbackToMail(
                detailMessage = "direct action is not available",
            ),
        )
    }

    @Test
    fun `send should surface invalid payload as hard rejection`() = runTest {
        val testSubject = RelayTaskMailDirectNewTaskSender(
            relayConnectionClient = FakeDirectRelayConnectionClient(
                sendPacketResult = Result.failure(
                    RelayServerException(
                        code = "invalid_payload",
                        message = "task_text is required",
                    ),
                ),
            ),
            timestampProvider = { "2026-03-21T12:30:00Z" },
            requestIdFactory = { "req_001" },
        )

        val result = testSubject.send(minimalDraft())

        assertThat(result).isEqualTo(
            TaskMailDirectNewTaskResult.Rejected(
                errorMessage = "task_text is required",
            ),
        )
    }

    @Test
    fun `send should surface packet ack hard rejection when relay returns error code`() = runTest {
        val testSubject = RelayTaskMailDirectNewTaskSender(
            relayConnectionClient = FakeDirectRelayConnectionClient(
                sendPacketResult = Result.success(
                    RelayPacketAck(
                        packetId = "android-taskmail:new-task:req_001",
                        accepted = false,
                        receiptId = "receipt-1",
                        receivedAt = "2026-03-21T12:30:01Z",
                        errorCode = "invalid_payload",
                        errorMessage = "new_task.task_text must be a non-empty string",
                    ),
                ),
            ),
            timestampProvider = { "2026-03-21T12:30:00Z" },
            requestIdFactory = { "req_001" },
        )

        val result = testSubject.send(minimalDraft())

        assertThat(result).isEqualTo(
            TaskMailDirectNewTaskResult.Rejected(
                errorMessage = "new_task.task_text must be a non-empty string",
            ),
        )
    }

    @Test
    fun `send should surface packet ack hard rejection when error message carries hard code prefix`() = runTest {
        val testSubject = RelayTaskMailDirectNewTaskSender(
            relayConnectionClient = FakeDirectRelayConnectionClient(
                sendPacketResult = Result.success(
                    RelayPacketAck(
                        packetId = "android-taskmail:new-task:req_001",
                        accepted = false,
                        receiptId = "receipt-1",
                        receivedAt = "2026-03-21T12:30:01Z",
                        errorMessage = "validation_failed: repo_path must be a non-empty string",
                    ),
                ),
            ),
            timestampProvider = { "2026-03-21T12:30:00Z" },
            requestIdFactory = { "req_001" },
        )

        val result = testSubject.send(minimalDraft())

        assertThat(result).isEqualTo(
            TaskMailDirectNewTaskResult.Rejected(
                errorMessage = "validation_failed: repo_path must be a non-empty string",
            ),
        )
    }

    @Test
    fun `send should keep packet ack capability rejection on fallback-classified path`() = runTest {
        val testSubject = RelayTaskMailDirectNewTaskSender(
            relayConnectionClient = FakeDirectRelayConnectionClient(
                sendPacketResult = Result.success(
                    RelayPacketAck(
                        packetId = "android-taskmail:new-task:req_001",
                        accepted = false,
                        receiptId = "receipt-1",
                        receivedAt = "2026-03-21T12:30:01Z",
                        errorMessage = "direct action is not available",
                    ),
                ),
            ),
            timestampProvider = { "2026-03-21T12:30:00Z" },
            requestIdFactory = { "req_001" },
        )

        val result = testSubject.send(minimalDraft())

        assertThat(result).isEqualTo(
            TaskMailDirectNewTaskResult.FallbackToMail(
                detailMessage = "direct action is not available",
            ),
        )
    }
}

private class FakeDirectRelayConnectionClient(
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

private fun minimalDraft(): TaskMailNewTaskDraft {
    return TaskMailNewTaskDraft(
        senderAccountId = "account-1",
        backend = TaskMailBackend.Codex,
        repoPath = "E:\\projects\\android_task_manager",
        taskText = "Audit the current direct-send handoff path.",
        subjectTitle = "Audit the direct-send handoff path",
    )
}

private fun canonicalDraft(): TaskMailNewTaskDraft {
    return TaskMailNewTaskDraft(
        senderAccountId = "account-1",
        backend = TaskMailBackend.Codex,
        repoPath = "E:\\projects\\android_task_manager",
        workdir = "feature/taskmail/internal",
        taskText = "Audit the current direct-send handoff path.",
        subjectTitle = "Audit the direct-send handoff path",
        timeoutMinutes = 120,
        mode = TaskMailNewTaskMode.AnalysisOnly,
        permission = TaskMailNewTaskPermission.Highest,
        profile = "android",
        acceptanceCriteria = listOf(
            "List any contract mismatches.",
            "Do not change user-facing reply semantics.",
        ),
    )
}

private fun assertCanonicalPacket(sentPacket: RelayPacket) {
    assertThat(sentPacket.clientTraceId).isEqualTo("req_001")
    assertThat(sentPacket.sentAt).isEqualTo("2026-03-21T12:30:00Z")
    assertThat(sentPacket.dispatchMetadata.string("channel")).isEqualTo("taskmail_android_direct")
    assertThat(sentPacket.dispatchMetadata.string("schema_version")).isEqualTo("phase2-direct-outbound-contract-v1")
    assertThat(sentPacket.dispatchMetadata.string("action")).isEqualTo("new_task")
    assertThat(sentPacket.dispatchMetadata.string("fallback_policy")).isEqualTo("none")
    assertThat(sentPacket.taskRunPacket.string("schema_version")).isEqualTo("phase2-direct-outbound-contract-v1")
    assertThat(sentPacket.taskRunPacket.string("action")).isEqualTo("new_task")
    assertThat(sentPacket.taskRunPacket.string("request_id")).isEqualTo("req_001")

    val origin = sentPacket.taskRunPacket.jsonObject("origin")
    assertThat(origin.string("client")).isEqualTo("android_taskmail")
    assertThat(origin.string("sender_account_uuid")).isEqualTo("account-1")

    val newTask = sentPacket.taskRunPacket.jsonObject("new_task")
    assertThat(newTask.string("backend")).isEqualTo("codex")
    assertThat(newTask.string("repo_path")).isEqualTo("E:\\projects\\android_task_manager")
    assertThat(newTask.string("workdir")).isEqualTo("feature/taskmail/internal")
    assertThat(newTask.string("task_text")).isEqualTo("Audit the current direct-send handoff path.")
    assertThat(newTask.string("subject_title")).isEqualTo("Audit the direct-send handoff path")
    assertThat(newTask.int("timeout_minutes")).isEqualTo(120)
    assertThat(newTask.string("mode")).isEqualTo("analysis_only")
    assertThat(newTask.string("profile")).isEqualTo("android")
    assertThat(newTask.string("permission")).isEqualTo("highest")
    assertThat(newTask.stringArray("acceptance")).containsExactly(
        "List any contract mismatches.",
        "Do not change user-facing reply semantics.",
    )
}

private fun JsonObject.string(key: String): String {
    return getValue(key).jsonPrimitive.content
}

private fun JsonObject.int(key: String): Int {
    return getValue(key).jsonPrimitive.int
}

private fun JsonObject.jsonObject(key: String): JsonObject {
    return getValue(key).jsonObject
}

private fun JsonObject.stringArray(key: String): List<String> {
    return getValue(key).jsonArray.map { it.jsonPrimitive.content }
}
