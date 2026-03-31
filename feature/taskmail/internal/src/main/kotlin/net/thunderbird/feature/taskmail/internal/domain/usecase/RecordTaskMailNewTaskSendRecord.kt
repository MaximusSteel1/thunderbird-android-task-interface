package net.thunderbird.feature.taskmail.internal.domain.usecase

import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailDirectSendEvidence
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailNewTaskSendRecord
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskDraft
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskMailNewTaskSendRecordRepository

internal class RecordTaskMailNewTaskSendRecord(
    private val repository: TaskMailNewTaskSendRecordRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend operator fun invoke(
        draft: TaskMailNewTaskDraft,
        evidence: TaskMailDirectSendEvidence,
    ) {
        val senderAccountId = draft.senderAccountId ?: return
        repository.saveRecord(
            TaskMailNewTaskSendRecord(
                recordedAt = clock(),
                senderAccountId = senderAccountId,
                backend = draft.backend,
                repoPath = draft.repoPath,
                workdir = draft.workdir,
                evidence = evidence,
            ),
        )
    }
}
