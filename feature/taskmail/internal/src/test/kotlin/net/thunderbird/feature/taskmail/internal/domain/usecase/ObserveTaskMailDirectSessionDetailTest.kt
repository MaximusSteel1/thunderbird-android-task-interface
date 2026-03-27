package net.thunderbird.feature.taskmail.internal.domain.usecase

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import net.thunderbird.core.logging.LogMessage
import net.thunderbird.core.logging.LogTag
import net.thunderbird.core.logging.Logger
import net.thunderbird.feature.taskmail.internal.data.direct.TaskMailDirectSessionProjector
import net.thunderbird.feature.taskmail.internal.data.relay.RelayBootstrapManager
import net.thunderbird.feature.taskmail.internal.data.relay.RelayConnectionClient
import net.thunderbird.feature.taskmail.internal.data.relay.RelayTaskMailDirectSessionDetailSubscriber
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayEffectiveExecution
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayEvent
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayHelloAck
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayPacket
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayPacketAck
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayQuestion
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayQuestionState
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayResult
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelaySessionDelta
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelaySessionSnapshot
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelaySessionUpdate
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayStructuredPayload
import net.thunderbird.feature.taskmail.internal.domain.model.RelayBootstrapResult
import net.thunderbird.feature.taskmail.internal.domain.model.RelayBootstrapStatus
import net.thunderbird.feature.taskmail.internal.domain.model.RelayConnectionState
import net.thunderbird.feature.taskmail.internal.domain.model.RelayHealthStatus
import net.thunderbird.feature.taskmail.internal.domain.model.RelayTransportConfig
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionKey
import net.thunderbird.feature.taskmail.internal.preview.TaskMailPreviewData

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveTaskMailDirectSessionDetailTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @Test
    fun `invoke should send direct detail subscribe packet and emit snapshot projection`() = runTest(testDispatcher) {
        val relayConnectionClient = DirectDetailFakeRelayConnectionClient()
        val relayBootstrapManager = DirectDetailFakeRelayBootstrapManager(relayConnectionClient)
        val testSubject = createTestSubject(
            relayConnectionClient = relayConnectionClient,
            relayBootstrapManager = relayBootstrapManager,
            requestIdFactory = { "req_snapshot" },
        )

        val projectionDeferred = async {
            testSubject(sampleDetail()).first()
        }

        advanceUntilIdle()
        relayConnectionClient.emitSessionUpdate(
            snapshotUpdate(
                subscriptionId = "sub_snapshot",
                sequence = 1,
                workspaceId = "workspace_canonical",
                status = "running",
                lastSummary = "Direct running summary",
            ),
        )

        val projection = projectionDeferred.await()
        val subscription = relayConnectionClient.sentPackets.single().subscriptionObject()

        assertThat(relayBootstrapManager.connectCount).isEqualTo(1)
        assertThat(subscription["reason"]?.jsonPrimitive?.content).isEqualTo("detail_open")
        assertThat(subscription["workspace_id"]).isEqualTo(null)
        assertThat(subscription["repo_path"]?.jsonPrimitive?.content).isEqualTo("E:/projects/android_task_manager")
        assertThat(subscription["workdir"]?.jsonPrimitive?.content).isEqualTo("feature/taskmail/internal")
        assertThat(subscription["session_id"]?.jsonPrimitive?.content).isEqualTo("session_001")
        assertThat(subscription["thread_id"]?.jsonPrimitive?.content).isEqualTo("thread_001")
        assertThat(projection.canonicalWorkspaceId).isEqualTo("workspace_canonical")
        assertThat(projection.lastSummary).isEqualTo("Direct running summary")
        assertThat(projection.headerStatus.name).isEqualTo("Running")
    }

    @Test
    fun `invoke should subscribe without thread id when detail only has workspace and session ids`() = runTest(testDispatcher) {
        val relayConnectionClient = DirectDetailFakeRelayConnectionClient()
        val relayBootstrapManager = DirectDetailFakeRelayBootstrapManager(relayConnectionClient)
        val testSubject = createTestSubject(
            relayConnectionClient = relayConnectionClient,
            relayBootstrapManager = relayBootstrapManager,
            requestIdFactory = { "req_without_thread" },
        )

        val projectionDeferred = async {
            testSubject(sampleDetailWithoutThreadId()).first()
        }

        advanceUntilIdle()
        relayConnectionClient.emitSessionUpdate(
            snapshotUpdate(
                subscriptionId = "sub_without_thread",
                sequence = 1,
                workspaceId = "workspace_provisional",
                status = "running",
                lastSummary = "Direct running summary",
            ),
        )

        projectionDeferred.await()
        val subscription = relayConnectionClient.sentPackets.single().subscriptionObject()

        assertThat(subscription["workspace_id"]?.jsonPrimitive?.content).isEqualTo("workspace_provisional")
        assertThat(subscription["session_id"]?.jsonPrimitive?.content).isEqualTo("session_001")
        assertThat(subscription["thread_id"]).isEqualTo(null)
    }

    @Test
    fun `invoke should resubscribe after a gap with canonical workspace`() = runTest(testDispatcher) {
        val relayConnectionClient = DirectDetailFakeRelayConnectionClient()
        relayConnectionClient.queuePacketAck(packetAck("packet_initial"))
        relayConnectionClient.queuePacketAck(packetAck("packet_refresh"))

        val relayBootstrapManager = DirectDetailFakeRelayBootstrapManager(relayConnectionClient)
        val testSubject = createTestSubject(
            relayConnectionClient = relayConnectionClient,
            relayBootstrapManager = relayBootstrapManager,
            requestIdFactory = RequestIdFactory("req_initial", "req_refresh"),
        )

        val projectionsDeferred = async {
            testSubject(sampleDetail()).take(2).toList()
        }

        advanceUntilIdle()
        emitGapSequence(relayConnectionClient)
        advanceUntilIdle()
        relayConnectionClient.emitSessionUpdate(refreshSnapshotUpdate())

        val projections = projectionsDeferred.await()
        val firstSubscription = relayConnectionClient.sentPackets[0].subscriptionObject()
        val secondSubscription = relayConnectionClient.sentPackets[1].subscriptionObject()

        assertThat(projections.map { it.lastSummary }).containsExactly(
            "Running",
            "Waiting for input",
        )
        assertThat(secondSubscription["reason"]?.jsonPrimitive?.content).isEqualTo("detail_refresh")
        assertThat(secondSubscription["workspace_id"]?.jsonPrimitive?.content).isEqualTo("workspace_canonical")
        assertThat(secondSubscription["last_known_sequence"]?.jsonPrimitive?.content).isEqualTo("1")
        assertThat(firstSubscription["workspace_id"]).isEqualTo(null)
        assertThat(projections.last().pendingQuestionIds).containsExactly("question_001")
    }

    @Test
    fun `invoke should log subscribe acceptance and emitted projection`() = runTest(testDispatcher) {
        val relayConnectionClient = DirectDetailFakeRelayConnectionClient()
        val relayBootstrapManager = DirectDetailFakeRelayBootstrapManager(relayConnectionClient)
        val logger = DirectDetailFakeLogger()
        val testSubject = createTestSubject(
            relayConnectionClient = relayConnectionClient,
            relayBootstrapManager = relayBootstrapManager,
            requestIdFactory = { "req_logging" },
            logger = logger,
        )

        val projectionDeferred = async {
            testSubject(sampleDetail()).first()
        }

        advanceUntilIdle()
        relayConnectionClient.emitSessionUpdate(
            snapshotUpdate(
                subscriptionId = "sub_logging",
                sequence = 1,
                workspaceId = "workspace_canonical",
                status = "running",
                lastSummary = "Direct running summary",
            ),
        )
        projectionDeferred.await()

        val debugLog = logger.debugMessages.joinToString("\n")

        assertThat(debugLog).contains("Direct detail subscribe accepted reason=detail_open")
        assertThat(debugLog).contains("Direct detail emitted projection status=Running")
    }

    @Test
    fun `invoke should retry when direct detail subscribe is rejected because session is not materialized yet`() =
        runTest(testDispatcher) {
            val relayConnectionClient = DirectDetailFakeRelayConnectionClient().apply {
                queuePacketAck(
                    rejectedPacketAck(
                        packetId = "packet_initial",
                        errorCode = "session_not_found",
                    ),
                )
                queuePacketAck(packetAck("packet_retry"))
            }
            val relayBootstrapManager = DirectDetailFakeRelayBootstrapManager(relayConnectionClient)
            val logger = DirectDetailFakeLogger()
            val testSubject = createTestSubject(
                relayConnectionClient = relayConnectionClient,
                relayBootstrapManager = relayBootstrapManager,
                requestIdFactory = RequestIdFactory("req_initial", "req_retry"),
                logger = logger,
                sessionNotFoundRetryDelayMillis = 0L,
                sessionNotFoundMaxRetryAttempts = 2,
            )

            val projectionDeferred = async {
                testSubject(sampleDetail()).first()
            }

            advanceUntilIdle()
            relayConnectionClient.emitSessionUpdate(
                snapshotUpdate(
                    subscriptionId = "sub_retry",
                    sequence = 1,
                    workspaceId = "workspace_canonical",
                    status = "done",
                    lastSummary = "xxaa",
                ),
            )

            val projection = projectionDeferred.await()
            val debugLog = logger.debugMessages.joinToString("\n")

            assertThat(relayConnectionClient.sentPackets.map { packet ->
                packet.subscriptionObject()["reason"]?.jsonPrimitive?.content
            }).containsExactly("detail_open", "detail_open")
            assertThat(projection.headerStatus.name).isEqualTo("Done")
            assertThat(projection.lastSummary).isEqualTo("xxaa")
            assertThat(debugLog).contains(
                "Direct detail subscribe waiting for session materialization reason=detail_open attempt=1/2",
            )
        }

    @Test
    fun `invoke should merge relay event and result into emitted projection control-plane snapshot`() = runTest(testDispatcher) {
        val relayConnectionClient = DirectDetailFakeRelayConnectionClient()
        val relayBootstrapManager = DirectDetailFakeRelayBootstrapManager(relayConnectionClient)
        val testSubject = createTestSubject(
            relayConnectionClient = relayConnectionClient,
            relayBootstrapManager = relayBootstrapManager,
            requestIdFactory = { "req_control_plane" },
        )

        val projectionsDeferred = async {
            testSubject(sampleDetail()).take(3).toList()
        }

        advanceUntilIdle()
        relayConnectionClient.emitSessionUpdate(
            snapshotUpdate(
                subscriptionId = "sub_control_plane",
                sequence = 1,
                workspaceId = "workspace_canonical",
                status = "running",
                lastSummary = "Direct running summary",
            ),
        )
        relayConnectionClient.emitServerEvent(
            RelayEvent(
                eventId = "evt_001",
                requestId = "cmd_001",
                receiptId = "receipt_001",
                eventType = "running",
                payload = buildJsonObject {
                    put("workspace_id", "workspace_canonical")
                    put("session_id", "session_001")
                    put("run_id", "run_001")
                    put("summary", "Relay event summary.")
                },
            ),
        )
        relayConnectionClient.emitServerResult(
            RelayResult(
                resultId = "res_001",
                requestId = "cmd_001",
                receiptId = "receipt_001",
                resultType = "task_outcome",
                finalStatus = "done",
                payload = buildJsonObject {
                    put("workspace_id", "workspace_canonical")
                    put("session_id", "session_001")
                    put("run_id", "run_001")
                    put("summary", "Relay completed.")
                },
                structuredPayload = RelayStructuredPayload(
                    kind = "task_outcome",
                    payload = buildJsonObject {
                        put("session_id", "session_001")
                        put("summary", "Relay completed.")
                    },
                ),
                effectiveExecution = RelayEffectiveExecution(
                    backend = "codex",
                    profile = "strong",
                    permission = "highest",
                    resolvedModel = "gpt-5-codex",
                ),
            ),
        )

        val finalProjection = projectionsDeferred.await().last()

        assertThat(finalProjection.controlPlaneSnapshot?.events?.single()?.eventType).isEqualTo("running")
        assertThat(finalProjection.controlPlaneSnapshot?.result?.summary).isEqualTo("Relay completed.")
        assertThat(finalProjection.controlPlaneSnapshot?.result?.effectiveExecution?.resolvedModel)
            .isEqualTo("gpt-5-codex")
    }
}

