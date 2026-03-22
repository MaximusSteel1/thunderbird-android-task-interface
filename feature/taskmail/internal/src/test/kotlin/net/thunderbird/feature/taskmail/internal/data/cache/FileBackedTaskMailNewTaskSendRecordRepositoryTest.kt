package net.thunderbird.feature.taskmail.internal.data.cache

import assertk.assertThat
import assertk.assertions.isEqualTo
import java.nio.file.Files
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import net.thunderbird.feature.taskmail.internal.domain.model.RelayBootstrapStatus
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailBackend
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailDirectOutcome
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailDirectSendEvidence
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailDirectSwitchGate
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailNewTaskSendRecord

class FileBackedTaskMailNewTaskSendRecordRepositoryTest {

    @Test
    fun `save should persist and reload the latest record per sender account`() = runTest {
        val tempDirectory = Files.createTempDirectory("taskmail-new-task-send-record-repo").toFile()
        val repository = FileBackedTaskMailNewTaskSendRecordRepository(
            storageDirectory = tempDirectory,
        )

        val olderRecord = TaskMailNewTaskSendRecord(
            recordedAt = 100L,
            senderAccountId = "account-1",
            backend = TaskMailBackend.Codex,
            repoPath = "E:/projects/android_task_manager",
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
        val otherAccountRecord = TaskMailNewTaskSendRecord(
            recordedAt = 150L,
            senderAccountId = "account-2",
            backend = TaskMailBackend.OpenCode,
            repoPath = "E:/projects/mail_based_task_manager",
            workdir = "docs",
            evidence = TaskMailDirectSendEvidence(
                bootstrapStatus = RelayBootstrapStatus.NotConfigured,
                outcome = TaskMailDirectOutcome.MailFallbackSucceeded,
                switchGate = TaskMailDirectSwitchGate.FallbackRequired,
                fallbackReason = "not_configured",
            ),
        )

        repository.saveRecord(olderRecord)
        repository.saveRecord(otherAccountRecord)
        repository.saveRecord(newerRecord)

        val reloadedRepository = FileBackedTaskMailNewTaskSendRecordRepository(
            storageDirectory = tempDirectory,
        )

        assertThat(reloadedRepository.getLatestRecord("account-1")).isEqualTo(newerRecord)
        assertThat(reloadedRepository.getLatestRecord("account-2")).isEqualTo(otherAccountRecord)
    }
}
