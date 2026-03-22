package net.thunderbird.feature.taskmail.internal.data.relay

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayPacket
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayPacketAck
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailDirectNewTaskResult
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailDirectNewTaskSender
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskDraft

private const val PHASE2_SCHEMA_VERSION = "phase2-direct-outbound-contract-v1"
private const val DIRECT_ACTION_NEW_TASK = "new_task"
private const val ORIGIN_CLIENT = "android_taskmail"
private const val PACKET_ID_PREFIX = "android-taskmail:new-task:"
private const val REQUEST_ID_PREFIX = "req_"
private const val DISPATCH_CHANNEL = "taskmail_android_direct"
private const val FALLBACK_POLICY_MAIL = "mail"
private const val DEFAULT_DIRECT_REJECTION_MESSAGE = "Relay rejected direct task request."
private val HARD_REJECTION_CODES = setOf(
    "invalid_payload",
    "validation_failed",
    "unauthorized",
)

internal class RelayTaskMailDirectNewTaskSender(
    private val relayConnectionClient: RelayConnectionClient,
    private val timestampProvider: () -> String = ::currentUtcTimestamp,
    private val requestIdFactory: () -> String = ::nextRequestId,
) : TaskMailDirectNewTaskSender {

    override suspend fun send(draft: TaskMailNewTaskDraft): TaskMailDirectNewTaskResult {
        val requestId = requestIdFactory()
        return relayConnectionClient.sendPacket(buildPacket(draft, requestId)).fold(
            onSuccess = { packetAck ->
                packetAck.toDirectNewTaskResult(requestId)
            },
            onFailure = { error ->
                error.toDirectNewTaskResult()
            },
        )
    }

    private fun buildPacket(
        draft: TaskMailNewTaskDraft,
        requestId: String,
    ): RelayPacket {
        return RelayPacket(
            packetId = "$PACKET_ID_PREFIX$requestId",
            clientTraceId = requestId,
            taskRunPacket = buildTaskRunPacket(
                draft = draft,
                requestId = requestId,
            ),
            dispatchMetadata = buildDispatchMetadata(),
            sentAt = timestampProvider(),
        )
    }

    private fun buildTaskRunPacket(
        draft: TaskMailNewTaskDraft,
        requestId: String,
    ) = buildJsonObject {
        put("schema_version", PHASE2_SCHEMA_VERSION)
        put("action", DIRECT_ACTION_NEW_TASK)
        put("request_id", requestId)
        put(
            "origin",
            buildJsonObject {
                put("client", ORIGIN_CLIENT)
                put("sender_account_uuid", draft.senderAccountId)
            },
        )
        put(
            "new_task",
            buildJsonObject {
                put("backend", draft.backend.wireValue)
                put("repo_path", draft.repoPath)
                draft.workdir?.takeIf(String::isNotBlank)?.let { put("workdir", it) }
                put("task_text", draft.taskText)
                put("subject_title", draft.subjectTitle)
                draft.timeoutMinutes?.let { put("timeout_minutes", it) }
                put("mode", draft.mode.wireValue)
                draft.profile?.takeIf(String::isNotBlank)?.let { put("profile", it) }
                draft.permission.wireValue?.let { put("permission", it) }
                put("acceptance", draft.acceptanceCriteria.toJsonArray())
            },
        )
    }

    private fun buildDispatchMetadata() = buildJsonObject {
        put("channel", DISPATCH_CHANNEL)
        put("schema_version", PHASE2_SCHEMA_VERSION)
        put("action", DIRECT_ACTION_NEW_TASK)
        put("fallback_policy", FALLBACK_POLICY_MAIL)
    }

    private companion object {
        fun currentUtcTimestamp(): String {
            return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date())
        }

        fun nextRequestId(): String {
            return REQUEST_ID_PREFIX + UUID.randomUUID()
                .toString()
                .replace("-", "")
        }
    }
}

private fun Throwable.toDirectNewTaskResult(): TaskMailDirectNewTaskResult {
    return when (this) {
        is RelayServerException -> {
            if (code in HARD_REJECTION_CODES) {
                TaskMailDirectNewTaskResult.Rejected(message)
            } else {
                TaskMailDirectNewTaskResult.FallbackToMail(message)
            }
        }

        else -> TaskMailDirectNewTaskResult.FallbackToMail(message)
    }
}

private fun RelayPacketAck.toDirectNewTaskResult(requestId: String): TaskMailDirectNewTaskResult {
    return if (accepted) {
        TaskMailDirectNewTaskResult.Accepted(
            requestId = requestId,
            receiptId = receiptId,
            transportMessageId = transportMessageId,
        )
    } else {
        val relayErrorCode = errorCode.normalizedRelayErrorCode() ?: errorMessage.extractRelayErrorCode()
        if (relayErrorCode in HARD_REJECTION_CODES) {
            TaskMailDirectNewTaskResult.Rejected(
                errorMessage = errorMessage?.takeIf(String::isNotBlank) ?: DEFAULT_DIRECT_REJECTION_MESSAGE,
            )
        } else {
            TaskMailDirectNewTaskResult.FallbackToMail(errorMessage)
        }
    }
}

private fun List<String>.toJsonArray(): JsonArray {
    return buildJsonArray {
        this@toJsonArray
            .map(String::trim)
            .filter(String::isNotEmpty)
            .forEach { add(JsonPrimitive(it)) }
    }
}

private fun String?.normalizedRelayErrorCode(): String? {
    return this?.trim()
        ?.lowercase(Locale.US)
        ?.takeIf(String::isNotEmpty)
}

private fun String?.extractRelayErrorCode(): String? {
    val message = this?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: return null

    return message
        .substringBefore(':')
        .trim()
        .lowercase(Locale.US)
        .takeIf { it in HARD_REJECTION_CODES }
}
