package net.thunderbird.feature.taskmail.internal.validation

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailBackend
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSessionStatus
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailStatusLabel
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionDetail
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionSummary
import net.thunderbird.feature.taskmail.internal.domain.model.TaskTimelineDirection
import net.thunderbird.feature.taskmail.internal.domain.model.TaskTimelineItem
import net.thunderbird.feature.taskmail.internal.domain.model.TaskWorkspaceSummary

internal class UiSimulator {

    private fun String.displayWidth(): Int {
        return this.sumOf { char ->
            if (char.code > 0x7F) 2 else 1
        }
    }

    private fun String.padDisplay(width: Int): String {
        val currentWidth = this.displayWidth()
        return if (currentWidth >= width) {
            this
        } else {
            this + " ".repeat(width - currentWidth)
        }
    }

    fun printStatistics(
        totalMessages: Int,
        taskMailCount: Int,
        userMessageCount: Int,
        systemMessageCount: Int,
        workspaceCount: Int,
        sessionCount: Int,
    ) {
        println()
        println("╔════════════════════════════════════════╗")
        println("║           TaskMail 解析统计             ║")
        println("╠════════════════════════════════════════╣")
        println("║ 总邮件数:           ${totalMessages.toString().padDisplay(18)}║")
        println("║ TaskMail 邮件:      ${taskMailCount.toString().padDisplay(18)}║")
        println("║   - 用户消息:       ${userMessageCount.toString().padDisplay(18)}║")
        println("║   - 系统消息:       ${systemMessageCount.toString().padDisplay(18)}║")
        println("║ 非 TaskMail:        ${(totalMessages - taskMailCount).toString().padDisplay(18)}║")
        println("╠════════════════════════════════════════╣")
        println("║ Workspace 数量:     ${workspaceCount.toString().padDisplay(18)}║")
        println("║ Session 数量:       ${sessionCount.toString().padDisplay(18)}║")
        println("╚════════════════════════════════════════╝")
    }

    fun printWorkspaceList(workspaces: List<TaskWorkspaceSummary>) {
        println()
        workspaces.forEachIndexed { index, workspace ->
            printWorkspaceCard(workspace, index + 1)
        }
    }

    private fun printWorkspaceCard(workspace: TaskWorkspaceSummary, index: Int) {
        println("═══════════════════════════════════════════════════════════════")
        println("WORKSPACE #$index: ${workspace.title}")
        workspace.subtitle?.let { println("  Subtitle: $it") }
        println("  Sessions: ${workspace.sessionCount}")
        println("───────────────────────────────────────────────────────────────")

        workspace.sessions.forEach { session ->
            printSessionRow(session)
        }
    }

    private fun printSessionRow(session: TaskSessionSummary) {
        println("  Session: ${session.sessionName}")
        println("    ID: ${session.key.sessionId ?: session.key.threadId}")
        println("    Status: ${session.status.toDisplayString()}")
        println("    Backend: ${session.backend.toDisplayString()}")
        session.lastSummary?.let { summary ->
            val truncated = truncateText(summary, 60)
            println("    Last Summary: $truncated")
        }
        println("    Pending Question: ${if (session.pendingQuestion) "Yes" else "No"}")
        println("───────────────────────────────────────────────────────────────")
    }

    fun printSessionDetail(detail: TaskSessionDetail) {
        println()
        println("═══════════════════════════════════════════════════════════════")
        println("SESSION DETAIL: ${detail.key.sessionId ?: detail.key.threadId}")
        println("  Name: ${detail.sessionName}")
        println("  Backend: ${detail.backend.toDisplayString()}")
        println("  Status: ${detail.status.toDisplayString()}")
        println("  Repo: ${detail.repoPath}")
        detail.workdir?.let { println("  Workdir: $it") }
        detail.lastSummary?.let { println("  Summary: ${truncateText(it, 80)}") }

        detail.question?.let { q ->
            println("───────────────────────────────────────────────────────────────")
            println("  PENDING QUESTION:")
            println("    ID: ${q.questionId}")
            println("    Question: ${q.questionText}")
            if (q.choices.isNotEmpty()) {
                println("    Choices:")
                q.choices.forEachIndexed { i, choice ->
                    println("      ${'A' + i}. $choice")
                }
            }
        }

        println("───────────────────────────────────────────────────────────────")
        println("TIMELINE (${detail.timeline.size} messages):")
        println("───────────────────────────────────────────────────────────────")

        detail.timeline.forEachIndexed { index, item ->
            printTimelineItem(item, index + 1)
        }
    }

    private fun printTimelineItem(item: TaskTimelineItem, index: Int) {
        val statusLabel = item.statusLabel?.let { " • ${it.toDisplayString()}" } ?: ""
        val timestamp = formatTimestamp(item.timestamp)

        println("[$index] ${item.direction.toDisplayString()}$statusLabel • $timestamp")

        item.summary?.let { summary ->
            println("    Summary: ${truncateText(summary, 60)}")
        }

        val bodyPreview = truncateText(item.body.plainText, 200)
        if (bodyPreview.isNotBlank()) {
            println("    Body (${item.body.plainText.length} chars):")
            bodyPreview.lines().take(5).forEach { line ->
                println("      $line")
            }
            if (bodyPreview.lines().size > 5) {
                println("      ...")
            }
        }
        println("───────────────────────────────────────────────────────────────")
    }

    private fun truncateText(text: String, maxLength: Int): String {
        val normalized = text.replace(Regex("\\s+"), " ").trim()
        return if (normalized.length <= maxLength) {
            normalized
        } else {
            normalized.take(maxLength) + "..."
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        val instant = Instant.ofEpochMilli(timestamp)
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())
        return formatter.format(instant)
    }

    private fun TaskMailBackend.toDisplayString(): String = when (this) {
        TaskMailBackend.OpenCode -> "OpenCode"
        TaskMailBackend.Codex -> "Codex"
    }

    private fun TaskMailSessionStatus.toDisplayString(): String = when (this) {
        TaskMailSessionStatus.Queued -> "queued"
        TaskMailSessionStatus.Running -> "running"
        TaskMailSessionStatus.WaitingUser -> "waiting_user"
        TaskMailSessionStatus.Done -> "done"
        TaskMailSessionStatus.Failed -> "failed"
        TaskMailSessionStatus.Killed -> "killed"
        TaskMailSessionStatus.Unknown -> "unknown"
    }

    private fun TaskMailStatusLabel.toDisplayString(): String = name.uppercase()

    private fun TaskTimelineDirection.toDisplayString(): String = when (this) {
        TaskTimelineDirection.Incoming -> "Incoming"
        TaskTimelineDirection.Outgoing -> "Outgoing"
        TaskTimelineDirection.System -> "System"
    }
}
