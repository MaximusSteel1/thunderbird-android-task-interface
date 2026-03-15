package net.thunderbird.feature.taskmail.internal.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.fsck.k9.message.Attachment
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.thunderbird.core.logging.Logger
import net.thunderbird.feature.taskmail.internal.domain.model.TaskReplyAttachment

private const val TAG = "TaskMailReplyAttachments"
private const val ATTACHMENT_CACHE_DIRECTORY = "taskmail-reply-attachments"
private const val ATTACHMENT_FILE_PREFIX = "taskmail_reply_"

internal class LegacyTaskMailReplyAttachmentResolver(
    private val context: Context,
    private val logger: Logger,
) : TaskMailReplyAttachmentResolver {

    override suspend fun resolveSelectedAttachments(uriStrings: List<String>): List<TaskReplyAttachment> = withContext(
        Dispatchers.IO,
    ) {
        uriStrings.distinct()
            .map(::resolveSelectedAttachment)
    }

    override suspend fun buildOutgoingAttachments(
        attachments: List<TaskReplyAttachment>,
    ): Result<List<Attachment>> = withContext(Dispatchers.IO) {
        runCatching {
            attachments.map(::buildOutgoingAttachment)
        }.onFailure { error ->
            logger.warn(TAG, error) { "Failed to prepare TaskMail reply attachment payloads" }
        }
    }

    private fun resolveSelectedAttachment(uriString: String): TaskReplyAttachment {
        val uri = Uri.parse(uriString)
        val metadata = runCatching { queryAttachmentMetadata(uri) }
            .onFailure { error ->
                logger.warn(TAG, error) { "Failed to query TaskMail reply attachment metadata" }
            }
            .getOrDefault(AttachmentMetadata())

        val displayName = metadata.displayName
            ?.takeIf { it.isNotBlank() }
            ?: fallbackDisplayName(uri)
        val contentType = metadata.contentType
            ?: guessMimeType(displayName)

        return TaskReplyAttachment(
            id = uriString,
            uriString = uriString,
            displayName = displayName,
            contentType = contentType,
            sizeBytes = metadata.sizeBytes,
            isImage = isImageAttachment(
                contentType = contentType,
                displayName = displayName,
            ),
        )
    }

    private fun buildOutgoingAttachment(attachment: TaskReplyAttachment): Attachment {
        val uri = Uri.parse(attachment.uriString)
        val targetDirectory = File(context.cacheDir, ATTACHMENT_CACHE_DIRECTORY).apply { mkdirs() }
        val temporaryFile = File.createTempFile(
            ATTACHMENT_FILE_PREFIX,
            attachment.displayName.toTempFileSuffix(),
            targetDirectory,
        )

        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            FileOutputStream(temporaryFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        } ?: error("TaskMail reply attachment stream unavailable")

        return TaskMailPreparedAttachment(
            fileName = temporaryFile.absolutePath,
            contentType = attachment.contentType ?: guessMimeType(attachment.displayName),
            name = attachment.displayName,
            size = attachment.sizeBytes ?: temporaryFile.length(),
        )
    }

    private fun queryAttachmentMetadata(uri: Uri): AttachmentMetadata {
        val contentResolver = context.contentResolver
        var displayName: String? = null
        var sizeBytes: Long? = null

        contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val displayNameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (displayNameColumn >= 0 && !cursor.isNull(displayNameColumn)) {
                    displayName = cursor.getString(displayNameColumn)
                }

                val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) {
                    sizeBytes = cursor.getLong(sizeColumn)
                }
            }
        }

        return AttachmentMetadata(
            displayName = displayName,
            sizeBytes = sizeBytes,
            contentType = contentResolver.getType(uri),
        )
    }

    private fun fallbackDisplayName(uri: Uri): String {
        return uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() }
            ?: "attachment"
    }

    private fun guessMimeType(displayName: String): String? {
        val extension = displayName.substringAfterLast('.', missingDelimiterValue = "")
            .lowercase()
            .takeIf { it.isNotBlank() }
            ?: return null

        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    }

    private fun isImageAttachment(
        contentType: String?,
        displayName: String,
    ): Boolean {
        return contentType?.startsWith("image/") == true ||
            guessMimeType(displayName)?.startsWith("image/") == true
    }
}

private data class AttachmentMetadata(
    val displayName: String? = null,
    val sizeBytes: Long? = null,
    val contentType: String? = null,
)

private data class TaskMailPreparedAttachment(
    override val fileName: String?,
    override val contentType: String?,
    override val name: String?,
    override val size: Long?,
    override val state: Attachment.LoadingState = Attachment.LoadingState.COMPLETE,
    override val isInternalAttachment: Boolean = false,
) : Attachment

private fun String.toTempFileSuffix(): String {
    val extension = substringAfterLast('.', missingDelimiterValue = "")
        .takeIf { it.isNotBlank() }
        ?: return ".tmp"

    return ".${extension.take(MAX_TEMP_FILE_EXTENSION_LENGTH)}"
}

private const val MAX_TEMP_FILE_EXTENSION_LENGTH = 16
