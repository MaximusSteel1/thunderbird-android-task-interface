package net.thunderbird.feature.taskmail.internal.data

import com.fsck.k9.mail.internet.MimeMessage
import net.thunderbird.feature.taskmail.internal.domain.reply.TaskMailReplyRequest

internal interface TaskMailMimeMessageFactory {
    suspend fun create(
        request: TaskMailReplyRequest,
        sourceMessage: TaskMailReplySourceMessage,
    ): Result<MimeMessage>
}
