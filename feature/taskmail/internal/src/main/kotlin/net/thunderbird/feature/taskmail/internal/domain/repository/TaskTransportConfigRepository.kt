package net.thunderbird.feature.taskmail.internal.domain.repository

import net.thunderbird.feature.taskmail.internal.domain.model.RelayTransportConfig

internal interface TaskTransportConfigRepository {
    fun getRelayTransportConfig(): RelayTransportConfig

    fun saveRelayTransportConfig(config: RelayTransportConfig): Boolean
}
