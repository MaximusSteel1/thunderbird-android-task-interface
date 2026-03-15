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
}
