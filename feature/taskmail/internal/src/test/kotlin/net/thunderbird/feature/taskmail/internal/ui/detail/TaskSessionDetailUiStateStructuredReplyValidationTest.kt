package net.thunderbird.feature.taskmail.internal.ui.detail

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

internal class TaskSessionDetailUiStateStructuredReplyValidationTest {

    @Test
    fun `canSendReply returns false when a required multi question answer is missing`() {
        val testSubject = structuredReplyUiState(
            pendingQuestions = listOf(
                requiredChoiceQuestion(
                    questionId = "phase2_entry_position",
                    choices = listOf("top", "below", "section"),
                ),
                requiredChoiceQuestion(
                    questionId = "phase2_icon_strings",
                    choices = listOf("provide", "reuse", "placeholder"),
                ),
            ),
        )

        val result = testSubject.canSendReply(
            draftText = """
                Answers:
                phase2_entry_position: below
            """.trimIndent(),
        )

        assertThat(result).isEqualTo(false)
    }

    @Test
    fun `canSendReply returns false when structured reply contains unknown question id`() {
        val testSubject = structuredReplyUiState(
            pendingQuestions = listOf(
                requiredChoiceQuestion(
                    questionId = "phase2_entry_position",
                    choices = listOf("top", "below", "section"),
                ),
                requiredChoiceQuestion(
                    questionId = "phase2_icon_strings",
                    choices = listOf("provide", "reuse", "placeholder"),
                ),
            ),
        )

        val result = testSubject.canSendReply(
            draftText = """
                Answers:
                phase2_entry_position: below
                unknown_question: provide
                phase2_icon_strings: provide
            """.trimIndent(),
        )

        assertThat(result).isEqualTo(false)
    }

    @Test
    fun `canSendReply returns false when choice answer uses label instead of canonical value`() {
        val testSubject = structuredReplyUiState(
            pendingQuestions = listOf(
                requiredChoiceQuestion(
                    questionId = "phase2_entry_position",
                    choices = listOf("top", "below", "section"),
                ),
                requiredChoiceQuestion(
                    questionId = "phase2_icon_strings",
                    choices = listOf("provide", "reuse", "placeholder"),
                ),
            ),
        )

        val result = testSubject.canSendReply(
            draftText = """
                Answers:
                phase2_entry_position: below
                phase2_icon_strings: You provide
            """.trimIndent(),
        )

        assertThat(result).isEqualTo(false)
    }

    @Test
    fun `canSendReply returns true when all required answers are present and optional answer is omitted`() {
        val testSubject = structuredReplyUiState(
            pendingQuestions = listOf(
                requiredChoiceQuestion(
                    questionId = "phase2_entry_position",
                    choices = listOf("top", "below", "section"),
                ),
                TaskPendingQuestionUi(
                    questionId = "phase2_icon_strings",
                    questionText = "Who provides icon and string resources?",
                    choices = listOf(
                        TaskPendingQuestionChoiceUi(value = "provide"),
                        TaskPendingQuestionChoiceUi(value = "reuse"),
                        TaskPendingQuestionChoiceUi(value = "placeholder"),
                    ).toImmutableQuestionChoices(),
                    isRequired = false,
                ),
            ),
        )

        val result = testSubject.canSendReply(
            draftText = """
                Answers:
                phase2_entry_position: below
            """.trimIndent(),
        )

        assertThat(result).isEqualTo(true)
    }

    @Test
    fun `canSendReply keeps two line structured answer format compatible`() {
        val testSubject = structuredReplyUiState(
            pendingQuestions = listOf(
                requiredChoiceQuestion(
                    questionId = "phase2_entry_position",
                    choices = listOf("top", "below", "section"),
                ),
            ),
        )

        val result = testSubject.canSendReply(
            draftText = """
                Answers:
                question_id: phase2_entry_position
                below
            """.trimIndent(),
        )

        assertThat(result).isEqualTo(true)
    }
}

private fun structuredReplyUiState(
    pendingQuestions: List<TaskPendingQuestionUi>,
): TaskSessionDetailUiState {
    return TaskSessionDetailUiState(
        sessionName = "Structured reply validation",
        backend = "Codex",
        status = "WaitingUser",
        repoPath = "E:/projects/android_task_manager",
        pendingQuestions = pendingQuestions.toImmutableQuestions(),
        requiresStructuredReply = true,
        canReply = true,
        canQueryStatus = true,
    )
}

private fun requiredChoiceQuestion(
    questionId: String,
    choices: List<String>,
): TaskPendingQuestionUi {
    return TaskPendingQuestionUi(
        questionId = questionId,
        questionText = questionId,
        choices = choices
            .map(::TaskPendingQuestionChoiceUi)
            .toImmutableQuestionChoices(),
        isRequired = true,
    )
}

private fun List<TaskPendingQuestionUi>.toImmutableQuestions() =
    kotlinx.collections.immutable.persistentListOf<TaskPendingQuestionUi>().addAll(this)

private fun List<TaskPendingQuestionChoiceUi>.toImmutableQuestionChoices() =
    kotlinx.collections.immutable.persistentListOf<TaskPendingQuestionChoiceUi>().addAll(this)
