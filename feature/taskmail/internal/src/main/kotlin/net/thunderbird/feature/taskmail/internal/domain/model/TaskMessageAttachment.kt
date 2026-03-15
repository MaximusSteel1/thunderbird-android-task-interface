package net.thunderbird.feature.taskmail.internal.domain.model

internal data class TaskMessageAttachment(
    val id: String,
    val displayName: String,
    val contentType: String? = null,
    val sizeBytes: Long? = null,
    val isInline: Boolean = false,
    val isImage: Boolean = false,
    val contentId: String? = null,
    val internalUriString: String? = null,
    val accountUuid: String? = null,
    val folderId: Long? = null,
    val messageServerId: String? = null,
    val partId: Long? = null,
    val isContentAvailable: Boolean = false,
)
