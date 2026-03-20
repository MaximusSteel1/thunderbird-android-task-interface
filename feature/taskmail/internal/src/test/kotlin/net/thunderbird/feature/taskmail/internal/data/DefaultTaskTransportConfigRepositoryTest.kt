package net.thunderbird.feature.taskmail.internal.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import net.thunderbird.feature.taskmail.internal.domain.model.RelayTransportConfig

class DefaultTaskTransportConfigRepositoryTest {

    @Test
    fun `getRelayTransportConfig should return saved relay config`() {
        val settingsStorage = FakeTaskMailSettingsStorage().apply {
            putString(TaskMailSettingsKeys.RELAY_ENABLED, "true")
            putString(TaskMailSettingsKeys.RELAY_HOST, "relay.example.org")
            putString(TaskMailSettingsKeys.RELAY_PORT, "9443")
            putString(TaskMailSettingsKeys.RELAY_USE_TLS, "true")
            putString(TaskMailSettingsKeys.RELAY_PATH, "/relay")
            putString(TaskMailSettingsKeys.RELAY_TRANSPORT_TOKEN, "secret-token")
        }
        val testSubject = DefaultTaskTransportConfigRepository(settingsStorage)

        val config = testSubject.getRelayTransportConfig()

        assertThat(config).isEqualTo(
            RelayTransportConfig(
                enabled = true,
                host = "relay.example.org",
                port = 9443,
                useTls = true,
                path = "/relay",
                transportToken = "secret-token",
            ),
        )
    }

    @Test
    fun `saveRelayTransportConfig should persist normalized relay config`() {
        val settingsStorage = FakeTaskMailSettingsStorage()
        val testSubject = DefaultTaskTransportConfigRepository(settingsStorage)

        val result = testSubject.saveRelayTransportConfig(
            RelayTransportConfig(
                enabled = true,
                host = "  relay.example.org ",
                port = 8787,
                useTls = false,
                path = "relay",
                transportToken = " secret-token ",
            ),
        )

        assertThat(result).isEqualTo(true)
        assertThat(settingsStorage.getStringOrNull(TaskMailSettingsKeys.RELAY_HOST)).isEqualTo("relay.example.org")
        assertThat(settingsStorage.getStringOrNull(TaskMailSettingsKeys.RELAY_PATH)).isEqualTo("/relay")
        assertThat(settingsStorage.getStringOrNull(TaskMailSettingsKeys.RELAY_TRANSPORT_TOKEN))
            .isEqualTo("secret-token")
    }
}