private fun createTestSubject(
    relayConnectionClient: DirectDetailFakeRelayConnectionClient,
    relayBootstrapManager: DirectDetailFakeRelayBootstrapManager,
    requestIdFactory: () -> String,
    logger: DirectDetailFakeLogger = DirectDetailFakeLogger(),
    sessionNotFoundRetryDelayMillis: Long = 1_000L,
    sessionNotFoundMaxRetryAttempts: Int = 20,
): DefaultObserveTaskMailDirectSessionDetail {
    return DefaultObserveTaskMailDirectSessionDetail(
        relayBootstrapManager = relayBootstrapManager,
        relayConnectionClient = relayConnectionClient,
        directSessionDetailSubscriber = RelayTaskMailDirectSessionDetailSubscriber(
            relayConnectionClient = relayConnectionClient,
            timestampProvider = { "2026-03-21T18:00:00Z" },
            requestIdFactory = requestIdFactory,
        ),
        projector = TaskMailDirectSessionProjector(),
        logger = logger,
        sessionNotFoundRetryDelayMillis = sessionNotFoundRetryDelayMillis,
        sessionNotFoundMaxRetryAttempts = sessionNotFoundMaxRetryAttempts,
    )
}

private suspend fun emitGapSequence(
    relayConnectionClient: DirectDetailFakeRelayConnectionClient,
) {
    relayConnectionClient.emitSessionUpdate(
        snapshotUpdate(
            subscriptionId = "sub_initial",
            sequence = 1,
            workspaceId = "workspace_canonical",
            status = "running",
            lastSummary = "Running",
        ),
    )
    relayConnectionClient.emitSessionUpdate(
        deltaUpdate(
            subscriptionId = "sub_initial",
            sequence = 3,
            status = "awaiting_user_input",
            lastSummary = "Waiting",
        ),
    )
}

