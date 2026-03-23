package net.thunderbird.feature.taskmail.internal.ui.relaydebug

import app.k9mail.core.ui.compose.testing.mvi.MviContext
import app.k9mail.core.ui.compose.testing.mvi.MviTurbines
import app.k9mail.core.ui.compose.testing.mvi.advanceUntilIdle
import app.k9mail.core.ui.compose.testing.mvi.runMviTest
import app.k9mail.core.ui.compose.testing.mvi.turbinesWithInitialStateCheck
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.coroutines.flow.MutableStateFlow
import net.thunderbird.feature.taskmail.internal.data.relay.RelayBootstrapManager
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayHelloAck
import net.thunderbird.feature.taskmail.internal.domain.model.RelayBootstrapResult
import net.thunderbird.feature.taskmail.internal.domain.model.RelayBootstrapStatus
import net.thunderbird.feature.taskmail.internal.domain.model.RelayConnectionState
import net.thunderbird.feature.taskmail.internal.domain.model.RelayHealthStatus
import net.thunderbird.feature.taskmail.internal.domain.model.RelayTransportConfig
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskMailProjectSyncDebugSettingsRepository
import net.thunderbird.feature.taskmail.internal.domain.transportprobe.TaskMailTransportProbeDispatchResult
import net.thunderbird.feature.taskmail.internal.domain.transportprobe.TaskMailTransportProbeDispatchStatus
import net.thunderbird.feature.taskmail.internal.domain.transportprobe.TaskMailTransportProbeRequest
import net.thunderbird.feature.taskmail.internal.domain.transportprobe.TaskMailTransportProbeSender
import net.thunderbird.feature.taskmail.internal.domain.usecase.SendTaskMailTransportProbe

class TaskMailRelayDebugViewModelTest {

