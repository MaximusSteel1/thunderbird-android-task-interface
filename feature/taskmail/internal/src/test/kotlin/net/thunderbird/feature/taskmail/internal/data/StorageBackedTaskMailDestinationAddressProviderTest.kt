package net.thunderbird.feature.taskmail.internal.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import net.thunderbird.feature.taskmail.internal.BuildConfig

class StorageBackedTaskMailDestinationAddressProviderTest {

    @Test
    fun `getDestinationAddress should return trimmed configured bot mailbox`() {
        val settingsStorage = FakeTaskMailSettingsStorage()
        settingsStorage.putString(TaskMailSettingsKeys.BOT_MAILBOX_ADDRESS, "  bot@example.org ")

        val testSubject = StorageBackedTaskMailDestinationAddressProvider(
            settingsStorage = settingsStorage,
        )

        val destinationAddress = testSubject.getDestinationAddress()

        assertThat(destinationAddress).isEqualTo("bot@example.org")
    }

    @Test
    fun `getDestinationAddress should return latest saved bot mailbox`() {
        val settingsStorage = FakeTaskMailSettingsStorage()
        val testSubject = StorageBackedTaskMailDestinationAddressProvider(
            settingsStorage = settingsStorage,
        )

        settingsStorage.putString(TaskMailSettingsKeys.BOT_MAILBOX_ADDRESS, "saved@example.org")

        val destinationAddress = testSubject.getDestinationAddress()

        assertThat(destinationAddress).isEqualTo("saved@example.org")
    }

    @Test
    fun `getDestinationAddress should return debug default or null when key is missing`() {
        val testSubject = StorageBackedTaskMailDestinationAddressProvider(
            settingsStorage = FakeTaskMailSettingsStorage(),
        )

        val destinationAddress = testSubject.getDestinationAddress()

        val expectedDefault = BuildConfig.TASKMAIL_DEFAULT_BOT_MAILBOX
            .trim()
            .takeIf { it.isNotEmpty() }

        assertThat(destinationAddress).isEqualTo(expectedDefault)
    }
}
