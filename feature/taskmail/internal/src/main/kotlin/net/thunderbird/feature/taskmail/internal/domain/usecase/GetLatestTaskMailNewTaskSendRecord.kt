package net.thunderbird.feature.taskmail.internal.domain.usecase

import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailNewTaskSendRecord
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskMailNewTaskSendRecordRepository

internal class GetLatestTaskMailNewTaskSendRecord(
    private val repository: TaskMailNewTaskSendRecordRepository,
) {
    suspend operator fun invoke(senderAccountId: String): TaskMailNewTaskSendRecord? {
        return repository.getLatestRecord(senderAccountId)
    }
}
