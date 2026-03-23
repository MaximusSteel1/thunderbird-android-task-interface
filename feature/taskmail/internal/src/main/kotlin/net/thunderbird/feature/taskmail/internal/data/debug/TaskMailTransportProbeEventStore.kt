package net.thunderbird.feature.taskmail.internal.data.debug

import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.thunderbird.core.logging.Logger

private const val TAG = "TaskMailTransportProbeStore"
private const val MANIFEST_FILE_NAME = "manifest.json"
private const val EVENTS_FILE_NAME = "events.jsonl"
private const val TIMELINE_JSON_FILE_NAME = "timeline.json"
private const val TIMELINE_MARKDOWN_FILE_NAME = "timeline.md"

internal interface TaskMailTransportProbeEventStore {
    fun saveManifest(manifest: TaskMailTransportProbeManifest): String
    suspend fun appendEvent(event: TaskMailTransportProbeRecordedEvent)
    fun artifactDirectoryPath(probeId: String): String
}

@Serializable
internal data class TaskMailTransportProbeManifest(
    val schemaVersion: Int = 1,
    val probeVersion: String = "taskmail-transport-probe-payload-v1",
    val probeId: String,
    val scenario: String,
    val direction: String,
    val transportKind: String,
    val payloadTextSha256: String,
    val payloadTextLength: Int,
    val createdAt: String,
    val requestId: String,
    val packetId: String,
    val receiptId: String? = null,
    val resultId: String? = null,
)

@Serializable
internal data class TaskMailTransportProbeRecordedEvent(
    val probeId: String,
    val eventType: String,
    val actor: String,
    val recordedAt: String,
    val clockSource: String,
    val monotonicMs: Long,
    val summary: String,
    val requestId: String? = null,
    val packetId: String? = null,
    val receiptId: String? = null,
    val resultId: String? = null,
    val relayResultType: String? = null,
    val relayStatus: String? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
)

internal data class TaskMailTransportProbeManifestInput(
    val probeId: String,
    val scenario: String,
    val direction: String,
    val transportKind: String,
    val payloadText: String,
    val createdAt: String,
    val requestId: String,
    val packetId: String,
)

internal class FileBackedTaskMailTransportProbeEventStore(
    private val storageDirectory: File,
    private val logger: Logger,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
) : TaskMailTransportProbeEventStore {
    private val writeMutex = Mutex()

    override fun saveManifest(manifest: TaskMailTransportProbeManifest): String {
        val probeDirectory = probeDirectory(manifest.probeId)
        runCatching {
            probeDirectory.mkdirs()
            writeAtomically(
                targetFile = File(probeDirectory, MANIFEST_FILE_NAME),
                content = json.encodeToString(manifest),
            )
        }.onFailure { error ->
            logger.warn(TAG, error) { "Failed to persist transport probe manifest." }
        }

        return probeDirectory.absolutePath
    }

    override suspend fun appendEvent(event: TaskMailTransportProbeRecordedEvent) {
        writeMutex.withLock {
            val probeDirectory = probeDirectory(event.probeId)
            val existingEvents = loadEvents(probeDirectory) + event
            runCatching {
                probeDirectory.mkdirs()
                writeAtomically(
                    targetFile = File(probeDirectory, EVENTS_FILE_NAME),
                    content = existingEvents.joinToString(separator = "\n") { recordedEvent ->
                        json.encodeToString(recordedEvent)
                    },
                )
                writeAtomically(
                    targetFile = File(probeDirectory, TIMELINE_JSON_FILE_NAME),
                    content = json.encodeToString(existingEvents),
                )
                writeAtomically(
                    targetFile = File(probeDirectory, TIMELINE_MARKDOWN_FILE_NAME),
                    content = existingEvents.toTimelineMarkdown(event.probeId),
                )
            }.onFailure { error ->
                logger.warn(TAG, error) { "Failed to persist transport probe event." }
            }
        }
    }

    override fun artifactDirectoryPath(probeId: String): String {
        return probeDirectory(probeId).absolutePath
    }

    private fun loadEvents(probeDirectory: File): List<TaskMailTransportProbeRecordedEvent> {
        val eventsFile = File(probeDirectory, EVENTS_FILE_NAME)
        if (!eventsFile.exists()) return emptyList()

        return eventsFile.readLines(StandardCharsets.UTF_8)
            .mapNotNull { line ->
                line.takeIf(String::isNotBlank)
                    ?.let { payload ->
                        runCatching {
                            json.decodeFromString<TaskMailTransportProbeRecordedEvent>(payload)
                        }.getOrNull()
                    }
            }
    }

    private fun probeDirectory(probeId: String): File {
        return File(storageDirectory, probeId)
    }

    private fun writeAtomically(
        targetFile: File,
        content: String,
    ) {
        targetFile.parentFile?.mkdirs()
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
        tempFile.writeText(content, StandardCharsets.UTF_8)

        if (targetFile.exists() && !targetFile.delete()) {
            throw IOException("Unable to replace existing file: ${targetFile.absolutePath}")
        }

        if (!tempFile.renameTo(targetFile)) {
            tempFile.copyTo(targetFile, overwrite = true)
            if (!tempFile.delete()) {
                tempFile.deleteOnExit()
            }
        }
    }
}

internal fun buildTransportProbeManifest(
    input: TaskMailTransportProbeManifestInput,
    receiptId: String? = null,
    resultId: String? = null,
): TaskMailTransportProbeManifest {
    return TaskMailTransportProbeManifest(
        probeId = input.probeId,
        scenario = input.scenario,
        direction = input.direction,
        transportKind = input.transportKind,
        payloadTextSha256 = input.payloadText.sha256Hex(),
        payloadTextLength = input.payloadText.length,
        createdAt = input.createdAt,
        requestId = input.requestId,
        packetId = input.packetId,
        receiptId = receiptId,
        resultId = resultId,
    )
}

internal fun currentTransportProbeTimestamp(): String {
    return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
    }.format(Date())
}

private fun String.sha256Hex(): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(StandardCharsets.UTF_8))

    return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
}

private fun List<TaskMailTransportProbeRecordedEvent>.toTimelineMarkdown(
    probeId: String,
): String {
    return buildString {
        appendLine("# Transport Probe $probeId")
        appendLine()
        this@toTimelineMarkdown.forEach { event ->
            append("- ")
            append(event.recordedAt)
            append(" | ")
            append(event.actor)
            append(" | ")
            append(event.eventType)
            append(" | ")
            append(event.summary.sanitizeTimelineValue())
            event.requestId?.takeIf(String::isNotBlank)?.let { requestId ->
                append(" | request_id=")
                append(requestId.sanitizeTimelineValue())
            }
            event.packetId?.takeIf(String::isNotBlank)?.let { packetId ->
                append(" | packet_id=")
                append(packetId.sanitizeTimelineValue())
            }
            event.receiptId?.takeIf(String::isNotBlank)?.let { receiptId ->
                append(" | receipt_id=")
                append(receiptId.sanitizeTimelineValue())
            }
            event.resultId?.takeIf(String::isNotBlank)?.let { resultId ->
                append(" | result_id=")
                append(resultId.sanitizeTimelineValue())
            }
            event.errorCode?.takeIf(String::isNotBlank)?.let { errorCode ->
                append(" | error_code=")
                append(errorCode.sanitizeTimelineValue())
            }
            appendLine()
        }
    }
}

private fun String.sanitizeTimelineValue(): String {
    return replace("\r", " ")
        .replace("\n", " ")
        .replace("|", "/")
        .trim()
}
