package net.thunderbird.feature.taskmail.internal.domain.parser

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlin.test.Test
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailBackend
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSessionLifecycle
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSessionStatus

class TaskStateCapsuleParserTest {

    private val testSubject = TaskStateCapsuleParser()

    @Test
    fun `parse should return null when state block is incomplete`() {
        // Arrange
        val text = """
            ---TASK-STATE-BEGIN---
            thread_id: thread_001
            status: done
        """.trimIndent()

        // Act
        val result = testSubject.parse(text)

        // Assert
        assertThat(result).isNull()
    }

    @Test
    fun `parse should return last complete state block`() {
        // Arrange
        val text = """
            ---TASK-STATE-BEGIN---
            thread_id: thread_old
            status: running
            ---TASK-STATE-END---
            
            ---TASK-STATE-BEGIN---
            thread_id: thread_new
            workspace_id: workspace_001
            session_id: session_001
            session_name: Demo task
            task_id: task_001
            backend: codex
            repo_path: D:\repo
            workdir: src
            mode: modify
            status: done
            lifecycle: ended
            last_active_at: 2026-03-18T16:12:00Z
            last_progress_at: 2026-03-18T16:10:00Z
            last_summary: Finished successfully
            ---TASK-STATE-END---
        """.trimIndent()

        // Act
        val result = testSubject.parse(text)

        // Assert
        assertThat(result).isEqualTo(
            TaskStateCapsule(
                threadId = "thread_new",
                workspaceId = "workspace_001",
                sessionId = "session_001",
                sessionName = "Demo task",
                taskId = "task_001",
                backend = TaskMailBackend.Codex,
                repoPath = "D:\\repo",
                workdir = "src",
                mode = "modify",
                status = TaskMailSessionStatus.Done,
                lifecycle = TaskMailSessionLifecycle.Ended,
                lastActiveAt = "2026-03-18T16:12:00Z",
                lastProgressAt = "2026-03-18T16:10:00Z",
                lastSummary = "Finished successfully",
            ),
        )
    }

    @Test
    fun `parse should flatten multiline values`() {
        // Arrange
        val text = """
            ---TASK-STATE-BEGIN---
            thread_id: thread_001
            status: running
            last_summary: Need more context
              before modifying shared files
            ---TASK-STATE-END---
        """.trimIndent()

        // Act
        val result = testSubject.parse(text)

        // Assert
        assertThat(result).isEqualTo(
            TaskStateCapsule(
                threadId = "thread_001",
                status = TaskMailSessionStatus.Running,
                lastSummary = "Need more context before modifying shared files",
            ),
        )
    }

    @Test
    fun `parse should keep paused from status`() {
        // Arrange
        val text = """
            ---TASK-STATE-BEGIN---
            thread_id: thread_001
            status: paused
            paused_from_status: awaiting_user_input
            ---TASK-STATE-END---
        """.trimIndent()

        // Act
        val result = testSubject.parse(text)

        // Assert
        assertThat(result).isEqualTo(
            TaskStateCapsule(
                threadId = "thread_001",
                status = TaskMailSessionStatus.Paused,
                pausedFromStatus = TaskMailSessionStatus.WaitingUser,
            ),
        )
    }

    @Test
    fun `parse should recover flattened single line state fields`() {
        // Arrange
        val text = """
            ---TASK-STATE-BEGIN---
            thread_id: thread_026 workspace_id: workspace_1ed1d100687a session_id: thread_026 session_name: 时间线测试 task_id: 20260315_142209_86ff backend: opencode repo_path: E:\projects\test_folder_for_task_manager workdir: mode: modify status: done lifecycle: active last_active_at: 2026-03-18T18:10:00 last_progress_at: 2026-03-18T18:09:30 last_summary: 1. Hi
            ---TASK-STATE-END---
        """.trimIndent()

        // Act
        val result = testSubject.parse(text)

        // Assert
        assertThat(result).isEqualTo(
            TaskStateCapsule(
                threadId = "thread_026",
                workspaceId = "workspace_1ed1d100687a",
                sessionId = "thread_026",
                sessionName = "时间线测试",
                taskId = "20260315_142209_86ff",
                backend = TaskMailBackend.OpenCode,
                repoPath = "E:\\projects\\test_folder_for_task_manager",
                workdir = "",
                mode = "modify",
                status = TaskMailSessionStatus.Done,
                lifecycle = TaskMailSessionLifecycle.Active,
                lastActiveAt = "2026-03-18T18:10:00",
                lastProgressAt = "2026-03-18T18:09:30",
                lastSummary = "1. Hi",
            ),
        )
    }
}
