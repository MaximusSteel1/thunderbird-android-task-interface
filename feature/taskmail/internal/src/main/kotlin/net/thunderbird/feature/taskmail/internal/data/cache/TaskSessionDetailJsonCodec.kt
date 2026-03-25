@file:Suppress("TooManyFunctions")

package net.thunderbird.feature.taskmail.internal.data.cache

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import net.thunderbird.feature.taskmail.internal.data.controlplane.protocol.ControlPlaneArtifactManifest
import net.thunderbird.feature.taskmail.internal.data.controlplane.protocol.ControlPlaneCommandAck
import net.thunderbird.feature.taskmail.internal.data.controlplane.protocol.ControlPlaneEvent
import net.thunderbird.feature.taskmail.internal.data.controlplane.protocol.ControlPlaneResult
import net.thunderbird.feature.taskmail.internal.domain.model.TaskBodyRenderMode
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailBackend
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSessionLifecycle
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSessionStatus
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailStatusLabel
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMessageAttachment
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMessageBody
import net.thunderbird.feature.taskmail.internal.domain.model.TaskRichTextBlock
import net.thunderbird.feature.taskmail.internal.domain.model.TaskRichTextDocument
import net.thunderbird.feature.taskmail.internal.domain.model.TaskRichTextInline
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionControlPlaneSnapshot
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionDetail
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionKey
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionReplyContext
import net.thunderbird.feature.taskmail.internal.domain.model.TaskTimelineDirection
import net.thunderbird.feature.taskmail.internal.domain.model.TaskTimelineItem
import net.thunderbird.feature.taskmail.internal.domain.model.TaskWorkspaceKey
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskQuestionCapsule

internal class TaskSessionDetailJsonCodec(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun encode(detail: TaskSessionDetail): String {
        return detail.toJsonObject(json).toString()
    }

    fun decode(detailJson: String): TaskSessionDetail? {
        return runCatching {
            json.parseToJsonElement(detailJson)
                .jsonObject
                .toTaskSessionDetail(json)
        }.getOrNull()
    }
}

private fun TaskSessionDetail.toJsonObject(json: Json): JsonObject {
    return buildJsonObject {
        put("key", key.toJsonObject())
        put("workspace", workspace.toJsonObject())
        put("sessionName", sessionName)
        putNullable("backend", backend.wireValue)
        putNullable("status", status.name)
        putNullable("lifecycle", lifecycle?.name)
        put("repoPath", repoPath)
        putNullable("workdir", workdir)
        putNullable("lastSummary", lastSummary)
        putNullable("pausedFromStatus", pausedFromStatus?.name)
        putNullable("lastActiveAt", lastActiveAt)
        putNullable("lastProgressAt", lastProgressAt)
        put(
            "pendingQuestions",
            JsonArray(pendingQuestions.map(TaskQuestionCapsule::toJsonObject)),
        )
        replyContext?.let { context ->
            put("replyContext", context.toJsonObject())
        }
        put(
            "timeline",
            JsonArray(timeline.map(TaskTimelineItem::toJsonObject)),
        )
        controlPlaneSnapshot?.let { snapshot ->
            put("controlPlaneSnapshot", snapshot.toJsonObject(json))
        }
    }
}

private fun TaskSessionControlPlaneSnapshot.toJsonObject(json: Json): JsonObject {
    return buildJsonObject {
        commandAck?.let { ack ->
            put(
                "commandAck",
                json.encodeToJsonElement(ControlPlaneCommandAck.serializer(), ack),
            )
        }
        if (events.isNotEmpty()) {
            put(
                "events",
                JsonArray(
                    events.map { event ->
                        json.encodeToJsonElement(ControlPlaneEvent.serializer(), event)
                    },
                ),
            )
        }
        result?.let { terminalResult ->
            put(
                "result",
                json.encodeToJsonElement(ControlPlaneResult.serializer(), terminalResult),
            )
        }
        artifactManifest?.let { manifest ->
            put(
                "artifactManifest",
                json.encodeToJsonElement(ControlPlaneArtifactManifest.serializer(), manifest),
            )
        }
    }
}

private fun TaskSessionKey.toJsonObject(): JsonObject {
    return buildJsonObject {
        putNullable("workspaceId", workspaceId)
        putNullable("sessionId", sessionId)
        putNullable("threadId", threadId)
    }
}

private fun TaskWorkspaceKey.toJsonObject(): JsonObject {
    return buildJsonObject {
        putNullable("workspaceId", workspaceId)
        put("repoPath", repoPath)
        putNullable("workdir", workdir)
    }
}

