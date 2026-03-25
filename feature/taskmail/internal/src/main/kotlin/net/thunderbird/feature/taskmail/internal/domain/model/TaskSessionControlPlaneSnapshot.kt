package net.thunderbird.feature.taskmail.internal.domain.model

import java.util.LinkedHashMap
import net.thunderbird.feature.taskmail.internal.data.controlplane.protocol.ControlPlaneArtifactManifest
import net.thunderbird.feature.taskmail.internal.data.controlplane.protocol.ControlPlaneCommandAck
import net.thunderbird.feature.taskmail.internal.data.controlplane.protocol.ControlPlaneEvent
import net.thunderbird.feature.taskmail.internal.data.controlplane.protocol.ControlPlaneResult

internal data class TaskSessionControlPlaneSnapshot(
    val commandAck: ControlPlaneCommandAck? = null,
    val events: List<ControlPlaneEvent> = emptyList(),
    val result: ControlPlaneResult? = null,
    val artifactManifest: ControlPlaneArtifactManifest? = null,
)

internal fun TaskSessionControlPlaneSnapshot?.merge(
    incoming: TaskSessionControlPlaneSnapshot?,
): TaskSessionControlPlaneSnapshot? {
    return when {
        this == null -> incoming
        incoming == null -> this
        else -> TaskSessionControlPlaneSnapshot(
            commandAck = incoming.commandAck ?: commandAck,
            events = mergeControlPlaneEvents(
                existing = events,
                incoming = incoming.events,
            ),
            result = incoming.result ?: result,
            artifactManifest = incoming.artifactManifest ?: artifactManifest,
        )
    }
}

private fun mergeControlPlaneEvents(
    existing: List<ControlPlaneEvent>,
    incoming: List<ControlPlaneEvent>,
): List<ControlPlaneEvent> {
    if (existing.isEmpty()) return incoming.takeLast(MAX_CONTROL_PLANE_EVENTS)
    if (incoming.isEmpty()) return existing.takeLast(MAX_CONTROL_PLANE_EVENTS)

    val mergedEvents = LinkedHashMap<String, ControlPlaneEvent>()
    (existing + incoming).forEach { event ->
        val stableId = event.eventId.ifBlank {
            buildString {
                append(event.commandId)
                append(':')
                append(event.eventType)
                event.emittedAt?.takeIf(String::isNotBlank)?.let {
                    append(':')
                    append(it)
                }
            }
        }
        mergedEvents.remove(stableId)
        mergedEvents[stableId] = event
    }

    return mergedEvents.values.toList().takeLast(MAX_CONTROL_PLANE_EVENTS)
}

private const val MAX_CONTROL_PLANE_EVENTS = 20
