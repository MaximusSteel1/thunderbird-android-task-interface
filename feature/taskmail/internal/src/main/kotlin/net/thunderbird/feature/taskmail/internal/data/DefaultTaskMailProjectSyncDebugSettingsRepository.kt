package net.thunderbird.feature.taskmail.internal.data

import net.thunderbird.feature.taskmail.internal.domain.repository.TaskMailProjectSyncDebugSettingsRepository

internal class DefaultTaskMailProjectSyncDebugSettingsRepository(
    private val settingsStorage: TaskMailSettingsStorage,
) : TaskMailProjectSyncDebugSettingsRepository {

    override fun isFileLoggingEnabled(): Boolean {
        return settingsStorage.getStringOrNull(TaskMailSettingsKeys.PROJECT_SYNC_DEBUG_FILE_LOGGING_ENABLED)
            ?.toBooleanStrictOrNull()
            ?: false
    }

    override fun setFileLoggingEnabled(enabled: Boolean): Boolean {
        return settingsStorage.putString(
            TaskMailSettingsKeys.PROJECT_SYNC_DEBUG_FILE_LOGGING_ENABLED,
            enabled.toString(),
        )
    }
}
