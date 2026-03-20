package net.thunderbird.feature.taskmail.internal.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import net.thunderbird.core.android.account.AccountRemovedListener
import net.thunderbird.core.android.account.AccountsChangeListener
import net.thunderbird.core.android.account.Identity
import net.thunderbird.core.android.account.LegacyAccountDto
import net.thunderbird.core.android.account.LegacyAccountDtoManager

class LegacyTaskMailSenderAccountSourceTest {

    @Test
    fun `getSenderAccounts should include only finished setup accounts`() {
        val testSubject = LegacyTaskMailSenderAccountSource(
            accountManager = FakeLegacyAccountDtoManager(
                accounts = listOf(
                    account(
                        uuid = "11111111-1111-1111-1111-111111111111",
                        name = "Work",
                        email = "work@example.org",
                        finishedSetup = true,
                    ),
                    account(
                        uuid = "22222222-2222-2222-2222-222222222222",
                        name = "Draft",
                        email = "draft@example.org",
                        finishedSetup = false,
                    ),
                ),
            ),
        )

        val result = testSubject.getSenderAccounts()

        assertEquals(1, result.size)
        assertEquals("11111111-1111-1111-1111-111111111111", result.single().accountUuid)
        assertEquals("Work <work@example.org>", result.single().displayLabel)
    }

    @Test
    fun `getAccount should return null for unfinished accounts`() {
        val testSubject = LegacyTaskMailSenderAccountSource(
            accountManager = FakeLegacyAccountDtoManager(
                accounts = listOf(
                    account(
                        uuid = "11111111-1111-1111-1111-111111111111",
                        name = "Draft",
                        email = "draft@example.org",
                        finishedSetup = false,
                    ),
                ),
            ),
        )

        val result = testSubject.getAccount("11111111-1111-1111-1111-111111111111")

        assertNull(result)
    }
}

private class FakeLegacyAccountDtoManager(
    private val accounts: List<LegacyAccountDto>,
) : LegacyAccountDtoManager {
    override fun getAccounts(): List<LegacyAccountDto> = accounts

    override fun getAccountsFlow(): Flow<List<LegacyAccountDto>> = emptyFlow()

    override fun getAccount(accountUuid: String): LegacyAccountDto? = accounts.firstOrNull { it.uuid == accountUuid }

    override fun getAccountFlow(accountUuid: String): Flow<LegacyAccountDto?> = emptyFlow()

    override fun addAccountRemovedListener(listener: AccountRemovedListener) = Unit

    override fun moveAccount(account: LegacyAccountDto, newPosition: Int) = Unit

    override fun addOnAccountsChangeListener(accountsChangeListener: AccountsChangeListener) = Unit

    override fun removeOnAccountsChangeListener(accountsChangeListener: AccountsChangeListener) = Unit

    override fun saveAccount(account: LegacyAccountDto) = Unit
}

private fun account(
    uuid: String,
    name: String,
    email: String,
    finishedSetup: Boolean,
): LegacyAccountDto {
    return LegacyAccountDto(uuid = uuid).apply {
        isFinishedSetup = finishedSetup
        this.name = name
        identities = mutableListOf(
            Identity(
                name = name,
                email = email,
            ),
        )
    }
}
