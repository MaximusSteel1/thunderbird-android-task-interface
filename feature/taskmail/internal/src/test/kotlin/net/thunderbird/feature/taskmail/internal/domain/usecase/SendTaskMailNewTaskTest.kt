package net.thunderbird.feature.taskmail.internal.domain.usecase

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailBackend
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskDraft
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskRequest
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskResult
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskSender

class SendTaskMailNewTaskTest {

    @Test
    fun `invoke should build canonical subject and body`() = runTest {
        val sender = FakeTaskMailNewTaskSender()
        val testSubject = SendTaskMailNewTask(sender)

        val result = testSubject(
            TaskMailNewTaskDraft(
                senderAccountId = "account-1",
                backend = TaskMailBackend.Codex,
                repoPath = "E:\\projects\\android_task_manager",
                taskText = "Audit the new TaskMail screen flow.",
                subjectTitle = "Audit the new TaskMail screen flow.",
            ),
        )

        assertThat(result).isEqualTo(TaskMailNewTaskResult.success())
        assertThat(sender.requests).hasSize(1)
        assertThat(sender.requests.single()).isEqualTo(
            TaskMailNewTaskRequest(
                accountUuid = "account-1",
                subject = "[CX] Audit the new TaskMail screen flow.",
                body = """
                    Repo: E:\projects\android_task_manager

                    Task:
                    Audit the new TaskMail screen flow.
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `invoke should return sender failure`() = runTest {
        val sender = FakeTaskMailNewTaskSender(
            result = TaskMailNewTaskResult.failure("send failed"),
        )
        val testSubject = SendTaskMailNewTask(sender)

        val result = testSubject(
            TaskMailNewTaskDraft(
                senderAccountId = "account-1",
                backend = TaskMailBackend.OpenCode,
                repoPath = "repo",
                taskText = "Task body",
                subjectTitle = "Task body",
            ),
        )

        assertThat(result).isEqualTo(TaskMailNewTaskResult.failure("send failed"))
    }
}

private class FakeTaskMailNewTaskSender(
    private val result: TaskMailNewTaskResult = TaskMailNewTaskResult.success(),
) : TaskMailNewTaskSender {
    val requests = mutableListOf<TaskMailNewTaskRequest>()

    override suspend fun send(request: TaskMailNewTaskRequest): TaskMailNewTaskResult {
        requests += request
        return result
    }
}
