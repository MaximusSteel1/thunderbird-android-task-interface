package net.thunderbird.feature.taskmail.internal.domain.reply

internal class TaskMailReplyBodySerializer {

    fun serialize(input: TaskMailReplyBodyInput): String {
        return when (input.mode) {
            TaskMailReplyMode.ContinueSession,
            TaskMailReplyMode.AnswerSingleQuestion,
            TaskMailReplyMode.AnswerMultiQuestion,
            -> input.userText

            TaskMailReplyMode.ResumeSession -> serializeResumeBody(input.userText)
            TaskMailReplyMode.StatusQuery -> STATUS_QUERY_BODY
        }
    }

    private fun serializeResumeBody(userText: String): String {
        val normalizedBody = userText.trimStart()

        if (normalizedBody.isBlank()) {
            return RESUME_SESSION_BODY
        }

        return if (normalizedBody.startsWith(RESUME_SESSION_BODY) &&
            normalizedBody.drop(RESUME_SESSION_BODY.length).firstOrNull()?.isLetterOrDigit() != true
        ) {
            normalizedBody
        } else {
            "$RESUME_SESSION_BODY\n$normalizedBody"
        }
    }

    private companion object {
        const val STATUS_QUERY_BODY = "/status"
        const val RESUME_SESSION_BODY = "/resume"
    }
}
