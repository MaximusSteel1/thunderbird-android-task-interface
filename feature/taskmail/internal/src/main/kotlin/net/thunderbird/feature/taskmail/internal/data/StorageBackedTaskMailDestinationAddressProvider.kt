package net.thunderbird.feature.taskmail.internal.data

import net.thunderbird.feature.taskmail.internal.BuildConfig

internal class StorageBackedTaskMailDestinationAddressProvider(
    private val settingsStorage: TaskMailSettingsStorage,
) : TaskMailDestinationAddressProvider {

    override fun getDestinationAddress(): String? {
        return settingsStorage.getStringOrNull(TaskMailSettingsKeys.BOT_MAILBOX_ADDRESS)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: BuildConfig.TASKMAIL_DEFAULT_BOT_MAILBOX
                .trim()
                .takeIf { it.isNotEmpty() }
    }
}
