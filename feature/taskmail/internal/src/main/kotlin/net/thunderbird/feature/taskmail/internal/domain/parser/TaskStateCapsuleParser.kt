package net.thunderbird.feature.taskmail.internal.domain.parser

import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailBackend
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSessionStatus

internal class TaskStateCapsuleParser {

    fun parse(text: String): TaskStateCapsule? {
        return stateBlockRegex.findAll(text)
            .lastOrNull()
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.let(::parseFields)
            ?.let { fields ->
                fields["thread_id"]?.let { threadId ->
                    TaskStateCapsule(
                        threadId = threadId,
                        workspaceId = fields["workspace_id"],
                        sessionId = fields["session_id"],
                        sessionName = fields["session_name"],
                        taskId = fields["task_id"],
                        backend = TaskMailBackend.fromWireValue(fields["backend"]),
                        repoPath = fields["repo_path"],
                        workdir = fields["workdir"],
                        mode = fields["mode"],
                        status = TaskMailSessionStatus.fromWireValue(fields["status"]),
                        lastSummary = fields["last_summary"],
                    )
                }
            }
    }

    private fun parseFields(content: String): Map<String, String> {
        val fields = linkedMapOf<String, String>()
        var currentKey: String? = null

        content.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { line ->
                val separatorIndex = line.indexOf(':')

                if (separatorIndex > 0) {
                    val key = line.substring(0, separatorIndex).trim()
                    val value = normalizeWhitespace(line.substring(separatorIndex + 1))
                    fields[key] = value
                    currentKey = key
                } else if (currentKey != null) {
                    val previousValue = fields.getValue(currentKey)
                    fields[currentKey] = normalizeWhitespace("$previousValue $line")
                }
            }

        return fields
    }

    private fun normalizeWhitespace(value: String): String {
        return value.replace(whitespaceRegex, " ").trim()
    }

    private companion object {
        val stateBlockRegex = Regex(
            pattern = "---TASK-STATE-BEGIN---(.*?)---TASK-STATE-END---",
            options = setOf(RegexOption.DOT_MATCHES_ALL),
        )
        val whitespaceRegex = Regex("\\s+")
    }
}
