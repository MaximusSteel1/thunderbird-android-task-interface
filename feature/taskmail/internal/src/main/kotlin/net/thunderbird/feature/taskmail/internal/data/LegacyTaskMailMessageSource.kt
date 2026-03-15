package net.thunderbird.feature.taskmail.internal.data

import app.k9mail.legacy.mailstore.MessageDetailsAccessor
import app.k9mail.legacy.mailstore.MessageListRepository
import com.fsck.k9.mailstore.LocalStoreProvider
import com.fsck.k9.mailstore.MessageColumns
import com.fsck.k9.message.SimpleMessageFormat
import com.fsck.k9.message.extractors.BodyTextExtractor
import net.thunderbird.core.android.account.LegacyAccountDto
import net.thunderbird.core.android.account.LegacyAccountDtoManager
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailBackend
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailStatusLabel
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskMailEnvelope
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskMailMessageDetector

internal class LegacyTaskMailMessageSource(
    private val accountManager: LegacyAccountDtoManager,
    private val messageListRepository: MessageListRepository,
    private val localStoreProvider: LocalStoreProvider,
    private val attachmentMetadataExtractor: LegacyTaskMailAttachmentMetadataExtractor,
    private val messageDetector: TaskMailMessageDetector = TaskMailMessageDetector(),
) : TaskMailMessageSource {

    override suspend fun getMessages(): List<TaskMailMessage> {
        return accountManager.getAccounts()
            .asSequence()
            .filter(LegacyAccountDto::isFinishedSetup)
            .flatMap { account ->
                loadTaskMailMessages(account).asSequence()
            }
            .sortedBy(TaskMailMessage::timestamp)
            .toList()
    }

    private fun loadTaskMailMessages(account: LegacyAccountDto): List<TaskMailMessage> {
        val candidateThreads = messageListRepository.getThreadedMessages(
            accountUuid = account.uuid,
            selection = selection,
            selectionArgs = selectionArgs,
            sortOrder = candidateSortOrder,
            messageMapper = ::toCandidate,
        )

        return candidateThreads
            .asSequence()
            .map(TaskMailCandidate::threadRootId)
            .distinct()
            .flatMap { threadRootId ->
                loadThreadMessages(account, threadRootId).asSequence()
            }
            .filter { it.detection.isTaskMail }
            .toList()
    }

    private fun loadThreadMessages(account: LegacyAccountDto, threadRootId: Long): List<TaskMailMessage> {
        val localStore = localStoreProvider.getInstance(account)

        return messageListRepository.getThread(
            accountUuid = account.uuid,
            threadId = threadRootId,
            sortOrder = threadSortOrder,
            messageMapper = { message ->
                val localMessage = runCatching {
                    val localFolder = localStore.getFolder(message.folderId)
                    localFolder.getMessage(message.messageServerId)
                }.getOrNull()
                val bodyText = localMessage
                    ?.let { loadedMessage ->
                        runCatching {
                            BodyTextExtractor.getBodyTextFromMessage(loadedMessage, SimpleMessageFormat.TEXT)
                        }.getOrDefault("")
                    }
                    .orEmpty()
                val rawBodyText = bodyText.takeIf { it.isNotBlank() }
                    ?: message.preview.takeIf { it.isPreviewTextAvailable }?.previewText
                        .orEmpty()
                val attachments = localMessage
                    ?.let { loadedMessage ->
                        runCatching {
                            attachmentMetadataExtractor.extract(loadedMessage)
                        }.getOrDefault(emptyList())
                    }
                    .orEmpty()

                val detection = messageDetector.detect(
                    TaskMailEnvelope(
                        messageId = "${message.folderId}:${message.messageServerId}",
                        subject = message.subject.orEmpty(),
                        fromAddress = message.fromAddresses.firstOrNull()?.address.orEmpty(),
                        timestamp = message.messageDate.takeUnless { it <= 0 } ?: message.internalDate,
                        plainTextBody = rawBodyText,
                    ),
                )

                TaskMailMessage(
                    accountUuid = account.uuid,
                    folderId = message.folderId,
                    messageServerId = message.messageServerId,
                    threadRootId = message.threadRoot,
                    timestamp = message.messageDate.takeUnless { it <= 0 } ?: message.internalDate,
                    subject = message.subject.orEmpty(),
                    rawBodyText = rawBodyText,
                    attachments = attachments,
                    detection = detection,
                    isFromCurrentUser = message.fromAddresses.any { address ->
                        address.address.equals(account.email, ignoreCase = true)
                    },
                )
            },
        )
    }

    private fun toCandidate(message: MessageDetailsAccessor): TaskMailCandidate {
        return TaskMailCandidate(
            threadRootId = message.threadRoot,
        )
    }

    private data class TaskMailCandidate(
        val threadRootId: Long,
    )

    private companion object {
        private val subjectTokens = buildList {
            addAll(TaskMailBackend.entries.map { it.subjectPrefix })
            addAll(TaskMailStatusLabel.entries.map { it.subjectToken })
            add("[S:")
        }

        val selection = subjectTokens.joinToString(" OR ") { "${MessageColumns.SUBJECT} LIKE ?" }
        val selectionArgs = subjectTokens.map { "%$it%" }.toTypedArray()
        val candidateSortOrder = "${MessageColumns.DATE} DESC, ${MessageColumns.ID} DESC"
        val threadSortOrder = "${MessageColumns.DATE} ASC, ${MessageColumns.ID} ASC"
    }
}
