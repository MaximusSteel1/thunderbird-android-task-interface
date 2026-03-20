package net.thunderbird.feature.taskmail.internal.domain.newtask

internal data class TaskMailNewTaskRequest(
    val accountUuid: String,
    val subject: String,
    val body: String,
)
