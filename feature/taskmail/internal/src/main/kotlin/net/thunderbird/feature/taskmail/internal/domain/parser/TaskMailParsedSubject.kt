package net.thunderbird.feature.taskmail.internal.domain.parser

import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailBackend
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailStatusLabel

internal data class TaskMailParsedSubject(
    val backend: TaskMailBackend?,
    val statusLabel: TaskMailStatusLabel?,
    val sessionIdFromSubject: String?,
    val subjectText: String,
    val isReplyLike: Boolean,
)
