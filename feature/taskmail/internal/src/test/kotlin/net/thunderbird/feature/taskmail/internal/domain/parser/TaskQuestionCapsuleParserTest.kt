package net.thunderbird.feature.taskmail.internal.domain.parser

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlin.test.Test

class TaskQuestionCapsuleParserTest {

    private val testSubject = TaskQuestionCapsuleParser()

    @Test
    fun `parse should return null when question block does not exist`() {
        // Arrange
        val text = "No question block here"

        // Act
        val result = testSubject.parse(text)

        // Assert
        assertThat(result).isNull()
    }

    @Test
    fun `parse should split choices and keep last complete question block`() {
        // Arrange
        val text = """
            ---TASK-QUESTION-BEGIN---
            question_id: question_old
            question_text: Ignore me
            choices: yes | no
            ---TASK-QUESTION-END---
            
            ---TASK-QUESTION-BEGIN---
            question_id: question_task_001
            question_text: Should I update both files?
            choices: yes | no | later
            ---TASK-QUESTION-END---
        """.trimIndent()

        // Act
        val result = testSubject.parse(text)

        // Assert
        assertThat(result).isEqualTo(
            TaskQuestionCapsule(
                questionId = "question_task_001",
                questionText = "Should I update both files?",
                choices = listOf("yes", "no", "later"),
                questionType = "single_choice",
            ),
        )
    }

    @Test
    fun `parse should flatten multiline question text`() {
        // Arrange
        val text = """
            ---TASK-QUESTION-BEGIN---
            question_id: question_task_002
            question_text: Should I update both files
              before moving forward?
            choices: yes | no
            ---TASK-QUESTION-END---
        """.trimIndent()

        // Act
        val result = testSubject.parse(text)

        // Assert
        assertThat(result).isEqualTo(
            TaskQuestionCapsule(
                questionId = "question_task_002",
                questionText = "Should I update both files before moving forward?",
                choices = listOf("yes", "no"),
                questionType = "single_choice",
            ),
        )
    }

    @Test
    fun `parseAll should preserve a multi-question set with labels and metadata`() {
        // Arrange
        val text = """
            ---TASK-QUESTION-BEGIN---
            question_set_id: phase2_clarifications
            question_id: phase2_entry_position
            question_type: single_choice
            required: true
            question_text: Where should the Tasks drawer entry be placed?
            choices: top | below | section
            choice_labels: top=Account list top | below=Account list bottom | section=Standalone section
            ---TASK-QUESTION-END---
            ---TASK-QUESTION-BEGIN---
            question_set_id: phase2_clarifications
            question_id: phase2_icon_strings
            question_type: single_choice
            required: false
            question_text: Who provides icon and string resources?
            choices: provide | reuse | placeholder
            choice_labels: provide=You provide | reuse=Reuse existing | placeholder=Temporary placeholder
            ---TASK-QUESTION-END---
        """.trimIndent()

        // Act
        val result = testSubject.parseAll(text)

        // Assert
        assertThat(result).containsExactly(
            TaskQuestionCapsule(
                questionId = "phase2_entry_position",
                questionText = "Where should the Tasks drawer entry be placed?",
                choices = listOf("top", "below", "section"),
                questionSetId = "phase2_clarifications",
                questionType = "single_choice",
                required = true,
                choiceLabels = mapOf(
                    "top" to "Account list top",
                    "below" to "Account list bottom",
                    "section" to "Standalone section",
                ),
            ),
            TaskQuestionCapsule(
                questionId = "phase2_icon_strings",
                questionText = "Who provides icon and string resources?",
                choices = listOf("provide", "reuse", "placeholder"),
                questionSetId = "phase2_clarifications",
                questionType = "single_choice",
                required = false,
                choiceLabels = mapOf(
                    "provide" to "You provide",
                    "reuse" to "Reuse existing",
                    "placeholder" to "Temporary placeholder",
                ),
            ),
        )
        assertThat(testSubject.parse(text)?.questionId).isEqualTo("phase2_icon_strings")
    }

