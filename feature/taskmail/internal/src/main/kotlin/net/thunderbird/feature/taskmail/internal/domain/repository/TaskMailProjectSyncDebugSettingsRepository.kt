package net.thunderbird.feature.taskmail.internal.domain.repository

internal interface TaskMailProjectSyncDebugSettingsRepository {
    fun isFileLoggingEnabled(): Boolean

    fun setFileLoggingEnabled(enabled: Boolean): Boolean
}
