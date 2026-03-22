package net.thunderbird.feature.taskmail.internal.domain.sessionaction

internal interface TaskMailDirectSessionActionSender {
    suspend fun send(request: TaskMailDirectSessionActionRequest): TaskMailDirectSessionActionResult
}
