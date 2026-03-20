package net.thunderbird.feature.taskmail.internal.data.relay

import kotlinx.coroutines.flow.StateFlow
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayHelloAck
import net.thunderbird.feature.taskmail.internal.domain.model.RelayConnectionState
import net.thunderbird.feature.taskmail.internal.domain.model.RelayTransportConfig

internal interface RelayConnectionClient {
    val connectionState: StateFlow<RelayConnectionState>

    suspend fun connect(config: RelayTransportConfig): Result<RelayHelloAck>

    suspend fun disconnect()
}
