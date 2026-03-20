package net.thunderbird.feature.taskmail.internal.domain.usecase

import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailBotMailboxSettings
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskMailBotMailboxSettingsRepository

internal class GetTaskMailBotMailboxSettings(
    private val repository: TaskMailBotMailboxSettingsRepository,
) {
    operator fun invoke(): TaskMailBotMailboxSettings {
        return repository.getSettings()
    }
}
