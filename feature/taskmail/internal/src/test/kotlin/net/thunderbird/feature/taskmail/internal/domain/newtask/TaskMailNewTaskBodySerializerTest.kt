package net.thunderbird.feature.taskmail.internal.domain.newtask

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailBackend

class TaskMailNewTaskBodySerializerTest {

    private val testSubject = TaskMailNewTaskBodySerializer()

    @Test
    fun `serialize should emit canonical minimum body`() {
        val body = testSubject.serialize(
            TaskMailNewTaskDraft(
                senderAccountId = "account-1",
                backend = TaskMailBackend.Codex,
                repoPath = "E:\\projects\\android_task_manager",
                taskText = "Audit the new TaskMail screen flow.",
                subjectTitle = "Audit the new TaskMail screen flow.",
            ),
        )

        assertThat(body).isEqualTo(
            """
            Repo: E:\projects\android_task_manager

            Task:
            Audit the new TaskMail screen flow.
            """.trimIndent(),
        )
    }

    @Test
    fun `serialize should omit default mode and permission`() {
        val body = testSubject.serialize(
            TaskMailNewTaskDraft(
                senderAccountId = "account-1",
                backend = TaskMailBackend.OpenCode,
                repoPath = "repo",
                taskText = "Task body",
                subjectTitle = "Task body",
                mode = TaskMailNewTaskMode.Modify,
                permission = TaskMailNewTaskPermission.Default,
            ),
        )

        assertThat(body).isEqualTo(
            """
            Repo: repo

            Task:
            Task body
            """.trimIndent(),
        )
    }

    @Test
    fun `serialize should emit advanced fields in canonical order`() {
        val body = testSubject.serialize(
            TaskMailNewTaskDraft(
                senderAccountId = "account-1",
                backend = TaskMailBackend.Codex,
                repoPath = "repo",
                taskText = "Task body",
                subjectTitle = "Task body",
                workdir = "feature/taskmail/internal",
                timeoutMinutes = 120,
                mode = TaskMailNewTaskMode.AnalysisOnly,
                profile = "android",
                permission = TaskMailNewTaskPermission.Highest,
                acceptanceCriteria = listOf("Do not modify code", "- Provide a concise risk list"),
            ),
        )

        assertThat(body).isEqualTo(
            """
            Repo: repo
            Workdir: feature/taskmail/internal
            Timeout: 120
            Mode: analysis_only
            Profile: android
            Permission: highest

            Task:
            Task body

            Acceptance:
            - Do not modify code
            - Provide a concise risk list
            """.trimIndent(),
        )
    }

    @Test
    fun `serialize should omit blank optional fields and normalize acceptance lines`() {
        val body = testSubject.serialize(
            TaskMailNewTaskDraft(
                senderAccountId = "account-1",
                backend = TaskMailBackend.Codex,
                repoPath = " repo ",
                taskText = "Task body",
                subjectTitle = "Task body",
                workdir = "   ",
                profile = "",
                acceptanceCriteria = listOf("  ", "Keep tests focused", "- Preserve draft content", ""),
            ),
        )

        assertThat(body).isEqualTo(
            """
            Repo: repo

            Task:
            Task body

            Acceptance:
            - Keep tests focused
            - Preserve draft content
            """.trimIndent(),
        )
    }
}
