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
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionDetail
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionKey
import net.thunderbird.feature.taskmail.internal.domain.model.isCompatibleWith
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskSessionDetailRepository

internal class FileBackedTaskSessionDetailRepository(
    storageDirectory: File,
    private val codec: TaskSessionDetailJsonCodec = TaskSessionDetailJsonCodec(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) : TaskSessionDetailRepository {
    private val storageFile = File(storageDirectory, STORAGE_FILE_NAME)
    private val writeMutex = Mutex()
    private var sessionDetails: List<TaskSessionDetail> = loadSessionDetails()

    override suspend fun getTaskSessionDetail(key: TaskSessionKey): TaskSessionDetail? {
        return sessionDetails.firstOrNull { detail -> detail.key == key }
            ?: sessionDetails.firstOrNull { detail -> detail.key.isCompatibleWith(key) }
    }

    override suspend fun getTaskSessionDetails(): List<TaskSessionDetail> {
        return sessionDetails
    }

    override suspend fun replaceAllSessionDetails(details: List<TaskSessionDetail>) {
        writeMutex.withLock {
            persistSessionDetails(details)
        }
    }

    override suspend fun upsertSessionDetails(details: List<TaskSessionDetail>) {
        if (details.isEmpty()) return

        writeMutex.withLock {
            val mergedDetails = sessionDetails.toMutableList()

            details.forEach { detail ->
                val exactIndex = mergedDetails.indexOfFirst { existing -> existing.key == detail.key }
                val compatibleIndex = if (exactIndex >= 0) {
                    -1
                } else {
                    mergedDetails.indexOfFirst { existing -> existing.key.isCompatibleWith(detail.key) }
                }

                if (exactIndex >= 0) {
                    mergedDetails[exactIndex] = detail
                } else if (compatibleIndex >= 0) {
                    mergedDetails[compatibleIndex] = detail
                } else {
                    mergedDetails += detail
                }
            }

            persistSessionDetails(mergedDetails)
        }
    }

    override suspend fun removeSessionDetails(keys: List<TaskSessionKey>) {
        if (keys.isEmpty()) return

        writeMutex.withLock {
            val remainingDetails = sessionDetails.filterNot { detail ->
                keys.any { key ->
                    detail.key == key || detail.key.isCompatibleWith(key)
                }
            }

            if (remainingDetails.size == sessionDetails.size) return@withLock

            persistSessionDetails(remainingDetails)
        }
    }

    private fun loadSessionDetails(): List<TaskSessionDetail> {
        if (!storageFile.exists()) return emptyList()

        return runCatching {
            json.parseToJsonElement(storageFile.readText())
                .jsonObject
                .let(::decodeSessionDetails)
        }.getOrDefault(emptyList())
    }

    private fun writeSessionDetails(details: List<TaskSessionDetail>) {
        storageFile.parentFile?.mkdirs()
        storageFile.writeText(
            buildJsonObject {
                put("version", STORAGE_VERSION)
                put(
                    "sessionDetails",
                    JsonArray(
                        details.map { detail ->
                            JsonPrimitive(codec.encode(detail))
                        },
                    ),
                )
            }.toString(),
        )
    }

    private fun decodeSessionDetails(root: JsonObject): List<TaskSessionDetail> {
        if (root["version"]?.jsonPrimitive?.intOrNull != STORAGE_VERSION) {
            return emptyList()
        }

        return root["sessionDetails"]
            ?.jsonArray
            ?.mapNotNull { element ->
                codec.decode(element.jsonPrimitive.contentOrNull.orEmpty())
            }
            .orEmpty()
    }

    private fun persistSessionDetails(details: List<TaskSessionDetail>) {
        val persistedDetails = details.sortedWith(
            compareBy<TaskSessionDetail>(
                { it.key.workspaceId.orEmpty() },
                { it.key.sessionId.orEmpty() },
                { it.key.threadId.orEmpty() },
            ),
        )
        writeSessionDetails(persistedDetails)
        sessionDetails = persistedDetails
    }

    private companion object {
        const val STORAGE_VERSION = 3
        const val STORAGE_FILE_NAME = "taskmail_session_details.json"
    }
}

private fun JsonObjectBuilder.put(name: String, value: String) {
    put(name, JsonPrimitive(value))
}

private fun JsonObjectBuilder.put(name: String, value: Int) {
    put(name, JsonPrimitive(value))
}