private fun TaskQuestionCapsule.toJsonObject(): JsonObject {
    return buildJsonObject {
        put("questionId", questionId)
        put("questionText", questionText)
        put(
            "choices",
            buildJsonArray {
                choices.forEach { choice -> add(JsonPrimitive(choice)) }
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

private fun TaskSessionReplyContext.toJsonObject(): JsonObject {
    return buildJsonObject {
        put("accountUuid", accountUuid)
        put("folderId", folderId)
        put("messageServerId", messageServerId)
        putNullable("threadRootId", threadRootId)
        putNullable("anchorTimestamp", anchorTimestamp)
    }
}

private fun TaskTimelineItem.toJsonObject(): JsonObject {
    return buildJsonObject {
        put("id", id)
        put("timestamp", timestamp)
        put("direction", direction.name)
        putNullable("statusLabel", statusLabel?.name)
        putNullable("summary", summary)
        put("body", body.toJsonObject())
        put(
            "attachments",
            JsonArray(attachments.map(TaskMessageAttachment::toJsonObject)),
        )
        put(
            "businessEventKeys",
            buildJsonArray {
                businessEventKeys.forEach { key -> add(JsonPrimitive(key)) }
            },
        )
    }
}

private fun TaskMessageBody.toJsonObject(): JsonObject {
    return buildJsonObject {
        put("plainTextFallback", plainTextFallback)
        put("renderMode", renderMode.name)
        putNullable("sourceHtml", sourceHtml)
        richDocument?.let { document ->
            put("richDocument", document.toJsonObject())
        }
    }
}

private fun TaskRichTextDocument.toJsonObject(): JsonObject {
    return buildJsonObject {
        put(
            "blocks",
            JsonArray(blocks.map(TaskRichTextBlock::toJsonObject)),
        )
    }
}

private fun TaskRichTextBlock.toJsonObject(): JsonObject {
    return when (this) {
        is TaskRichTextBlock.Paragraph -> buildJsonObject {
            put("type", "paragraph")
            put("inlines", JsonArray(inlines.map(TaskRichTextInline::toJsonObject)))
        }

        is TaskRichTextBlock.Heading -> buildJsonObject {
            put("type", "heading")
            put("level", level)
            put("inlines", JsonArray(inlines.map(TaskRichTextInline::toJsonObject)))
        }

        is TaskRichTextBlock.Quote -> buildJsonObject {
            put("type", "quote")
            put("blocks", JsonArray(blocks.map(TaskRichTextBlock::toJsonObject)))
        }

        is TaskRichTextBlock.CodeBlock -> buildJsonObject {
            put("type", "code_block")
            putNullable("languageHint", languageHint)
            put("text", text)
        }

        is TaskRichTextBlock.BulletList -> buildJsonObject {
            put("type", "bullet_list")
            put("items", items.toJsonArray())
        }

        is TaskRichTextBlock.OrderedList -> buildJsonObject {
            put("type", "ordered_list")
            put("items", items.toJsonArray())
        }

        is TaskRichTextBlock.Table -> buildJsonObject {
            put("type", "table")
            put("headers", headers.toInlineRowsJsonArray())
            put("rows", rows.toTableRowsJsonArray())
        }

        is TaskRichTextBlock.InlineImage -> buildJsonObject {
            put("type", "inline_image")
            putNullable("attachmentId", attachmentId)
            putNullable("contentId", contentId)
            putNullable("altText", altText)
            putNullable("caption", caption)
            putNullable("mimeType", mimeType)
            put("isSvg", isSvg)
        }

        TaskRichTextBlock.Divider -> buildJsonObject {
            put("type", "divider")
        }

        is TaskRichTextBlock.UnsupportedHtml -> buildJsonObject {
            put("type", "unsupported_html")
            put("fallbackText", fallbackText)
        }
    }
}

private fun TaskRichTextInline.toJsonObject(): JsonObject {
    return when (this) {
        is TaskRichTextInline.Text -> buildJsonObject {
            put("type", "text")
            put("text", text)
        }

        is TaskRichTextInline.Strong -> buildJsonObject {
            put("type", "strong")
            put("text", text)
        }

        is TaskRichTextInline.Emphasis -> buildJsonObject {
            put("type", "emphasis")
            put("text", text)
        }

        is TaskRichTextInline.Code -> buildJsonObject {
            put("type", "code")
            put("text", text)
        }

        is TaskRichTextInline.Link -> buildJsonObject {
            put("type", "link")
            put("text", text)
            put("href", href)
        }
    }
}

private fun List<List<TaskRichTextBlock>>.toJsonArray(): JsonArray {
    return buildJsonArray {
        forEach { blocks ->
            add(JsonArray(blocks.map(TaskRichTextBlock::toJsonObject)))
        }
    }
}

private fun List<List<TaskRichTextInline>>.toInlineRowsJsonArray(): JsonArray {
    return buildJsonArray {
        forEach { row ->
            add(JsonArray(row.map(TaskRichTextInline::toJsonObject)))
        }
    }
}

private fun List<List<List<TaskRichTextInline>>>.toTableRowsJsonArray(): JsonArray {
    return buildJsonArray {
        forEach { row ->
            add(
                JsonArray(
                    row.map { cell ->
                        JsonArray(cell.map(TaskRichTextInline::toJsonObject))
                    },
                ),
            )
        }
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

private fun JsonObject.toTaskSessionDetail(json: Json): TaskSessionDetail {
    val pendingQuestions = arrayObjects("pendingQuestions").map(JsonObject::toTaskQuestionCapsule)

    return TaskSessionDetail(
        key = objectValue("key")?.toTaskSessionKey() ?: error("Missing key"),
        workspace = objectValue("workspace")?.toTaskWorkspaceKey() ?: error("Missing workspace"),
        sessionName = string("sessionName"),
        backend = TaskMailBackend.fromWireValue(optionalString("backend")) ?: TaskMailBackend.Codex,
        status = TaskMailSessionStatus.entries.firstOrNull { status ->
            status.name == optionalString("status")
        } ?: TaskMailSessionStatus.Unknown,
        lifecycle = TaskMailSessionLifecycle.fromWireValue(optionalString("lifecycle")),
        repoPath = string("repoPath"),
        workdir = optionalString("workdir"),
        lastSummary = optionalString("lastSummary"),
        pausedFromStatus = TaskMailSessionStatus.entries.firstOrNull { status ->
            status.name == optionalString("pausedFromStatus")
        },
        lastActiveAt = optionalString("lastActiveAt"),
        lastProgressAt = optionalString("lastProgressAt"),
        question = pendingQuestions.lastOrNull(),
        pendingQuestions = pendingQuestions,
        replyContext = objectValue("replyContext")?.toTaskSessionReplyContext(),
        timeline = arrayObjects("timeline").map(JsonObject::toTaskTimelineItem),
        controlPlaneSnapshot = objectValue("controlPlaneSnapshot")?.toTaskSessionControlPlaneSnapshot(json),
    )
}

private fun JsonObject.toTaskSessionControlPlaneSnapshot(json: Json): TaskSessionControlPlaneSnapshot {
    return TaskSessionControlPlaneSnapshot(
        commandAck = objectValue("commandAck")?.let { ackObject ->
            json.decodeFromJsonElement(ControlPlaneCommandAck.serializer(), ackObject)
        },
        events = arrayObjects("events").map { eventObject ->
            json.decodeFromJsonElement(ControlPlaneEvent.serializer(), eventObject)
        },
        result = objectValue("result")?.let { resultObject ->
            json.decodeFromJsonElement(ControlPlaneResult.serializer(), resultObject)
        },
        artifactManifest = objectValue("artifactManifest")?.let { manifestObject ->
            json.decodeFromJsonElement(ControlPlaneArtifactManifest.serializer(), manifestObject)
        },
    )
}

private fun JsonObject.toTaskSessionKey(): TaskSessionKey {
    return TaskSessionKey(
        workspaceId = optionalString("workspaceId"),
        sessionId = optionalString("sessionId"),
        threadId = optionalString("threadId"),
    )
}

private fun JsonObject.toTaskWorkspaceKey(): TaskWorkspaceKey {
    return TaskWorkspaceKey(
        workspaceId = optionalString("workspaceId"),
        repoPath = string("repoPath"),
        workdir = optionalString("workdir"),
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

private fun JsonObject.toTaskSessionReplyContext(): TaskSessionReplyContext {
    return TaskSessionReplyContext(
        accountUuid = string("accountUuid"),
        folderId = long("folderId"),
        messageServerId = string("messageServerId"),
        threadRootId = optionalLong("threadRootId"),
        anchorTimestamp = optionalLong("anchorTimestamp"),
    )
}

private fun JsonObject.toTaskTimelineItem(): TaskTimelineItem {
    return TaskTimelineItem(
        id = string("id"),
        timestamp = long("timestamp"),
        direction = TaskTimelineDirection.entries.firstOrNull { direction ->
            direction.name == optionalString("direction")
        } ?: TaskTimelineDirection.System,
        statusLabel = TaskMailStatusLabel.entries.firstOrNull { label ->
            label.name == optionalString("statusLabel")
        },
        summary = optionalString("summary"),
        body = objectValue("body")?.toTaskMessageBody() ?: error("Missing body"),
        attachments = arrayObjects("attachments").map(JsonObject::toTaskMessageAttachment),
        businessEventKeys = array("businessEventKeys"),
    )
}

private fun JsonObject.toTaskMessageBody(): TaskMessageBody {
    return TaskMessageBody(
        plainTextFallback = string("plainTextFallback"),
        renderMode = TaskBodyRenderMode.entries.firstOrNull { renderMode ->
            renderMode.name == optionalString("renderMode")
        } ?: TaskBodyRenderMode.PlainTextOnly,
        richDocument = objectValue("richDocument")?.toTaskRichTextDocument(),
        sourceHtml = optionalString("sourceHtml"),
    )
}

private fun JsonObject.toTaskRichTextDocument(): TaskRichTextDocument {
    return TaskRichTextDocument(
        blocks = arrayObjects("blocks").map(JsonObject::toTaskRichTextBlock),
    )
}

private fun JsonObject.toTaskRichTextBlock(): TaskRichTextBlock {
    return when (optionalString("type")) {
        "paragraph" -> TaskRichTextBlock.Paragraph(
            inlines = arrayObjects("inlines").map(JsonObject::toTaskRichTextInline),
        )

        "heading" -> TaskRichTextBlock.Heading(
            level = optionalInt("level") ?: 1,
            inlines = arrayObjects("inlines").map(JsonObject::toTaskRichTextInline),
        )

        "quote" -> TaskRichTextBlock.Quote(
            blocks = arrayObjects("blocks").map(JsonObject::toTaskRichTextBlock),
        )

        "code_block" -> TaskRichTextBlock.CodeBlock(
            languageHint = optionalString("languageHint"),
            text = string("text"),
        )

        "bullet_list" -> TaskRichTextBlock.BulletList(
            items = arrayArraysOfObjects("items").map { blocks ->
                blocks.map(JsonObject::toTaskRichTextBlock)
            },
        )

        "ordered_list" -> TaskRichTextBlock.OrderedList(
            items = arrayArraysOfObjects("items").map { blocks ->
                blocks.map(JsonObject::toTaskRichTextBlock)
            },
        )

        "table" -> TaskRichTextBlock.Table(
            headers = arrayArraysOfObjects("headers").map { inlines ->
                inlines.map(JsonObject::toTaskRichTextInline)
            },
            rows = arrayNestedInlineRows("rows"),
        )

        "inline_image" -> TaskRichTextBlock.InlineImage(
            attachmentId = optionalString("attachmentId"),
            contentId = optionalString("contentId"),
            altText = optionalString("altText"),
            caption = optionalString("caption"),
            mimeType = optionalString("mimeType"),
            isSvg = optionalBoolean("isSvg") ?: false,
        )

        "divider" -> TaskRichTextBlock.Divider

        else -> TaskRichTextBlock.UnsupportedHtml(
            fallbackText = optionalString("fallbackText").orEmpty(),
        )
    }
}

private fun JsonObject.toTaskRichTextInline(): TaskRichTextInline {
    return when (optionalString("type")) {
        "strong" -> TaskRichTextInline.Strong(text = string("text"))
        "emphasis" -> TaskRichTextInline.Emphasis(text = string("text"))
        "code" -> TaskRichTextInline.Code(text = string("text"))
        "link" -> TaskRichTextInline.Link(
            text = string("text"),
            href = string("href"),
        )

        else -> TaskRichTextInline.Text(text = string("text"))
    }
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

private fun JsonObject.optionalInt(name: String): Int? {
    return this[name]?.jsonPrimitive?.intOrNull
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

private fun JsonObject.arrayArraysOfObjects(name: String): List<List<JsonObject>> {
    return this[name]
        ?.jsonArray
        ?.mapNotNull { element ->
            (element as? JsonArray)?.mapNotNull { child -> child as? JsonObject }
        }
        .orEmpty()
}

private fun JsonObject.arrayNestedInlineRows(name: String): List<List<List<TaskRichTextInline>>> {
    return this[name]
        ?.jsonArray
        ?.mapNotNull { rowElement ->
            (rowElement as? JsonArray)?.mapNotNull { cellElement ->
                (cellElement as? JsonArray)?.mapNotNull { inlineElement ->
                    (inlineElement as? JsonObject)?.toTaskRichTextInline()
                }
            }
        }
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

private fun JsonObjectBuilder.put(name: String, value: Int) {
    put(name, JsonPrimitive(value))
}

private fun JsonObjectBuilder.put(name: String, value: Boolean) {
    put(name, JsonPrimitive(value))
}
