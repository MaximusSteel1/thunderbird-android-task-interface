package net.thunderbird.feature.taskmail.internal.domain.reply

import net.thunderbird.feature.taskmail.internal.domain.newtask.toCanonicalWireValue

internal class TaskMailReplyBodySerializer {

    fun serialize(input: TaskMailReplyBodyInput): String {
        val baseBody = when (input.mode) {
            TaskMailReplyMode.ContinueSession,
            TaskMailReplyMode.AnswerSingleQuestion,
            TaskMailReplyMode.AnswerMultiQuestion,
            -> input.userText

            TaskMailReplyMode.ResumeSession -> serializeResumeBody(input.userText)
            TaskMailReplyMode.StatusQuery -> STATUS_QUERY_BODY
        }

        return when {
            input.mode == TaskMailReplyMode.StatusQuery -> baseBody
            input.permission == null -> baseBody
            else -> prependPermissionHeader(
                body = baseBody,
                permission = input.permission.toCanonicalWireValue(),
                mode = input.mode,
            )
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

    private fun prependPermissionHeader(
        body: String,
        permission: String,
        mode: TaskMailReplyMode,
    ): String {
        return when (mode) {
            TaskMailReplyMode.ResumeSession -> {
                val lines = body.lineSequence().toList()
                buildString {
                    append(lines.firstOrNull().orEmpty())
                    appendLine()
                    append("Permission: ")
                    append(permission)
                    if (lines.size > 1) {
                        appendLine()
                        append(lines.drop(1).joinToString(separator = "\n"))
                    }
                }
            }

            else -> buildString {
                append("Permission: ")
                append(permission)
                if (body.isNotEmpty()) {
                    appendLine()
                    append(body)
                }
            }
        }
    }

    private companion object {
        const val STATUS_QUERY_BODY = "/status"
        const val RESUME_SESSION_BODY = "/resume"
    }
}
