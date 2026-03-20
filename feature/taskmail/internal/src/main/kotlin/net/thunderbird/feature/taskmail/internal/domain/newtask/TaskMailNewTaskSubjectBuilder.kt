package net.thunderbird.feature.taskmail.internal.domain.newtask

import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailBackend

internal class TaskMailNewTaskSubjectBuilder {

    fun build(
        backend: TaskMailBackend,
        subjectTitle: String,
    ): String {
        return "${backend.subjectPrefix} ${subjectTitle.trim()}".trim()
    }
}
