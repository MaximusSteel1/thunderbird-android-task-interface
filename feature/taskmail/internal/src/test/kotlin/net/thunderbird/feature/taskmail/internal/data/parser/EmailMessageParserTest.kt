package net.thunderbird.feature.taskmail.internal.data.parser

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import net.thunderbird.feature.taskmail.internal.data.TaskMailMessage
import net.thunderbird.feature.taskmail.internal.data.ingress.EmailIngressMessage
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailBackend
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskMailDetection
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskMailParsedSubject

class EmailMessageParserTest {

    private val testSubject = EmailMessageParser()

    @Test
    fun `parse should detect task mail and preserve user sender state`() {
        // Arrange
        val rawMessage = EmailIngressMessage(
            accountUuid = "account-1",
            accountEmailAddress = "user@example.com",
            folderId = 42L,
            messageServerId = "server-1",
            threadRootId = 7L,
            timestamp = 123L,
            subject = "[OC] Refactor floor_shear",
            fromAddresses = listOf("user@example.com"),
            rawBodyText = "Please refactor floor_shear.",
        )

        // Act
        val result = testSubject.parse(rawMessage)

        // Assert
        assertThat(result).isEqualTo(
            TaskMailMessage(
                accountUuid = "account-1",
                folderId = 42L,
                messageServerId = "server-1",
                threadRootId = 7L,
                timestamp = 123L,
                subject = "[OC] Refactor floor_shear",
                rawBodyText = "Please refactor floor_shear.",
                htmlBody = null,
                internetMessageId = null,
                attachments = emptyList(),
                detection = TaskMailDetection(
                    isTaskMail = true,
                    isSystemMessage = false,
                    parsedSubject = TaskMailParsedSubject(
                        backend = TaskMailBackend.OpenCode,
                        statusLabel = null,
                        sessionIdFromSubject = null,
                        subjectText = "Refactor floor_shear",
                        isReplyLike = false,
                    ),
                    stateCapsule = null,
                    questionCapsule = null,
                ),
                isFromCurrentUser = true,
            ),
        )
    }
}
