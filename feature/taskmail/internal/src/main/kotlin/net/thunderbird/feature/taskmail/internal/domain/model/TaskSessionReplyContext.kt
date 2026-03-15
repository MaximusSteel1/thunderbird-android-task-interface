package net.thunderbird.feature.taskmail.internal.domain.model

internal data class TaskSessionReplyContext(
    val accountUuid: String,
    val folderId: Long,
    val messageServerId: String,
    val threadRootId: Long? = null,
    val anchorTimestamp: Long? = null,
)
