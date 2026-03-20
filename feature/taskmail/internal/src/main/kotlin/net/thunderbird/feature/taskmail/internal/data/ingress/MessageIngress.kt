package net.thunderbird.feature.taskmail.internal.data.ingress

internal const val DEFAULT_TASKMAIL_RECENT_MESSAGE_LIMIT = 10

internal interface MessageIngress<RawMessage> {
    suspend fun fetchLatest(recentMessageLimit: Int = DEFAULT_TASKMAIL_RECENT_MESSAGE_LIMIT): List<RawMessage>

    suspend fun fetchSince(
        cursor: String,
        recentMessageLimit: Int = DEFAULT_TASKMAIL_RECENT_MESSAGE_LIMIT,
    ): List<RawMessage> {
        return fetchLatest(recentMessageLimit = recentMessageLimit)
    }
}
