package net.thunderbird.feature.taskmail.internal.ui.detail

import net.thunderbird.feature.taskmail.internal.domain.model.TaskTimelineItem

internal fun mergeTimeline(
    mailTimeline: List<TaskTimelineItem>,
    directTimeline: List<TaskTimelineItem>?,
): List<TaskTimelineItem> {
    val visibleDirectTimeline = directTimeline
        ?.takeIf(List<TaskTimelineItem>::isNotEmpty)
        ?.let { items ->
            val mailBusinessEventKeys = mailTimeline
                .asSequence()
                .flatMap { item -> item.businessEventKeys.asSequence() }
                .filter(String::isNotBlank)
                .toSet()

            items.filterNot { directItem ->
                directItem.businessEventKeys.any { businessEventKey -> businessEventKey in mailBusinessEventKeys }
            }
        }
        .orEmpty()

    return if (visibleDirectTimeline.isEmpty()) {
        mailTimeline
    } else {
        (mailTimeline + visibleDirectTimeline)
            .sortedWith(compareBy(TaskTimelineItem::timestamp, TaskTimelineItem::id))
    }
}
