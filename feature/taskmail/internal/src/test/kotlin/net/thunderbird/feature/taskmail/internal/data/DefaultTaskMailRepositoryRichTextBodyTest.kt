package net.thunderbird.feature.taskmail.internal.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import kotlinx.coroutines.test.runTest
import net.thunderbird.feature.taskmail.internal.domain.model.TaskBodyRenderMode
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailBackend
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSessionStatus
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMessageAttachment
import net.thunderbird.feature.taskmail.internal.domain.model.TaskRichTextBlock
import net.thunderbird.feature.taskmail.internal.domain.model.TaskRichTextInline
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionKey
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskMailDetection
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskMailParsedSubject
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskQuestionCapsule
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskStateCapsule
import org.junit.Test

internal class DefaultTaskMailRepositoryRichTextBodyTest {

    @Test
    fun `getTaskSessionDetail projects supported html into rich document when article fragment is present`() = runTest {
        val result = repositoryWithHtmlBody().getTaskSessionDetail(
            TaskSessionKey(
                sessionId = "session-1",
                threadId = "thread-100",
            ),
        )

        assertThat(result).isNotNull()
        val detail = result!!
        assertThat(detail.lastSummary).isEqualTo("Need confirmation from user.")
        assertThat(detail.timeline.first().summary).isEqualTo("Looks good.")
        val outgoingBody = detail.timeline.first().body
        assertThat(outgoingBody.plainTextFallback).isEqualTo("Looks good.")
        assertThat(outgoingBody.renderMode).isEqualTo(TaskBodyRenderMode.RichText)
        assertThat(outgoingBody.sourceHtml).isEqualTo(
            """
                <article class="task-mail">
                  <h2>Looks good.</h2>
                  <p>Attached the latest chart.</p>
                  <p><img src="cid:chart-1" alt="Result chart" type="image/svg+xml" /></p>
                </article>
            """.trimIndent(),
        )
        val richDocument = outgoingBody.richDocument
        assertThat(richDocument).isNotNull()
        assertThat(richDocument!!.blocks.size).isEqualTo(3)
        assertThat(richDocument.blocks[0]).isEqualTo(
            TaskRichTextBlock.Heading(
                level = 2,
                inlines = listOf(TaskRichTextInline.Text("Looks good.")),
            ),
        )
        assertThat(richDocument.blocks[1]).isEqualTo(
            TaskRichTextBlock.Paragraph(
                inlines = listOf(
                    TaskRichTextInline.Text("Attached the latest chart."),
                ),
            ),
        )
        assertThat(richDocument.blocks[2]).isEqualTo(
            TaskRichTextBlock.InlineImage(
                attachmentId = "attachment-chart-1",
                contentId = "chart-1",
                altText = "Result chart",
                mimeType = "image/svg+xml",
                isSvg = true,
            ),
        )
    }

    @Test
    fun `getTaskWorkspaceSummaries keeps session summaries plain text based when rich html detail bodies exist`() =
        runTest {
            val result = repositoryWithHtmlBody().getTaskWorkspaceSummaries()

            assertThat(result.single().sessions.single().lastSummary).isEqualTo("Need confirmation from user.")
        }

    @Test
    fun `getTaskSessionDetail treats image svg attachments as svg inline images`() = runTest {
        val result = repositoryWithHtmlBody(
            attachmentContentType = "image/svg",
            htmlImageType = "image/svg",
        ).getTaskSessionDetail(
            TaskSessionKey(
                sessionId = "session-1",
                threadId = "thread-100",
            ),
        )

        assertThat(result).isNotNull()
        val richDocument = result!!.timeline.first().body.richDocument
        assertThat(richDocument).isNotNull()
        assertThat(richDocument!!.blocks[2]).isEqualTo(
            TaskRichTextBlock.InlineImage(
                attachmentId = "attachment-chart-1",
                contentId = "chart-1",
                altText = "Result chart",
                mimeType = "image/svg",
                isSvg = true,
            ),
        )
    }

    @Test
    fun `getTaskSessionDetail falls back to plain text for structured system html bodies`() = runTest {
        val result = repositoryWithHtmlBody(
            systemHtmlBody = """
                <article class="task-mail">
                  <h2>Context</h2>
                  <p>Status: waiting_user</p>
                  <p>Session ID: session-1</p>
                  <p>Thread ID: thread-100</p>
                  <p>Task ID: task-100</p>
                  <p>Backend: codex</p>
                  <p>Repo: E:/projects/android_task_manager</p>
                  <pre>task-state-capsule
                ---TASK-STATE-BEGIN---
                thread_id: thread-100
                workspace_id: workspace-1
                session_id: session-1
                ---TASK-STATE-END---</pre>
                </article>
            """.trimIndent(),
        ).getTaskSessionDetail(
            TaskSessionKey(
                sessionId = "session-1",
                threadId = "thread-100",
            ),
        )

        assertThat(result).isNotNull()
        val detail = result!!
        val systemBody = detail.timeline.last().body
        assertThat(detail.timeline.last().summary).isEqualTo("Need confirmation from user.")
        assertThat(systemBody.plainTextFallback).isEqualTo("Need confirmation from user.")
        assertThat(systemBody.renderMode).isEqualTo(TaskBodyRenderMode.PlainTextOnly)
        assertThat(systemBody.richDocument).isEqualTo(null)
    }

