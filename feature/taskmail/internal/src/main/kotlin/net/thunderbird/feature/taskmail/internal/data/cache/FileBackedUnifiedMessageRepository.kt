@file:Suppress("TooManyFunctions")

package net.thunderbird.feature.taskmail.internal.data.cache

import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import net.thunderbird.feature.taskmail.internal.domain.model.UnifiedMessage
import net.thunderbird.feature.taskmail.internal.domain.repository.UnifiedMessageRepository

internal class FileBackedUnifiedMessageRepository(
    storageDirectory: File,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : UnifiedMessageRepository {
    private val storageFile = File(storageDirectory, STORAGE_FILE_NAME)
    private val writeMutex = Mutex()
    private val messages = MutableStateFlow(loadMessages())

    override fun observeMessages(taskId: String): Flow<List<UnifiedMessage>> {
        return messages.map { cachedMessages ->
            cachedMessages.filter { message -> message.taskId == taskId }
        }
    }

    override suspend fun getAllMessages(): List<UnifiedMessage> {
        return messages.value
    }

    override suspend fun upsertMessages(messages: List<UnifiedMessage>) {
        if (messages.isEmpty()) return

        writeMutex.withLock {
            val mergedMessages = this.messages.value
                .associateBy(UnifiedMessage::storageKey)
                .toMutableMap()

            messages.forEach { message ->
                mergedMessages[message.storageKey()] = message
            }

            val persistedMessages = mergedMessages.values.sortedWith(unifiedMessageComparator)
            writeMessages(persistedMessages)
            this.messages.value = persistedMessages
        }
    }

    override suspend fun findBySourceMessageId(
        source: String,
        sourceMessageId: String,
    ): UnifiedMessage? {
        return messages.value.firstOrNull { message ->
            message.source == source && message.sourceMessageId == sourceMessageId
        }
    }

    private fun loadMessages(): List<UnifiedMessage> {
        if (!storageFile.exists()) return emptyList()

        return runCatching {
            json.parseToJsonElement(storageFile.readText())
                .jsonObject
                .let(::decodeMessages)
                .sortedWith(unifiedMessageComparator)
        }.getOrDefault(emptyList())
    }

    private fun writeMessages(messages: List<UnifiedMessage>) {
        storageFile.parentFile?.mkdirs()
        storageFile.writeText(
            buildJsonObject {
                put("version", STORAGE_VERSION)
                put(
                    "messages",
                    JsonArray(messages.map(UnifiedMessage::toJsonObject)),
                )
            }.toString(),
        )
    }

    private fun decodeMessages(root: JsonObject): List<UnifiedMessage> {
        return root["messages"]
            ?.jsonArray
            ?.mapNotNull { element ->
                (element as? JsonObject)?.toUnifiedMessage()
            }
            .orEmpty()
    }

    private companion object {
        const val STORAGE_VERSION = 1
        const val STORAGE_FILE_NAME = "taskmail_unified_messages.json"

        val unifiedMessageComparator = compareBy<UnifiedMessage>(
            UnifiedMessage::createdAt,
            UnifiedMessage::source,
            UnifiedMessage::sourceMessageId,
        )
    }
}

private fun UnifiedMessage.storageKey(): String = "$source|$sourceMessageId"

private fun UnifiedMessage.toJsonObject(): JsonObject {
    return buildJsonObject {
        put("source", source)
        put("sourceMessageId", sourceMessageId)
        putNullable("taskId", taskId)
        put("createdAt", createdAt)
        put("contentHash", contentHash)
        put("parserVersion", parserVersion)
        put("payloadJson", payloadJson)
        put("messageJson", messageJson)
    }
}

private fun JsonObject.toUnifiedMessage(): UnifiedMessage {
    return UnifiedMessage(
        source = string("source"),
        sourceMessageId = string("sourceMessageId"),
        taskId = optionalString("taskId"),
        createdAt = long("createdAt"),
        contentHash = string("contentHash"),
        parserVersion = int("parserVersion"),
        payloadJson = string("payloadJson"),
        messageJson = string("messageJson"),
    )
}

private fun JsonObject.string(name: String): String {
    return this[name]?.jsonPrimitive?.contentOrNull.orEmpty()
}

private fun JsonObject.optionalString(name: String): String? {
    return this[name]?.jsonPrimitive?.contentOrNull
}

private fun JsonObject.long(name: String): Long {
    return this[name]?.jsonPrimitive?.longOrNull ?: 0L
}

private fun JsonObject.int(name: String): Int {
    return this[name]?.jsonPrimitive?.intOrNull ?: 0
}

private fun JsonObjectBuilder.putNullable(name: String, value: String?) {
    if (value != null) {
        put(name, JsonPrimitive(value))
    }
}

private fun JsonObjectBuilder.put(name: String, value: String) {
    put(name, JsonPrimitive(value))
}

private fun JsonObjectBuilder.put(name: String, value: Long) {
    put(name, JsonPrimitive(value))
}

private fun JsonObjectBuilder.put(name: String, value: Int) {
    put(name, JsonPrimitive(value))
}
