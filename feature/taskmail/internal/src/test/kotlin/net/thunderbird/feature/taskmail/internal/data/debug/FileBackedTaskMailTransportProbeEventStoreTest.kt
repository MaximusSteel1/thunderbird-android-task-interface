package net.thunderbird.feature.taskmail.internal.data.debug

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.test.runTest
import net.thunderbird.core.logging.Logger
import org.junit.Test

class FileBackedTaskMailTransportProbeEventStoreTest {

    @Test
    fun `saveManifest and appendEvent should persist manifest timeline and jsonl`() = runTest {
        val storageDirectory = createTempDirectory().toFile()
        val testSubject = FileBackedTaskMailTransportProbeEventStore(
            storageDirectory = storageDirectory,
            logger = NoOpProbeLogger,
        )
        val manifest = buildTransportProbeManifest(
            input = TaskMailTransportProbeManifestInput(
                probeId = "probe_001",
                scenario = "android_direct_ping_to_vps_to_pc",
                direction = "android_to_pc",
                transportKind = "relay_direct",
                payloadText = "PING relay path",
                createdAt = "2026-03-23T20:00:00.000Z",
                requestId = "req_001",
                packetId = "pkt_001",
            ),
        )

        val artifactPath = testSubject.saveManifest(manifest)
        testSubject.appendEvent(
            TaskMailTransportProbeRecordedEvent(
                probeId = "probe_001",
                eventType = "android_relay_probe_submitted",
                actor = "android_client",
                recordedAt = "2026-03-23T20:00:01.000Z",
                clockSource = "android_wall_clock",
                monotonicMs = 42L,
                summary = "Relay packet accepted.",
                requestId = "req_001",
                packetId = "pkt_001",
                receiptId = "receipt_001",
                relayResultType = "transport_probe_result",
                relayStatus = "completed",
            ),
        )

        val probeDirectory = File(artifactPath)
        val manifestContent = File(probeDirectory, "manifest.json").readText()
        val eventsContent = File(probeDirectory, "events.jsonl").readText()
        val timelineContent = File(probeDirectory, "timeline.md").readText()

        assertThat(manifestContent).contains("\"probeId\":\"probe_001\"")
        assertThat(manifestContent).contains("\"payloadTextSha256\"")
        assertThat(eventsContent).contains("\"eventType\":\"android_relay_probe_submitted\"")
        assertThat(eventsContent).contains("\"receiptId\":\"receipt_001\"")
        assertThat(timelineContent).contains("# Transport Probe probe_001")
        assertThat(timelineContent).contains("request_id=req_001")
        assertThat(timelineContent).contains("relay_result_type=transport_probe_result")
        assertThat(timelineContent).contains("relay_status=completed")
        assertThat(testSubject.artifactDirectoryPath("probe_001")).isEqualTo(probeDirectory.absolutePath)
    }
}

private object NoOpProbeLogger : Logger {
    override fun verbose(tag: String?, throwable: Throwable?, message: () -> String) = Unit
    override fun debug(tag: String?, throwable: Throwable?, message: () -> String) = Unit
    override fun info(tag: String?, throwable: Throwable?, message: () -> String) = Unit
    override fun warn(tag: String?, throwable: Throwable?, message: () -> String) = Unit
    override fun error(tag: String?, throwable: Throwable?, message: () -> String) = Unit
}
