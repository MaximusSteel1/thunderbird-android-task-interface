package net.thunderbird.feature.taskmail.internal.data

import com.fsck.k9.Preferences

internal class PreferencesBackedTaskMailSettingsStorage(
    private val preferences: Preferences,
) : TaskMailSettingsStorage {

    override fun getStringOrNull(key: String): String? {
        return preferences.storage.getStringOrNull(key)
    }

    override fun putString(key: String, value: String?): Boolean {
        return preferences.createStorageEditor()
            .putString(key, value)
            .commit()
    }
}
