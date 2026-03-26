package net.thunderbird.feature.taskmail.internal.data.controlplane.protocol

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskDraft
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskPermission

private const val NEW_TASK_COMMAND_TYPE = "new_task"
private const val DEFAULT_NEW_TASK_SOURCE = "android"

internal fun TaskMailNewTaskDraft.toControlPlaneNewTaskCommand(
    commandId: String,
    issuedAt: String,
    issuerId: String? = null,
    source: String = DEFAULT_NEW_TASK_SOURCE,
): ControlPlaneCommand {
    return ControlPlaneCommand(
        commandId = requireNormalizedText(commandId, "commandId"),
        commandType = NEW_TASK_COMMAND_TYPE,
        pcId = requireNormalizedText(pcId, "pcId"),
        workspaceId = requireNormalizedText(workspaceId, "workspaceId"),
        executionPolicy = toCanonicalExecutionPolicy(),
        payload = buildJsonObject {
            repoPath.trim()
                .takeIf(String::isNotEmpty)
                ?.let { put("repo_path", it) }
            workdir?.trim()
                ?.takeIf(String::isNotEmpty)
                ?.let { put("workdir", it) }
            put("task_text", requireNormalizedText(taskText, "taskText"))
            if (mode != net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskMode.Modify) {
                put("mode", mode.wireValue)
            }
            timeoutMinutes?.let { put("timeout_seconds", it.toLong() * 60L) }
            val normalizedAcceptance = acceptanceCriteria.toCanonicalAcceptanceCriteria()
            if (normalizedAcceptance.isNotEmpty()) {
                put(
                    "acceptance",
                    buildJsonArray {
                        normalizedAcceptance.forEach { add(JsonPrimitive(it)) }
                    },
                )
            }
            source.trim()
                .takeIf(String::isNotEmpty)
                ?.let { put("source", it) }
        },
        issuedAt = requireNormalizedText(issuedAt, "issuedAt"),
        issuerId = issuerId?.trim()?.takeIf(String::isNotEmpty),
    )
}

internal fun TaskMailNewTaskDraft.toControlPlaneNewTaskDispatchMessage(
    messageId: String,
    traceId: String,
    commandId: String,
    connectionEpoch: Long,
    sentAt: String,
    issuedAt: String = sentAt,
    issuerId: String? = null,
    source: String = DEFAULT_NEW_TASK_SOURCE,
): ControlPlaneCommandDispatchMessage {
    require(connectionEpoch >= 0L) {
        "connectionEpoch must be >= 0"
    }

    val command = toControlPlaneNewTaskCommand(
        commandId = commandId,
        issuedAt = issuedAt,
        issuerId = issuerId,
        source = source,
    )

    return ControlPlaneCommandDispatchMessage(
        messageId = requireNormalizedText(messageId, "messageId"),
        traceId = requireNormalizedText(traceId, "traceId"),
        pcId = requireNormalizedText(command.pcId, "pcId"),
        connectionEpoch = connectionEpoch,
        sentAt = requireNormalizedText(sentAt, "sentAt"),
        payload = command,
    )
}

internal fun TaskMailNewTaskDraft.toCanonicalExecutionPolicy(): ControlPlaneExecutionPolicy {
    val requestedPolicy = executionPolicy
    return ControlPlaneExecutionPolicy(
        backend = requestedPolicy?.backend?.trim()?.takeIf(String::isNotEmpty) ?: backend.wireValue,
        profile = requestedPolicy?.profile?.trim()?.takeIf(String::isNotEmpty)
            ?: profile?.trim()?.takeIf(String::isNotEmpty),
        permission = requestedPolicy?.permission?.trim()?.takeIf(String::isNotEmpty)
            ?: permission.toControlPlanePermission(),
        backendTransport = requestedPolicy?.backendTransport?.trim()?.takeIf(String::isNotEmpty),
        resolvedModel = requestedPolicy?.resolvedModel?.trim()?.takeIf(String::isNotEmpty),
    )
}

private fun TaskMailNewTaskPermission.toControlPlanePermission(): String? {
    return when (this) {
        TaskMailNewTaskPermission.Default -> "default"
        TaskMailNewTaskPermission.Highest -> "highest"
    }
}

internal fun List<String>.toCanonicalAcceptanceCriteria(): List<String> {
    return map { item ->
        item.trim()
            .removePrefix("- ")
            .trim()
    }.filter(String::isNotEmpty)
}

private fun requireNormalizedText(value: String?, fieldName: String): String {
    return value?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: error("$fieldName is required.")
}
