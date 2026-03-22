package net.thunderbird.feature.taskmail.internal.domain.usecase

import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailDirectSendEvidence
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSessionActionSendRecord
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskMailSessionActionSendRecordRepository
import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailDirectSessionActionRequest

internal class RecordTaskMailSessionActionSendRecord(
    private val repository: TaskMailSessionActionSendRecordRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend operator fun invoke(
        request: TaskMailDirectSessionActionRequest,
        evidence: TaskMailDirectSendEvidence,
    ): TaskMailSessionActionSendRecord {
        return TaskMailSessionActionSendRecord(
            recordedAt = clock(),
            actionType = request.actionType,
            target = request.target,
            evidence = evidence,
        ).also { record ->
            repository.saveRecord(record)
        }
    }
}
