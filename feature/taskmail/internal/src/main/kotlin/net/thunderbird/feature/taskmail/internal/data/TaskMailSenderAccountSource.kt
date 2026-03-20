package net.thunderbird.feature.taskmail.internal.data

import net.thunderbird.core.android.account.LegacyAccountDto
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSenderAccount

internal interface TaskMailSenderAccountSource {
    fun getSenderAccounts(): List<TaskMailSenderAccount>

    fun getAccount(accountUuid: String): LegacyAccountDto?
}
