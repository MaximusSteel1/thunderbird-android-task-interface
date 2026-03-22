package net.thunderbird.feature.taskmail.internal.domain.sessionaction

internal sealed interface TaskMailDirectSessionActionResult {
    data class Accepted(
        val actionType: TaskMailDirectSessionActionType,
        val requestId: String,
        val receiptId: String,
        val transportMessageId: String? = null,
    ) : TaskMailDirectSessionActionResult

    data class FallbackToMail(
        val detailMessage: String? = null,
    ) : TaskMailDirectSessionActionResult

    data class Rejected(
        val errorMessage: String,
    ) : TaskMailDirectSessionActionResult
}
