package net.thunderbird.feature.taskmail.internal.domain.model

internal data class MessageSyncState(
    val source: String,
    val scopeKey: String,
    val lastCursor: String?,
    val lastSyncAt: Long?,
)
