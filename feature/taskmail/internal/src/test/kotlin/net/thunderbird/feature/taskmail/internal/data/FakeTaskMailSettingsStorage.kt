package net.thunderbird.feature.taskmail.internal.data

internal class FakeTaskMailSettingsStorage(
    private val values: MutableMap<String, String> = mutableMapOf(),
) : TaskMailSettingsStorage {
    override fun getStringOrNull(key: String): String? = values[key]

    override fun putString(key: String, value: String?): Boolean {
        if (value == null) {
            values.remove(key)
        } else {
            values[key] = value
        }

        return true
    }
}
