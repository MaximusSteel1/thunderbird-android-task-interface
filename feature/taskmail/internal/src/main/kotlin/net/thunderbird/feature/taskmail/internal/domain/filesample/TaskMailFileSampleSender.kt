package net.thunderbird.feature.taskmail.internal.domain.filesample

import net.thunderbird.feature.taskmail.internal.domain.model.RelayTransportConfig

internal interface TaskMailFileSampleSender {
    suspend fun send(
        config: RelayTransportConfig,
        request: TaskMailFileSampleRequest,
    ): TaskMailFileSampleResult
}
