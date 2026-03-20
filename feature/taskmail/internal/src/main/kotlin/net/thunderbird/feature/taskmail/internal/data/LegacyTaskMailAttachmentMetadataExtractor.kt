package net.thunderbird.feature.taskmail.internal.data

import android.net.Uri
import com.fsck.k9.mail.Part
import com.fsck.k9.mail.internet.MessageExtractor
import com.fsck.k9.mail.internet.MimeHeader
import com.fsck.k9.mailstore.AttachmentViewInfo
import com.fsck.k9.mailstore.LocalMessage
import com.fsck.k9.mailstore.LocalPart
import com.fsck.k9.message.extractors.AttachmentInfoExtractor
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMessageAttachment

internal class LegacyTaskMailAttachmentMetadataExtractor(
    private val attachmentInfoExtractor: AttachmentInfoExtractor,
) {
    fun extract(localMessage: LocalMessage): List<TaskMessageAttachment> {
        val attachmentParts = mutableListOf<Part>()
        MessageExtractor.findViewablesAndAttachments(localMessage, null, attachmentParts)

        return attachmentInfoExtractor.extractAttachmentInfoForView(attachmentParts)
            .map(AttachmentViewInfo::toTaskMessageAttachment)
            .filter(TaskMessageAttachment::shouldDisplayInTaskTimeline)
            .deduplicateForTaskTimeline()
    }
}

internal fun TaskMessageAttachment.shouldDisplayInTaskTimeline(): Boolean {
    return contentType?.startsWith("multipart/", ignoreCase = true) != true
}

internal fun List<TaskMessageAttachment>.deduplicateForTaskTimeline(): List<TaskMessageAttachment> {
    val deduplicated = linkedMapOf<String, TaskMessageAttachment>()

    for (attachment in this) {
        val deduplicationKey = attachment.taskTimelineDeduplicationKey()
        deduplicated[deduplicationKey] = deduplicated[deduplicationKey]
            ?.mergeForTaskTimeline(attachment)
            ?: attachment
    }

    return deduplicated.values.toList()
}

private fun AttachmentViewInfo.toTaskMessageAttachment(): TaskMessageAttachment {
    val contentId = part.extractContentId()
    val localMessage = when (val localPart = part) {
        is LocalPart -> localPart.message
        is LocalMessage -> localPart
        else -> null
    }
    val internalUriString = internalUri
        .takeUnless { it == Uri.EMPTY }
        ?.toString()
        ?.takeIf { it.isNotBlank() }

    return TaskMessageAttachment(
        id = internalUriString ?: fallbackAttachmentId(contentId),
        displayName = displayName,
        contentType = mimeType?.takeIf { it.isNotBlank() },
        sizeBytes = size.takeIf { it >= 0L },
        isInline = inlineAttachment,
        isImage = isSupportedImage,
        contentId = contentId,
        internalUriString = internalUriString,
        accountUuid = when (val localPart = part) {
            is LocalPart -> localPart.accountUuid
            is LocalMessage -> localPart.account.uuid
            else -> null
        },
        folderId = localMessage?.folder?.databaseId,
        messageServerId = localMessage?.uid,
        partId = when (val localPart = part) {
            is LocalPart -> localPart.partId
            is LocalMessage -> localPart.messagePartId
            else -> null
        },
        isContentAvailable = isContentAvailable(),
    )
}

private fun TaskMessageAttachment.taskTimelineDeduplicationKey(): String {
    return partId?.let { "part:$it" }
        ?: internalUriString?.takeIf { it.isNotBlank() }?.let { "uri:$it" }
        ?: buildString {
            append("fallback:")
            append(contentId?.takeIf { it.isNotBlank() } ?: "_")
            append('|')
            append(displayName.ifBlank { "_" })
            append('|')
            append(sizeBytes ?: -1L)
            append('|')
            append(contentType?.takeIf { it.isNotBlank() } ?: "_")
        }
}

private fun TaskMessageAttachment.mergeForTaskTimeline(
    other: TaskMessageAttachment,
): TaskMessageAttachment {
    val mergedInternalUriString = internalUriString ?: other.internalUriString

    return copy(
        id = mergedInternalUriString ?: id,
        displayName = displayName.takeIf { it.isNotBlank() } ?: other.displayName,
        contentType = contentType ?: other.contentType,
        sizeBytes = sizeBytes ?: other.sizeBytes,
        isInline = isInline || other.isInline,
        isImage = isImage || other.isImage,
        contentId = contentId ?: other.contentId,
        internalUriString = mergedInternalUriString,
        accountUuid = accountUuid ?: other.accountUuid,
        folderId = folderId ?: other.folderId,
        messageServerId = messageServerId ?: other.messageServerId,
        partId = partId ?: other.partId,
        isContentAvailable = isContentAvailable || other.isContentAvailable,
    )
}

private fun Part.extractContentId(): String? {
    return getHeader(MimeHeader.HEADER_CONTENT_ID)
        .firstOrNull()
        ?.trim()
        ?.removePrefix("<")
        ?.removeSuffix(">")
        ?.takeIf { it.isNotBlank() }
}

private fun AttachmentViewInfo.fallbackAttachmentId(contentId: String?): String {
    return buildString {
        append(displayName.ifBlank { "attachment" })
        contentId?.takeIf { it.isNotBlank() }?.let { value ->
            append('#')
            append(value)
        }
    }
}
