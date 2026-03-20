package net.thunderbird.feature.taskmail.internal.domain.repository

import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailBotMailboxSettings

internal interface TaskMailBotMailboxSettingsRepository {
    fun getSettings(): TaskMailBotMailboxSettings

    fun saveAddress(address: String): Boolean
}
