package net.thunderbird.feature.taskmail.internal.domain.projectsync

internal sealed interface TaskMailDirectProjectSyncResult {
    data class Accepted(
        val requestId: String,
        val receiptId: String,
        val transportMessageId: String? = null,
    ) : TaskMailDirectProjectSyncResult

    data class FallbackToMail(
        val detailMessage: String? = null,
        val requestId: String? = null,
        val receiptId: String? = null,
        val transportMessageId: String? = null,
    ) : TaskMailDirectProjectSyncResult

    data class Rejected(
        val errorMessage: String,
        val requestId: String? = null,
        val receiptId: String? = null,
        val transportMessageId: String? = null,
    ) : TaskMailDirectProjectSyncResult
}