private fun singleQuestionState(): RelayQuestionState {
    return RelayQuestionState(
        questionSetId = "question_set_001",
        questionCount = 1,
        questions = listOf(
            RelayQuestion(
                questionId = "question_001",
                questionText = "Proceed?",
                questionType = "single_choice",
                choices = listOf("approve", "decline"),
                choiceLabels = mapOf(
                    "approve" to "Ship it",
                    "decline" to "Not yet",
                ),
            ),
        ),
    )
}

private fun refreshSnapshotUpdate(): RelaySessionUpdate {
    return snapshotUpdate(
        subscriptionId = "sub_refresh",
        sequence = 4,
        workspaceId = "workspace_canonical",
        status = "awaiting_user_input",
        lastSummary = "Waiting for input",
        questionState = singleQuestionState(),
    )
}

private class DirectDetailFakeRelayBootstrapManager(
    private val relayConnectionClient: DirectDetailFakeRelayConnectionClient,
) : RelayBootstrapManager {
    override val connectionState: StateFlow<RelayConnectionState> = relayConnectionClient.connectionState
    var connectCount: Int = 0

    override fun loadConfig(): RelayTransportConfig {
        return RelayTransportConfig(
            enabled = true,
            host = "124.223.41.153",
            port = 8787,
            useTls = false,
            path = "/relay",
            transportToken = "token_123",
        )
    }

    override fun saveConfig(config: RelayTransportConfig): Boolean = true

    override suspend fun probeHealth(config: RelayTransportConfig): Result<RelayHealthStatus> {
        return Result.failure(IllegalStateException("Not used by this test"))
    }

    override suspend fun connect(config: RelayTransportConfig): Result<RelayHelloAck> {
        connectCount += 1
        return relayConnectionClient.connect(config)
    }

    override suspend fun disconnect() {
        relayConnectionClient.disconnect()
    }

    override suspend fun bootstrap(config: RelayTransportConfig): RelayBootstrapResult {
        return RelayBootstrapResult(
            status = RelayBootstrapStatus.HelloAck,
            helloAck = RelayHelloAck(
                messageType = "hello_ack",
                connectionId = "conn_test",
                serverTime = "2026-03-21T18:00:00Z",
                heartbeatSeconds = 30,
            ),
        )
    }
}

