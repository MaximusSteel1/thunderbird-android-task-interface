package net.thunderbird.feature.taskmail.internal.data

import com.fsck.k9.message.Attachment
import net.thunderbird.feature.taskmail.internal.domain.model.TaskReplyAttachment

internal interface TaskMailReplyAttachmentResolver {
    suspend fun resolveSelectedAttachments(uriStrings: List<String>): List<TaskReplyAttachment>

    suspend fun buildOutgoingAttachments(attachments: List<TaskReplyAttachment>): Result<List<Attachment>>
}
