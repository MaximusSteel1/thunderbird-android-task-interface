package net.thunderbird.feature.taskmail.internal.domain.usecase

import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailDirectAcceptedEvidence

internal sealed interface TaskMailDirectAttemptResult<out T> {
    data class Accepted<T>(
        val payload: T,
        val acceptedEvidence: TaskMailDirectAcceptedEvidence = TaskMailDirectAcceptedEvidence(),
    ) : TaskMailDirectAttemptResult<T>

    data class FallbackToMail(
        val detailMessage: String? = null,
        val requestId: String? = null,
        val receiptId: String? = null,
        val transportMessageId: String? = null,
    ) : TaskMailDirectAttemptResult<Nothing>

    data class Rejected(
        val errorMessage: String,
        val requestId: String? = null,
        val receiptId: String? = null,
        val transportMessageId: String? = null,
    ) : TaskMailDirectAttemptResult<Nothing>
}
