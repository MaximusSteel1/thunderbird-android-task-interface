package net.thunderbird.feature.taskmail.internal.domain.model

import androidx.compose.runtime.Immutable

@Immutable
internal data class TaskAttachmentActionTarget(
    val attachmentId: String,
    val displayName: String,
    val contentType: String? = null,
    val legacyMail: LegacyMailAttachmentTarget? = null,
    val relayArtifact: RelayArtifactAttachmentTarget? = null,
) {
    init {
        require((legacyMail != null) xor (relayArtifact != null)) {
            "Exactly one attachment source must be present."
        }
    }
}

@Immutable
internal data class LegacyMailAttachmentTarget(
    val internalUriString: String,
    val accountUuid: String? = null,
    val folderId: Long? = null,
    val messageServerId: String? = null,
    val partId: Long? = null,
    val isContentAvailable: Boolean = false,
)

@Immutable
internal data class RelayArtifactAttachmentTarget(
    val kind: String,
    val fileId: String? = null,
    val metadataUrl: String? = null,
    val contentUrl: String? = null,
    val url: String? = null,
    val contentType: String? = null,
    val encoding: String? = null,
    val data: String? = null,
)

internal fun buildRelayArtifactActionTarget(
    attachmentId: String,
    displayName: String,
    contentType: String? = null,
    kind: String,
    fileId: String? = null,
    metadataUrl: String? = null,
    contentUrl: String? = null,
    url: String? = null,
    relayContentType: String? = null,
    encoding: String? = null,
    data: String? = null,
): TaskAttachmentActionTarget? {
    val normalizedFileId = fileId?.trim()?.takeIf(String::isNotEmpty)
    val normalizedMetadataUrl = metadataUrl?.trim()?.takeIf(String::isNotEmpty)
    val normalizedContentUrl = contentUrl?.trim()?.takeIf(String::isNotEmpty)
    val normalizedUrl = url?.trim()?.takeIf(String::isNotEmpty)
    if (
        normalizedFileId == null &&
        normalizedMetadataUrl == null &&
        normalizedContentUrl == null &&
        normalizedUrl == null
    ) {
        return null
    }

    return TaskAttachmentActionTarget(
        attachmentId = attachmentId,
        displayName = displayName,
        contentType = contentType,
        relayArtifact = RelayArtifactAttachmentTarget(
            kind = kind.trim(),
            fileId = normalizedFileId,
            metadataUrl = normalizedMetadataUrl,
            contentUrl = normalizedContentUrl,
            url = normalizedUrl,
            contentType = relayContentType?.trim()?.takeIf(String::isNotEmpty),
            encoding = encoding?.trim()?.takeIf(String::isNotEmpty),
            data = data?.trim()?.takeIf(String::isNotEmpty),
        ),
    )
}
