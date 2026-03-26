package net.thunderbird.feature.taskmail.internal.domain.model

internal fun TaskSessionDetail.lastUpdatedAt(): Long {
    return timeline.lastOrNull()?.timestamp ?: 0L
}
