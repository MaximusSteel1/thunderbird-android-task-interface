package net.thunderbird.feature.taskmail.internal.data.ingress

import app.k9mail.legacy.mailstore.MessageDetailsAccessor
import app.k9mail.legacy.mailstore.MessageListRepository
import com.fsck.k9.mail.FetchProfile
import com.fsck.k9.mail.internet.MessageExtractor
import com.fsck.k9.mail.internet.MimeUtility
import com.fsck.k9.mailstore.LocalStoreProvider
import com.fsck.k9.mailstore.MessageColumns
import com.fsck.k9.message.SimpleMessageFormat
import com.fsck.k9.message.extractors.BodyTextExtractor
import net.thunderbird.core.android.account.LegacyAccountDto
import net.thunderbird.core.android.account.LegacyAccountDtoManager
import net.thunderbird.feature.taskmail.internal.data.LegacyTaskMailAttachmentMetadataExtractor
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailBackend
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailStatusLabel
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMessageAttachment

internal class EmailIngress(
    private val accountManager: LegacyAccountDtoManager,
    private val messageListRepository: MessageListRepository,
    private val localStoreProvider: LocalStoreProvider,
    private val attachmentMetadataExtractor: LegacyTaskMailAttachmentMetadataExtractor,
) : MessageIngress<EmailIngressMessage> {

    override suspend fun fetchLatest(recentMessageLimit: Int): List<EmailIngressMessage> {
        return accountManager.getAccounts()
            .asSequence()
            .filter(LegacyAccountDto::isFinishedSetup)
            .flatMap { account ->
                loadAccountMessages(
                    account = account,
                    recentMessageLimit = recentMessageLimit,
                ).asSequence()
            }
            .sortedBy(EmailIngressMessage::timestamp)
            .toList()
    }

    override suspend fun fetchSince(
        cursor: String,
        recentMessageLimit: Int,
    ): List<EmailIngressMessage> {
        // Cursor-aware mailbox delta fetch is introduced in M2 with persisted sync state.
        return fetchLatest(recentMessageLimit = recentMessageLimit)
    }

    private fun loadAccountMessages(
        account: LegacyAccountDto,
        recentMessageLimit: Int,
    ): List<EmailIngressMessage> {
        val candidateThreads = messageListRepository.getThreadedMessages(
            accountUuid = account.uuid,
            selection = selection,
            selectionArgs = selectionArgs,
            sortOrder = candidateSortOrder,
            messageMapper = ::toCandidate,
        )

        return candidateThreads
            .asSequence()
            .let { sequence ->
                if (recentMessageLimit == Int.MAX_VALUE) {
                    sequence
                } else {
                    sequence.take(recentMessageLimit)
                }
            }
            .map(TaskMailCandidate::threadRootId)
            .distinct()
            .flatMap { threadRootId ->
                loadThreadMessages(
                    account = account,
                    threadRootId = threadRootId,
                ).asSequence()
            }
            .toList()
    }

    private fun loadThreadMessages(account: LegacyAccountDto, threadRootId: Long): List<EmailIngressMessage> {
        val localStore = localStoreProvider.getInstance(account)

        return messageListRepository.getThread(
            accountUuid = account.uuid,
            threadId = threadRootId,
            sortOrder = threadSortOrder,
            messageMapper = { message ->
                val localMessage = loadLocalMessage(
                    message = message,
                    localStore = localStore,
                )
                val bodies = extractBodies(
                    message = message,
                    localMessage = localMessage,
                )

                EmailIngressMessage(
                    accountUuid = account.uuid,
                    accountEmailAddress = account.email,
                    folderId = message.folderId,
                    messageServerId = message.messageServerId,
                    threadRootId = message.threadRoot,
                    timestamp = message.messageDate.takeUnless { it <= 0 } ?: message.internalDate,
                    subject = message.subject.orEmpty(),
                    fromAddresses = message.fromAddresses.mapNotNull { it.address },
                    rawBodyText = bodies.rawBodyText,
                    htmlBody = bodies.htmlBody,
                    internetMessageId = localMessage?.messageId?.takeIf { it.isNotBlank() },
                    attachments = extractAttachments(localMessage),
                )
            },
        )
    }

    private fun loadLocalMessage(
        message: MessageDetailsAccessor,
        localStore: com.fsck.k9.mailstore.LocalStore,
    ): com.fsck.k9.mailstore.LocalMessage? {
        return runCatching {
            val localFolder = localStore.getFolder(message.folderId)
            val localMessage = localFolder.getMessage(message.messageServerId)
                ?: localFolder.getMessage(message.id)
                ?: return@runCatching null

            val fetchProfile = FetchProfile().apply {
                add(FetchProfile.Item.BODY)
            }
            localFolder.fetch(listOf(localMessage), fetchProfile, null)
            localMessage
        }.getOrNull()
    }

    private fun toCandidate(message: MessageDetailsAccessor): TaskMailCandidate {
        return TaskMailCandidate(
            threadRootId = message.threadRoot,
        )
    }

    private fun extractHtmlBody(localMessage: com.fsck.k9.mailstore.LocalMessage): String? {
        return runCatching {
            val htmlPart = MimeUtility.findFirstPartByMimeType(localMessage, "text/html")
                ?: return@runCatching null

            MessageExtractor.getTextFromPart(htmlPart)
        }.getOrNull()
    }

    private fun extractBodies(
        message: MessageDetailsAccessor,
        localMessage: com.fsck.k9.mailstore.LocalMessage?,
    ): ExtractedBodies {
        val plainTextBody = localMessage
            ?.let { loadedMessage ->
                runCatching {
                    BodyTextExtractor.getBodyTextFromMessage(loadedMessage, SimpleMessageFormat.TEXT)
                }.getOrDefault("")
            }
            .orEmpty()

        return ExtractedBodies(
            rawBodyText = plainTextBody.takeIf { it.isNotBlank() }
                ?: message.preview.takeIf { it.isPreviewTextAvailable }?.previewText.orEmpty(),
            htmlBody = localMessage?.let(::extractHtmlBody)?.takeIf { it.isNotBlank() },
        )
    }

    private fun extractAttachments(localMessage: com.fsck.k9.mailstore.LocalMessage?): List<TaskMessageAttachment> {
        return localMessage
            ?.let { loadedMessage ->
                runCatching {
                    attachmentMetadataExtractor.extract(loadedMessage)
                }.getOrDefault(emptyList())
            }
            .orEmpty()
    }

    private data class TaskMailCandidate(
        val threadRootId: Long,
    )

    private data class ExtractedBodies(
        val rawBodyText: String,
        val htmlBody: String?,
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

internal data class EmailIngressMessage(
    val accountUuid: String,
    val accountEmailAddress: String,
    val folderId: Long,
    val messageServerId: String,
    val threadRootId: Long,
    val timestamp: Long,
    val subject: String,
    val fromAddresses: List<String>,
    val rawBodyText: String,
    val htmlBody: String? = null,
    val internetMessageId: String? = null,
    val attachments: List<TaskMessageAttachment> = emptyList(),
)
