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
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSessionActionSendRecord
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskMailSessionActionSendRecordRepository
import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailDirectSessionActionTarget

internal class FileBackedTaskMailSessionActionSendRecordRepository(
    storageDirectory: File,
    private val codec: TaskMailSessionActionSendRecordJsonCodec = TaskMailSessionActionSendRecordJsonCodec(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) : TaskMailSessionActionSendRecordRepository {
    private val storageFile = File(storageDirectory, STORAGE_FILE_NAME)
    private val writeMutex = Mutex()
    private var records: List<TaskMailSessionActionSendRecord> = loadRecords()

    override suspend fun getLatestRecord(
        target: TaskMailDirectSessionActionTarget,
    ): TaskMailSessionActionSendRecord? {
        return records.firstOrNull { record ->
            record.target.sessionId == target.sessionId &&
                (
                    target.workspaceId == null ||
                        record.target.workspaceId == target.workspaceId
                    )
        }
    }

    override suspend fun saveRecord(record: TaskMailSessionActionSendRecord) {
        writeMutex.withLock {
            persistRecords(
                (listOf(record) + records)
                    .distinctBy { candidate ->
                        listOf(
                            candidate.target.workspaceId.orEmpty(),
                            candidate.target.sessionId,
                            candidate.actionType.name,
                            candidate.recordedAt.toString(),
                            candidate.evidence.outcome.name,
                            candidate.evidence.switchGate.name,
                            candidate.evidence.requestId.orEmpty(),
                            candidate.evidence.receiptId.orEmpty(),
                        ).joinToString("|")
                    }
                    .take(MAX_RECORD_COUNT),
            )
        }
    }

    private fun loadRecords(): List<TaskMailSessionActionSendRecord> {
        if (!storageFile.exists()) return emptyList()

        return runCatching {
            json.parseToJsonElement(storageFile.readText())
                .jsonObject
                .let(::decodeRecords)
        }.getOrDefault(emptyList())
    }

    private fun decodeRecords(root: JsonObject): List<TaskMailSessionActionSendRecord> {
        if (root["version"]?.jsonPrimitive?.intOrNull != STORAGE_VERSION) {
            return emptyList()
        }

        return root["records"]
            ?.jsonArray
            ?.mapNotNull { element ->
                codec.decode(element.jsonPrimitive.contentOrNull.orEmpty())
            }
            .orEmpty()
            .sortedByDescending(TaskMailSessionActionSendRecord::recordedAt)
    }

    private fun persistRecords(records: List<TaskMailSessionActionSendRecord>) {
        val persistedRecords = records
            .sortedByDescending(TaskMailSessionActionSendRecord::recordedAt)
            .take(MAX_RECORD_COUNT)
        writeRecords(persistedRecords)
        this.records = persistedRecords
    }

    private fun writeRecords(records: List<TaskMailSessionActionSendRecord>) {
        storageFile.parentFile?.mkdirs()
        storageFile.writeText(
            buildJsonObject {
                put("version", STORAGE_VERSION)
                put(
                    "records",
                    JsonArray(
                        records.map { record ->
                            JsonPrimitive(codec.encode(record))
                        },
                    ),
                )
            }.toString(),
        )
    }

    private companion object {
        const val STORAGE_VERSION = 1
        const val STORAGE_FILE_NAME = "taskmail_session_action_send_records.json"
        const val MAX_RECORD_COUNT = 50
    }
}

private fun JsonObjectBuilder.put(name: String, value: String) {
    put(name, JsonPrimitive(value))
}

private fun JsonObjectBuilder.put(name: String, value: Int) {
    put(name, JsonPrimitive(value))
}