    @Test
    fun `parseAll should preserve duplicate question blocks from source mail`() {
        // Arrange
        val text = """
            ---TASK-QUESTION-BEGIN---
            question_set_id: phase3_duplicate_question
            question_id: q_branch
            question_type: single_choice
            required: true
            question_text: Which branch should I use?
            choices: main | release
            choice_labels: main=Main branch | release=Release branch
            ---TASK-QUESTION-END---
            ---TASK-QUESTION-BEGIN---
            question_set_id: phase3_duplicate_question
            question_id: q_branch
            question_type: single_choice
            required: true
            question_text: Which branch should I use?
            choices: main | release
            choice_labels: main=Main branch | release=Release branch
            ---TASK-QUESTION-END---
        """.trimIndent()

        // Act
        val result = testSubject.parseAll(text)

        // Assert
        assertThat(result).containsExactly(
            TaskQuestionCapsule(
                questionId = "q_branch",
                questionText = "Which branch should I use?",
                choices = listOf("main", "release"),
                questionSetId = "phase3_duplicate_question",
                questionType = "single_choice",
                required = true,
                choiceLabels = mapOf(
                    "main" to "Main branch",
                    "release" to "Release branch",
                ),
            ),
            TaskQuestionCapsule(
                questionId = "q_branch",
                questionText = "Which branch should I use?",
                choices = listOf("main", "release"),
                questionSetId = "phase3_duplicate_question",
                questionType = "single_choice",
                required = true,
                choiceLabels = mapOf(
                    "main" to "Main branch",
                    "release" to "Release branch",
                ),
            ),
        )
    }

    @Test
    fun `parseAll should return empty list for conflicting question set ids`() {
        // Arrange
        val text = """
            ---TASK-QUESTION-BEGIN---
            question_set_id: phase2_a
            question_id: q1
            question_text: First question?
            ---TASK-QUESTION-END---
            ---TASK-QUESTION-BEGIN---
            question_set_id: phase2_b
            question_id: q2
            question_text: Second question?
            ---TASK-QUESTION-END---
        """.trimIndent()

        // Act
        val result = testSubject.parseAll(text)

        // Assert
        assertThat(result).isEmpty()
        assertThat(testSubject.parse(text)).isNull()
    }

    @Test
    fun `parseAll should recover flattened multi question blocks`() {
        // Arrange
        val text = """
            ---TASK-QUESTION-BEGIN---
            question_set_id: phase2_clarifications question_id: phase2_entry_position question_type: single_choice required: true question_text: Where should the Tasks drawer entry be placed? choices: top | below | section choice_labels: top=Account list top | below=Account list bottom | section=Standalone section
            ---TASK-QUESTION-END---
            ---TASK-QUESTION-BEGIN---
            question_set_id: phase2_clarifications question_id: phase2_icon_strings question_type: single_choice required: false question_text: Who provides icon and string resources? choices: provide | reuse | placeholder choice_labels: provide=You provide | reuse=Reuse existing | placeholder=Temporary placeholder
            ---TASK-QUESTION-END---
        """.trimIndent()

        // Act
        val result = testSubject.parseAll(text)

        // Assert
        assertThat(result).containsExactly(
            TaskQuestionCapsule(
                questionId = "phase2_entry_position",
                questionText = "Where should the Tasks drawer entry be placed?",
                choices = listOf("top", "below", "section"),
                questionSetId = "phase2_clarifications",
                questionType = "single_choice",
                required = true,
                choiceLabels = mapOf(
                    "top" to "Account list top",
                    "below" to "Account list bottom",
                    "section" to "Standalone section",
                ),
            ),
            TaskQuestionCapsule(
                questionId = "phase2_icon_strings",
                questionText = "Who provides icon and string resources?",
                choices = listOf("provide", "reuse", "placeholder"),
                questionSetId = "phase2_clarifications",
                questionType = "single_choice",
                required = false,
                choiceLabels = mapOf(
                    "provide" to "You provide",
                    "reuse" to "Reuse existing",
                    "placeholder" to "Temporary placeholder",
                ),
            ),
        )
    }
}
