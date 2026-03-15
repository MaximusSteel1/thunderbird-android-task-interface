package net.thunderbird.feature.taskmail.internal.validation

import kotlinx.coroutines.runBlocking
import net.thunderbird.feature.taskmail.internal.data.DefaultTaskMailRepository
import net.thunderbird.feature.taskmail.internal.data.LegacyTaskMailBodyExtractor
import net.thunderbird.feature.taskmail.internal.data.TaskMailMessage
import net.thunderbird.feature.taskmail.internal.domain.model.TaskWorkspaceSummary
import org.junit.Test

internal class TaskMailValidationRunner {

    companion object {
        private const val DEFAULT_JSON_PATH =
            "E:\\projects\\mail_based_task_manager\\scripts\\test_fetch_latest_100.json"
    }

    @Test
    fun runValidation() {
        val jsonPath = DEFAULT_JSON_PATH

        println("=".repeat(60))
        println("TaskMail Phase 1.5 Validation Runner")
        println("=".repeat(60))
        println("JSON file: $jsonPath")
        println()

        runBlocking {
            runValidation(jsonPath)
        }
    }

    private suspend fun runValidation(jsonPath: String) {
        val jsonSource = JsonMessageSource.fromFile(jsonPath)
        val repository = DefaultTaskMailRepository(
            messageSource = jsonSource,
            bodyExtractor = LegacyTaskMailBodyExtractor(),
        )

        val messages = jsonSource.getMessages()
        val workspaces = repository.getTaskWorkspaceSummaries()
        val stats = calculateStatistics(messages, workspaces)
        val uiSimulator = UiSimulator()

        uiSimulator.printStatistics(
            totalMessages = stats.totalMessages,
            taskMailCount = stats.taskMailCount,
            userMessageCount = stats.userMessageCount,
            systemMessageCount = stats.systemMessageCount,
            workspaceCount = workspaces.size,
            sessionCount = stats.sessionCount,
        )

        uiSimulator.printWorkspaceList(workspaces)

        println()
        println("=".repeat(60))
        println("Session Details")
        println("=".repeat(60))

        workspaces.forEach { workspace ->
            workspace.sessions.take(3).forEach { session ->
                val detail = repository.getTaskSessionDetail(session.key)
                if (detail != null) {
                    uiSimulator.printSessionDetail(detail)
                }
            }
        }

        printAnomaliesReport(stats.anomalies)
    }

    private fun calculateStatistics(
        messages: List<TaskMailMessage>,
        workspaces: List<TaskWorkspaceSummary>,
    ): Statistics {
        val taskMailMessages = messages.filter { it.detection.isTaskMail }
        val userMessages = taskMailMessages.filter { !it.detection.isSystemMessage }
        val systemMessages = taskMailMessages.filter { it.detection.isSystemMessage }
        val sessionCount = workspaces.sumOf { it.sessionCount }

        val anomalies = buildList {
            messages.forEach { message ->
                if (!message.detection.isTaskMail && message.detection.parsedSubject.backend != null) {
                    add(
                        Anomaly(
                            type = AnomalyType.NonTaskMailWithBackend,
                            messageId = message.messageServerId,
                            details = buildString {
                                append("Has backend ")
                                append(message.detection.parsedSubject.backend)
                                append(" but is not recognized as TaskMail")
                            },
                        ),
                    )
                }

                if (message.detection.isTaskMail && message.rawBodyText.isBlank()) {
                    add(
                        Anomaly(
                            type = AnomalyType.EmptyBody,
                            messageId = message.messageServerId,
                            details = "TaskMail message has an empty body",
                        ),
                    )
                }

                val hasStatusToken = message.detection.parsedSubject.statusLabel != null
                if (message.detection.isSystemMessage && message.detection.stateCapsule == null && !hasStatusToken) {
                    add(
                        Anomaly(
                            type = AnomalyType.SystemMessageWithoutState,
                            messageId = message.messageServerId,
                            details = "System message is missing a TASK-STATE block",
                        ),
                    )
                }
            }
        }

        return Statistics(
            totalMessages = messages.size,
            taskMailCount = taskMailMessages.size,
            userMessageCount = userMessages.size,
            systemMessageCount = systemMessages.size,
            sessionCount = sessionCount,
            anomalies = anomalies,
        )
    }

    private fun printAnomaliesReport(anomalies: List<Anomaly>) {
        if (anomalies.isEmpty()) {
            println()
            println("=".repeat(60))
            println("No anomalies detected.")
            println("=".repeat(60))
            return
        }

        fun String.displayWidth(): Int = sumOf { if (it.code > 0x7F) 2 else 1 }

        fun String.padDisplay(width: Int): String {
            val currentWidth = displayWidth()
            return if (currentWidth >= width) this else this + " ".repeat(width - currentWidth)
        }

        val divider = "-".repeat(72)

        println()
        println(divider)
        println("Anomalies")
        println(divider)

        anomalies.groupBy(Anomaly::type).forEach { (type, items) ->
            println(type.description.padDisplay(38))
            items.take(5).forEach { anomaly ->
                println("  - message_id: ${anomaly.messageId.take(22).padDisplay(22)}")
                println("    ${anomaly.details.take(36).padDisplay(36)}")
            }
            if (items.size > 5) {
                println("  ... and ${items.size - 5} more")
            }
            println(divider)
        }
    }

    private data class Statistics(
        val totalMessages: Int,
        val taskMailCount: Int,
        val userMessageCount: Int,
        val systemMessageCount: Int,
        val sessionCount: Int,
        val anomalies: List<Anomaly>,
    )

    private data class Anomaly(
        val type: AnomalyType,
        val messageId: String,
        val details: String,
    )

    private enum class AnomalyType(val description: String) {
        NonTaskMailWithBackend("Non-TaskMail mail tagged with a backend"),
        EmptyBody("TaskMail message with an empty body"),
        SystemMessageWithoutState("System message without TASK-STATE"),
    }
}
