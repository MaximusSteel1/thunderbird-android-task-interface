package net.thunderbird.feature.taskmail.internal.data.relay

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.test.runTest
import net.thunderbird.feature.taskmail.internal.data.debug.TaskMailTransportProbeEventStore
import net.thunderbird.feature.taskmail.internal.data.debug.TaskMailTransportProbeManifest
import net.thunderbird.feature.taskmail.internal.data.debug.TaskMailTransportProbeRecordedEvent
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayCommand
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayCommandAck
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayEvent
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayHelloAck
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayResult
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelaySessionUpdate
import net.thunderbird.feature.taskmail.internal.domain.model.RelayConnectionState
import net.thunderbird.feature.taskmail.internal.domain.model.RelayTransportConfig
import net.thunderbird.feature.taskmail.internal.domain.transportprobe.TaskMailTransportProbeDispatchStatus
import net.thunderbird.feature.taskmail.internal.domain.transportprobe.TaskMailTransportProbeRequest

class RelayTaskMailTransportProbeSenderTest {

    @Test
    fun `send should record matching relay event and result metadata`() = runTest {
        val eventStore = FakeTaskMailTransportProbeEventStore()
        val relayConnectionClient = FakeTransportProbeRelayConnectionClient(
            eventsToEmitOnSend = listOf(
                RelayEvent(
                    requestId = "req_other",
                    packetId = "pkt_other",
                    eventType = "vps_probe_bridge_started",
                    sentAt = "2026-03-23T11:00:01.000Z",
                ),
                RelayEvent(
                    requestId = "probe_req_001",
                    packetId = "android-control:transport-probe:probe_req_001",
                    eventType = "vps_probe_bridge_finished",
                    sentAt = "2026-03-23T11:00:02.000Z",
                ),
            ),
            resultsToEmitOnSend = listOf(
                RelayResult(
                    requestId = "probe_req_001",
                    packetId = "android-control:transport-probe:probe_req_001",
                    receiptId = "receipt-1",
                    resultId = "result-1",
                    resultType = "transport_probe_result",
                    status = "completed",
                ),
            ),
            sendCommandResult = Result.success(
                RelayCommandAck(
                    requestId = "probe_req_001",
                    packetId = "android-control:transport-probe:probe_req_001",
                    commandType = "transport_probe",
                    payloadSchema = "taskmail-transport-probe-payload-v1",
                    accepted = true,
                    receiptId = "receipt-1",
                    receivedAt = "2026-03-23T11:00:03Z",
                ),
            ),
        )
        val testSubject = RelayTaskMailTransportProbeSender(
            relayConnectionClient = relayConnectionClient,
            eventStore = eventStore,
            requestIdFactory = { "probe_req_001" },
            wallClockProvider = { "2026-03-23T11:00:00Z" },
            monotonicTimeProvider = monotonicSequence(start = 10L),
            resultObserveWindowMillis = 100L,
        )

        val result = testSubject.send(
            config = relayConfig(),
            request = TaskMailTransportProbeRequest(
                probeId = "probe_001",
                payloadText = "PING relay path",
                timeoutSeconds = 180,
            ),
        )

        assertThat(result.status).isEqualTo(TaskMailTransportProbeDispatchStatus.ResultCompleted)
        assertThat(result.receiptId).isEqualTo("receipt-1")
        assertThat(result.resultId).isEqualTo("result-1")
        assertThat(result.resultType).isEqualTo("transport_probe_result")
        assertThat(result.resultStatus).isEqualTo("completed")
        assertThat(relayConnectionClient.connectCalls).isEqualTo(1)
        assertThat(relayConnectionClient.disconnectCalls).isEqualTo(1)
        assertThat(relayConnectionClient.connectSupportedPayloadSchemas.single()).isEqualTo(
            listOf(
                "taskmail-bootstrap-control-contract-v2",
                "taskmail-transport-probe-payload-v1",
            ),
        )
        assertThat(relayConnectionClient.sendCommandAckTimeoutMillis.single()).isEqualTo(180_100L)
        assertThat(relayConnectionClient.sentCommands.single().packetId)
            .isEqualTo("android-control:transport-probe:probe_req_001")
        assertThat(eventStore.events.any { it.eventType == "vps_probe_bridge_finished" }).isEqualTo(true)
        assertThat(eventStore.events.any { it.eventType == "vps_probe_bridge_started" }).isEqualTo(false)

        val relayEvent = eventStore.events.first { it.eventType == "vps_probe_bridge_finished" }
        assertThat(relayEvent.actor).isEqualTo("relay_server")
        assertThat(relayEvent.clockSource).isEqualTo("relay_wall_clock")
        assertThat(relayEvent.recordedAt).isEqualTo("2026-03-23T11:00:02.000Z")

        val resultEvent = eventStore.events.first { it.eventType == "android_probe_result_observed" }
        assertThat(resultEvent.resultId).isEqualTo("result-1")
        assertThat(resultEvent.relayResultType).isEqualTo("transport_probe_result")
        assertThat(resultEvent.relayStatus).isEqualTo("completed")
        assertThat(eventStore.savedManifests.last().receiptId).isEqualTo("receipt-1")
        assertThat(eventStore.savedManifests.last().resultId).isEqualTo("result-1")
    }

