package net.thunderbird.feature.taskmail.internal.domain.usecase

import net.thunderbird.feature.taskmail.internal.domain.repository.TaskMailBotMailboxSettingsRepository

internal class SaveTaskMailBotMailboxSettings(
    private val repository: TaskMailBotMailboxSettingsRepository,
) {
    operator fun invoke(address: String): Boolean {
        return repository.saveAddress(address)
    }
}
