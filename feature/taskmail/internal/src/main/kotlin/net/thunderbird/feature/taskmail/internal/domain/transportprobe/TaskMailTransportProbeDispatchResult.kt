package net.thunderbird.feature.taskmail.internal.domain.transportprobe

internal enum class TaskMailTransportProbeDispatchStatus {
    AcceptedAwaitingResult,
    ResultCompleted,
    ResultFailed,
    Rejected,
    Failed,
}

internal data class TaskMailTransportProbeDispatchResult(
    val probeId: String,
    val status: TaskMailTransportProbeDispatchStatus,
    val requestId: String,
    val packetId: String,
    val artifactDirectoryPath: String,
    val receiptId: String? = null,
    val resultId: String? = null,
    val resultType: String? = null,
    val resultStatus: String? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
)
