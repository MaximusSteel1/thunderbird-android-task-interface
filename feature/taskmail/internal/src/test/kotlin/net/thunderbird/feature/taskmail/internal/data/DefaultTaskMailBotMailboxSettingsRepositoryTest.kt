package net.thunderbird.feature.taskmail.internal.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import net.thunderbird.feature.taskmail.internal.BuildConfig
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailBotMailboxSettings

class DefaultTaskMailBotMailboxSettingsRepositoryTest {

    @Test
    fun `getSettings should prefer saved address over build default`() {
        val settingsStorage = FakeTaskMailSettingsStorage().apply {
            putString(TaskMailSettingsKeys.BOT_MAILBOX_ADDRESS, " saved@example.org ")
        }
        val testSubject = DefaultTaskMailBotMailboxSettingsRepository(settingsStorage)

        val settings = testSubject.getSettings()

        assertThat(settings.address).isEqualTo("saved@example.org")
        assertThat(settings.source).isEqualTo(TaskMailBotMailboxSettings.Source.Saved)
    }

    @Test
    fun `getSettings should return build default or missing when saved address is absent`() {
        val testSubject = DefaultTaskMailBotMailboxSettingsRepository(FakeTaskMailSettingsStorage())

        val settings = testSubject.getSettings()
        val expectedDefault = BuildConfig.TASKMAIL_DEFAULT_BOT_MAILBOX
            .trim()
            .takeIf { it.isNotEmpty() }

        if (expectedDefault != null) {
            assertThat(settings.address).isEqualTo(expectedDefault)
            assertThat(settings.source).isEqualTo(TaskMailBotMailboxSettings.Source.BuildDefault)
        } else {
            assertThat(settings.address).isEqualTo(null)
            assertThat(settings.source).isEqualTo(TaskMailBotMailboxSettings.Source.Missing)
        }
    }

    @Test
    fun `saveAddress should persist trimmed address`() {
        val settingsStorage = FakeTaskMailSettingsStorage()
        val testSubject = DefaultTaskMailBotMailboxSettingsRepository(settingsStorage)

        val isSaved = testSubject.saveAddress("  saved@example.org  ")

        assertThat(isSaved).isEqualTo(true)
        assertThat(settingsStorage.getStringOrNull(TaskMailSettingsKeys.BOT_MAILBOX_ADDRESS))
            .isEqualTo("saved@example.org")
    }
}
