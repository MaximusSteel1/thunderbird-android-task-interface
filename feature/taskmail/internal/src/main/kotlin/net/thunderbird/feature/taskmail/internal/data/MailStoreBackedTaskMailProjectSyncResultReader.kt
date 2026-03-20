package net.thunderbird.feature.taskmail.internal.data

import app.k9mail.legacy.mailstore.MessageDetailsAccessor
import com.fsck.k9.mail.FetchProfile
import com.fsck.k9.mailstore.LocalStoreProvider
import com.fsck.k9.mailstore.MessageColumns
import com.fsck.k9.message.SimpleMessageFormat
import com.fsck.k9.message.extractors.BodyTextExtractor
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailProjectSyncResult
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskMailProjectSyncResultParser

private const val SYNC_SUBJECT = "[SYNC]"

internal class MailStoreBackedTaskMailProjectSyncResultReader(
    private val senderAccountSource: TaskMailSenderAccountSource,
    private val messageListRepository: app.k9mail.legacy.mailstore.MessageListRepository,
    private val localStoreProvider: LocalStoreProvider,
    private val parser: TaskMailProjectSyncResultParser = TaskMailProjectSyncResultParser(),
) : TaskMailProjectSyncResultReader {

    override suspend fun getLatestResult(accountUuid: String): TaskMailProjectSyncResult? {
        val account = senderAccountSource.getAccount(accountUuid) ?: return null
        val localStore = localStoreProvider.getInstance(account)
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
                loadThreadSyncResults(
                    account = account,
                    localStore = localStore,
                    threadRootId = threadRootId,
                ).asSequence()
            }
            .maxByOrNull(TaskMailProjectSyncResult::receivedAt)
    }

    private fun loadThreadSyncResults(
        account: net.thunderbird.core.android.account.LegacyAccountDto,
        localStore: com.fsck.k9.mailstore.LocalStore,
        threadRootId: Long,
    ): List<TaskMailProjectSyncResult> {
        return messageListRepository.getThread(
            accountUuid = account.uuid,
            threadId = threadRootId,
            sortOrder = threadSortOrder,
            messageMapper = { message ->
                val localMessage = loadLocalMessage(
                    message = message,
                    localStore = localStore,
                )
                val bodyText = localMessage
                    ?.let { loadedMessage ->
                        runCatching {
                            BodyTextExtractor.getBodyTextFromMessage(loadedMessage, SimpleMessageFormat.TEXT)
                        }.getOrDefault("")
                    }
                    .orEmpty()
                val isFromCurrentUser = message.fromAddresses.any { address ->
                    address.address.equals(account.email, ignoreCase = true)
                }
                if (bodyText.isBlank() || isFromCurrentUser) {
                    null
                } else {
                    parser.parse(
                        bodyText = bodyText,
                        receivedAt = message.messageDate.takeUnless { it <= 0 } ?: message.internalDate,
                    )
                }
            },
        ).filterNotNull()
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

    private data class TaskMailCandidate(
        val threadRootId: Long,
    )
}

private val selection = "${MessageColumns.SUBJECT} LIKE ?"
private val selectionArgs = arrayOf("%$SYNC_SUBJECT%")
private val candidateSortOrder = "${MessageColumns.DATE} DESC, ${MessageColumns.ID} DESC"
private val threadSortOrder = "${MessageColumns.DATE} DESC, ${MessageColumns.ID} DESC"
