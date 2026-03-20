package net.thunderbird.feature.taskmail.internal.data.cache

import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import net.thunderbird.feature.taskmail.internal.domain.model.MessageSyncState
import net.thunderbird.feature.taskmail.internal.domain.repository.MessageSyncStateRepository

internal class FileBackedMessageSyncStateRepository(
    storageDirectory: File,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : MessageSyncStateRepository {
    private val storageFile = File(storageDirectory, STORAGE_FILE_NAME)
    private val writeMutex = Mutex()
    private var states = loadStates()

    override suspend fun getState(
        source: String,
        scopeKey: String,
    ): MessageSyncState? {
        return states[stateKey(source = source, scopeKey = scopeKey)]
    }

    override suspend fun upsertState(state: MessageSyncState) {
        writeMutex.withLock {
            states = states.toMutableMap().apply {
                put(stateKey(source = state.source, scopeKey = state.scopeKey), state)
            }
            writeStates(states.values.sortedWith(syncStateComparator))
        }
    }

    private fun loadStates(): Map<String, MessageSyncState> {
        if (!storageFile.exists()) return emptyMap()

        return runCatching {
            json.parseToJsonElement(storageFile.readText())
                .jsonObject
                .decodeStates()
                .associateBy { state ->
                    stateKey(source = state.source, scopeKey = state.scopeKey)
                }
        }.getOrDefault(emptyMap())
    }

    private fun writeStates(states: List<MessageSyncState>) {
        storageFile.parentFile?.mkdirs()
        storageFile.writeText(
            buildJsonObject {
                put("version", STORAGE_VERSION)
                put(
                    "states",
                    JsonArray(states.map(MessageSyncState::toJsonObject)),
                )
            }.toString(),
        )
    }

    private fun JsonObject.decodeStates(): List<MessageSyncState> {
        return this["states"]
            ?.jsonArray
            ?.mapNotNull { element ->
                (element as? JsonObject)?.toMessageSyncState()
            }
            .orEmpty()
    }

    private companion object {
        const val STORAGE_VERSION = 1
        const val STORAGE_FILE_NAME = "taskmail_message_sync_state.json"

        val syncStateComparator = compareBy<MessageSyncState>(
            MessageSyncState::source,
            MessageSyncState::scopeKey,
        )
    }
}

private fun MessageSyncState.toJsonObject(): JsonObject {
    return buildJsonObject {
        put("source", source)
        put("scopeKey", scopeKey)
        putNullable("lastCursor", lastCursor)
        putNullable("lastSyncAt", lastSyncAt)
    }
}

private fun JsonObject.toMessageSyncState(): MessageSyncState {
    return MessageSyncState(
        source = string("source"),
        scopeKey = string("scopeKey"),
        lastCursor = optionalString("lastCursor"),
        lastSyncAt = optionalLong("lastSyncAt"),
    )
}

private fun stateKey(
    source: String,
    scopeKey: String,
): String = "$source|$scopeKey"

private fun JsonObject.string(name: String): String {
    return this[name]?.jsonPrimitive?.contentOrNull.orEmpty()
}

private fun JsonObject.optionalString(name: String): String? {
    return this[name]?.jsonPrimitive?.contentOrNull
}

private fun JsonObject.optionalLong(name: String): Long? {
    return this[name]?.jsonPrimitive?.longOrNull
}

private fun JsonObjectBuilder.putNullable(name: String, value: String?) {
    if (value != null) {
        put(name, JsonPrimitive(value))
    }
}

private fun JsonObjectBuilder.putNullable(name: String, value: Long?) {
    if (value != null) {
        put(name, JsonPrimitive(value))
    }
}

private fun JsonObjectBuilder.put(name: String, value: String) {
    put(name, JsonPrimitive(value))
}

private fun JsonObjectBuilder.put(name: String, value: Int) {
    put(name, JsonPrimitive(value))
}
