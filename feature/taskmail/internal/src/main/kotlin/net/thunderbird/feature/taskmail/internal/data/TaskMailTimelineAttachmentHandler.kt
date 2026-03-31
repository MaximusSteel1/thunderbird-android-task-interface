package net.thunderbird.feature.taskmail.internal.data

import android.content.Intent
import net.thunderbird.feature.taskmail.internal.domain.model.TaskAttachmentActionTarget

internal interface TaskMailTimelineAttachmentHandler {
    suspend fun createOpenIntent(attachment: TaskAttachmentActionTarget): Result<Intent>

    suspend fun saveAttachmentTo(
        attachment: TaskAttachmentActionTarget,
        destinationUriString: String,
    ): Result<Unit>
}
