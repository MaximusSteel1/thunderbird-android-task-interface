package net.thunderbird.feature.taskmail.internal.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

class LegacyTaskMailBodyExtractorTest {

    private val testSubject = LegacyTaskMailBodyExtractor()

    @Test
    fun `extract user message text should return reply delta before quoted reply`() {
        // Arrange
        val bodyText = """
            I will update both files.

            On Fri, Mar 13, 2026 at 10:00 AM Test wrote:
            > Please update both files.
        """.trimIndent()

        // Act
        val result = testSubject.extractUserMessageText(bodyText)

        // Assert
        assertThat(result).isEqualTo("I will update both files.")
    }

    @Test
    fun `extract user message text should remove task capsules before trimming`() {
        // Arrange
        val bodyText = """
            Choice: A

            ---TASK-QUESTION-BEGIN---
            question_id: q1
            question_text: Allow API changes?
            choices: yes | no
            ---TASK-QUESTION-END---

            ---TASK-STATE-BEGIN---
            thread_id: thread_001
            status: awaiting_user_input
            ---TASK-STATE-END---
        """.trimIndent()

        // Act
        val result = testSubject.extractUserMessageText(bodyText)

        // Assert
        assertThat(result).isEqualTo("Choice: A")
    }

    @Test
    fun `extract user message text should fall back to normalized body when no quote marker is found`() {
        // Arrange
        val bodyText = """

            Added the missing test case.

        """.trimIndent()

        // Act
        val result = testSubject.extractUserMessageText(bodyText)

        // Assert
        assertThat(result).isEqualTo("Added the missing test case.")
    }

    @Test
    fun `extract user message text should remove inline quoted chinese headers`() {
        // Arrange
        val bodyText = """
            依次列出我让你说的内容 发件人: Task_runner <assistant@example.com> 发送时间: 2026-03-15 14:20:34 收件人: user@example.com 主题: [DONE][S:thread_026] 时间线测试
            Status: DONE
            Reply: Oho
            ---TASK-STATE-BEGIN---
            thread_id: thread_026
            session_id: thread_026
            backend: opencode
            status: done
            ---TASK-STATE-END---
        """.trimIndent()

        // Act
        val result = testSubject.extractUserMessageText(bodyText)

        // Assert
        assertThat(result).isEqualTo("依次列出我让你说的内容")
    }

    @Test
    fun `extract system message text should prefer reply block`() {
        // Arrange
        val bodyText = """
            Status: done
            Summary: Finished successfully

            Reply:
            Updated both files and added tests.

            ---TASK-STATE-BEGIN---
            thread_id: thread_001
            status: done
            ---TASK-STATE-END---
        """.trimIndent()

        // Act
        val result = testSubject.extractSystemMessageText(bodyText)

        // Assert
        assertThat(result).isEqualTo("Updated both files and added tests.")
    }

    @Test
    fun `extract system message text should fall back to summary line`() {
        // Arrange
        val bodyText = """
            Status: running
            Summary: Need more context before changing shared API

            ---TASK-STATE-BEGIN---
            thread_id: thread_001
            status: running
            ---TASK-STATE-END---
        """.trimIndent()

        // Act
        val result = testSubject.extractSystemMessageText(bodyText)

        // Assert
        assertThat(result).isEqualTo("Need more context before changing shared API")
    }

    @Test
    fun `extract system message text should return question and choices`() {
        // Arrange
        val bodyText = """
            Status: awaiting_user_input
            Question: Should I update both files?
            Choices: yes | no

            ---TASK-QUESTION-BEGIN---
            question_id: q1
            question_text: Should I update both files?
            choices: yes | no
            ---TASK-QUESTION-END---
        """.trimIndent()

        // Act
        val result = testSubject.extractSystemMessageText(bodyText)

        // Assert
        assertThat(result).isEqualTo(
            """
            Question: Should I update both files?
            Choices: yes | no
            """.trimIndent(),
        )
    }

    @Test
    fun `extract system message text should return status when only status is available`() {
        // Arrange
        val bodyText = """
            Status: running

            ---TASK-STATE-BEGIN---
            thread_id: thread_001
            status: running
            ---TASK-STATE-END---
        """.trimIndent()

        // Act
        val result = testSubject.extractSystemMessageText(bodyText)

        // Assert
        assertThat(result).isEqualTo("Status: running")
    }

    @Test
    fun `extract system message text should recover narrative from flattened metadata blocks`() {
        // Arrange
        val bodyText = """
            Status: DONE Session ID: thread_025 Thread ID: thread_025 Task ID: 20260314_191621_95d6 Backend: codex Repo: E:\projects\folder_for_task_manager Workdir: src\legacy

            Please review the current session first, then continue the regression pass.
            ---TASK-STATE-BEGIN--- thread_id: thread_025 workspace_id: workspace_1 session_id: thread_025 session_name: Attachment retest 2 task_id: 20260314_191621_95d6 backend: codex repo_path: E:\projects\folder_for_task_manager workdir: src\legacy mode: modify status: done last_summary: Status: DONE Session ID: thread_025 Thread ID: thread_025 ---TASK-STATE-END---
        """.trimIndent()

        // Act
        val result = testSubject.extractSystemMessageText(bodyText)

        // Assert
        assertThat(result).isEqualTo("Please review the current session first, then continue the regression pass.")
    }

    @Test
    fun `extract system message text should recover flattened reply blocks`() {
        // Arrange
        val bodyText = """
            Status: DONE Reply: 1. Hi 2. Ho 3. Aha 4. Oho

            ---TASK-STATE-BEGIN--- thread_id: thread_026 workspace_id: workspace_1ed1d100687a session_id: thread_026 session_name: Timeline test task_id: 20260315_142209_86ff backend: opencode repo_path: E:\projects\test_folder_for_task_manager workdir: mode: modify status: done last_summary: 1. Hi ---TASK-STATE-END---
        """.trimIndent()

        // Act
        val result = testSubject.extractSystemMessageText(bodyText)

        // Assert
        assertThat(result).isEqualTo("1. Hi 2. Ho 3. Aha 4. Oho")
    }
}