    private fun repositoryWithHtmlBody(
        attachmentContentType: String = "image/svg+xml",
        htmlImageType: String = "image/svg+xml",
        systemHtmlBody: String? = null,
    ): DefaultTaskMailRepository {
        return DefaultTaskMailRepository(
            messageSource = FakeTaskMailMessageSource(
                messages = listOf(
                    outgoingUserMessage(
                        attachmentContentType = attachmentContentType,
                        htmlImageType = htmlImageType,
                    ),
                    questionSystemMessage(htmlBody = systemHtmlBody),
                ),
            ),
        )
    }

    private fun outgoingUserMessage(
        attachmentContentType: String = "image/svg+xml",
        htmlImageType: String = "image/svg+xml",
    ): TaskMailMessage {
        return TaskMailMessage(
            accountUuid = "account-1",
            folderId = 1L,
            messageServerId = "msg-user",
            threadRootId = 100L,
            timestamp = 100L,
            subject = "Re: [CX] [S:session-1] Implement parser",
            rawBodyText = "Looks good.",
            htmlBody = """
                <article class="task-mail">
                  <h2>Looks good.</h2>
                  <p>Attached the latest chart.</p>
                  <p><img src="cid:chart-1" alt="Result chart" type="$htmlImageType" /></p>
                </article>
            """.trimIndent(),
            attachments = listOf(
                TaskMessageAttachment(
                    id = "attachment-chart-1",
                    displayName = "result_chart.svg",
                    contentType = attachmentContentType,
                    isInline = true,
                    isImage = true,
                    contentId = "<chart-1>",
                ),
            ),
            detection = TaskMailDetection(
                isTaskMail = true,
                isSystemMessage = false,
                parsedSubject = TaskMailParsedSubject(
                    backend = TaskMailBackend.Codex,
                    statusLabel = null,
                    sessionIdFromSubject = "session-1",
                    subjectText = "Implement parser",
                    isReplyLike = true,
                ),
            ),
            isFromCurrentUser = true,
        )
    }

    private fun questionSystemMessage(htmlBody: String? = null): TaskMailMessage {
        return TaskMailMessage(
            accountUuid = "account-1",
            folderId = 1L,
            messageServerId = "msg-system",
            threadRootId = 100L,
            timestamp = 150L,
            subject = "[QUESTION] [CX] [S:session-1] Implement parser",
            rawBodyText = """
                Summary: Need confirmation from user.

                ---TASK-QUESTION-BEGIN---
                question_id: question-session-1
                question_text: Proceed with repository wiring?
                choices: yes|no
                ---TASK-QUESTION-END---

                ---TASK-STATE-BEGIN---
                thread_id: thread-100
                workspace_id: workspace-1
                session_id: session-1
                session_name: Implement parser
                repo_path: E:/projects/android_task_manager
                workdir: feature/taskmail
                backend: codex
                status: waiting_user
                last_summary: Need confirmation from user.
                ---TASK-STATE-END---
            """.trimIndent(),
            htmlBody = htmlBody,
            detection = TaskMailDetection(
                isTaskMail = true,
                isSystemMessage = true,
                parsedSubject = TaskMailParsedSubject(
                    backend = TaskMailBackend.Codex,
                    statusLabel = null,
                    sessionIdFromSubject = "session-1",
                    subjectText = "Implement parser",
                    isReplyLike = false,
                ),
                stateCapsule = TaskStateCapsule(
                    threadId = "thread-100",
                    workspaceId = "workspace-1",
                    sessionId = "session-1",
                    sessionName = "Implement parser",
                    backend = TaskMailBackend.Codex,
                    repoPath = "E:/projects/android_task_manager",
                    workdir = "feature/taskmail",
                    status = TaskMailSessionStatus.WaitingUser,
                    lastSummary = "Need confirmation from user.",
                ),
                questionCapsule = TaskQuestionCapsule(
                    questionId = "question-session-1",
                    questionText = "Proceed with repository wiring?",
                    choices = listOf("yes", "no"),
                ),
            ),
            isFromCurrentUser = false,
        )
    }

    private class FakeTaskMailMessageSource(
        private val messages: List<TaskMailMessage>,
    ) : TaskMailMessageSource {
        override suspend fun getMessages(): List<TaskMailMessage> = messages
    }
}
