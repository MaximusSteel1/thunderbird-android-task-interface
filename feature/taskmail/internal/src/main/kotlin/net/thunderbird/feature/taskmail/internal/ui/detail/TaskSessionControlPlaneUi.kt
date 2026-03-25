package net.thunderbird.feature.taskmail.internal.ui.detail

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionControlPlaneSnapshot
import net.thunderbird.feature.taskmail.internal.data.controlplane.protocol.ControlPlaneArtifact
import net.thunderbird.feature.taskmail.internal.data.controlplane.protocol.ControlPlaneArtifactManifest
import net.thunderbird.feature.taskmail.internal.data.controlplane.protocol.ControlPlaneCommandAck
import net.thunderbird.feature.taskmail.internal.data.controlplane.protocol.ControlPlaneEvent
import net.thunderbird.feature.taskmail.internal.data.controlplane.protocol.ControlPlaneExecutionPolicy
import net.thunderbird.feature.taskmail.internal.data.controlplane.protocol.ControlPlaneResult

internal data class TaskSessionControlPlaneOverlay(
    val recentContext: TaskRecentContextUi? = null,
    val resultSummary: TaskResultSummaryUi? = null,
    val artifacts: ImmutableList<TaskSessionArtifactUi> = persistentListOf(),
)

internal fun buildTaskSessionControlPlaneOverlay(
    commandAck: ControlPlaneCommandAck? = null,
    events: List<ControlPlaneEvent> = emptyList(),
    result: ControlPlaneResult? = null,
    artifactManifest: ControlPlaneArtifactManifest? = null,
): TaskSessionControlPlaneOverlay {
    val artifacts = artifactManifest?.artifacts
        .orEmpty()
        .map(ControlPlaneArtifact::toTaskSessionArtifactUi)
        .toImmutableList()

    return TaskSessionControlPlaneOverlay(
        recentContext = buildControlPlaneRecentContext(
            commandAck = commandAck,
            events = events,
            result = result,
        ),
        resultSummary = result?.toTaskResultSummaryUi(artifactCount = artifacts.size),
        artifacts = artifacts,
    )
}

internal fun TaskSessionDetailUiState.withControlPlaneOverlay(
    overlay: TaskSessionControlPlaneOverlay,
): TaskSessionDetailUiState {
    return copy(
        recentContext = overlay.recentContext ?: recentContext,
        resultSummary = overlay.resultSummary ?: resultSummary,
        artifacts = if (overlay.artifacts.isNotEmpty()) overlay.artifacts else artifacts,
    )
}

internal fun TaskSessionDetailUiState.withControlPlaneSnapshot(
    snapshot: TaskSessionControlPlaneSnapshot,
): TaskSessionDetailUiState {
    return withControlPlaneOverlay(
        buildTaskSessionControlPlaneOverlay(
            commandAck = snapshot.commandAck,
            events = snapshot.events,
            result = snapshot.result,
            artifactManifest = snapshot.artifactManifest,
        ),
    )
}

private fun buildControlPlaneRecentContext(
    commandAck: ControlPlaneCommandAck?,
    events: List<ControlPlaneEvent>,
    result: ControlPlaneResult?,
): TaskRecentContextUi? {
    val latestEvent = events.lastOrNull()
    val latestAssistantMessage = result?.summary
        ?.takeIf(String::isNotBlank)
        ?: latestEvent?.toContextMessage()
        ?: commandAck?.toContextMessage()
    val waitingForUserText = events
        .lastOrNull { event -> event.eventType.equals("awaiting_user_input", ignoreCase = true) }
        ?.payload
        ?.stringOrNull("prompt")
        ?: events
            .lastOrNull { event -> event.eventType.equals("awaiting_user_input", ignoreCase = true) }
            ?.payload
            ?.stringOrNull("summary")

    return if (latestAssistantMessage == null && waitingForUserText == null) {
        null
    } else {
        TaskRecentContextUi(
            latestAssistantMessage = latestAssistantMessage,
            waitingForUserText = waitingForUserText,
        )
    }
}

