package net.thunderbird.feature.taskmail.internal.data

import net.thunderbird.feature.taskmail.internal.domain.model.TaskMessageAttachment
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskMailDetection

internal interface TaskMailMessageSource {
    suspend fun getMessages(): List<TaskMailMessage>
}

internal data class TaskMailMessage(
    val accountUuid: String,
    val folderId: Long,
    val messageServerId: String,
    val threadRootId: Long,
    val timestamp: Long,
    val subject: String,
    val rawBodyText: String,
    val htmlBody: String? = null,
    val internetMessageId: String? = null,
    val attachments: List<TaskMessageAttachment> = emptyList(),
    val detection: TaskMailDetection,
    val isFromCurrentUser: Boolean,
)
