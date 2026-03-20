package net.thunderbird.feature.taskmail.internal.domain.reply

internal enum class TaskMailReplyMode {
    ContinueSession,
    ResumeSession,
    AnswerSingleQuestion,
    AnswerMultiQuestion,
    StatusQuery,
}
