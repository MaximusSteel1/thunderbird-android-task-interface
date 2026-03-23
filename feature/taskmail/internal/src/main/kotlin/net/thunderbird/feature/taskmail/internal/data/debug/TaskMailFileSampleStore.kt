package net.thunderbird.feature.taskmail.internal.data.debug

import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.thunderbird.core.logging.Logger

private const val TAG = "TaskMailFileSampleStore"
private const val MANIFEST_FILE_NAME = "manifest.json"

internal interface TaskMailFileSampleStore {
    fun saveManifest(manifest: TaskMailFileSampleManifest): String
    fun writeText(
        sampleId: String,
        fileName: String,
        content: String,
    )

    fun writeBytes(
        sampleId: String,
        fileName: String,
        content: ByteArray,
    )

    fun artifactDirectoryPath(sampleId: String): String
}

@Serializable
internal data class TaskMailFileSampleManifest(
    val schemaVersion: Int = 1,
    val sampleVersion: String = "taskmail-control-artifact-contract-v1",
    val sampleId: String,
    val createdAt: String,
    val traceId: String,
    val artifactId: String,
    val fileName: String,
    val mimeType: String,
    val byteSize: Long,
    val expectedSha256: String,
    val observedSha256: String? = null,
    val fileId: String? = null,
    val storedAt: String? = null,
    val metadataUrl: String? = null,
    val downloadUrl: String? = null,
    val status: String,
    val errorCode: String? = null,
    val errorMessage: String? = null,
)

internal class FileBackedTaskMailFileSampleStore(
    private val storageDirectory: File,
    private val logger: Logger,
    private val json: Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    },
) : TaskMailFileSampleStore {
    override fun saveManifest(manifest: TaskMailFileSampleManifest): String {
        val sampleDirectory = sampleDirectory(manifest.sampleId)
        runCatching {
            sampleDirectory.mkdirs()
            writeTextAtomically(
                targetFile = File(sampleDirectory, MANIFEST_FILE_NAME),
                content = json.encodeToString(manifest),
            )
        }.onFailure { error ->
            logger.warn(TAG, error) { "Failed to persist TaskMail file sample manifest." }
        }

        return sampleDirectory.absolutePath
    }

    override fun writeText(
        sampleId: String,
        fileName: String,
        content: String,
    ) {
        val sampleDirectory = sampleDirectory(sampleId)
        runCatching {
            sampleDirectory.mkdirs()
            writeTextAtomically(
                targetFile = File(sampleDirectory, fileName),
                content = content,
            )
        }.onFailure { error ->
            logger.warn(TAG, error) { "Failed to persist TaskMail file sample text artifact." }
        }
    }

    override fun writeBytes(
        sampleId: String,
        fileName: String,
        content: ByteArray,
    ) {
        val sampleDirectory = sampleDirectory(sampleId)
        runCatching {
            sampleDirectory.mkdirs()
            writeBytesAtomically(
                targetFile = File(sampleDirectory, fileName),
                content = content,
            )
        }.onFailure { error ->
            logger.warn(TAG, error) { "Failed to persist TaskMail file sample binary artifact." }
        }
    }

    override fun artifactDirectoryPath(sampleId: String): String {
        return sampleDirectory(sampleId).absolutePath
    }

    private fun sampleDirectory(sampleId: String): File {
        return File(storageDirectory, sampleId)
    }

    private fun writeTextAtomically(
        targetFile: File,
        content: String,
    ) {
        targetFile.parentFile?.mkdirs()
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
        tempFile.writeText(content, StandardCharsets.UTF_8)
        replaceTargetFile(
            tempFile = tempFile,
            targetFile = targetFile,
        )
    }

    private fun writeBytesAtomically(
        targetFile: File,
        content: ByteArray,
    ) {
        targetFile.parentFile?.mkdirs()
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
        tempFile.writeBytes(content)
        replaceTargetFile(
            tempFile = tempFile,
            targetFile = targetFile,
        )
    }

    private fun replaceTargetFile(
        tempFile: File,
        targetFile: File,
    ) {
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

internal fun currentTaskMailFileSampleTimestamp(): String {
    return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
    }.format(Date())
}
