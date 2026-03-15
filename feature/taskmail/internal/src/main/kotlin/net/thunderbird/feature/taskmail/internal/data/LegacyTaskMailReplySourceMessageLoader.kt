package net.thunderbird.feature.taskmail.internal.data

import app.k9mail.legacy.message.controller.MessageReference
import com.fsck.k9.mailstore.LocalStoreProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.thunderbird.core.android.account.LegacyAccountDtoManager
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionReplyContext

internal class LegacyTaskMailReplySourceMessageLoader(
    private val accountManager: LegacyAccountDtoManager,
    private val localStoreProvider: LocalStoreProvider,
) : TaskMailReplySourceMessageLoader {

    override suspend fun load(context: TaskSessionReplyContext): TaskMailReplySourceMessage? = withContext(
        Dispatchers.IO,
    ) {
        val account = accountManager.getAccount(context.accountUuid) ?: return@withContext null
        val localStore = localStoreProvider.getInstance(account)
        val localFolder = localStore.getFolder(context.folderId)
        val localMessage = runCatching {
            localFolder.getMessage(context.messageServerId)
        }.getOrNull() ?: return@withContext null

        TaskMailReplySourceMessage(
            account = account,
            message = localMessage,
            messageReference = MessageReference(
                context.accountUuid,
                context.folderId,
                context.messageServerId,
            ),
        )
    }
}
