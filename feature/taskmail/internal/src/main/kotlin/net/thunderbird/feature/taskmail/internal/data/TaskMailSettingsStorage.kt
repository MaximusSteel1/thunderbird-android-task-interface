package net.thunderbird.feature.taskmail.internal.data

internal interface TaskMailSettingsStorage {
    fun getStringOrNull(key: String): String?

    fun putString(key: String, value: String?): Boolean
}
