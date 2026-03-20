package net.thunderbird.feature.taskmail.internal.domain.parser

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailBackend
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailStatusLabel

class TaskMailSubjectParserTest {

    private val testSubject = TaskMailSubjectParser()

    @Test
    fun `parse should extract backend from new task subject`() {
        // Arrange
        val subject = "[OC] Refactor floor_shear"

        // Act
        val result = testSubject.parse(subject)

        // Assert
        assertThat(result).isEqualTo(
            TaskMailParsedSubject(
                backend = TaskMailBackend.OpenCode,
                statusLabel = null,
                sessionIdFromSubject = null,
                subjectText = "Refactor floor_shear",
                isReplyLike = false,
            ),
        )
    }

    @Test
    fun `parse should extract status session id backend and reply prefix`() {
        // Arrange
        val subject = "Re: [DONE][S:session-42] [CX] Analyze floor_shear"

        // Act
        val result = testSubject.parse(subject)

        // Assert
        assertThat(result).isEqualTo(
            TaskMailParsedSubject(
                backend = TaskMailBackend.Codex,
                statusLabel = TaskMailStatusLabel.Done,
                sessionIdFromSubject = "session-42",
                subjectText = "Analyze floor_shear",
                isReplyLike = true,
            ),
        )
    }

    @Test
    fun `parse should treat AW prefix as reply like and preserve paused token`() {
        // Arrange
        val subject = "AW: [PAUSED][S:session-42] [CX] Analyze floor_shear"

        // Act
        val result = testSubject.parse(subject)

        // Assert
        assertThat(result).isEqualTo(
            TaskMailParsedSubject(
                backend = TaskMailBackend.Codex,
                statusLabel = TaskMailStatusLabel.Paused,
                sessionIdFromSubject = "session-42",
                subjectText = "Analyze floor_shear",
                isReplyLike = true,
            ),
        )
    }

    @Test
    fun `parse should leave unknown subject unchanged`() {
        // Arrange
        val subject = "Weekly planning notes"

        // Act
        val result = testSubject.parse(subject)

        // Assert
        assertThat(result).isEqualTo(
            TaskMailParsedSubject(
                backend = null,
                statusLabel = null,
                sessionIdFromSubject = null,
                subjectText = "Weekly planning notes",
                isReplyLike = false,
            ),
        )
    }
}
