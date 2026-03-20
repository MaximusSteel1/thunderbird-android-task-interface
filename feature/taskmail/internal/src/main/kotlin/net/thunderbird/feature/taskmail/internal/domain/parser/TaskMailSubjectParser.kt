package net.thunderbird.feature.taskmail.internal.domain.parser

import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailBackend
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailStatusLabel

internal class TaskMailSubjectParser {

    fun parse(subject: String): TaskMailParsedSubject {
        var remainingSubject = subject.trim()
        var isReplyLike = false
        var backend: TaskMailBackend? = null
        var statusLabel: TaskMailStatusLabel? = null
        var sessionIdFromSubject: String? = null

        while (true) {
            val replyPrefix = replyPrefixes.firstOrNull { remainingSubject.startsWith(it, ignoreCase = true) } ?: break
            remainingSubject = remainingSubject.substring(replyPrefix.length).trimStart()
            isReplyLike = true
        }

        var consumedToken = consumeLeadingToken(remainingSubject)
        while (consumedToken != null) {
            remainingSubject = consumedToken.remainingSubject
            statusLabel = consumedToken.statusLabel ?: statusLabel
            backend = consumedToken.backend ?: backend
            sessionIdFromSubject = consumedToken.sessionIdFromSubject ?: sessionIdFromSubject
            consumedToken = consumeLeadingToken(remainingSubject)
        }

        return TaskMailParsedSubject(
            backend = backend,
            statusLabel = statusLabel,
            sessionIdFromSubject = sessionIdFromSubject,
            subjectText = remainingSubject.trim(),
            isReplyLike = isReplyLike,
        )
    }

    private fun consumeLeadingToken(subject: String): ConsumedSubjectToken? {
        val token = leadingTokenRegex.find(subject)?.value ?: return null
        val remainingSubject = subject.removePrefix(token).trimStart()
        val statusLabel = TaskMailStatusLabel.fromSubjectToken(token)
        val backend = TaskMailBackend.fromSubjectPrefix(token)
        val sessionIdFromSubject = sessionTokenRegex.matchEntire(token)?.groupValues?.getOrNull(1)

        return if (statusLabel != null || backend != null || sessionIdFromSubject != null) {
            ConsumedSubjectToken(
                remainingSubject = remainingSubject,
                statusLabel = statusLabel,
                backend = backend,
                sessionIdFromSubject = sessionIdFromSubject,
            )
        } else {
            null
        }
    }

    private data class ConsumedSubjectToken(
        val remainingSubject: String,
        val statusLabel: TaskMailStatusLabel?,
        val backend: TaskMailBackend?,
        val sessionIdFromSubject: String?,
    )

    private companion object {
        val replyPrefixes = listOf(
            "Re:",
            "AW:",
            "FW:",
            "Fwd:",
            "\u56de\u590d:",
            "\u56de\u590d\uFF1A",
            "\u7B54\u590D:",
            "\u7B54\u590D\uFF1A",
        )
        val leadingTokenRegex = Regex("^\\[[^\\]]+\\]")
        val sessionTokenRegex = Regex("^\\[S:([^\\]]+)\\]$", RegexOption.IGNORE_CASE)
    }
}
