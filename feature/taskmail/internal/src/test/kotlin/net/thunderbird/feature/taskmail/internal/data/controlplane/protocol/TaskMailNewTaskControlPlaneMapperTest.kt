package net.thunderbird.feature.taskmail.internal.data.controlplane.protocol

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailBackend
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskDraft
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskMode
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskPermission

class TaskMailNewTaskControlPlaneMapperTest {

    @Test
    fun `toControlPlaneNewTaskCommand should build canonical control-plane new_task payload`() {
        val command = canonicalDraft().toControlPlaneNewTaskCommand(
            commandId = "cmd_001",
            issuedAt = "2026-03-26T10:00:00Z",
            issuerId = "android_user",
        )

        assertThat(command).isEqualTo(
            ControlPlaneCommand(
                commandId = "cmd_001",
                commandType = "new_task",
                pcId = "pc_home",
                workspaceId = "workspace_android_task_manager",
                sessionId = null,
                executionPolicy = ControlPlaneExecutionPolicy(
                    backend = "codex",
                    profile = "default",
                    permission = "highest",
                    backendTransport = "sdk",
                ),
                payload = command.payload,
                issuedAt = "2026-03-26T10:00:00Z",
                issuerId = "android_user",
            ),
        )

        assertThat(command.payload.string("repo_path")).isEqualTo("E:/projects/android_task_manager")
        assertThat(command.payload.string("workdir")).isEqualTo("feature/taskmail/internal")
        assertThat(command.payload.string("task_text")).isEqualTo("Audit the VPS-first handshake slice.")
        assertThat(command.payload.string("mode")).isEqualTo("analysis_only")
        assertThat(command.payload.int("timeout_seconds")).isEqualTo(5400)
        assertThat(command.payload.string("source")).isEqualTo("android")
        assertThat(command.payload.stringArray("acceptance")).containsExactly(
            "List only the mainline blockers.",
            "Do not widen scope.",
        )
    }

    @Test
    fun `toControlPlaneNewTaskCommand should require pc and workspace routing identities`() {
        val missingPcDraft = canonicalDraft().copy(pcId = " ")
        val missingWorkspaceDraft = canonicalDraft().copy(workspaceId = null)

        assertFailsWith<IllegalStateException> {
            missingPcDraft.toControlPlaneNewTaskCommand(
                commandId = "cmd_001",
                issuedAt = "2026-03-26T10:00:00Z",
            )
        }
        assertFailsWith<IllegalStateException> {
            missingWorkspaceDraft.toControlPlaneNewTaskCommand(
                commandId = "cmd_001",
                issuedAt = "2026-03-26T10:00:00Z",
            )
        }
    }

    @Test
    fun `toControlPlaneNewTaskDispatchMessage should wrap canonical envelope metadata`() {
        val message = canonicalDraft().toControlPlaneNewTaskDispatchMessage(
            messageId = "msg_cmd_001",
            traceId = "trace_cmd_001",
            commandId = "cmd_001",
            connectionEpoch = 12,
            sentAt = "2026-03-26T10:00:01Z",
            issuedAt = "2026-03-26T10:00:00Z",
        )

        assertThat(message).isEqualTo(
            ControlPlaneCommandDispatchMessage(
                messageId = "msg_cmd_001",
                traceId = "trace_cmd_001",
                pcId = "pc_home",
                connectionEpoch = 12,
                sentAt = "2026-03-26T10:00:01Z",
                payload = message.payload,
            ),
        )
        assertThat(message.payload.commandId).isEqualTo("cmd_001")
        assertThat(message.payload.workspaceId).isEqualTo("workspace_android_task_manager")
    }
}

private fun canonicalDraft(): TaskMailNewTaskDraft {
    return TaskMailNewTaskDraft(
        senderAccountId = "account_primary",
        backend = TaskMailBackend.Codex,
        repoPath = "E:/projects/android_task_manager",
        workdir = "feature/taskmail/internal",
        taskText = "Audit the VPS-first handshake slice.",
        subjectTitle = "Audit the handshake slice",
        mode = TaskMailNewTaskMode.AnalysisOnly,
        timeoutMinutes = 90,
        permission = TaskMailNewTaskPermission.Highest,
        profile = "android",
        acceptanceCriteria = listOf(
            "- List only the mainline blockers.",
            "  Do not widen scope.  ",
        ),
        pcId = "pc_home",
        workspaceId = "workspace_android_task_manager",
        executionPolicy = ControlPlaneExecutionPolicy(
            backend = "codex",
            profile = "default",
            permission = "highest",
            backendTransport = "sdk",
        ),
    )
}

private fun JsonObject.string(key: String): String {
    return getValue(key).jsonPrimitive.content
}

private fun JsonObject.int(key: String): Int {
    return getValue(key).jsonPrimitive.content.toInt()
}

private fun JsonObject.stringArray(key: String): List<String> {
    return getValue(key).jsonArray.map { it.jsonPrimitive.content }
}
