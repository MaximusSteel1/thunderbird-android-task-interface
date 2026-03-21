package net.thunderbird.feature.taskmail.internal.domain.newtask

internal sealed interface TaskMailDirectNewTaskResult {
    data class Accepted(
        val receiptId: String,
        val transportMessageId: String? = null,
    ) : TaskMailDirectNewTaskResult

    data class FallbackToMail(
        val detailMessage: String? = null,
    ) : TaskMailDirectNewTaskResult

    data class Rejected(
        val errorMessage: String,
    ) : TaskMailDirectNewTaskResult
}
