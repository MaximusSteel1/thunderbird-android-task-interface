package net.thunderbird.feature.taskmail.internal.domain.parser

internal class TaskMailMessageDetector(
    private val subjectParser: TaskMailSubjectParser = TaskMailSubjectParser(),
    private val stateCapsuleParser: TaskStateCapsuleParser = TaskStateCapsuleParser(),
    private val questionCapsuleParser: TaskQuestionCapsuleParser = TaskQuestionCapsuleParser(),
) {

    fun detect(envelope: TaskMailEnvelope): TaskMailDetection {
        val parsedSubject = subjectParser.parse(envelope.subject)
        val shouldParseCapsules = !parsedSubject.isReplyLike
        val stateCapsule = envelope.plainTextBody
            .takeIf { shouldParseCapsules }
            ?.let(stateCapsuleParser::parse)
        val questionCapsules = envelope.plainTextBody
            .takeIf { shouldParseCapsules }
            ?.let(questionCapsuleParser::parseAll)
            .orEmpty()
        val questionCapsule = questionCapsules.lastOrNull()

        val hasTaskMailSubject = parsedSubject.backend != null ||
            parsedSubject.statusLabel != null ||
            parsedSubject.sessionIdFromSubject != null
        val hasCapsule = stateCapsule != null || questionCapsules.isNotEmpty()
        val isTaskMail = hasTaskMailSubject || hasCapsule
        val isSystemMessage = !parsedSubject.isReplyLike && (parsedSubject.statusLabel != null || hasCapsule)

        return TaskMailDetection(
            isTaskMail = isTaskMail,
            isSystemMessage = isSystemMessage,
            parsedSubject = parsedSubject,
            stateCapsule = stateCapsule,
            questionCapsule = questionCapsule,
            questionCapsules = questionCapsules,
        )
    }
}
