package net.thunderbird.feature.taskmail.internal.ui.history

import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionHistorySnapshotAttachment
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionHistorySnapshotRound
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionProcessItem
import net.thunderbird.feature.taskmail.internal.ui.detail.TaskTimelineAttachmentUi
import net.thunderbird.feature.taskmail.internal.ui.detail.TaskTimelineItemUi
import net.thunderbird.feature.taskmail.internal.ui.detail.toTimelineAttachmentUi
import net.thunderbird.feature.taskmail.internal.ui.detail.toProcessSectionUi

internal object TaskSessionHistorySnapshotRoundMapper {
    fun merge(
        snapshotRounds: ImmutableList<TaskSessionHistorySnapshotRound>,
        localRounds: ImmutableList<TaskSessionHistoryRoundUi>,
        fallbackSpeakerLabel: String,
    ): ImmutableList<TaskSessionHistoryRoundUi> {
        if (snapshotRounds.isEmpty()) return localRounds

        val localRoundsByNumber = localRounds.associateBy(TaskSessionHistoryRoundUi::roundNumber)
        return snapshotRounds
            .map { snapshotRound ->
                snapshotRound.toUiRound(
                    localFallback = localRoundsByNumber[snapshotRound.roundNumber],
                    fallbackSpeakerLabel = fallbackSpeakerLabel,
                )
            }
            .toImmutableList()
    }
}

private fun TaskSessionHistorySnapshotRound.toUiRound(
    localFallback: TaskSessionHistoryRoundUi?,
    fallbackSpeakerLabel: String,
): TaskSessionHistoryRoundUi {
    val inputAttachmentItems = preferredAttachmentItems(
        snapshotAttachments = inputAttachments,
        localAttachments = localFallback?.inputAttachments ?: persistentListOf(),
    )
    val resultAttachmentItems = preferredAttachmentItems(
        snapshotAttachments = resultAttachments,
        localAttachments = localFallback?.resultAttachments ?: persistentListOf(),
    )
    val processSection = if (processItems.isNotEmpty()) {
        processItems.toProcessSectionUi(
            title = "Process",
            supportingText = processSupportingText(status),
            defaultExpanded = status.isRunningLikeStatus(),
        )
    } else {
        localFallback?.processSection
    }
    val previewAttachments = (inputAttachmentItems + resultAttachmentItems)
        .distinctBy(TaskTimelineAttachmentUi::id)
        .take(3)
        .map(TaskTimelineAttachmentUi::toPreviewUi)
        .toImmutableList()
    val totalAttachmentCount = (inputAttachmentItems + resultAttachmentItems)
        .distinctBy(TaskTimelineAttachmentUi::id)
        .size

    val effectiveInputText = inputText
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?: localFallback?.inputText
    val effectiveResultText = resultText.trim()
        .takeIf(String::isNotBlank)
        ?: localFallback?.resultText
        ?: "No stable result was captured for this round."

    return TaskSessionHistoryRoundUi(
        id = roundId,
        roundNumber = roundNumber,
        timestampLabel = createdAt.toTimestampLabel(localFallback?.timestampLabel),
        statusLabel = status.toStatusLabel(localFallback?.statusLabel),
        inputPreview = effectiveInputText ?: "No preserved input is available for this round.",
        resultPreview = effectiveResultText.take(160),
        inputText = effectiveInputText,
        resultText = effectiveResultText,
        processSection = processSection,
        inputAttachments = inputAttachmentItems,
        resultAttachments = resultAttachmentItems,
        previewAttachments = previewAttachments,
        totalAttachmentCount = totalAttachmentCount,
        speakerLabel = speakerLabel.ifBlank { localFallback?.speakerLabel ?: fallbackSpeakerLabel },
    )
}

private fun preferredAttachmentItems(
    snapshotAttachments: ImmutableList<TaskSessionHistorySnapshotAttachment>,
    localAttachments: ImmutableList<TaskTimelineAttachmentUi>,
): ImmutableList<TaskTimelineAttachmentUi> {
    return if (localAttachments.isNotEmpty()) {
        localAttachments
    } else {
        snapshotAttachments
            .map(TaskSessionHistorySnapshotAttachment::toTimelineAttachmentUi)
            .toImmutableList()
    }
}

private fun TaskTimelineAttachmentUi.toPreviewUi(): TaskSessionHistoryAttachmentPreviewUi {
    return TaskSessionHistoryAttachmentPreviewUi(
        attachmentId = id,
        label = previewLabel(),
        isActionAvailable = isActionAvailable,
    )
}

private fun TaskTimelineAttachmentUi.previewLabel(): String {
    if (isImage) return "IMG"

    val extension = displayName
        .substringAfterLast('.', "")
        .takeIf(String::isNotBlank)
        ?.uppercase(Locale.getDefault())
        ?.take(4)
    val contentTypeLabel = contentType
        ?.substringAfterLast('/')
        ?.takeIf(String::isNotBlank)
        ?.uppercase(Locale.getDefault())
        ?.take(4)

    return extension ?: contentTypeLabel ?: "FILE"
}

private fun String.toTimestampLabel(fallback: String?): String {
    val epochMillis = toEpochMillis()
    if (epochMillis <= 0L) return fallback ?: "Unknown time"
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(epochMillis))
}

private fun String.toEpochMillis(): Long {
    return runCatching {
        OffsetDateTime.parse(this).toInstant().toEpochMilli()
    }.recoverCatching {
        LocalDateTime.parse(this, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }.getOrDefault(0L)
}

private fun String.toStatusLabel(fallback: String? = null): String {
    return when (trim().lowercase(Locale.getDefault())) {
        "queued" -> "Queued"
        "running" -> "Running"
        "waiting_user", "awaiting_user_input" -> "WaitingUser"
        "paused" -> "Paused"
        "done", "success" -> "Done"
        "failed" -> "Failed"
        "killed" -> "Killed"
        else -> fallback ?: trim().ifBlank { "Unknown" }
    }
}

private fun processSupportingText(status: String): String {
    return if (status.isRunningLikeStatus()) {
        "Assistant output is shown in order while this round is still running."
    } else {
        "Open the preserved assistant process behind this round when you need more detail."
    }
}

private fun String.isRunningLikeStatus(): Boolean {
    return trim().lowercase(Locale.getDefault()) in setOf("queued", "running")
}
