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
            val mergedDetails = sessionDetails
                .associateBy(TaskSessionDetail::key)
                .toMutableMap()

            details.forEach { detail ->
                mergedDetails[detail.key] = detail
            }

            persistSessionDetails(mergedDetails.values.toList())
        }
    }

    override suspend fun removeSessionDetails(keys: List<TaskSessionKey>) {
        if (keys.isEmpty()) return

        writeMutex.withLock {
            val keysToRemove = keys.toSet()
            val remainingDetails = sessionDetails.filterNot { detail ->
                detail.key in keysToRemove
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
            compareBy<TaskSessionDetail>({ it.key.sessionId.orEmpty() }, { it.key.threadId }),
        )
        writeSessionDetails(persistedDetails)
        sessionDetails = persistedDetails
    }

    private companion object {
        const val STORAGE_VERSION = 2
        const val STORAGE_FILE_NAME = "taskmail_session_details.json"
    }
}

private fun JsonObjectBuilder.put(name: String, value: String) {
    put(name, JsonPrimitive(value))
}

private fun JsonObjectBuilder.put(name: String, value: Int) {
    put(name, JsonPrimitive(value))
}
