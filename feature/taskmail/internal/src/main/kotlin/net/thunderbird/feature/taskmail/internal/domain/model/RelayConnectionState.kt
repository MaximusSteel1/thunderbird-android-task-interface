package net.thunderbird.feature.taskmail.internal.domain.model

internal sealed interface RelayConnectionState {
    data object Idle : RelayConnectionState

    data class Connecting(
        val relayUrl: String,
    ) : RelayConnectionState

    data class Connected(
        val connectionId: String,
        val serverTime: String,
        val heartbeatSeconds: Int,
    ) : RelayConnectionState

    data class Failed(
        val message: String,
    ) : RelayConnectionState
}
