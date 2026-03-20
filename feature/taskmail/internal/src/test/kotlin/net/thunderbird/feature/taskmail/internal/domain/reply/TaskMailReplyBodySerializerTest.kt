package net.thunderbird.feature.taskmail.internal.domain.reply

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

class TaskMailReplyBodySerializerTest {

    private val testSubject = TaskMailReplyBodySerializer()

    @Test
    fun `continue session should preserve raw user text`() {
        val body = testSubject.serialize(
            TaskMailReplyBodyInput(
                mode = TaskMailReplyMode.ContinueSession,
                userText = "  keep whitespace  ",
            ),
        )

        assertThat(body).isEqualTo("  keep whitespace  ")
    }

    @Test
    fun `single question answer should serialize selected choice only`() {
        val body = testSubject.serialize(
            TaskMailReplyBodyInput(
                mode = TaskMailReplyMode.AnswerSingleQuestion,
                userText = "below",
            ),
        )

        assertThat(body).isEqualTo("below")
    }

    @Test
    fun `multi question answer should preserve structured answer body`() {
        val body = testSubject.serialize(
            TaskMailReplyBodyInput(
                mode = TaskMailReplyMode.AnswerMultiQuestion,
                userText = "Answers:\nphase2_entry_position: below",
            ),
        )

        assertThat(body).isEqualTo("Answers:\nphase2_entry_position: below")
    }

    @Test
    fun `resume session should prepend slash resume before user text`() {
        val body = testSubject.serialize(
            TaskMailReplyBodyInput(
                mode = TaskMailReplyMode.ResumeSession,
                userText = "Please continue with the cleanup.",
            ),
        )

        assertThat(body).isEqualTo("/resume\nPlease continue with the cleanup.")
    }

    @Test
    fun `resume session should emit slash resume when body is blank`() {
        val body = testSubject.serialize(
            TaskMailReplyBodyInput(
                mode = TaskMailReplyMode.ResumeSession,
                userText = "",
            ),
        )

        assertThat(body).isEqualTo("/resume")
    }

    @Test
    fun `status query should ignore user text and emit status command`() {
        val body = testSubject.serialize(
            TaskMailReplyBodyInput(
                mode = TaskMailReplyMode.StatusQuery,
                userText = "ignored",
            ),
        )

        assertThat(body).isEqualTo("/status")
    }
}
