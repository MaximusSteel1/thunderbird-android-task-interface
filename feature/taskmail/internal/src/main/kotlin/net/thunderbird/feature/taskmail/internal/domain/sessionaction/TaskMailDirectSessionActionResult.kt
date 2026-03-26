package net.thunderbird.feature.taskmail.internal.domain.sessionaction

import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionControlPlaneSnapshot

internal sealed interface TaskMailDirectSessionActionResult {
    data class Accepted(
        val actionType: TaskMailDirectSessionActionType,
        val requestId: String,
        val receiptId: String,
        val transportMessageId: String? = null,
        val controlPlaneSnapshot: TaskSessionControlPlaneSnapshot? = null,
    ) : TaskMailDirectSessionActionResult

    data class FallbackToMail(
        val detailMessage: String? = null,
        val requestId: String? = null,
        val receiptId: String? = null,
        val transportMessageId: String? = null,
    ) : TaskMailDirectSessionActionResult

    data class Rejected(
        val errorMessage: String,
        val requestId: String? = null,
        val receiptId: String? = null,
        val transportMessageId: String? = null,
    ) : TaskMailDirectSessionActionResult
}
