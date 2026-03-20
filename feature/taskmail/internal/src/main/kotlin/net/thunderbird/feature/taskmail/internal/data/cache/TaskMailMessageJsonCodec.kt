@file:Suppress("TooManyFunctions")

package net.thunderbird.feature.taskmail.internal.data.cache

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import net.thunderbird.feature.taskmail.internal.data.TaskMailMessage
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailBackend
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSessionLifecycle
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSessionStatus
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailStatusLabel
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMessageAttachment
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskMailDetection
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskMailParsedSubject
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskQuestionCapsule
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskStateCapsule

internal class TaskMailMessageJsonCodec(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun encode(message: TaskMailMessage): String {
        return message.toJsonObject().toString()
    }

    fun decode(messageJson: String): TaskMailMessage? {
        return runCatching {
            json.parseToJsonElement(messageJson)
                .jsonObject
                .toTaskMailMessage()
        }.getOrNull()
    }
}

private fun TaskMailMessage.toJsonObject(): JsonObject {
    return buildJsonObject {
        put("accountUuid", accountUuid)
        put("folderId", folderId)
        put("messageServerId", messageServerId)
        put("threadRootId", threadRootId)
        put("timestamp", timestamp)
        put("subject", subject)
        put("rawBodyText", rawBodyText)
        putNullable("htmlBody", htmlBody)
        putNullable("internetMessageId", internetMessageId)
        put(
            "attachments",
            JsonArray(attachments.map(TaskMessageAttachment::toJsonObject)),
        )
        put("detection", detection.toJsonObject())
        put("isFromCurrentUser", isFromCurrentUser)
    }
}

private fun TaskMailDetection.toJsonObject(): JsonObject {
    return buildJsonObject {
        put("isTaskMail", isTaskMail)
        put("isSystemMessage", isSystemMessage)
        put("parsedSubject", parsedSubject.toJsonObject())
        stateCapsule?.let { capsule ->
            put("stateCapsule", capsule.toJsonObject())
        }
        put(
            "questionCapsules",
            JsonArray(questionCapsules.map(TaskQuestionCapsule::toJsonObject)),
        )
    }
}

private fun TaskMailParsedSubject.toJsonObject(): JsonObject {
    return buildJsonObject {
        putNullable("backend", backend?.wireValue)
        putNullable("statusLabel", statusLabel?.subjectToken)
        putNullable("sessionIdFromSubject", sessionIdFromSubject)
        put("subjectText", subjectText)
        put("isReplyLike", isReplyLike)
    }
}

private fun TaskStateCapsule.toJsonObject(): JsonObject {
    return buildJsonObject {
        put("threadId", threadId)
        putNullable("workspaceId", workspaceId)
        putNullable("sessionId", sessionId)
        putNullable("sessionName", sessionName)
        putNullable("taskId", taskId)
        putNullable("backend", backend?.wireValue)
        putNullable("repoPath", repoPath)
        putNullable("workdir", workdir)
        putNullable("mode", mode)
        putNullable("status", status?.name)
        putNullable("lifecycle", lifecycle?.name)
        putNullable("pausedFromStatus", pausedFromStatus?.name)
        putNullable("lastActiveAt", lastActiveAt)
        putNullable("lastProgressAt", lastProgressAt)
        putNullable("lastSummary", lastSummary)
    }
}

private fun TaskQuestionCapsule.toJsonObject(): JsonObject {
    return buildJsonObject {
        put("questionId", questionId)
        put("questionText", questionText)
        put(
            "choices",
            buildJsonArray {
                choices.forEach { choice ->
                    add(JsonPrimitive(choice))
                }
            },
        )
        putNullable("questionSetId", questionSetId)
        putNullable("questionType", questionType)
        put("required", required)
        put(
            "choiceLabels",
            buildJsonObject {
                choiceLabels.forEach { (key, value) ->
                    put(key, JsonPrimitive(value))
                }
            },
        )
    }
}

private fun TaskMessageAttachment.toJsonObject(): JsonObject {
    return buildJsonObject {
        put("id", id)
        put("displayName", displayName)
        putNullable("contentType", contentType)
        putNullable("sizeBytes", sizeBytes)
        put("isInline", isInline)
        put("isImage", isImage)
        putNullable("contentId", contentId)
        putNullable("internalUriString", internalUriString)
        putNullable("accountUuid", accountUuid)
        putNullable("folderId", folderId)
        putNullable("messageServerId", messageServerId)
        putNullable("partId", partId)
        put("isContentAvailable", isContentAvailable)
    }
}

private fun JsonObject.toTaskMailMessage(): TaskMailMessage {
    return TaskMailMessage(
        accountUuid = string("accountUuid"),
        folderId = long("folderId"),
        messageServerId = string("messageServerId"),
        threadRootId = long("threadRootId"),
        timestamp = long("timestamp"),
        subject = string("subject"),
        rawBodyText = string("rawBodyText"),
        htmlBody = optionalString("htmlBody"),
        internetMessageId = optionalString("internetMessageId"),
        attachments = arrayObjects("attachments").map(JsonObject::toTaskMessageAttachment),
        detection = objectValue("detection")?.toTaskMailDetection() ?: error("Missing detection"),
        isFromCurrentUser = optionalBoolean("isFromCurrentUser") ?: false,
    )
}

