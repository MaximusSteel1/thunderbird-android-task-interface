package net.thunderbird.feature.taskmail.internal.ui.detail

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.thunderbird.feature.taskmail.internal.data.controlplane.protocol.ControlPlaneArtifact
import net.thunderbird.feature.taskmail.internal.data.controlplane.protocol.ControlPlaneArtifactManifest
import net.thunderbird.feature.taskmail.internal.data.controlplane.protocol.ControlPlaneCommandAck
import net.thunderbird.feature.taskmail.internal.data.controlplane.protocol.ControlPlaneDownloadRef
import net.thunderbird.feature.taskmail.internal.data.controlplane.protocol.ControlPlaneEvent
import net.thunderbird.feature.taskmail.internal.data.controlplane.protocol.ControlPlaneExecutionPolicy
import net.thunderbird.feature.taskmail.internal.data.controlplane.protocol.ControlPlaneResult
import net.thunderbird.feature.taskmail.internal.data.controlplane.protocol.ControlPlaneStructuredPayload
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionControlPlaneSnapshot

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
        assertThat(overlay.artifacts.single().displayName).isEqualTo("summary.md")
        assertThat(overlay.artifacts.single().isActionAvailable).isEqualTo(true)
    }

    @Test
    fun `withControlPlaneSnapshot should surface matching preserved result body`() {
        val state = TaskSessionDetailUiState(
            sessionName = "Android VPS slice",
            backend = "Codex",
            status = "Done",
            repoPath = "E:/projects/android_task_manager",
            timeline = persistentListOf(
                TaskTimelineItemUi(
                    id = "timeline_001",
                    timestamp = 1L,
                    direction = "System",
                    statusLabel = "Done",
                    summary = "Completed the Android VPS-first readiness slice.",
                    plainText = """
                        Completed the Android VPS-first readiness slice.

                        Changed files:
                        - TaskNewTaskViewModel.kt
                        - TaskWorkspaceViewModel.kt
                    """.trimIndent(),
                ),
            ),
        )
        val snapshot = TaskSessionControlPlaneSnapshot(
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
        )

        val projectedState = state.withControlPlaneSnapshot(snapshot)
        val resultBody = projectedState.resultBody?.plainText.orEmpty()

        assertThat(resultBody).contains("Changed files:")
        assertThat(resultBody).contains("TaskWorkspaceViewModel.kt")
    }
}
