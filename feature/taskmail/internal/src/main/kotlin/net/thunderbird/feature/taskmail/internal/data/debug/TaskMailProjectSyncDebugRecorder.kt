package net.thunderbird.feature.taskmail.internal.data.debug

import java.io.File
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import net.thunderbird.core.logging.Logger
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskMailProjectSyncDebugSettingsRepository

private const val TAG = "TaskMailProjectSyncDebugRecorder"
private const val DEBUG_LOG_FILE_NAME = "project-sync-debug.log"
private const val MAX_LOG_FILE_SIZE_BYTES = 512_000L

internal interface TaskMailProjectSyncDebugRecorder {
    fun record(
        event: String,
        vararg details: Pair<String, Any?>,
    )
}

internal object NoOpTaskMailProjectSyncDebugRecorder : TaskMailProjectSyncDebugRecorder {
    override fun record(
        event: String,
        vararg details: Pair<String, Any?>,
    ) = Unit
}

internal class FileBackedTaskMailProjectSyncDebugRecorder(
    private val storageDirectory: File,
    private val settingsRepository: TaskMailProjectSyncDebugSettingsRepository,
    private val logger: Logger,
    private val timestampProvider: () -> String = ::currentTimestamp,
) : TaskMailProjectSyncDebugRecorder {
    private val storageFile = File(storageDirectory, DEBUG_LOG_FILE_NAME)
    private val lock = Any()

    override fun record(
        event: String,
        vararg details: Pair<String, Any?>,
    ) {
        if (!settingsRepository.isFileLoggingEnabled()) {
            return
        }

        val line = buildLogLine(
            event = event,
            details = details,
        )

        synchronized(lock) {
            runCatching {
                storageFile.parentFile?.mkdirs()
                if (storageFile.exists() && storageFile.length() > MAX_LOG_FILE_SIZE_BYTES) {
                    storageFile.writeText("", StandardCharsets.UTF_8)
                }
                storageFile.appendText("$line\r\n", StandardCharsets.UTF_8)
            }.onFailure { error ->
                logger.warn(TAG, error) { "Failed to append project sync debug trace." }
            }
        }
    }

    private fun buildLogLine(
        event: String,
        details: Array<out Pair<String, Any?>>,
    ): String {
        val renderedDetails = details.mapNotNull { (key, value) ->
            value?.toString()
                ?.sanitizeDebugValue()
                ?.let { sanitizedValue -> "$key=$sanitizedValue" }
        }

        return buildString {
            append(timestampProvider())
            append(" | event=")
            append(event.sanitizeDebugValue())
            if (renderedDetails.isNotEmpty()) {
                append(" | ")
                append(renderedDetails.joinToString(" | "))
            }
        }
    }

    private companion object {
        fun currentTimestamp(): String {
            return SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US)
                .format(Date())
        }
    }
}

private fun String.sanitizeDebugValue(): String {
    return replace("\r", " ")
        .replace("\n", " ")
        .replace("|", "/")
        .trim()
}
