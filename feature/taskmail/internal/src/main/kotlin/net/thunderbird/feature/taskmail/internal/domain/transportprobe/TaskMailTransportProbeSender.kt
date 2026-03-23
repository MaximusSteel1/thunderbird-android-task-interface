package net.thunderbird.feature.taskmail.internal.domain.transportprobe

import net.thunderbird.feature.taskmail.internal.domain.model.RelayTransportConfig

internal interface TaskMailTransportProbeSender {
    suspend fun send(
        config: RelayTransportConfig,
        request: TaskMailTransportProbeRequest,
    ): TaskMailTransportProbeDispatchResult
}
