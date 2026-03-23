package net.thunderbird.feature.taskmail.internal.ui.relaydebug

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import net.thunderbird.core.ui.contract.mvi.BaseViewModel
import net.thunderbird.feature.taskmail.internal.data.relay.RelayBootstrapManager
import net.thunderbird.feature.taskmail.internal.domain.model.RelayConnectionState
import net.thunderbird.feature.taskmail.internal.domain.model.RelayHealthStatus
import net.thunderbird.feature.taskmail.internal.domain.model.RelayTransportConfig
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskMailProjectSyncDebugSettingsRepository
import net.thunderbird.feature.taskmail.internal.domain.transportprobe.TaskMailTransportProbeDispatchResult
import net.thunderbird.feature.taskmail.internal.domain.transportprobe.TaskMailTransportProbeDispatchStatus
import net.thunderbird.feature.taskmail.internal.domain.transportprobe.TaskMailTransportProbeRequest
import net.thunderbird.feature.taskmail.internal.domain.usecase.SendTaskMailTransportProbe
import net.thunderbird.feature.taskmail.internal.ui.relaydebug.TaskMailRelayDebugContract.Effect
import net.thunderbird.feature.taskmail.internal.ui.relaydebug.TaskMailRelayDebugContract.Event
import net.thunderbird.feature.taskmail.internal.ui.relaydebug.TaskMailRelayDebugContract.State

private const val CONFIG_REQUIRED_ERROR = "Relay host, port, and transport token are required."
private const val PORT_INVALID_ERROR = "Enter a valid relay port."

internal class TaskMailRelayDebugViewModel(
    private val relayBootstrapManager: RelayBootstrapManager,
    private val projectSyncDebugSettingsRepository: TaskMailProjectSyncDebugSettingsRepository,
    private val sendTaskMailTransportProbe: SendTaskMailTransportProbe,
    initialState: State = State(),
) : BaseViewModel<State, Event, Effect>(initialState),
    TaskMailRelayDebugContract.ViewModel {

    init {
        viewModelScope.launch {
            relayBootstrapManager.connectionState.collect { connectionState ->
                updateState { state ->
                    state.copy(
                        connectionState = connectionState,
                        isConnecting = connectionState is RelayConnectionState.Connecting,
                    )
                }
            }
        }
    }

    override fun event(event: Event) {
        when (event) {
            Event.LoadData -> handleOneTimeEvent(event, ::loadData)
            Event.SaveClicked -> saveConfig()
            Event.ProbeHealthClicked -> probeHealth()
            Event.SendDirectProbeClicked -> sendDirectProbe()
            Event.ConnectClicked -> connect()
            Event.DisconnectClicked -> disconnect()
            Event.DismissErrors -> updateState { it.copy(healthError = null, actionError = null) }
            else -> handleFormEvent(event)
        }
    }

    private fun handleFormEvent(event: Event) {
        when (event) {
            is Event.RelayEnabledChanged -> updateState { it.copy(relayEnabled = event.value) }
            is Event.ProjectSyncDebugFileLoggingChanged -> {
                updateState { it.copy(projectSyncDebugFileLoggingEnabled = event.value) }
            }
            is Event.HostChanged -> updateState { it.copy(host = event.value) }
            is Event.PortChanged -> updateState { it.copy(port = event.value) }
            is Event.UseTlsChanged -> updateState { it.copy(useTls = event.value) }
            is Event.TransportTokenChanged -> updateState { it.copy(transportToken = event.value) }
            is Event.ProbePayloadTextChanged -> updateState { it.copy(probePayloadText = event.value) }
            else -> Unit
        }
    }

    private fun loadData() {
        val config = relayBootstrapManager.loadConfig()
        updateState {
            it.copy(
                relayEnabled = config.enabled,
                projectSyncDebugFileLoggingEnabled = projectSyncDebugSettingsRepository.isFileLoggingEnabled(),
                host = config.host,
                port = config.port.toString(),
                useTls = config.useTls,
                transportToken = config.transportToken,
            )
        }
    }

    private fun saveConfig() {
        val config = currentConfig() ?: return
        updateState { it.copy(isSaving = true, actionError = null) }
        val isRelayConfigSaved = relayBootstrapManager.saveConfig(config)
        val isDebugSettingSaved = projectSyncDebugSettingsRepository.setFileLoggingEnabled(
            state.value.projectSyncDebugFileLoggingEnabled,
        )
        updateState { it.copy(isSaving = false) }
        emitEffect(
            Effect.ShowMessage(
                if (isRelayConfigSaved && isDebugSettingSaved) {
                    "Relay config saved."
                } else {
                    "Unable to save relay config."
                },
            ),
        )
    }

    private fun probeHealth() {
        val config = currentConfig() ?: return
        updateState {
            it.copy(
                isProbingHealth = true,
                healthError = null,
                actionError = null,
            )
        }

        viewModelScope.launch {
            relayBootstrapManager.probeHealth(config).fold(
                onSuccess = { healthStatus ->
                    updateState {
                        it.copy(
                            isProbingHealth = false,
                            healthSummary = healthStatus.toSummary(),
                        )
                    }
                },
                onFailure = { error ->
                    updateState {
                        it.copy(
                            isProbingHealth = false,
                            healthError = error.message ?: "Relay health probe failed.",
                        )
                    }
                },
            )
        }
    }

    private fun connect() {
        val config = currentConfig() ?: return
        updateState { it.copy(actionError = null) }
        viewModelScope.launch {
            relayBootstrapManager.connect(config).fold(
                onSuccess = { helloAck ->
                    emitEffect(
                        Effect.ShowMessage(
                            "Relay connected: ${helloAck.connectionId}",
                        ),
                    )
                },
                onFailure = { error ->
                    updateState {
                        it.copy(
                            actionError = error.message ?: "Relay connection failed.",
                        )
                    }
                },
            )
        }
    }

    private fun sendDirectProbe() {
        val config = currentConfig() ?: return
        val payloadText = state.value.probePayloadText.trim()
        if (payloadText.isEmpty()) {
            updateState { it.copy(actionError = "Transport probe text is required.") }
            return
        }

        updateState {
            it.copy(
                isSendingProbe = true,
                actionError = null,
                lastProbeSummary = null,
            )
        }

        viewModelScope.launch {
            runCatching {
                sendTaskMailTransportProbe(
                    config = config,
                    request = TaskMailTransportProbeRequest(
                        probeId = nextProbeId(),
                        payloadText = payloadText,
                    ),
                )
            }.fold(
                onSuccess = { result ->
                    updateState {
                        it.copy(
                            isSendingProbe = false,
                            lastProbeSummary = result.toSummary(),
                            lastProbeArtifactPath = result.artifactDirectoryPath,
                        )
                    }
                    emitEffect(
                        Effect.ShowMessage(
                            "Transport probe ${result.status.name} (${result.probeId})",
                        ),
                    )
                },
                onFailure = { error ->
                    updateState {
                        it.copy(
                            isSendingProbe = false,
                            actionError = error.message ?: "Transport probe failed.",
                        )
                    }
                },
            )
        }
    }

    private fun disconnect() {
        viewModelScope.launch {
            relayBootstrapManager.disconnect()
            emitEffect(Effect.ShowMessage("Relay disconnected."))
        }
    }

    private fun currentConfig(): RelayTransportConfig? {
        val port = state.value.port.trim()
            .toIntOrNull()
            ?.takeIf { it > 0 }

        return when {
            state.value.host.trim().isEmpty() || state.value.transportToken.trim().isEmpty() -> {
                updateState { it.copy(actionError = CONFIG_REQUIRED_ERROR) }
                null
            }

            port == null -> {
                updateState { it.copy(actionError = PORT_INVALID_ERROR) }
                null
            }

            else -> RelayTransportConfig(
                enabled = state.value.relayEnabled,
                host = state.value.host.trim(),
                port = port,
                useTls = state.value.useTls,
                transportToken = state.value.transportToken.trim(),
            )
        }
    }
}

