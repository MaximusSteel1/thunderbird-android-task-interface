package net.thunderbird.feature.taskmail.internal.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.annotation.WorkerThread
import androidx.core.content.FileProvider
import app.k9mail.legacy.message.controller.SimpleMessagingListener
import com.fsck.k9.controller.MessagingController
import com.fsck.k9.helper.MimeTypeUtil
import com.fsck.k9.helper.MimeTypeUtil.DEFAULT_ATTACHMENT_MIME_TYPE
import com.fsck.k9.mail.Message
import com.fsck.k9.mail.Multipart
import com.fsck.k9.mail.Part
import com.fsck.k9.mailstore.LocalMessage
import com.fsck.k9.mailstore.LocalPart
import com.fsck.k9.mailstore.LocalStoreProvider
import com.fsck.k9.provider.AttachmentTempFileProvider
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import net.thunderbird.core.android.account.LegacyAccountDto
import net.thunderbird.core.android.account.LegacyAccountDtoManager
import net.thunderbird.core.logging.Logger
import net.thunderbird.feature.taskmail.internal.data.relay.RelayFileSurfaceClient
import net.thunderbird.feature.taskmail.internal.domain.model.LegacyMailAttachmentTarget
import net.thunderbird.feature.taskmail.internal.domain.model.RelayArtifactAttachmentTarget
import net.thunderbird.feature.taskmail.internal.domain.model.TaskAttachmentActionTarget
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskTransportConfigRepository

private const val TAG = "TaskMailTimelineAttachments"
private const val TEMP_DIRECTORY_NAME = "temp"
private const val TEMPFILE_PROVIDER_AUTHORITY_SUFFIX = ".tempfileprovider"

