package net.thunderbird.feature.taskmail.internal.ui.history

import androidx.compose.runtime.Immutable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import net.thunderbird.feature.taskmail.internal.ui.detail.TaskProcessSectionUi
import net.thunderbird.feature.taskmail.internal.ui.detail.TaskSessionDetailUiState
import net.thunderbird.feature.taskmail.internal.ui.detail.TaskTimelineAttachmentUi
import net.thunderbird.feature.taskmail.internal.ui.detail.TaskTimelineItemUi
import net.thunderbird.feature.taskmail.internal.ui.detail.toLocalProcessSectionUi

@Immutable
internal data class TaskSessionHistoryRoundUi(
    val id: String,
    val roundNumber: Int,
    val timestampLabel: String,
    val statusLabel: String,
    val inputPreview: String,
    val resultPreview: String,
    val inputText: String?,
    val resultText: String,
    val processSection: TaskProcessSectionUi? = null,
    val inputAttachments: ImmutableList<TaskTimelineAttachmentUi> = persistentListOf(),
    val resultAttachments: ImmutableList<TaskTimelineAttachmentUi> = persistentListOf(),
    val previewAttachments: ImmutableList<TaskSessionHistoryAttachmentPreviewUi> = persistentListOf(),
    val totalAttachmentCount: Int = 0,
    val speakerLabel: String,
)

@Immutable
internal data class TaskSessionHistoryAttachmentPreviewUi(
    val attachmentId: String,
    val label: String,
    val isActionAvailable: Boolean,
)

internal object TaskSessionHistoryRoundProjector {
    fun project(detail: TaskSessionDetailUiState): ImmutableList<TaskSessionHistoryRoundUi> {
        val chronologicalTimeline = detail.timeline.asReversed()
        if (chronologicalTimeline.isEmpty()) return persistentListOf()
        val groupedRounds = chronologicalTimeline.splitIntoRoundGroups()
        val latestRoundIndex = groupedRounds.lastIndex

        return groupedRounds
            .mapIndexed { index, group ->
                group.toHistoryRound(
                    roundNumber = index + 1,
                    speakerLabel = detail.backend,
                    supplementalResultAttachments = if (index == latestRoundIndex) {
                        detail.artifacts
                    } else {
                        persistentListOf()
                    },
                )
            }
            .asReversed()
            .toImmutableList()
    }
}

private fun List<TaskTimelineItemUi>.splitIntoRoundGroups(): List<List<TaskTimelineItemUi>> {
    val groups = mutableListOf<List<TaskTimelineItemUi>>()
    var currentGroup = mutableListOf<TaskTimelineItemUi>()

    forEach { item ->
        if (item.isOutgoing() && currentGroup.isNotEmpty()) {
            groups += currentGroup.toList()
            currentGroup = mutableListOf()
        }

        currentGroup += item
    }

    if (currentGroup.isNotEmpty()) {
        groups += currentGroup.toList()
    }

    return groups
}

private fun List<TaskTimelineItemUi>.toHistoryRound(
    roundNumber: Int,
    speakerLabel: String,
    supplementalResultAttachments: ImmutableList<TaskTimelineAttachmentUi> = persistentListOf(),
): TaskSessionHistoryRoundUi {
    val inputItem = firstOrNull(TaskTimelineItemUi::isOutgoing)
    val itemsAfterInput = inputItem
        ?.let { input -> dropWhile { it.id != input.id }.drop(1) }
        ?: this
    val resultItem = itemsAfterInput.lastOrNull() ?: inputItem ?: last()
    val rawProcessItems = itemsAfterInput
        .dropLast(1)
        .toImmutableList()

    val inputText = inputItem?.normalizedText()
    val resultText = resultItem.normalizedText()
        ?: resultItem.summary?.takeIf(String::isNotBlank)
        ?: "No stable result was captured for this round."
    val inputAttachments = inputItem
        ?.attachments
        ?.distinctBy(TaskTimelineAttachmentUi::id)
        ?.toImmutableList()
        ?: persistentListOf()
    val resultAttachments = itemsAfterInput
        .flatMap(TaskTimelineItemUi::attachments)
        .plus(supplementalResultAttachments)
        .distinctBy(TaskTimelineAttachmentUi::id)
        .toImmutableList()
    val allAttachments = (inputAttachments + resultAttachments)
        .distinctBy(TaskTimelineAttachmentUi::id)
    val previewAttachments = allAttachments
        .take(3)
        .map(TaskTimelineAttachmentUi::toPreviewUi)
        .toImmutableList()

    return TaskSessionHistoryRoundUi(
        id = buildString {
            append("round_")
            append(roundNumber)
            append('_')
            append(resultItem.id)
        },
        roundNumber = roundNumber,
        timestampLabel = formatHistoryTimestamp(resultItem.timestamp),
        statusLabel = resultItem.statusLabel ?: resultItem.direction,
        inputPreview = inputText ?: "No preserved input is available for this round.",
        resultPreview = resultItem.summary
            ?.takeIf(String::isNotBlank)
            ?: resultText,
        inputText = inputText,
        resultText = resultText,
        processSection = rawProcessItems.toLocalProcessSectionUi(
            title = "Process",
            supportingText = processSupportingText(resultItem.statusLabel),
            defaultExpanded = resultItem.statusLabel.isRunningLikeStatus(),
        ),
        inputAttachments = inputAttachments,
        resultAttachments = resultAttachments,
        previewAttachments = previewAttachments,
        totalAttachmentCount = allAttachments.size,
        speakerLabel = speakerLabel,
    )
}

private fun TaskTimelineItemUi.normalizedText(): String? {
    return plainText.trim().takeIf(String::isNotBlank)
}

private fun TaskTimelineItemUi.isOutgoing(): Boolean {
    return direction.equals("Outgoing", ignoreCase = true)
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

private fun formatHistoryTimestamp(timestamp: Long): String {
    if (timestamp <= 0L) return "Unknown time"
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}

private fun processSupportingText(statusLabel: String?): String {
    return if (statusLabel.isRunningLikeStatus()) {
        "Assistant output is shown in order while this round is still running."
    } else {
        "Open the preserved assistant process behind this round when you need more detail."
    }
}

private fun String?.isRunningLikeStatus(): Boolean {
    return this?.trim()?.equals("Running", ignoreCase = true) == true ||
        this?.trim()?.equals("Queued", ignoreCase = true) == true
}
