package net.thunderbird.feature.taskmail.internal.domain.parser

internal class TaskMailMessageDetector(
    private val subjectParser: TaskMailSubjectParser = TaskMailSubjectParser(),
    private val stateCapsuleParser: TaskStateCapsuleParser = TaskStateCapsuleParser(),
    private val questionCapsuleParser: TaskQuestionCapsuleParser = TaskQuestionCapsuleParser(),
) {

    fun detect(envelope: TaskMailEnvelope): TaskMailDetection {
        val parsedSubject = subjectParser.parse(envelope.subject)
        val stateCapsule = stateCapsuleParser.parse(envelope.plainTextBody)
        val questionCapsules = questionCapsuleParser.parseAll(envelope.plainTextBody)
        val questionCapsule = questionCapsules.lastOrNull()

        val hasTaskMailSubject = parsedSubject.backend != null ||
            parsedSubject.statusLabel != null ||
            parsedSubject.sessionIdFromSubject != null
        val hasCapsule = stateCapsule != null
        val isTaskMail = hasTaskMailSubject || hasCapsule
        val isSystemMessage = parsedSubject.statusLabel != null || hasCapsule

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
