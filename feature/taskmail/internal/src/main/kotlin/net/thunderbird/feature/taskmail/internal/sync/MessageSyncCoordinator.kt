package net.thunderbird.feature.taskmail.internal.sync

import net.thunderbird.feature.taskmail.internal.data.ingress.DEFAULT_TASKMAIL_RECENT_MESSAGE_LIMIT

internal const val DEFAULT_MESSAGE_SYNC_SOURCE = "email"
internal const val DEFAULT_MESSAGE_SYNC_SCOPE_KEY = "global"
internal const val DEFAULT_TASKMAIL_BOOTSTRAP_MESSAGE_LIMIT = Int.MAX_VALUE

internal enum class MessageSyncMode {
    Incremental,
    Bootstrap,
    Recovery,
}

internal data class MessageSyncRequest(
    val source: String = DEFAULT_MESSAGE_SYNC_SOURCE,
    val scopeKey: String = DEFAULT_MESSAGE_SYNC_SCOPE_KEY,
    val recentMessageLimit: Int = DEFAULT_TASKMAIL_RECENT_MESSAGE_LIMIT,
    val mode: MessageSyncMode = MessageSyncMode.Incremental,
)

internal interface MessageSyncCoordinator {
    suspend fun sync(request: MessageSyncRequest = MessageSyncRequest()): Result<Unit>
}
