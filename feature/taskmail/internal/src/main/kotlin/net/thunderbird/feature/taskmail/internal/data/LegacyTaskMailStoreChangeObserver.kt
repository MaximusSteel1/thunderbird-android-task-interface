package net.thunderbird.feature.taskmail.internal.data

import app.k9mail.legacy.mailstore.MessageListChangedListener
import app.k9mail.legacy.mailstore.MessageListRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

internal class LegacyTaskMailStoreChangeObserver(
    private val messageListRepository: MessageListRepository,
) : TaskMailStoreChangeObserver {
    override fun changes(): Flow<Unit> = callbackFlow {
        val listener = MessageListChangedListener {
            trySend(Unit)
        }

        messageListRepository.addListener(listener)

        awaitClose {
            messageListRepository.removeListener(listener)
        }
    }
}
