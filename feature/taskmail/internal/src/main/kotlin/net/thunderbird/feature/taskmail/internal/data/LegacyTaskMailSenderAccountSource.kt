package net.thunderbird.feature.taskmail.internal.data

import net.thunderbird.core.android.account.LegacyAccountDto
import net.thunderbird.core.android.account.LegacyAccountDtoManager
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSenderAccount

internal class LegacyTaskMailSenderAccountSource(
    private val accountManager: LegacyAccountDtoManager,
) : TaskMailSenderAccountSource {

    override fun getSenderAccounts(): List<TaskMailSenderAccount> {
        return accountManager.getAccounts()
            .asSequence()
            .filter(LegacyAccountDto::isFinishedSetup)
            .map { account ->
                TaskMailSenderAccount(
                    accountUuid = account.uuid,
                    displayName = account.displayName,
                    emailAddress = account.email,
                )
            }
            .sortedBy(TaskMailSenderAccount::displayLabel)
            .toList()
    }

    override fun getAccount(accountUuid: String): LegacyAccountDto? {
        return accountManager.getAccount(accountUuid)
            ?.takeIf(LegacyAccountDto::isFinishedSetup)
    }
}