    @Test
    fun `send should return accepted awaiting result when local observe window expires`() = runTest {
        val eventStore = FakeTaskMailTransportProbeEventStore()
        val relayConnectionClient = FakeTransportProbeRelayConnectionClient(
            sendCommandResult = Result.success(
                RelayCommandAck(
                    requestId = "probe_req_001",
                    packetId = "android-control:transport-probe:probe_req_001",
                    commandType = "transport_probe",
                    payloadSchema = "taskmail-transport-probe-payload-v1",
                    accepted = true,
                    receiptId = "receipt-1",
                    receivedAt = "2026-03-23T11:00:03Z",
                ),
            ),
        )
        val testSubject = RelayTaskMailTransportProbeSender(
            relayConnectionClient = relayConnectionClient,
            eventStore = eventStore,
            requestIdFactory = { "probe_req_001" },
            wallClockProvider = { "2026-03-23T11:00:00Z" },
            monotonicTimeProvider = monotonicSequence(start = 20L),
            resultObserveWindowMillis = 1L,
        )

        val result = testSubject.send(
            config = relayConfig(),
            request = TaskMailTransportProbeRequest(
                probeId = "probe_001",
                payloadText = "PING relay path",
            ),
        )

        assertThat(result.status).isEqualTo(TaskMailTransportProbeDispatchStatus.AcceptedAwaitingResult)
        assertThat(result.receiptId).isEqualTo("receipt-1")
        assertThat(eventStore.events.any { it.eventType == "android_probe_receive_timeout" }).isEqualTo(true)
        assertThat(eventStore.savedManifests.last().receiptId).isEqualTo("receipt-1")
        assertThat(eventStore.savedManifests.last().resultId).isEqualTo(null)
    }
}

private class FakeTransportProbeRelayConnectionClient(
    private val sendCommandResult: Result<RelayCommandAck> = Result.success(
        RelayCommandAck(
            packetId = "packet-1",
            accepted = true,
            receiptId = "receipt-1",
            receivedAt = "2026-03-23T11:00:01Z",
        ),
    ),
    private val eventsToEmitOnSend: List<RelayEvent> = emptyList(),
    private val resultsToEmitOnSend: List<RelayResult> = emptyList(),
) : RelayConnectionClient {
    private val mutableConnectionState = MutableStateFlow<RelayConnectionState>(RelayConnectionState.Idle)
    private val mutableSessionUpdates = MutableSharedFlow<RelaySessionUpdate>(extraBufferCapacity = 1)
    private val mutableServerEvents = MutableSharedFlow<RelayEvent>(extraBufferCapacity = 8)
    private val mutableServerResults = MutableSharedFlow<RelayResult>(extraBufferCapacity = 8)

    val sentCommands = mutableListOf<RelayCommand>()
    val connectSupportedPayloadSchemas = mutableListOf<List<String>>()
    val sendCommandAckTimeoutMillis = mutableListOf<Long>()
    var connectCalls = 0
        private set
    var disconnectCalls = 0
        private set

    override val connectionState: MutableStateFlow<RelayConnectionState> = mutableConnectionState
    override val sessionUpdates: SharedFlow<RelaySessionUpdate> = mutableSessionUpdates
    override val serverEvents: SharedFlow<RelayEvent> = mutableServerEvents
    override val serverResults: SharedFlow<RelayResult> = mutableServerResults

    override suspend fun connect(
        config: RelayTransportConfig,
        supportedPayloadSchemas: List<String>,
    ): Result<RelayHelloAck> {
        connectCalls += 1
        connectSupportedPayloadSchemas += supportedPayloadSchemas
        mutableConnectionState.value = RelayConnectionState.Connected(
            connectionId = "connection-1",
            serverTime = "2026-03-23T11:00:00Z",
            heartbeatSeconds = 30,
        )
        return Result.success(
            RelayHelloAck(
                messageType = "hello_ack",
                connectionId = "connection-1",
                serverTime = "2026-03-23T11:00:00Z",
                heartbeatSeconds = 30,
                acceptedPayloadSchemas = supportedPayloadSchemas,
            ),
        )
    }

    override suspend fun connect(config: RelayTransportConfig): Result<RelayHelloAck> {
        return connect(config = config, supportedPayloadSchemas = emptyList())
    }

    override suspend fun sendPacket(
        packet: net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayPacket,
        ackTimeoutMillis: Long,
    ): Result<net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayPacketAck> {
        error("Not needed in this test")
    }

    override suspend fun sendCommand(
        command: RelayCommand,
        ackTimeoutMillis: Long,
    ): Result<RelayCommandAck> {
        sentCommands += command
        sendCommandAckTimeoutMillis += ackTimeoutMillis
        eventsToEmitOnSend.forEach {
            mutableServerEvents.emit(it)
            kotlinx.coroutines.yield()
        }
        resultsToEmitOnSend.forEach {
            mutableServerResults.emit(it)
            kotlinx.coroutines.yield()
        }
        return sendCommandResult
    }

    override suspend fun disconnect() {
        disconnectCalls += 1
        mutableConnectionState.value = RelayConnectionState.Idle
    }
}

private class FakeTaskMailTransportProbeEventStore : TaskMailTransportProbeEventStore {
    val savedManifests = mutableListOf<TaskMailTransportProbeManifest>()
    val events = mutableListOf<TaskMailTransportProbeRecordedEvent>()

    override fun saveManifest(manifest: TaskMailTransportProbeManifest): String {
        savedManifests += manifest
        return artifactDirectoryPath(manifest.probeId)
    }

    override suspend fun appendEvent(event: TaskMailTransportProbeRecordedEvent) {
        events += event
    }

    override fun artifactDirectoryPath(probeId: String): String {
        return "E:/tmp/taskmail-transport-probe/$probeId"
    }
}

private fun relayConfig(): RelayTransportConfig {
    return RelayTransportConfig(
        enabled = true,
        host = "relay.example.com",
        port = 9001,
        useTls = true,
        transportToken = "secret-token",
    )
}

private fun monotonicSequence(start: Long): () -> Long {
    var value = start
    return {
        val current = value
        value += 1
        current
    }
}