internal class DefaultTaskMailTimelineAttachmentHandler(
    private val context: Context,
    private val accountManager: LegacyAccountDtoManager,
    private val localStoreProvider: LocalStoreProvider,
    private val messagingController: MessagingController,
    private val fileSurfaceClient: RelayFileSurfaceClient,
    private val transportConfigRepository: TaskTransportConfigRepository,
    private val logger: Logger,
) : TaskMailTimelineAttachmentHandler {

    override suspend fun createOpenIntent(attachment: TaskAttachmentActionTarget): Result<Intent> = withContext(
        Dispatchers.IO,
    ) {
        runCatching {
            val prepared = prepareAttachmentForViewing(attachment)
            createBestViewIntent(
                contentUri = prepared.contentUri,
                displayName = attachment.displayName,
                mimeType = prepared.mimeType,
            )
        }.onFailure { error ->
            logger.warn(TAG, error) { "Failed to prepare TaskMail attachment view intent" }
        }
    }

    override suspend fun saveAttachmentTo(
        attachment: TaskAttachmentActionTarget,
        destinationUriString: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            when {
                attachment.legacyMail != null -> {
                    val internalUri = ensureLegacyAttachmentContentAvailable(attachment.legacyMail)
                    copyAttachmentToDestination(
                        inputStreamProvider = {
                            context.contentResolver.openInputStream(internalUri)
                                ?: error("TaskMail attachment content stream is unavailable.")
                        },
                        destinationUri = Uri.parse(destinationUriString),
                    )
                }

                attachment.relayArtifact != null -> {
                    val downloadedFile = ensureRelayArtifactTempFile(
                        attachment = attachment,
                        relayArtifact = attachment.relayArtifact,
                    )
                    copyAttachmentToDestination(
                        inputStreamProvider = downloadedFile::inputStream,
                        destinationUri = Uri.parse(destinationUriString),
                    )
                }

                else -> error("TaskMail attachment source is unavailable.")
            }
        }.onFailure { error ->
            logger.warn(TAG, error) { "Failed to save TaskMail attachment" }
        }
    }

    private suspend fun prepareAttachmentForViewing(attachment: TaskAttachmentActionTarget): PreparedAttachment {
        return when {
            attachment.legacyMail != null -> {
                val internalUri = ensureLegacyAttachmentContentAvailable(attachment.legacyMail)
                PreparedAttachment(
                    contentUri = AttachmentTempFileProvider.createTempUriForContentUri(
                        context,
                        internalUri,
                        attachment.displayName,
                    ),
                    mimeType = attachment.contentType ?: DEFAULT_ATTACHMENT_MIME_TYPE,
                )
            }

            attachment.relayArtifact != null -> {
                val tempFile = ensureRelayArtifactTempFile(
                    attachment = attachment,
                    relayArtifact = attachment.relayArtifact,
                )
                PreparedAttachment(
                    contentUri = FileProvider.getUriForFile(
                        context,
                        context.packageName + TEMPFILE_PROVIDER_AUTHORITY_SUFFIX,
                        tempFile,
                        attachment.displayName,
                    ),
                    mimeType = attachment.contentType
                        ?.takeIf(String::isNotBlank)
                        ?: attachment.relayArtifact.contentType
                        ?.takeIf(String::isNotBlank)
                        ?: DEFAULT_ATTACHMENT_MIME_TYPE,
                )
            }

            else -> error("TaskMail attachment source is unavailable.")
        }
    }

    private suspend fun ensureLegacyAttachmentContentAvailable(attachment: LegacyMailAttachmentTarget): Uri {
        val internalUri = attachment.internalUriString
            .takeIf { it.isNotBlank() }
            ?.let(Uri::parse)
            ?: error("TaskMail attachment content is unavailable.")

        if (attachment.isContentAvailable) {
            return internalUri
        }

        val account = attachment.accountUuid
            ?.let(accountManager::getAccount)
            ?: error("TaskMail attachment account is unavailable.")
        val message = loadLocalMessage(attachment, account)
        val partId = attachment.partId ?: error("TaskMail attachment part is unavailable.")
        val part = findAttachmentPart(message, partId)
            ?: error("TaskMail attachment part could not be found.")

        downloadAttachment(
            account = account,
            message = message,
            part = part,
        )

        return internalUri
    }

    private fun loadLocalMessage(
        attachment: LegacyMailAttachmentTarget,
        account: LegacyAccountDto,
    ): LocalMessage {
        val folderId = attachment.folderId ?: error("TaskMail attachment folder is unavailable.")
        val messageServerId = attachment.messageServerId
            ?.takeIf { it.isNotBlank() }
            ?: error("TaskMail attachment message is unavailable.")
        val localStore = localStoreProvider.getInstance(account)
        val localFolder = localStore.getFolder(folderId)

        return localFolder.getMessage(messageServerId)
    }

    private suspend fun downloadAttachment(
        account: LegacyAccountDto,
        message: LocalMessage,
        part: Part,
    ) {
        suspendCancellableCoroutine { continuation ->
            messagingController.loadAttachment(
                account,
                message,
                part,
                object : SimpleMessagingListener() {
                    override fun loadAttachmentFinished(
                        account: LegacyAccountDto,
                        message: Message,
                        part: Part,
                    ) {
                        if (continuation.isActive) {
                            continuation.resume(Unit)
                        }
                    }

                    override fun loadAttachmentFailed(
                        account: LegacyAccountDto,
                        message: Message,
                        part: Part,
                        reason: String,
                    ) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(
                                IOException(reason.ifBlank { "TaskMail attachment download failed." }),
                            )
                        }
                    }
                },
            )
        }
    }

    private suspend fun ensureRelayArtifactTempFile(
        attachment: TaskAttachmentActionTarget,
        relayArtifact: RelayArtifactAttachmentTarget,
    ): File {
        val cacheKey = relayArtifact.fileId
            ?.takeIf(String::isNotBlank)
            ?: attachment.attachmentId.takeIf(String::isNotBlank)
            ?: sha1(
                relayArtifact.contentUrl
                    ?: relayArtifact.metadataUrl
                    ?: relayArtifact.url
                    ?: attachment.displayName,
            )
        val extension = attachment.displayName.substringAfterLast('.', "")
            .takeIf(String::isNotBlank)
            ?.let { ".$it" }
            .orEmpty()
        val tempFile = File(context.cacheDir, "$TEMP_DIRECTORY_NAME/$cacheKey$extension")
        if (tempFile.exists()) {
            return tempFile
        }

        tempFile.parentFile?.mkdirs()
        val download = downloadRelayArtifact(
            attachment = attachment,
            relayArtifact = relayArtifact,
        )
        tempFile.writeBytes(download.bytes)
        return tempFile
    }

    private suspend fun downloadRelayArtifact(
        attachment: TaskAttachmentActionTarget,
        relayArtifact: RelayArtifactAttachmentTarget,
    ): DownloadedRelayArtifact {
        val transportConfig = transportConfigRepository.getRelayTransportConfig().normalized()
        if (!transportConfig.isConfigured()) {
            error("TaskMail VPS file access is unavailable.")
        }

        val metadataEnvelope = when {
            relayArtifact.kind.equals("vps_file", ignoreCase = true) &&
                !relayArtifact.metadataUrl.isNullOrBlank() ->
                fileSurfaceClient.getMetadata(transportConfig, relayArtifact.metadataUrl)

            relayArtifact.kind.equals("vps_file", ignoreCase = true) &&
                !relayArtifact.fileId.isNullOrBlank() ->
                fileSurfaceClient.getMetadata(transportConfig, "/v1/files/${relayArtifact.fileId}")

            else -> null
        }

        val downloadUrl = relayArtifact.contentUrl
            ?.takeIf(String::isNotBlank)
            ?: metadataEnvelope?.artifact?.downloadUrl
            ?: relayArtifact.url?.takeIf(String::isNotBlank)
            ?: relayArtifact.fileId?.takeIf(String::isNotBlank)?.let { "/v1/files/$it/content" }
            ?: error("TaskMail attachment download URL is unavailable.")

        val downloaded = fileSurfaceClient.downloadContent(transportConfig, downloadUrl)
        val normalizedContentType = normalizeMimeType(downloaded.contentType)
            ?: attachment.contentType?.takeIf(String::isNotBlank)
            ?: relayArtifact.contentType?.takeIf(String::isNotBlank)
            ?: metadataEnvelope?.artifact?.mimeType?.takeIf(String::isNotBlank)
            ?: DEFAULT_ATTACHMENT_MIME_TYPE

        return DownloadedRelayArtifact(
            bytes = downloaded.bytes,
            contentType = normalizedContentType,
        )
    }

    private fun copyAttachmentToDestination(
        inputStreamProvider: () -> InputStream,
        destinationUri: Uri,
    ) {
        val contentResolver = context.contentResolver
        inputStreamProvider().use { inputStream ->
            contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
                inputStream.copyTo(outputStream)
            } ?: error("TaskMail attachment destination is unavailable.")
        }
    }

    @WorkerThread
    private fun createBestViewIntent(
        contentUri: Uri,
        displayName: String,
        mimeType: String,
    ): Intent {
        val inferredMimeType = MimeTypeUtil.getMimeTypeByExtension(displayName)

        val resolvedIntent = when {
            MimeTypeUtil.isDefaultMimeType(mimeType) -> getViewIntentForMimeType(contentUri, inferredMimeType)
            else -> {
                val directIntent = getViewIntentForMimeType(contentUri, mimeType)
                if (directIntent.hasResolvedActivities() || inferredMimeType == mimeType) {
                    directIntent
                } else {
                    getViewIntentForMimeType(contentUri, inferredMimeType)
                }
            }
        }

        return when {
            resolvedIntent.hasResolvedActivities() -> resolvedIntent.intent
            else -> getViewIntentForMimeType(contentUri, DEFAULT_ATTACHMENT_MIME_TYPE).intent
        }
    }

    private fun getViewIntentForMimeType(contentUri: Uri, mimeType: String): QueryIntentResult {
        val typedUri = AttachmentTempFileProvider.getMimeTypeUri(contentUri, mimeType)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(typedUri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
        }
        val activityCount = context.packageManager.queryIntentActivities(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY,
        ).size

        return QueryIntentResult(
            intent = intent,
            activityCount = activityCount,
        )
    }

    private fun findAttachmentPart(
        searchRoot: Part,
        partId: Long,
    ): Part? {
        val partStack = ArrayDeque<Part>()
        partStack.addLast(searchRoot)
        var resolvedPart: Part? = null

        while (partStack.isNotEmpty() && resolvedPart == null) {
            val part = partStack.removeLast()
            resolvedPart = when {
                part is LocalMessage && part.messagePartId == partId -> part
                part is LocalPart && part.partId == partId -> part
                else -> null
            }

            val body = part.body
            if (body is Multipart) {
                repeat(body.count) { index ->
                    partStack.addLast(body.getBodyPart(index))
                }
            }
            if (body is Part) {
                partStack.addLast(body)
            }
        }

        return resolvedPart
    }

    private fun sha1(value: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
        return digest.digest(value.toByteArray()).joinToString(separator = "") { byte ->
            "%02x".format(byte)
        }
    }

    private fun normalizeMimeType(value: String?): String? {
        return value
            ?.substringBefore(';')
            ?.trim()
            ?.takeIf(String::isNotEmpty)
    }
}

private data class PreparedAttachment(
    val contentUri: Uri,
    val mimeType: String,
)

private data class DownloadedRelayArtifact(
    val bytes: ByteArray,
    val contentType: String,
)

private data class QueryIntentResult(
    val intent: Intent,
    val activityCount: Int,
) {
    fun hasResolvedActivities(): Boolean = activityCount > 0
}