private fun JsonObject.toTaskMailDetection(): TaskMailDetection {
    val questionCapsules = arrayObjects("questionCapsules").map(JsonObject::toTaskQuestionCapsule)
    return TaskMailDetection(
        isTaskMail = optionalBoolean("isTaskMail") ?: false,
        isSystemMessage = optionalBoolean("isSystemMessage") ?: false,
        parsedSubject = objectValue("parsedSubject")?.toTaskMailParsedSubject() ?: error("Missing parsedSubject"),
        stateCapsule = objectValue("stateCapsule")?.toTaskStateCapsule(),
        questionCapsule = questionCapsules.lastOrNull(),
        questionCapsules = questionCapsules,
    )
}

private fun JsonObject.toTaskMailParsedSubject(): TaskMailParsedSubject {
    return TaskMailParsedSubject(
        backend = TaskMailBackend.fromWireValue(optionalString("backend")),
        statusLabel = TaskMailStatusLabel.fromSubjectToken(optionalString("statusLabel").orEmpty()),
        sessionIdFromSubject = optionalString("sessionIdFromSubject"),
        subjectText = string("subjectText"),
        isReplyLike = optionalBoolean("isReplyLike") ?: false,
    )
}

private fun JsonObject.toTaskStateCapsule(): TaskStateCapsule {
    return TaskStateCapsule(
        threadId = string("threadId"),
        workspaceId = optionalString("workspaceId"),
        sessionId = optionalString("sessionId"),
        sessionName = optionalString("sessionName"),
        taskId = optionalString("taskId"),
        backend = TaskMailBackend.fromWireValue(optionalString("backend")),
        repoPath = optionalString("repoPath"),
        workdir = optionalString("workdir"),
        mode = optionalString("mode"),
        status = TaskMailSessionStatus.entries.firstOrNull { status ->
            status.name == optionalString("status")
        },
        lifecycle = TaskMailSessionLifecycle.entries.firstOrNull { lifecycle ->
            lifecycle.name == optionalString("lifecycle")
        },
        pausedFromStatus = TaskMailSessionStatus.entries.firstOrNull { status ->
            status.name == optionalString("pausedFromStatus")
        },
        lastActiveAt = optionalString("lastActiveAt"),
        lastProgressAt = optionalString("lastProgressAt"),
        lastSummary = optionalString("lastSummary"),
    )
}

private fun JsonObject.toTaskQuestionCapsule(): TaskQuestionCapsule {
    return TaskQuestionCapsule(
        questionId = string("questionId"),
        questionText = string("questionText"),
        choices = array("choices"),
        questionSetId = optionalString("questionSetId"),
        questionType = optionalString("questionType"),
        required = optionalBoolean("required") ?: true,
        choiceLabels = objectValue("choiceLabels")
            ?.entries
            ?.mapNotNull { (key, value) ->
                value.jsonPrimitive.contentOrNull?.let { label -> key to label }
            }
            ?.toMap()
            .orEmpty(),
    )
}

private fun JsonObject.toTaskMessageAttachment(): TaskMessageAttachment {
    return TaskMessageAttachment(
        id = string("id"),
        displayName = string("displayName"),
        contentType = optionalString("contentType"),
        sizeBytes = optionalLong("sizeBytes"),
        isInline = optionalBoolean("isInline") ?: false,
        isImage = optionalBoolean("isImage") ?: false,
        contentId = optionalString("contentId"),
        internalUriString = optionalString("internalUriString"),
        accountUuid = optionalString("accountUuid"),
        folderId = optionalLong("folderId"),
        messageServerId = optionalString("messageServerId"),
        partId = optionalLong("partId"),
        isContentAvailable = optionalBoolean("isContentAvailable") ?: false,
    )
}

private fun JsonObject.string(name: String): String {
    return this[name]?.jsonPrimitive?.contentOrNull.orEmpty()
}

private fun JsonObject.optionalString(name: String): String? {
    return this[name]?.jsonPrimitive?.contentOrNull
}

private fun JsonObject.long(name: String): Long {
    return optionalLong(name) ?: 0L
}

private fun JsonObject.optionalLong(name: String): Long? {
    return this[name]?.jsonPrimitive?.longOrNull
}

private fun JsonObject.optionalBoolean(name: String): Boolean? {
    return this[name]?.jsonPrimitive?.booleanOrNull
}

private fun JsonObject.array(name: String): List<String> {
    return this[name]
        ?.jsonArray
        ?.mapNotNull { element -> element.jsonPrimitive.contentOrNull }
        .orEmpty()
}

private fun JsonObject.arrayObjects(name: String): List<JsonObject> {
    return this[name]
        ?.jsonArray
        ?.mapNotNull { element -> element as? JsonObject }
        .orEmpty()
}

private fun JsonObject.objectValue(name: String): JsonObject? {
    return this[name] as? JsonObject
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

private fun JsonObjectBuilder.put(name: String, value: Long) {
    put(name, JsonPrimitive(value))
}

private fun JsonObjectBuilder.put(name: String, value: Boolean) {
    put(name, JsonPrimitive(value))
}
