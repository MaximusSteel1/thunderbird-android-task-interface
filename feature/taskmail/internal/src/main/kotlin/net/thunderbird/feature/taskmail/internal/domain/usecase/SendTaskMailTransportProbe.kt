package net.thunderbird.feature.taskmail.internal.domain.usecase

import net.thunderbird.feature.taskmail.internal.domain.model.RelayTransportConfig
import net.thunderbird.feature.taskmail.internal.domain.transportprobe.TaskMailTransportProbeDispatchResult
import net.thunderbird.feature.taskmail.internal.domain.transportprobe.TaskMailTransportProbeRequest
import net.thunderbird.feature.taskmail.internal.domain.transportprobe.TaskMailTransportProbeSender

internal class SendTaskMailTransportProbe(
    private val sender: TaskMailTransportProbeSender,
) {
    suspend operator fun invoke(
        config: RelayTransportConfig,
        request: TaskMailTransportProbeRequest,
    ): TaskMailTransportProbeDispatchResult {
        return sender.send(
            config = config,
            request = request,
        )
    }
}
