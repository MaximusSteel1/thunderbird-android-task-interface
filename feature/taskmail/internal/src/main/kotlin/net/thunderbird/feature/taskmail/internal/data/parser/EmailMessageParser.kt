package net.thunderbird.feature.taskmail.internal.data.parser

import net.thunderbird.feature.taskmail.internal.data.TaskMailMessage
import net.thunderbird.feature.taskmail.internal.data.ingress.EmailIngressMessage
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskMailEnvelope
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskMailMessageDetector

internal class EmailMessageParser(
    private val messageDetector: TaskMailMessageDetector = TaskMailMessageDetector(),
) : IncomingMessageParser<EmailIngressMessage, TaskMailMessage> {

    override fun parse(message: EmailIngressMessage): TaskMailMessage {
        val detection = messageDetector.detect(
            TaskMailEnvelope(
                messageId = "${message.folderId}:${message.messageServerId}",
                subject = message.subject,
                fromAddress = message.fromAddresses.firstOrNull().orEmpty(),
                timestamp = message.timestamp,
                plainTextBody = message.rawBodyText,
                htmlBody = message.htmlBody,
            ),
        )

        return TaskMailMessage(
            accountUuid = message.accountUuid,
            folderId = message.folderId,
            messageServerId = message.messageServerId,
            threadRootId = message.threadRootId,
            timestamp = message.timestamp,
            subject = message.subject,
            rawBodyText = message.rawBodyText,
            htmlBody = message.htmlBody,
            internetMessageId = message.internetMessageId,
            attachments = message.attachments,
            detection = detection,
            isFromCurrentUser = message.fromAddresses.any { address ->
                address.equals(message.accountEmailAddress, ignoreCase = true)
            },
        )
    }
}
