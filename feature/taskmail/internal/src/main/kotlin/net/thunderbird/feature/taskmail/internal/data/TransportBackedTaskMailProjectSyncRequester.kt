package net.thunderbird.feature.taskmail.internal.data

import net.thunderbird.feature.taskmail.internal.data.debug.NoOpTaskMailProjectSyncDebugRecorder
import net.thunderbird.feature.taskmail.internal.data.debug.TaskMailProjectSyncDebugRecorder
import net.thunderbird.feature.taskmail.internal.data.transport.TaskMailNewTaskTransport
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailDirectAcceptedEvidence
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskRequest
import net.thunderbird.feature.taskmail.internal.domain.projectsync.TaskMailDirectProjectSyncResult
import net.thunderbird.feature.taskmail.internal.domain.projectsync.TaskMailDirectProjectSyncSender
import net.thunderbird.feature.taskmail.internal.domain.usecase.RunTaskMailDirectOrFallback
import net.thunderbird.feature.taskmail.internal.domain.usecase.TaskMailDirectAttemptResult
import net.thunderbird.feature.taskmail.internal.domain.usecase.TaskMailDirectOrFallbackResult

private const val SYNC_SUBJECT = "[SYNC]"
private const val PROJECT_SYNC_REQUEST_FAILURE = "Failed to send TaskMail project sync request."
private const val DEFAULT_DIRECT_REJECTION_MESSAGE = "Relay rejected direct TaskMail project sync request."

internal class TransportBackedTaskMailProjectSyncRequester(
    private val transport: TaskMailNewTaskTransport,
    private val directProjectSyncSender: TaskMailDirectProjectSyncSender,
    private val runTaskMailDirectOrFallback: RunTaskMailDirectOrFallback,
    private val debugRecorder: TaskMailProjectSyncDebugRecorder = NoOpTaskMailProjectSyncDebugRecorder,
) : TaskMailProjectSyncRequester {
    override suspend fun requestSync(accountUuid: String): Result<Unit> {
        debugRecorder.record(
            event = "project_sync_request_started",
            "trigger" to "primary_sync_button",
        )

        return when (
            val result = runTaskMailDirectOrFallback.execute(
                directSend = {
                    directProjectSyncSender.send(accountUuid).toDirectAttemptResult()
                },
                mailFallback = {
                    sendCanonicalSyncMail(
                        accountUuid = accountUuid,
                        trigger = "direct_fallback",
                    )
                },
            )
        ) {
            is TaskMailDirectOrFallbackResult.DirectAccepted<*> -> {
                debugRecorder.recordTaskMailDirectOutcome(
                    event = "project_sync_direct_accepted",
                    evidence = result.evidence,
                )
                Result.success(Unit)
            }

            is TaskMailDirectOrFallbackResult.MailFallbackSucceeded -> {
                debugRecorder.recordTaskMailDirectOutcome(
                    event = "project_sync_mail_fallback_succeeded",
                    evidence = result.evidence,
                )
                Result.success(Unit)
            }

            is TaskMailDirectOrFallbackResult.MailFallbackFailed -> {
                debugRecorder.recordTaskMailDirectOutcome(
                    event = "project_sync_mail_fallback_failed",
                    evidence = result.evidence,
                )
                Result.failure(
                    IllegalStateException(result.errorMessage ?: PROJECT_SYNC_REQUEST_FAILURE),
                )
            }

            is TaskMailDirectOrFallbackResult.DirectRejected -> {
                debugRecorder.recordTaskMailDirectOutcome(
                    event = "project_sync_direct_rejected",
                    evidence = result.evidence,
                )
                Result.failure(
                    IllegalStateException(
                        result.errorMessage.ifBlank { DEFAULT_DIRECT_REJECTION_MESSAGE },
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