private fun ControlPlaneCommandAck.toContextMessage(): String? {
    return reason?.takeIf(String::isNotBlank)
        ?: errorMessage?.takeIf(String::isNotBlank)
        ?: when (ackStatus.lowercase()) {
            "accepted" -> "Command accepted."
            "accepted_but_queued" -> "Command accepted and queued."
            "rejected" -> "Command rejected."
            else -> null
        }
}

private fun ControlPlaneEvent.toContextMessage(): String? {
    return payload.stringOrNull("summary")
        ?: payload.stringOrNull("message")
        ?: payload.stringOrNull("status_text")
        ?: when (eventType.lowercase()) {
            "queued" -> "Command queued."
            "accepted" -> "Command accepted."
            "running" -> "Run in progress."
            "awaiting_user_input" -> "Waiting for user input."
            "paused" -> "Run paused."
            "done" -> "Run completed."
            "failed" -> "Run failed."
            "killed" -> "Run killed."
            else -> null
        }
}

private fun ControlPlaneResult.toTaskResultSummaryUi(
    artifactCount: Int,
): TaskResultSummaryUi {
    val headline = when (finalStatus.lowercase()) {
        "done" -> "Latest run completed"
        "failed" -> "Latest run failed"
        "running" -> "Run in progress"
        "awaiting_user_input" -> "Waiting for your reply"
        "paused" -> "Session paused"
        else -> "Latest session result"
    }
    val supportingText = buildList {
        summary.takeIf(String::isNotBlank)?.let(::add)
        if (artifactCount > 0) {
            add("$artifactCount file" + if (artifactCount == 1) "" else "s")
        }
    }
        .joinToString(separator = " · ")
        .ifBlank { null }

    return TaskResultSummaryUi(
        headline = headline,
        supportingText = supportingText,
        statusLabel = finalStatus.toUiLabel(),
        effectiveExecutionSummary = effectiveExecution?.toDisplaySummary(),
    )
}

private fun ControlPlaneArtifact.toTaskSessionArtifactUi(): TaskSessionArtifactUi {
    val supportingText = buildList {
        role?.takeIf(String::isNotBlank)?.let(::add)
        kind.takeIf(String::isNotBlank)?.let(::add)
        contentType.takeIf(String::isNotBlank)?.let(::add)
        size.takeIf { it > 0 }?.let { add("${it} B") }
        downloadRef?.kind?.takeIf(String::isNotBlank)?.let(::add)
    }
        .joinToString(separator = " · ")
        .ifBlank { null }

    return TaskSessionArtifactUi(
        id = artifactId,
        title = name,
        supportingText = supportingText,
    )
}

private fun ControlPlaneExecutionPolicy.toDisplaySummary(): String? {
    return buildList {
        backend?.takeIf(String::isNotBlank)?.let { add("backend=${it}") }
        profile?.takeIf(String::isNotBlank)?.let { add("profile=${it}") }
        permission?.takeIf(String::isNotBlank)?.let { add("permission=${it}") }
        backendTransport?.takeIf(String::isNotBlank)?.let { add("transport=${it}") }
        resolvedModel?.takeIf(String::isNotBlank)?.let { add("model=${it}") }
    }
        .joinToString(separator = " · ")
        .ifBlank { null }
}

private fun String.toUiLabel(): String {
    return split('_')
        .filter(String::isNotBlank)
        .joinToString(separator = " ") { token ->
            token.lowercase().replaceFirstChar { firstChar ->
                if (firstChar.isLowerCase()) {
                    firstChar.titlecase()
                } else {
                    firstChar.toString()
                }
            }
        }
        .ifBlank { this }
}

private fun JsonObject.stringOrNull(key: String): String? {
    return this[key]
        ?.let { element -> runCatching { element.jsonPrimitive.content }.getOrNull() }
        ?.trim()
        ?.takeIf(String::isNotEmpty)
}
