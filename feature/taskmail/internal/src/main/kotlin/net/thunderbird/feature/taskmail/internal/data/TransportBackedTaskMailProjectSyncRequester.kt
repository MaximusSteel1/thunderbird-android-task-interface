package net.thunderbird.feature.taskmail.internal.data

import net.thunderbird.feature.taskmail.internal.data.debug.NoOpTaskMailProjectSyncDebugRecorder
import net.thunderbird.feature.taskmail.internal.data.debug.TaskMailProjectSyncDebugRecorder
import net.thunderbird.feature.taskmail.internal.data.relay.RelayBootstrapManager
import net.thunderbird.feature.taskmail.internal.data.transport.TaskMailNewTaskTransport
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailDirectAcceptedEvidence
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailDirectOutcome
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailDirectSendEvidence
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailDirectSwitchGate
import net.thunderbird.feature.taskmail.internal.domain.model.RelayBootstrapResult
import net.thunderbird.feature.taskmail.internal.domain.model.RelayBootstrapStatus
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskRequest
import net.thunderbird.feature.taskmail.internal.domain.projectsync.TaskMailDirectProjectSyncResult
import net.thunderbird.feature.taskmail.internal.domain.projectsync.TaskMailDirectProjectSyncSender
import net.thunderbird.feature.taskmail.internal.domain.usecase.TaskMailDirectAttemptResult

private const val SYNC_SUBJECT = "[SYNC]"
private const val PROJECT_SYNC_REQUEST_FAILURE = "Failed to send TaskMail project sync request."
private const val DEFAULT_DIRECT_REJECTION_MESSAGE = "Relay rejected direct TaskMail project sync request."

internal class TransportBackedTaskMailProjectSyncRequester(
    private val transport: TaskMailNewTaskTransport,
    private val directProjectSyncSender: TaskMailDirectProjectSyncSender,
    private val relayBootstrapManager: RelayBootstrapManager,
    private val debugRecorder: TaskMailProjectSyncDebugRecorder = NoOpTaskMailProjectSyncDebugRecorder,
) : TaskMailProjectSyncRequester {
    override suspend fun requestSync(accountUuid: String): Result<Unit> {
        debugRecorder.record(
            event = "project_sync_request_started",
            "trigger" to "primary_sync_button",
        )

        val bootstrapResult = runDirectBootstrapAttempt()
        if (bootstrapResult.status != RelayBootstrapStatus.HelloAck) {
            return sendMailFallback(
                accountUuid = accountUuid,
                bootstrapStatus = bootstrapResult.status,
                fallbackReason = bootstrapResult.detailMessage,
                trigger = "bootstrap_fallback",
            )
        }

        val directResult = try {
            runCatching {
                directProjectSyncSender.send(accountUuid).toDirectAttemptResult()
            }.getOrElse { error ->
                TaskMailDirectAttemptResult.FallbackToMail(error.message)
            }
        } finally {
            runCatching { relayBootstrapManager.disconnect() }
        }

        return when (directResult) {
            is TaskMailDirectAttemptResult.Accepted -> {
                val evidence = TaskMailDirectSendEvidence(
                    bootstrapStatus = bootstrapResult.status,
                    outcome = TaskMailDirectOutcome.DirectAccepted,
                    switchGate = TaskMailDirectSwitchGate.KeepDirectDefault,
                    requestId = directResult.acceptedEvidence.requestId,
                    receiptId = directResult.acceptedEvidence.receiptId,
                    transportMessageId = directResult.acceptedEvidence.transportMessageId,
                )
                debugRecorder.recordTaskMailDirectOutcome(
                    event = "project_sync_direct_accepted",
                    evidence = evidence,
                )
                Result.success(Unit)
            }

            is TaskMailDirectAttemptResult.FallbackToMail -> sendMailFallback(
                accountUuid = accountUuid,
                bootstrapStatus = bootstrapResult.status,
                fallbackReason = directResult.detailMessage,
                requestId = directResult.requestId,
                receiptId = directResult.receiptId,
                transportMessageId = directResult.transportMessageId,
                trigger = "direct_fallback",
            )

            is TaskMailDirectAttemptResult.Rejected -> {
                val evidence = TaskMailDirectSendEvidence(
                    bootstrapStatus = bootstrapResult.status,
                    outcome = TaskMailDirectOutcome.DirectRejected,
                    switchGate = TaskMailDirectSwitchGate.SwitchBlocker,
                    requestId = directResult.requestId,
                    receiptId = directResult.receiptId,
                    transportMessageId = directResult.transportMessageId,
                    errorMessage = directResult.errorMessage,
                )
                debugRecorder.recordTaskMailDirectOutcome(
                    event = "project_sync_direct_rejected",
                    evidence = evidence,
                )
                Result.failure(
                    IllegalStateException(
                        directResult.errorMessage.ifBlank { DEFAULT_DIRECT_REJECTION_MESSAGE },
                    ),
                )
            }
        }
    }

    override suspend fun requestSyncViaMail(accountUuid: String): Result<Unit> {
        debugRecorder.record(
            event = "project_sync_manual_mail_retry_started",
            "trigger" to "manual_mail_retry",
        )
        return sendCanonicalSyncMail(
            accountUuid = accountUuid,
            trigger = "manual_mail_retry",
        )
    }

    private suspend fun sendCanonicalSyncMail(
        accountUuid: String,
        trigger: String,
    ): Result<Unit> {
        debugRecorder.record(
            event = "project_sync_canonical_mail_send_started",
            "trigger" to trigger,
        )

        return transport.send(
            TaskMailNewTaskRequest(
                accountUuid = accountUuid,
                subject = SYNC_SUBJECT,
                body = "",
            ),
        ).onSuccess {
            debugRecorder.record(
                event = "project_sync_canonical_mail_send_succeeded",
                "trigger" to trigger,
            )
        }.onFailure { error ->
            debugRecorder.record(
                event = "project_sync_canonical_mail_send_failed",
                "trigger" to trigger,
                "error" to error.message,
            )
        }
    }

    private suspend fun runDirectBootstrapAttempt(): RelayBootstrapResult {
        return runCatching {
            relayBootstrapManager.bootstrapSavedConfig()
        }.getOrElse { error ->
            RelayBootstrapResult(
                status = RelayBootstrapStatus.ConnectFailure,
                detailMessage = error.message,
            )
        }
    }

    private suspend fun sendMailFallback(
        accountUuid: String,
        bootstrapStatus: RelayBootstrapStatus,
        fallbackReason: String?,
        requestId: String? = null,
        receiptId: String? = null,
        transportMessageId: String? = null,
        trigger: String,
    ): Result<Unit> {
        val evidence = sendCanonicalSyncMail(
            accountUuid = accountUuid,
            trigger = trigger,
        ).fold(
            onSuccess = {
                TaskMailDirectSendEvidence(
                    bootstrapStatus = bootstrapStatus,
                    outcome = TaskMailDirectOutcome.MailFallbackSucceeded,
                    switchGate = TaskMailDirectSwitchGate.FallbackRequired,
                    requestId = requestId,
                    receiptId = receiptId,
                    transportMessageId = transportMessageId,
                    fallbackReason = fallbackReason,
                )
            },
            onFailure = { error ->
                TaskMailDirectSendEvidence(
                    bootstrapStatus = bootstrapStatus,
                    outcome = TaskMailDirectOutcome.MailFallbackFailed,
                    switchGate = TaskMailDirectSwitchGate.FallbackRequired,
                    requestId = requestId,
                    receiptId = receiptId,
                    transportMessageId = transportMessageId,
                    fallbackReason = fallbackReason,
                    errorMessage = error.message,
                )
            },
        )

        return when (evidence.outcome) {
            TaskMailDirectOutcome.MailFallbackSucceeded -> {
                debugRecorder.recordTaskMailDirectOutcome(
                    event = "project_sync_mail_fallback_succeeded",
                    evidence = evidence,
                )
                Result.success(Unit)
            }

            TaskMailDirectOutcome.MailFallbackFailed -> {
                debugRecorder.recordTaskMailDirectOutcome(
                    event = "project_sync_mail_fallback_failed",
                    evidence = evidence,
                )
                Result.failure(
                    IllegalStateException(evidence.errorMessage ?: PROJECT_SYNC_REQUEST_FAILURE),
                )
            }

            else -> Result.failure(IllegalStateException(PROJECT_SYNC_REQUEST_FAILURE))
        }
    }
}

