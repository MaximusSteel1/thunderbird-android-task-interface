package net.thunderbird.feature.taskmail.internal.data

import com.fsck.k9.mail.internet.MimeMessage
import net.thunderbird.core.android.account.LegacyAccountDto
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskRequest

internal interface TaskMailNewTaskMimeMessageFactory {
    suspend fun create(
        request: TaskMailNewTaskRequest,
        account: LegacyAccountDto,
    ): Result<MimeMessage>
}