    @Test
    fun `load data should include persisted project sync debug logging toggle`() = runMviTest {
        with(TaskMailRelayDebugViewModelRobot(this, initialDebugFileLoggingEnabled = true)) {
            start()
            loadData()

            assertThat(viewModelState().projectSyncDebugFileLoggingEnabled).isEqualTo(true)
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `save clicked should persist project sync debug logging toggle`() = runMviTest {
        with(TaskMailRelayDebugViewModelRobot(this)) {
            start()
            loadData()
            changeProjectSyncDebugFileLoggingEnabled(true)
            save()

            assertThat(savedDebugFileLoggingValues).containsExactly(true)
            assertThat(awaitEffect()).isEqualTo(
                TaskMailRelayDebugContract.Effect.ShowMessage("Relay config saved."),
            )
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `send direct probe should expose summary and artifact path`() = runMviTest {
        with(TaskMailRelayDebugViewModelRobot(this)) {
            start()
            loadData()
            awaitState()
            changeProbePayloadText("PING relay path")
            awaitState()
            sendDirectProbe()

            assertThat(awaitState().isSendingProbe).isEqualTo(true)
            assertThat(awaitState().lastProbeSummary).isEqualTo(
                "probe_id=probe_test | status=AcceptedAwaitingResult | " +
                    "request_id=req_test | packet_id=pkt_test | receipt_id=receipt_test | " +
                    "note=accepted_without_result_window",
            )
            assertThat(viewModelState().lastProbeArtifactPath)
                .isEqualTo("E:/tmp/taskmail-debug/transport-probe/probe_test")
            assertThat(awaitEffect()).isEqualTo(
                TaskMailRelayDebugContract.Effect.ShowMessage(
                    "Transport probe AcceptedAwaitingResult (probe_test)",
                ),
            )
            ensureThatAllEventsAreConsumed()
        }
    }
}

private class TaskMailRelayDebugViewModelRobot(
    private val mviContext: MviContext,
    initialDebugFileLoggingEnabled: Boolean = false,
) {
    private val relayBootstrapManager = FakeRelayBootstrapManager()
    private val debugSettingsRepository = FakeTaskMailProjectSyncDebugSettingsRepository(
        initialValue = initialDebugFileLoggingEnabled,
    )
    private val transportProbeSender = FakeTaskMailTransportProbeSender()
    private val viewModel = TaskMailRelayDebugViewModel(
        relayBootstrapManager = relayBootstrapManager,
        projectSyncDebugSettingsRepository = debugSettingsRepository,
        sendTaskMailTransportProbe = SendTaskMailTransportProbe(transportProbeSender),
    )
    private lateinit var turbines: MviTurbines<TaskMailRelayDebugContract.State, TaskMailRelayDebugContract.Effect>

    val savedDebugFileLoggingValues: List<Boolean>
        get() = debugSettingsRepository.savedValues

    suspend fun start() {
        turbines = mviContext.turbinesWithInitialStateCheck(viewModel, TaskMailRelayDebugContract.State())
    }

    suspend fun loadData() {
        viewModel.event(TaskMailRelayDebugContract.Event.LoadData)
        mviContext.advanceUntilIdle()
    }

    fun changeProjectSyncDebugFileLoggingEnabled(value: Boolean) {
        viewModel.event(TaskMailRelayDebugContract.Event.ProjectSyncDebugFileLoggingChanged(value))
    }

    fun changeProbePayloadText(value: String) {
        viewModel.event(TaskMailRelayDebugContract.Event.ProbePayloadTextChanged(value))
    }

    suspend fun save() {
        viewModel.event(TaskMailRelayDebugContract.Event.SaveClicked)
        mviContext.advanceUntilIdle()
    }

    suspend fun sendDirectProbe() {
        viewModel.event(TaskMailRelayDebugContract.Event.SendDirectProbeClicked)
        mviContext.advanceUntilIdle()
    }

    suspend fun awaitEffect(): TaskMailRelayDebugContract.Effect {
        return turbines.awaitEffectItem()
    }

    suspend fun awaitState(): TaskMailRelayDebugContract.State {
        return turbines.awaitStateItem()
    }

    fun viewModelState(): TaskMailRelayDebugContract.State = viewModel.state.value

    suspend fun ensureThatAllEventsAreConsumed() {
        turbines.stateTurbine.cancelAndIgnoreRemainingEvents()
        turbines.effectTurbine.ensureAllEventsConsumed()
    }
}

private class FakeRelayBootstrapManager : RelayBootstrapManager {
    override val connectionState = MutableStateFlow<RelayConnectionState>(RelayConnectionState.Idle)
    private var config = RelayTransportConfig(
        enabled = false,
        host = "relay.example.org",
        port = 9443,
        useTls = true,
        transportToken = "transport-token",
    )

    override fun loadConfig(): RelayTransportConfig = config

    override fun saveConfig(config: RelayTransportConfig): Boolean {
        this.config = config
        return true
    }

    override suspend fun probeHealth(config: RelayTransportConfig): Result<RelayHealthStatus> {
        error("Not needed in this test")
    }

    override suspend fun connect(config: RelayTransportConfig): Result<RelayHelloAck> {
        error("Not needed in this test")
    }

    override suspend fun disconnect() = Unit

    override suspend fun bootstrap(config: RelayTransportConfig): RelayBootstrapResult {
        return RelayBootstrapResult(
            status = RelayBootstrapStatus.HelloAck,
            helloAck = RelayHelloAck(
                messageType = "hello_ack",
                connectionId = "connection_id",
                serverTime = "2026-03-23T20:00:00Z",
                heartbeatSeconds = 15,
            ),
        )
    }
}

private class FakeTaskMailProjectSyncDebugSettingsRepository(
    initialValue: Boolean,
) : TaskMailProjectSyncDebugSettingsRepository {
    private var currentValue = initialValue
    val savedValues = mutableListOf<Boolean>()

    override fun isFileLoggingEnabled(): Boolean = currentValue

    override fun setFileLoggingEnabled(enabled: Boolean): Boolean {
        savedValues += enabled
        currentValue = enabled
        return true
    }
}

private class FakeTaskMailTransportProbeSender : TaskMailTransportProbeSender {
    override suspend fun send(
        config: RelayTransportConfig,
        request: TaskMailTransportProbeRequest,
    ): TaskMailTransportProbeDispatchResult {
        return TaskMailTransportProbeDispatchResult(
            probeId = "probe_test",
            status = TaskMailTransportProbeDispatchStatus.AcceptedAwaitingResult,
            requestId = "req_test",
            packetId = "pkt_test",
            artifactDirectoryPath = "E:/tmp/taskmail-debug/transport-probe/probe_test",
            receiptId = "receipt_test",
        )
    }
}
