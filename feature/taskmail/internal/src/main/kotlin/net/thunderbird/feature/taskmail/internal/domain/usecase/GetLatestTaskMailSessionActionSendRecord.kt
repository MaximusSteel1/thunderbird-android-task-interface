package net.thunderbird.feature.taskmail.internal.domain.usecase

import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSessionActionSendRecord
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskMailSessionActionSendRecordRepository
import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailDirectSessionActionTarget

internal class GetLatestTaskMailSessionActionSendRecord(
    private val repository: TaskMailSessionActionSendRecordRepository,
) {
    suspend operator fun invoke(
        target: TaskMailDirectSessionActionTarget,
    ): TaskMailSessionActionSendRecord? {
        return repository.getLatestRecord(target)
    }
}
