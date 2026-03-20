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
import net.thunderbird.feature.taskmail.internal.data.ingress.EmailIngressMessage
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMessageAttachment

internal class EmailIngressPayloadJsonCodec(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun encode(message: EmailIngressMessage): String {
        return message.toJsonObject().toString()
    }

    fun decode(payloadJson: String): EmailIngressMessage? {
        return runCatching {
            json.parseToJsonElement(payloadJson)
                .jsonObject
                .toEmailIngressMessage()
        }.getOrNull()
    }
}

private fun EmailIngressMessage.toJsonObject(): JsonObject {
    return buildJsonObject {
        put("accountUuid", accountUuid)
        put("accountEmailAddress", accountEmailAddress)
        put("folderId", folderId)
        put("messageServerId", messageServerId)
        put("threadRootId", threadRootId)
        put("timestamp", timestamp)
        put("subject", subject)
        put("rawBodyText", rawBodyText)
        putNullable("htmlBody", htmlBody)
        putNullable("internetMessageId", internetMessageId)
        put(
            "fromAddresses",
            buildJsonArray {
                fromAddresses.forEach { address ->
                    add(JsonPrimitive(address))
                }
            },
        )
        put(
            "attachments",
            JsonArray(attachments.map(TaskMessageAttachment::toJsonObject)),
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

private fun JsonObject.toEmailIngressMessage(): EmailIngressMessage {
    return EmailIngressMessage(
        accountUuid = string("accountUuid"),
        accountEmailAddress = string("accountEmailAddress"),
        folderId = long("folderId"),
        messageServerId = string("messageServerId"),
        threadRootId = long("threadRootId"),
        timestamp = long("timestamp"),
        subject = string("subject"),
        fromAddresses = array("fromAddresses"),
        rawBodyText = string("rawBodyText"),
        htmlBody = optionalString("htmlBody"),
        internetMessageId = optionalString("internetMessageId"),
        attachments = arrayObjects("attachments").map(JsonObject::toTaskMessageAttachment),
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
