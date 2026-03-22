package net.thunderbird.feature.taskmail.internal.domain.model

internal data class TaskMailDirectSendEvidence(
    val bootstrapStatus: RelayBootstrapStatus,
    val outcome: TaskMailDirectOutcome,
    val switchGate: TaskMailDirectSwitchGate,
    val requestId: String? = null,
    val receiptId: String? = null,
    val transportMessageId: String? = null,
    val fallbackReason: String? = null,
    val errorMessage: String? = null,
)

internal enum class TaskMailDirectOutcome {
    DirectAccepted,
    MailFallbackSucceeded,
    MailFallbackFailed,
    DirectRejected,
}

internal enum class TaskMailDirectSwitchGate {
    KeepDirectDefault,
    FallbackRequired,
    SwitchBlocker,
}

internal data class TaskMailDirectAcceptedEvidence(
    val requestId: String? = null,
    val receiptId: String? = null,
    val transportMessageId: String? = null,
)
