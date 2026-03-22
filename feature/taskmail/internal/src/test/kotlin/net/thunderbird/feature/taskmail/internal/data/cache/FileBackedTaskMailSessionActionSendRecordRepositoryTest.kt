package net.thunderbird.feature.taskmail.internal.data.cache

import assertk.assertThat
import assertk.assertions.isEqualTo
import java.nio.file.Files
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import net.thunderbird.feature.taskmail.internal.domain.model.RelayBootstrapStatus
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailDirectOutcome
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailDirectSendEvidence
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailDirectSwitchGate
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSessionActionSendRecord
import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailDirectSessionActionTarget
import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailDirectSessionActionType

class FileBackedTaskMailSessionActionSendRecordRepositoryTest {

    @Test
    fun `save should persist and reload the latest record per canonical session target`() = runTest {
        val tempDirectory = Files.createTempDirectory("taskmail-session-action-send-record-repo").toFile()
        val repository = FileBackedTaskMailSessionActionSendRecordRepository(
            storageDirectory = tempDirectory,
        )

        val target = TaskMailDirectSessionActionTarget(
            workspaceId = "workspace-1",
            sessionId = "thread_101",
            threadId = "thread_101",
        )
        val olderRecord = TaskMailSessionActionSendRecord(
            recordedAt = 100L,
            actionType = TaskMailDirectSessionActionType.Reply,
            target = target,
            evidence = TaskMailDirectSendEvidence(
                bootstrapStatus = RelayBootstrapStatus.HelloAck,
                outcome = TaskMailDirectOutcome.DirectAccepted,
                switchGate = TaskMailDirectSwitchGate.KeepDirectDefault,
                requestId = "req_older",
                receiptId = "receipt-older",
            ),
        )
        val newerRecord = olderRecord.copy(
            recordedAt = 200L,
            evidence = olderRecord.evidence.copy(
                requestId = "req_newer",
                receiptId = "receipt-newer",
            ),
        )
        val otherTargetRecord = TaskMailSessionActionSendRecord(
            recordedAt = 150L,
            actionType = TaskMailDirectSessionActionType.Status,
            target = TaskMailDirectSessionActionTarget(
                workspaceId = "workspace-2",
                sessionId = "thread_202",
            ),
            evidence = TaskMailDirectSendEvidence(
                bootstrapStatus = RelayBootstrapStatus.NotConfigured,
                outcome = TaskMailDirectOutcome.MailFallbackSucceeded,
                switchGate = TaskMailDirectSwitchGate.FallbackRequired,
                fallbackReason = "not_configured",
            ),
        )

        repository.saveRecord(olderRecord)
        repository.saveRecord(otherTargetRecord)
        repository.saveRecord(newerRecord)

        val reloadedRepository = FileBackedTaskMailSessionActionSendRecordRepository(
            storageDirectory = tempDirectory,
        )

        assertThat(reloadedRepository.getLatestRecord(target)).isEqualTo(newerRecord)
        assertThat(
            reloadedRepository.getLatestRecord(
                target.copy(threadId = null),
            ),
        ).isEqualTo(newerRecord)
        assertThat(reloadedRepository.getLatestRecord(otherTargetRecord.target)).isEqualTo(otherTargetRecord)
    }
}
