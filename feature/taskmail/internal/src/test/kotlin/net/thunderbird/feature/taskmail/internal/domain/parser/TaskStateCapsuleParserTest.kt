package net.thunderbird.feature.taskmail.internal.domain.parser

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlin.test.Test
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailBackend
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
}
