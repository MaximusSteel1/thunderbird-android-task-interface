package net.thunderbird.feature.taskmail.internal.data

import net.thunderbird.feature.taskmail.internal.domain.model.RelayTransportConfig
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskTransportConfigRepository

internal class DefaultTaskTransportConfigRepository(
    private val settingsStorage: TaskMailSettingsStorage,
) : TaskTransportConfigRepository {

    override fun getRelayTransportConfig(): RelayTransportConfig {
        val enabled = settingsStorage.getStringOrNull(TaskMailSettingsKeys.RELAY_ENABLED)
            ?.toBooleanStrictOrNull()
            ?: false
        val host = settingsStorage.getStringOrNull(TaskMailSettingsKeys.RELAY_HOST)
            ?.trim()
            .takeUnless(String?::isNullOrEmpty)
            ?: RelayTransportConfig.DEFAULT_HOST
        val port = settingsStorage.getStringOrNull(TaskMailSettingsKeys.RELAY_PORT)
            ?.toIntOrNull()
            ?.takeIf { it > 0 }
            ?: RelayTransportConfig.DEFAULT_PORT
        val useTls = settingsStorage.getStringOrNull(TaskMailSettingsKeys.RELAY_USE_TLS)
            ?.toBooleanStrictOrNull()
            ?: RelayTransportConfig.DEFAULT_USE_TLS
        val path = settingsStorage.getStringOrNull(TaskMailSettingsKeys.RELAY_PATH)
            ?.trim()
            .takeUnless(String?::isNullOrEmpty)
            ?: RelayTransportConfig.DEFAULT_PATH
        val transportToken = settingsStorage.getStringOrNull(TaskMailSettingsKeys.RELAY_TRANSPORT_TOKEN)
            ?.trim()
            .orEmpty()
        val androidAppToken = settingsStorage.getStringOrNull(TaskMailSettingsKeys.ANDROID_APP_TOKEN)
            ?.trim()
            .orEmpty()

        return RelayTransportConfig(
            enabled = enabled,
            host = host,
            port = port,
            useTls = useTls,
            path = path,
            transportToken = transportToken,
            androidAppToken = androidAppToken,
        )
    }

    override fun saveRelayTransportConfig(config: RelayTransportConfig): Boolean {
        val normalized = config.normalized()
        val writes = listOf(
            settingsStorage.putString(TaskMailSettingsKeys.RELAY_ENABLED, normalized.enabled.toString()),
            settingsStorage.putString(TaskMailSettingsKeys.RELAY_HOST, normalized.host),
            settingsStorage.putString(TaskMailSettingsKeys.RELAY_PORT, normalized.port.toString()),
            settingsStorage.putString(TaskMailSettingsKeys.RELAY_USE_TLS, normalized.useTls.toString()),
            settingsStorage.putString(TaskMailSettingsKeys.RELAY_PATH, normalized.path),
            settingsStorage.putString(
                TaskMailSettingsKeys.RELAY_TRANSPORT_TOKEN,
                normalized.transportToken.takeIf(String::isNotBlank),
            ),
            settingsStorage.putString(
                TaskMailSettingsKeys.ANDROID_APP_TOKEN,
                normalized.androidAppToken.takeIf(String::isNotBlank),
            ),
        )

        return writes.all { it }
    }
}
