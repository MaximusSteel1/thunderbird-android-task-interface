package net.thunderbird.feature.taskmail.internal.domain.usecase

import net.thunderbird.feature.taskmail.internal.data.TaskMailSenderAccountSource
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSenderAccount

internal class GetTaskMailSenderAccounts(
    private val senderAccountSource: TaskMailSenderAccountSource,
) {
    operator fun invoke(): List<TaskMailSenderAccount> {
        return senderAccountSource.getSenderAccounts()
    }
}
