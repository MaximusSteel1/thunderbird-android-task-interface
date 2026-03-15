package net.thunderbird.feature.taskmail.internal.domain.model

internal data class TaskReplyAttachment(
    val id: String,
    val uriString: String,
    val displayName: String,
    val contentType: String? = null,
    val sizeBytes: Long? = null,
    val isImage: Boolean = false,
)
