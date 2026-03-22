package net.thunderbird.feature.taskmail.internal.domain.repository

import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailNewTaskSendRecord

internal interface TaskMailNewTaskSendRecordRepository {
    suspend fun getLatestRecord(senderAccountId: String): TaskMailNewTaskSendRecord?

    suspend fun saveRecord(record: TaskMailNewTaskSendRecord)
}
