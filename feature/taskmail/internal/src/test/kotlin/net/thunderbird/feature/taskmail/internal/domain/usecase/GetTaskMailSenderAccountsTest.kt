package net.thunderbird.feature.taskmail.internal.domain.usecase

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import net.thunderbird.core.android.account.LegacyAccountDto
import net.thunderbird.feature.taskmail.internal.data.TaskMailSenderAccountSource
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSenderAccount

class GetTaskMailSenderAccountsTest {

    @Test
    fun `invoke should return available sender accounts`() {
        val senderAccounts = listOf(
            TaskMailSenderAccount(
                accountUuid = "account-1",
                displayName = "Work",
                emailAddress = "work@example.org",
            ),
        )
        val testSubject = GetTaskMailSenderAccounts(
            senderAccountSource = FakeTaskMailSenderAccountSource(senderAccounts),
        )

        val result = testSubject()

        assertThat(result).isEqualTo(senderAccounts)
    }
}

private class FakeTaskMailSenderAccountSource(
    private val senderAccounts: List<TaskMailSenderAccount>,
) : TaskMailSenderAccountSource {
    override fun getSenderAccounts(): List<TaskMailSenderAccount> = senderAccounts

    override fun getAccount(accountUuid: String): LegacyAccountDto? = null
}
