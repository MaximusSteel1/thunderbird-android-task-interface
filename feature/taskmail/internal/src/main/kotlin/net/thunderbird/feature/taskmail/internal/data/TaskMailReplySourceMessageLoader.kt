package net.thunderbird.feature.taskmail.internal.data

import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionReplyContext

internal interface TaskMailReplySourceMessageLoader {
    suspend fun load(context: TaskSessionReplyContext): TaskMailReplySourceMessage?
}
