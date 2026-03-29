package net.thunderbird.feature.taskmail.internal.domain.repository

import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSessionActionSendRecord
import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailSessionActionTarget

internal interface TaskMailSessionActionSendRecordRepository {
    suspend fun getLatestRecord(target: TaskMailSessionActionTarget): TaskMailSessionActionSendRecord?

    suspend fun saveRecord(record: TaskMailSessionActionSendRecord)
}
