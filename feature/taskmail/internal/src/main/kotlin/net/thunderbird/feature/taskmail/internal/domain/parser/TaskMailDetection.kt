package net.thunderbird.feature.taskmail.internal.domain.parser

internal data class TaskMailDetection(
    val isTaskMail: Boolean,
    val isSystemMessage: Boolean,
    val parsedSubject: TaskMailParsedSubject,
    val stateCapsule: TaskStateCapsule? = null,
    val questionCapsule: TaskQuestionCapsule? = null,
    val questionCapsules: List<TaskQuestionCapsule> = questionCapsule?.let { listOf(it) } ?: emptyList(),
)
