package net.thunderbird.feature.taskmail.internal.data.debug

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import java.io.File
import kotlin.io.path.createTempDirectory
import net.thunderbird.core.logging.Logger
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskMailProjectSyncDebugSettingsRepository
import org.junit.Test

class FileBackedTaskMailProjectSyncDebugRecorderTest {

    @Test
    fun `record should append sanitized debug trace line`() {
        val storageDirectory = createTempDirectory().toFile()
        val testSubject = FileBackedTaskMailProjectSyncDebugRecorder(
            storageDirectory = storageDirectory,
            settingsRepository = FakeTaskMailProjectSyncDebugSettingsRepository(isEnabled = true),
            logger = NoOpLogger,
            timestampProvider = { "2026-03-23 19:21:00.000 +0800" },
        )

        testSubject.record(
            event = "project_sync_direct_packet_ack_received",
            "requestId" to "req_123",
            "errorMessage" to "line1\nline2|line3",
        )

        val content = File(storageDirectory, "project-sync-debug.log").readText()

        assertThat(content).contains("2026-03-23 19:21:00.000 +0800")
        assertThat(content).contains("event=project_sync_direct_packet_ack_received")
        assertThat(content).contains("requestId=req_123")
        assertThat(content).contains("errorMessage=line1 line2/line3")
        assertThat(content.endsWith("\r\n")).isEqualTo(true)
    }

    @Test
    fun `record should skip writing when disabled`() {
        val storageDirectory = createTempDirectory().toFile()
        val testSubject = FileBackedTaskMailProjectSyncDebugRecorder(
            storageDirectory = storageDirectory,
            settingsRepository = FakeTaskMailProjectSyncDebugSettingsRepository(isEnabled = false),
            logger = NoOpLogger,
            timestampProvider = { "2026-03-23 19:21:00.000 +0800" },
        )

        testSubject.record(
            event = "project_sync_direct_packet_ack_received",
            "requestId" to "req_123",
        )

        assertThat(File(storageDirectory, "project-sync-debug.log").exists()).isEqualTo(false)
    }
}

private class FakeTaskMailProjectSyncDebugSettingsRepository(
    private val isEnabled: Boolean,
) : TaskMailProjectSyncDebugSettingsRepository {
    override fun isFileLoggingEnabled(): Boolean = isEnabled

    override fun setFileLoggingEnabled(enabled: Boolean): Boolean = true
}

private object NoOpLogger : Logger {
    override fun verbose(
        tag: String?,
        throwable: Throwable?,
        message: () -> String,
    ) = Unit

    override fun debug(
        tag: String?,
        throwable: Throwable?,
        message: () -> String,
    ) = Unit

    override fun info(
        tag: String?,
        throwable: Throwable?,
        message: () -> String,
    ) = Unit

    override fun warn(
        tag: String?,
        throwable: Throwable?,
        message: () -> String,
    ) = Unit

    override fun error(
        tag: String?,
        throwable: Throwable?,
        message: () -> String,
    ) = Unit
}
