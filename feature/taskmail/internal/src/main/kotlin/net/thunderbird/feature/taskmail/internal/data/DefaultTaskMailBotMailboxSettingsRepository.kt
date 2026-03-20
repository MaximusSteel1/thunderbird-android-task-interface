package net.thunderbird.feature.taskmail.internal.data

import net.thunderbird.feature.taskmail.internal.BuildConfig
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailBotMailboxSettings
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskMailBotMailboxSettingsRepository

internal class DefaultTaskMailBotMailboxSettingsRepository(
    private val settingsStorage: TaskMailSettingsStorage,
) : TaskMailBotMailboxSettingsRepository {

    override fun getSettings(): TaskMailBotMailboxSettings {
        val savedAddress = settingsStorage.getStringOrNull(TaskMailSettingsKeys.BOT_MAILBOX_ADDRESS)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        if (savedAddress != null) {
            return TaskMailBotMailboxSettings(
                address = savedAddress,
                source = TaskMailBotMailboxSettings.Source.Saved,
            )
        }

        val defaultAddress = BuildConfig.TASKMAIL_DEFAULT_BOT_MAILBOX
            .trim()
            .takeIf { it.isNotEmpty() }

        return if (defaultAddress != null) {
            TaskMailBotMailboxSettings(
                address = defaultAddress,
                source = TaskMailBotMailboxSettings.Source.BuildDefault,
            )
        } else {
            TaskMailBotMailboxSettings(
                address = null,
                source = TaskMailBotMailboxSettings.Source.Missing,
            )
        }
    }

    override fun saveAddress(address: String): Boolean {
        return settingsStorage.putString(
            key = TaskMailSettingsKeys.BOT_MAILBOX_ADDRESS,
            value = address.trim().takeIf { it.isNotEmpty() },
        )
    }
}