private class DirectDetailFakeRelayConnectionClient : RelayConnectionClient {
    private val mutableConnectionState = MutableStateFlow<RelayConnectionState>(RelayConnectionState.Idle)
    private val mutableSessionUpdates = MutableSharedFlow<RelaySessionUpdate>(extraBufferCapacity = 16)
    private val mutableServerEvents = MutableSharedFlow<RelayEvent>(extraBufferCapacity = 1)
    private val mutableServerResults = MutableSharedFlow<RelayResult>(extraBufferCapacity = 1)
    private val packetAcks = ArrayDeque<RelayPacketAck>()

    val sentPackets = mutableListOf<RelayPacket>()

    override val connectionState: StateFlow<RelayConnectionState> = mutableConnectionState.asStateFlow()
    override val sessionUpdates: SharedFlow<RelaySessionUpdate> = mutableSessionUpdates.asSharedFlow()
    override val serverEvents: SharedFlow<RelayEvent> = mutableServerEvents.asSharedFlow()
    override val serverResults: SharedFlow<RelayResult> = mutableServerResults.asSharedFlow()

    override suspend fun connect(config: RelayTransportConfig): Result<RelayHelloAck> {
        mutableConnectionState.value = RelayConnectionState.Connected(
            connectionId = "conn_test",
            serverTime = "2026-03-21T18:00:00Z",
            heartbeatSeconds = 30,
        )
        return Result.success(
            RelayHelloAck(
                messageType = "hello_ack",
                connectionId = "conn_test",
                serverTime = "2026-03-21T18:00:00Z",
                heartbeatSeconds = 30,
            ),
        )
    }

