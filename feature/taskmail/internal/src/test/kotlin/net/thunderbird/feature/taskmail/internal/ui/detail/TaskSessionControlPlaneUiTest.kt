package net.thunderbird.feature.taskmail.internal.ui.detail

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import kotlin.test.Test
import net.thunderbird.feature.taskmail.internal.data.controlplane.protocol.ControlPlaneArtifact
import net.thunderbird.feature.taskmail.internal.data.controlplane.protocol.ControlPlaneArtifactManifest
import net.thunderbird.feature.taskmail.internal.data.controlplane.protocol.ControlPlaneCommandAck
import net.thunderbird.feature.taskmail.internal.data.controlplane.protocol.ControlPlaneDownloadRef
import net.thunderbird.feature.taskmail.internal.data.controlplane.protocol.ControlPlaneEvent
import net.thunderbird.feature.taskmail.internal.data.controlplane.protocol.ControlPlaneExecutionPolicy
import net.thunderbird.feature.taskmail.internal.data.controlplane.protocol.ControlPlaneResult
import net.thunderbird.feature.taskmail.internal.data.controlplane.protocol.ControlPlaneStructuredPayload
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class TaskSessionControlPlaneUiTest {

    @Test
    fun `buildTaskSessionControlPlaneOverlay should project canonical dto fields into detail ui`() {
        val overlay = buildTaskSessionControlPlaneOverlay(
            commandAck = ControlPlaneCommandAck(
                commandId = "cmd_01",
                ackStatus = "accepted_but_queued",
                queuePosition = 1,
            ),
            events = listOf(
                ControlPlaneEvent(
                    eventId = "evt_01",
                    commandId = "cmd_01",
                    workspaceId = "workspace_android_app",
                    sessionId = "session_001",
                    runId = "run_01",
                    eventType = "running",
                    payload = buildJsonObject {
                        put("summary", "Applying route/key cutover.")
                    },
                ),
            ),
            result = ControlPlaneResult(
                resultId = "res_01",
                commandId = "cmd_01",
                workspaceId = "workspace_android_app",
                sessionId = "session_001",
                runId = "run_01",
                finalStatus = "done",
                summary = "Completed the Android VPS-first readiness slice.",
                effectiveExecution = ControlPlaneExecutionPolicy(
                    backend = "codex",
                    profile = "strong",
                    permission = "highest",
                    backendTransport = "sdk",
                    resolvedModel = "gpt-5-codex",
                ),
                structuredPayload = ControlPlaneStructuredPayload(kind = "task_outcome"),
            ),
            artifactManifest = ControlPlaneArtifactManifest(
                runId = "run_01",
                artifacts = listOf(
                    ControlPlaneArtifact(
                        artifactId = "art_01",
                        name = "summary.md",
                        kind = "file",
                        role = "output",
                        contentType = "text/markdown",
                        size = 1024,
                        downloadRef = ControlPlaneDownloadRef(kind = "vps_file", fileId = "file_01"),
                    ),
                ),
            ),
        )

        assertThat(overlay.recentContext?.latestAssistantMessage)
            .isEqualTo("Completed the Android VPS-first readiness slice.")
        assertThat(overlay.resultSummary?.statusLabel).isEqualTo("Done")
        assertThat(overlay.resultSummary?.effectiveExecutionSummary)
            .isEqualTo("backend=codex · profile=strong · permission=highest · transport=sdk · model=gpt-5-codex")
        assertThat(overlay.artifacts.single().title).isEqualTo("summary.md")
    }
}
