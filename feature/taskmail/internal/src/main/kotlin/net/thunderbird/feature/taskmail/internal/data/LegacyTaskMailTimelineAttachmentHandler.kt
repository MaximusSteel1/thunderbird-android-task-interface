package net.thunderbird.feature.taskmail.internal.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.annotation.WorkerThread
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
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import net.thunderbird.core.android.account.LegacyAccountDto
import net.thunderbird.core.android.account.LegacyAccountDtoManager
import net.thunderbird.core.logging.Logger
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMessageAttachment

private const val TAG = "TaskMailTimelineAttachments"

internal class LegacyTaskMailTimelineAttachmentHandler(
    private val context: Context,
    private val accountManager: LegacyAccountDtoManager,
    private val localStoreProvider: LocalStoreProvider,
    private val messagingController: MessagingController,
    private val logger: Logger,
) : TaskMailTimelineAttachmentHandler {

    override suspend fun createOpenIntent(attachment: TaskMessageAttachment): Result<Intent> = withContext(
        Dispatchers.IO,
    ) {
        runCatching {
            val internalUri = ensureAttachmentContentAvailable(attachment)
            val tempUri = AttachmentTempFileProvider.createTempUriForContentUri(
                context,
                internalUri,
                attachment.displayName,
            )

            createBestViewIntent(
                contentUri = tempUri,
                displayName = attachment.displayName,
                mimeType = attachment.contentType ?: DEFAULT_ATTACHMENT_MIME_TYPE,
            )
        }.onFailure { error ->
            logger.warn(TAG, error) { "Failed to prepare TaskMail attachment view intent" }
        }
    }

    override suspend fun saveAttachmentTo(
        attachment: TaskMessageAttachment,
        destinationUriString: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val internalUri = ensureAttachmentContentAvailable(attachment)
            copyAttachmentToDestination(
                internalUri = internalUri,
                destinationUri = Uri.parse(destinationUriString),
            )
        }.onFailure { error ->
            logger.warn(TAG, error) { "Failed to save TaskMail attachment" }
        }
    }

    private suspend fun ensureAttachmentContentAvailable(attachment: TaskMessageAttachment): Uri {
        val internalUri = attachment.internalUriString
            ?.takeIf { it.isNotBlank() }
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
        attachment: TaskMessageAttachment,
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

    private fun copyAttachmentToDestination(
        internalUri: Uri,
        destinationUri: Uri,
    ) {
        val contentResolver = context.contentResolver
        contentResolver.openInputStream(internalUri)?.use { inputStream ->
            contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
                inputStream.copyTo(outputStream)
            } ?: error("TaskMail attachment destination is unavailable.")
        } ?: error("TaskMail attachment content stream is unavailable.")
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
}

private data class QueryIntentResult(
    val intent: Intent,
    val activityCount: Int,
) {
    fun hasResolvedActivities(): Boolean = activityCount > 0
}
