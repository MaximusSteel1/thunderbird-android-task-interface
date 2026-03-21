package net.thunderbird.feature.taskmail.internal.data.relay

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayHelloAck
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayPacket
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayPacketAck
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelaySessionUpdate
import net.thunderbird.feature.taskmail.internal.domain.model.RelayConnectionState
import net.thunderbird.feature.taskmail.internal.domain.model.RelayTransportConfig

internal interface RelayConnectionClient {
    val connectionState: StateFlow<RelayConnectionState>
    val sessionUpdates: SharedFlow<RelaySessionUpdate>

    suspend fun connect(config: RelayTransportConfig): Result<RelayHelloAck>

    suspend fun sendPacket(packet: RelayPacket): Result<RelayPacketAck>

    suspend fun disconnect()
}
