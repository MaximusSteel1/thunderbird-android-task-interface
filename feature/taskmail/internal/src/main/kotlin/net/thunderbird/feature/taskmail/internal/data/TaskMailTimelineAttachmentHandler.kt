package net.thunderbird.feature.taskmail.internal.data

import android.content.Intent
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMessageAttachment

internal interface TaskMailTimelineAttachmentHandler {
    suspend fun createOpenIntent(attachment: TaskMessageAttachment): Result<Intent>

    suspend fun saveAttachmentTo(
        attachment: TaskMessageAttachment,
        destinationUriString: String,
    ): Result<Unit>
}
