package net.thunderbird.feature.taskmail.internal.data.cache

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import net.thunderbird.feature.taskmail.internal.domain.model.RelayBootstrapStatus
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailBackend
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailDirectOutcome
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailDirectSendEvidence
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailDirectSwitchGate
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailNewTaskSendRecord

internal class TaskMailNewTaskSendRecordJsonCodec(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun encode(record: TaskMailNewTaskSendRecord): String {
        return buildJsonObject {
            put("recordedAt", record.recordedAt)
            put("senderAccountId", record.senderAccountId)
            put("backend", record.backend.wireValue)
            put("repoPath", record.repoPath)
            putNullable("workdir", record.workdir)
            put("evidence", record.evidence.toJsonObject())
        }.toString()
    }

    fun decode(recordJson: String): TaskMailNewTaskSendRecord? {
        return runCatching {
            json.parseToJsonElement(recordJson)
                .jsonObject
                .toTaskMailNewTaskSendRecord()
        }.getOrNull()
    }
}

private fun TaskMailDirectSendEvidence.toJsonObject(): JsonObject {
    return buildJsonObject {
        put("bootstrapStatus", bootstrapStatus.wireValue)
        put("outcome", outcome.name)
        put("switchGate", switchGate.name)
        putNullable("requestId", requestId)
        putNullable("receiptId", receiptId)
        putNullable("transportMessageId", transportMessageId)
        putNullable("fallbackReason", fallbackReason)
        putNullable("errorMessage", errorMessage)
    }
}

private fun JsonObject.toTaskMailNewTaskSendRecord(): TaskMailNewTaskSendRecord {
    return TaskMailNewTaskSendRecord(
        recordedAt = long("recordedAt"),
        senderAccountId = string("senderAccountId"),
        backend = TaskMailBackend.fromWireValue(optionalString("backend")) ?: TaskMailBackend.Codex,
        repoPath = string("repoPath"),
        workdir = optionalString("workdir"),
        evidence = objectValue("evidence")?.toTaskMailDirectSendEvidence() ?: error("Missing evidence"),
    )
}

private fun JsonObject.toTaskMailDirectSendEvidence(): TaskMailDirectSendEvidence {
    return TaskMailDirectSendEvidence(
        bootstrapStatus = RelayBootstrapStatus.entries.firstOrNull { status ->
            status.wireValue == optionalString("bootstrapStatus")
        } ?: RelayBootstrapStatus.ConnectFailure,
        outcome = TaskMailDirectOutcome.entries.firstOrNull { outcome ->
            outcome.name == optionalString("outcome")
        } ?: TaskMailDirectOutcome.MailFallbackFailed,
        switchGate = TaskMailDirectSwitchGate.entries.firstOrNull { switchGate ->
            switchGate.name == optionalString("switchGate")
        } ?: TaskMailDirectSwitchGate.FallbackRequired,
        requestId = optionalString("requestId"),
        receiptId = optionalString("receiptId"),
        transportMessageId = optionalString("transportMessageId"),
        fallbackReason = optionalString("fallbackReason"),
        errorMessage = optionalString("errorMessage"),
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

private fun JsonObject.objectValue(name: String): JsonObject? {
    return this[name] as? JsonObject
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
