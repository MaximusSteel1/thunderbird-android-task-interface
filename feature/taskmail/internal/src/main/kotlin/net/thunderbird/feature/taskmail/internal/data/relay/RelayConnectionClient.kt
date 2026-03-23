package net.thunderbird.feature.taskmail.internal.data.relay

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayCommand
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayCommandAck
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayEvent
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayHelloAck
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayPacket
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayPacketAck
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayResult
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelaySessionUpdate
import net.thunderbird.feature.taskmail.internal.domain.model.RelayConnectionState
import net.thunderbird.feature.taskmail.internal.domain.model.RelayTransportConfig

internal const val DEFAULT_PACKET_ACK_TIMEOUT_MILLIS = 15_000L

internal interface RelayConnectionClient {
    val connectionState: StateFlow<RelayConnectionState>
    val sessionUpdates: SharedFlow<RelaySessionUpdate>
    val serverEvents: SharedFlow<RelayEvent>
    val serverResults: SharedFlow<RelayResult>

    suspend fun connect(config: RelayTransportConfig): Result<RelayHelloAck>

    suspend fun connect(
        config: RelayTransportConfig,
        supportedPayloadSchemas: List<String>,
    ): Result<RelayHelloAck> = connect(config)

    suspend fun sendPacket(
        packet: RelayPacket,
        ackTimeoutMillis: Long = DEFAULT_PACKET_ACK_TIMEOUT_MILLIS,
    ): Result<RelayPacketAck>

    suspend fun sendCommand(
        command: RelayCommand,
        ackTimeoutMillis: Long = DEFAULT_PACKET_ACK_TIMEOUT_MILLIS,
    ): Result<RelayCommandAck> {
        return Result.failure(
            UnsupportedOperationException(
                "Relay control commands are not supported by this connection client.",
            ),
        )
    }

    suspend fun disconnect()
}