private fun TaskMailDirectProjectSyncResult.toDirectAttemptResult():
    TaskMailDirectAttemptResult<TaskMailDirectProjectSyncResult.Accepted> {
    return when (this) {
        is TaskMailDirectProjectSyncResult.Accepted -> {
            TaskMailDirectAttemptResult.Accepted(
                payload = this,
                acceptedEvidence = TaskMailDirectAcceptedEvidence(
                    requestId = requestId,
                    receiptId = receiptId,
                    transportMessageId = transportMessageId,
                ),
            )
        }

        is TaskMailDirectProjectSyncResult.FallbackToMail -> {
            TaskMailDirectAttemptResult.FallbackToMail(
                detailMessage = detailMessage,
                requestId = requestId,
                receiptId = receiptId,
                transportMessageId = transportMessageId,
            )
        }

        is TaskMailDirectProjectSyncResult.Rejected -> {
            TaskMailDirectAttemptResult.Rejected(
                errorMessage = errorMessage,
                requestId = requestId,
                receiptId = receiptId,
                transportMessageId = transportMessageId,
            )
        }
    }
}

private fun TaskMailProjectSyncDebugRecorder.recordTaskMailDirectOutcome(
    event: String,
    evidence: net.thunderbird.feature.taskmail.internal.domain.model.TaskMailDirectSendEvidence,
) {
    record(
        event = event,
        "bootstrapStatus" to evidence.bootstrapStatus,
        "outcome" to evidence.outcome,
        "switchGate" to evidence.switchGate,
        "requestId" to evidence.requestId,
        "receiptId" to evidence.receiptId,
        "transportMessageId" to evidence.transportMessageId,
        "fallbackReason" to evidence.fallbackReason,
        "errorMessage" to evidence.errorMessage,
    )
}
