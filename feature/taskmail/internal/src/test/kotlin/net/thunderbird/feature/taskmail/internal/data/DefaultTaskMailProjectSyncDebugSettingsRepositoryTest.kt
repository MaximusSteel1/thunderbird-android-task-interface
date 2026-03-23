package net.thunderbird.feature.taskmail.internal.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

class DefaultTaskMailProjectSyncDebugSettingsRepositoryTest {

    @Test
    fun `isFileLoggingEnabled should default to false`() {
        val testSubject = DefaultTaskMailProjectSyncDebugSettingsRepository(
            settingsStorage = FakeTaskMailSettingsStorage(),
        )

        val result = testSubject.isFileLoggingEnabled()

        assertThat(result).isEqualTo(false)
    }

    @Test
    fun `setFileLoggingEnabled should persist boolean as string`() {
        val settingsStorage = FakeTaskMailSettingsStorage()
        val testSubject = DefaultTaskMailProjectSyncDebugSettingsRepository(
            settingsStorage = settingsStorage,
        )

        val result = testSubject.setFileLoggingEnabled(true)

        assertThat(result).isEqualTo(true)
        assertThat(settingsStorage.getStringOrNull(TaskMailSettingsKeys.PROJECT_SYNC_DEBUG_FILE_LOGGING_ENABLED))
            .isEqualTo("true")
        assertThat(testSubject.isFileLoggingEnabled()).isEqualTo(true)
    }
}
