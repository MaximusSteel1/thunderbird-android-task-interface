package net.thunderbird.feature.taskmail.internal.domain.usecase

import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailDirectSessionActionRequest
import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailDirectSessionActionResult
import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailDirectSessionActionSender

internal class SendTaskMailDirectSessionAction(
    private val directSessionActionSender: TaskMailDirectSessionActionSender,
) {
    suspend operator fun invoke(
        request: TaskMailDirectSessionActionRequest,
    ): TaskMailDirectSessionActionResult {
        return directSessionActionSender.send(request)
    }
}

internal typealias SendTaskMailSessionAction = SendTaskMailDirectSessionAction
