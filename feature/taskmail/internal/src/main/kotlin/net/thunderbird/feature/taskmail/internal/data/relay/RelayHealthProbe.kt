package net.thunderbird.feature.taskmail.internal.data.relay

import net.thunderbird.feature.taskmail.internal.domain.model.RelayHealthStatus
import net.thunderbird.feature.taskmail.internal.domain.model.RelayTransportConfig

internal interface RelayHealthProbe {
    suspend fun probe(config: RelayTransportConfig): Result<RelayHealthStatus>
}
