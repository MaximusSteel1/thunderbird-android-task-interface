package net.thunderbird.feature.taskmail.internal.ui.relaydebug

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import net.thunderbird.core.ui.contract.mvi.BaseViewModel
import net.thunderbird.feature.taskmail.internal.data.relay.RelayConnectionClient
import net.thunderbird.feature.taskmail.internal.data.relay.RelayHealthProbe
import net.thunderbird.feature.taskmail.internal.domain.model.RelayConnectionState
import net.thunderbird.feature.taskmail.internal.domain.model.RelayHealthStatus
import net.thunderbird.feature.taskmail.internal.domain.model.RelayTransportConfig
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskTransportConfigRepository
import net.thunderbird.feature.taskmail.internal.ui.relaydebug.TaskMailRelayDebugContract.Effect
import net.thunderbird.feature.taskmail.internal.ui.relaydebug.TaskMailRelayDebugContract.Event
import net.thunderbird.feature.taskmail.internal.ui.relaydebug.TaskMailRelayDebugContract.State

private const val CONFIG_REQUIRED_ERROR = "Relay host, port, and transport token are required."
private const val PORT_INVALID_ERROR = "Enter a valid relay port."

internal class TaskMailRelayDebugViewModel(
    private val transportConfigRepository: TaskTransportConfigRepository,
    private val relayHealthProbe: RelayHealthProbe,
    private val relayConnectionClient: RelayConnectionClient,
    initialState: State = State(),
) : BaseViewModel<State, Event, Effect>(initialState),
    TaskMailRelayDebugContract.ViewModel {

    init {
        viewModelScope.launch {
            relayConnectionClient.connectionState.collect { connectionState ->
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
            is Event.RelayEnabledChanged -> updateState { it.copy(relayEnabled = event.value) }
            is Event.HostChanged -> updateState { it.copy(host = event.value) }
            is Event.PortChanged -> updateState { it.copy(port = event.value) }
            is Event.UseTlsChanged -> updateState { it.copy(useTls = event.value) }
            is Event.TransportTokenChanged -> updateState { it.copy(transportToken = event.value) }
            Event.SaveClicked -> saveConfig()
            Event.ProbeHealthClicked -> probeHealth()
            Event.ConnectClicked -> connect()
            Event.DisconnectClicked -> disconnect()
            Event.DismissErrors -> updateState { it.copy(healthError = null, actionError = null) }
        }
    }

    private fun loadData() {
        val config = transportConfigRepository.getRelayTransportConfig()
        updateState {
            it.copy(
                relayEnabled = config.enabled,
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
        val isSaved = transportConfigRepository.saveRelayTransportConfig(config)
        updateState { it.copy(isSaving = false) }
        emitEffect(
            Effect.ShowMessage(
                if (isSaved) "Relay config saved." else "Unable to save relay config.",
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
            relayHealthProbe.probe(config).fold(
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
            relayConnectionClient.connect(config).fold(
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

    private fun disconnect() {
        viewModelScope.launch {
            relayConnectionClient.disconnect()
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