    override suspend fun sendPacket(
        packet: RelayPacket,
        ackTimeoutMillis: Long,
    ): Result<RelayPacketAck> {
        sentPackets += packet
        val packetAck = packetAcks.removeFirstOrNull() ?: packetAck(packet.packetId)
        return Result.success(packetAck)
    }

    override suspend fun disconnect() {
        mutableConnectionState.value = RelayConnectionState.Idle
    }

    suspend fun emitSessionUpdate(update: RelaySessionUpdate) {
        mutableSessionUpdates.emit(update)
    }

    suspend fun emitServerEvent(event: RelayEvent) {
        mutableServerEvents.emit(event)
    }

    suspend fun emitServerResult(result: RelayResult) {
        mutableServerResults.emit(result)
    }

    fun queuePacketAck(packetAck: RelayPacketAck) {
        packetAcks += packetAck
    }
}

private class RequestIdFactory(
    private vararg val requestIds: String,
) : () -> String {
    private var index = 0

    override fun invoke(): String {
        return requestIds.getOrElse(index++) { requestIds.last() }
    }
}

private class DirectDetailFakeLogger : Logger {
    val debugMessages = mutableListOf<String>()

    override fun verbose(tag: LogTag?, throwable: Throwable?, message: () -> LogMessage) = Unit
    override fun debug(tag: LogTag?, throwable: Throwable?, message: () -> LogMessage) {
        debugMessages += message()
    }