private fun RelayHealthStatus.toSummary(): String {
    val listenPart = buildString {
        val host = listenHost.orEmpty()
        val port = listenPort?.toString().orEmpty()
        if (host.isNotBlank() && port.isNotBlank()) {
            append(host)
            append(':')
            append(port)
        }
    }

    return listOfNotNull(
        "status=$status".takeIf { status.isNotBlank() },
        service?.takeIf(String::isNotBlank)?.let { "service=$it" },
        listenPart.takeIf(String::isNotBlank)?.let { "listen=$it" },
        sessionCount?.let { "sessions=$it" },
        packetCount?.let { "packets=$it" },
        transportTokenId?.takeIf(String::isNotBlank)?.let { "token_id=$it" },
    ).joinToString(separator = " | ")
}

private fun TaskMailTransportProbeDispatchResult.toSummary(): String {
    return buildList {
        add("probe_id=$probeId")
        add("status=${status.name}")
        add("request_id=$requestId")
        add("packet_id=$packetId")
        receiptId?.takeIf(String::isNotBlank)?.let { add("receipt_id=$it") }
        resultId?.takeIf(String::isNotBlank)?.let { add("result_id=$it") }
        resultType?.takeIf(String::isNotBlank)?.let { add("result_type=$it") }
        resultStatus?.takeIf(String::isNotBlank)?.let { add("result_status=$it") }
        errorCode?.takeIf(String::isNotBlank)?.let { add("error_code=$it") }
        errorMessage?.takeIf(String::isNotBlank)?.let { add("error=$it") }
        if (status == TaskMailTransportProbeDispatchStatus.AcceptedAwaitingResult) {
            add("note=accepted_without_result_window")
        }
    }.joinToString(separator = " | ")
}

private fun nextProbeId(): String {
    return "probe_" + java.util.UUID.randomUUID()
        .toString()
        .replace("-", "")
}
