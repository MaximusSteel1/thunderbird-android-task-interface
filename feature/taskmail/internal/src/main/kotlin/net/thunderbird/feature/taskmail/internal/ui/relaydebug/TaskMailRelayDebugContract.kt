package net.thunderbird.feature.taskmail.internal.ui.relaydebug

import net.thunderbird.core.ui.contract.mvi.UnidirectionalViewModel
import net.thunderbird.feature.taskmail.internal.domain.model.RelayConnectionState

private const val DEFAULT_PROBE_PAYLOAD_TEXT = "PING android relay path"

internal interface TaskMailRelayDebugContract {

    interface ViewModel : UnidirectionalViewModel<State, Event, Effect>

    data class State(
        val relayEnabled: Boolean = false,
        val projectSyncDebugFileLoggingEnabled: Boolean = false,
        val host: String = "",
        val port: String = "",
        val useTls: Boolean = false,
        val transportToken: String = "",
        val androidAppToken: String = "",
        val probePayloadText: String = DEFAULT_PROBE_PAYLOAD_TEXT,
        val newTaskSendRecordsPath: String? = null,
        val sessionActionSendRecordsPath: String? = null,
        val isSaving: Boolean = false,
        val isProbingHealth: Boolean = false,
        val isConnecting: Boolean = false,
        val isSendingProbe: Boolean = false,
        val isSendingFileSample: Boolean = false,
        val healthSummary: String? = null,
        val lastProbeSummary: String? = null,
        val lastProbeArtifactPath: String? = null,
        val lastFileSampleSummary: String? = null,
        val lastFileSampleArtifactPath: String? = null,
        val healthError: String? = null,
        val connectionState: RelayConnectionState = RelayConnectionState.Idle,
        val actionError: String? = null,
    )

    sealed interface Event {
        data object LoadData : Event
        data class RelayEnabledChanged(val value: Boolean) : Event
        data class ProjectSyncDebugFileLoggingChanged(val value: Boolean) : Event
        data class HostChanged(val value: String) : Event
        data class PortChanged(val value: String) : Event
        data class UseTlsChanged(val value: Boolean) : Event
        data class TransportTokenChanged(val value: String) : Event
        data class AndroidAppTokenChanged(val value: String) : Event
        data class ProbePayloadTextChanged(val value: String) : Event
        data object SaveClicked : Event
        data object ProbeHealthClicked : Event
        data object SendDirectProbeClicked : Event
        data object SendFileSurfaceSampleClicked : Event
        data object ConnectClicked : Event
        data object DisconnectClicked : Event
        data object DismissErrors : Event
    }

    sealed interface Effect {
        data class ShowMessage(val message: String) : Effect
    }
}
