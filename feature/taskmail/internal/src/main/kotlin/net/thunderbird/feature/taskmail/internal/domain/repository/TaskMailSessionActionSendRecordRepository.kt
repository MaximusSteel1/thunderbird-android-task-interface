package net.thunderbird.feature.taskmail.internal.domain.repository

import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSessionActionSendRecord
import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailDirectSessionActionTarget

internal interface TaskMailSessionActionSendRecordRepository {
    suspend fun getLatestRecord(target: TaskMailDirectSessionActionTarget): TaskMailSessionActionSendRecord?

    suspend fun saveRecord(record: TaskMailSessionActionSendRecord)
}
