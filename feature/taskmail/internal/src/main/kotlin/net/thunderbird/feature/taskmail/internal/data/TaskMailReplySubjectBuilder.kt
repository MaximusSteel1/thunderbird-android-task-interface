package net.thunderbird.feature.taskmail.internal.data

import net.thunderbird.feature.taskmail.internal.domain.parser.TaskMailSubjectParser

internal class TaskMailReplySubjectBuilder(
    private val subjectParser: TaskMailSubjectParser = TaskMailSubjectParser(),
) {

    fun build(anchorSubject: String?): String {
        val parsedSubject = subjectParser.parse(anchorSubject.orEmpty())
        val normalizedSubject = buildList {
            parsedSubject.statusLabel?.let { add(it.subjectToken) }
            parsedSubject.sessionIdFromSubject?.let { add("[S:$it]") }
            parsedSubject.backend?.let { add(it.subjectPrefix) }
            parsedSubject.subjectText
                .takeIf { it.isNotBlank() }
                ?.let(::add)
        }.joinToString(separator = " ")
            .ifBlank {
                parsedSubject.subjectText
                    .takeIf { it.isNotBlank() }
                    ?: anchorSubject.orEmpty().trim()
            }

        return if (normalizedSubject.isBlank()) {
            "Re:"
        } else {
            "Re: $normalizedSubject"
        }
    }
}
