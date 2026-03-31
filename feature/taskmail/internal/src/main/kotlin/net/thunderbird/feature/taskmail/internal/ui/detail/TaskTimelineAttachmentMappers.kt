package net.thunderbird.feature.taskmail.internal.ui.detail

import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionHistorySnapshotAttachment

internal fun TaskSessionHistorySnapshotAttachment.toTimelineAttachmentUi(): TaskTimelineAttachmentUi {
    return TaskTimelineAttachmentUi(
        id = attachmentId,
        displayName = displayName,
        contentType = contentType,
        sizeBytes = sizeBytes,
        isImage = isImage,
        actionTarget = actionTarget,
    )
}