    override fun info(tag: LogTag?, throwable: Throwable?, message: () -> LogMessage) = Unit
    override fun warn(tag: LogTag?, throwable: Throwable?, message: () -> LogMessage) = Unit
    override fun error(tag: LogTag?, throwable: Throwable?, message: () -> LogMessage) = Unit
}

private fun sampleDetail() = TaskMailPreviewData.sessionDetails.first().copy(
    workspace = TaskMailPreviewData.sessionDetails.first().workspace.copy(
        workspaceId = null,
        workdir = "feature/taskmail/internal",
    ),
    workdir = "feature/taskmail/internal",
)

private fun sampleDetailWithoutThreadId() = sampleDetail().copy(
    key = TaskSessionKey(
        workspaceId = "workspace_provisional",
        sessionId = "session_001",
        threadId = null,
    ),
    workspace = sampleDetail().workspace.copy(
        workspaceId = "workspace_provisional",
        workdir = "feature/taskmail/internal",
    ),
)

private fun snapshotUpdate(
    subscriptionId: String,
    sequence: Long,
    workspaceId: String,
    status: String,
    lastSummary: String,
    questionState: RelayQuestionState? = null,
): RelaySessionUpdate {
    return RelaySessionUpdate(
        schemaVersion = "phase3-direct-inbound-wire-v1",
        subscriptionId = subscriptionId,
        workspaceId = workspaceId,
        sessionId = "session_001",
        threadId = "thread_001",
        taskId = "task_001",
        updateId = "update_$sequence",
        sequence = sequence,
        sentAt = "2026-03-21T18:00:0${sequence}Z",
        updateType = "session_snapshot",
        sessionSnapshot = RelaySessionSnapshot(
            sessionName = "Build TaskMail Phase 1",
            backend = "codex",
            repoPath = "E:/projects/android_task_manager",
            workdir = "feature/taskmail/internal",
            status = status,
            lifecycle = "active",
            lastSummary = lastSummary,
            lastActiveAt = "2026-03-21T18:00:00Z",
            lastProgressAt = "2026-03-21T18:00:00Z",
            questionState = questionState,
        ),
    )
}

private fun deltaUpdate(
    subscriptionId: String,
    sequence: Long,
    status: String,
    lastSummary: String,
): RelaySessionUpdate {
    return RelaySessionUpdate(
        schemaVersion = "phase3-direct-inbound-wire-v1",
        subscriptionId = subscriptionId,
        workspaceId = "workspace_canonical",
        sessionId = "session_001",
        threadId = "thread_001",
        taskId = "task_001",
        updateId = "update_$sequence",
        sequence = sequence,
        sentAt = "2026-03-21T18:00:0${sequence}Z",
        updateType = "session_delta",
        sessionDelta = RelaySessionDelta(
            deltaType = "state_transition",
            stateTransition = net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayStateTransition(
                status = status,
                lifecycle = "active",
                lastSummary = lastSummary,
            ),
        ),
    )
}

private fun packetAck(packetId: String): RelayPacketAck {
    return RelayPacketAck(
        packetId = packetId,
        accepted = true,
        receiptId = "receipt_$packetId",
        receivedAt = "2026-03-21T18:00:00Z",
    )
}

private fun rejectedPacketAck(
    packetId: String,
    errorCode: String,
): RelayPacketAck {
    return RelayPacketAck(
        packetId = packetId,
        accepted = false,
        receiptId = "receipt_$packetId",
        receivedAt = "2026-03-21T18:00:00Z",
        errorCode = errorCode,
        errorMessage = "rejected: $errorCode",
    )
}

private fun RelayPacket.subscriptionObject(): JsonObject {
    return taskRunPacket["subscription"]?.jsonObject
        ?: error("Missing subscription payload in relay packet.")
}
