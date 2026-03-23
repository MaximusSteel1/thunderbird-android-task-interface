package net.thunderbird.feature.taskmail.internal.domain.usecase

import net.thunderbird.feature.taskmail.internal.domain.filesample.TaskMailFileSampleRequest
import net.thunderbird.feature.taskmail.internal.domain.filesample.TaskMailFileSampleResult
import net.thunderbird.feature.taskmail.internal.domain.filesample.TaskMailFileSampleSender
import net.thunderbird.feature.taskmail.internal.domain.model.RelayTransportConfig

internal class SendTaskMailFileSample(
    private val sender: TaskMailFileSampleSender,
) {
    suspend operator fun invoke(
        config: RelayTransportConfig,
        request: TaskMailFileSampleRequest,
    ): TaskMailFileSampleResult {
        return sender.send(
            config = config,
            request = request,
        )
    }
}
