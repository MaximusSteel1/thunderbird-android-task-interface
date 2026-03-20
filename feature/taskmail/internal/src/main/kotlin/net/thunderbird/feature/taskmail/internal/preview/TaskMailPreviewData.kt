package net.thunderbird.feature.taskmail.internal.preview

import net.thunderbird.feature.taskmail.internal.domain.model.TaskBodyRenderMode
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailBackend
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSessionStatus
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailStatusLabel
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMessageAttachment
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMessageBody
import net.thunderbird.feature.taskmail.internal.domain.model.TaskRichTextBlock
import net.thunderbird.feature.taskmail.internal.domain.model.TaskRichTextDocument
import net.thunderbird.feature.taskmail.internal.domain.model.TaskRichTextInline
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionDetail
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionKey
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionReplyContext
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionSummary
import net.thunderbird.feature.taskmail.internal.domain.model.TaskTimelineDirection
import net.thunderbird.feature.taskmail.internal.domain.model.TaskTimelineItem
import net.thunderbird.feature.taskmail.internal.domain.model.TaskWorkspaceKey
import net.thunderbird.feature.taskmail.internal.domain.model.TaskWorkspaceSummary
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskQuestionCapsule

internal object TaskMailPreviewData {
    private val workspaceKey = TaskWorkspaceKey(
        workspaceId = "workspace_001",
        repoPath = "E:/projects/android_task_manager",
        workdir = "feature/taskmail",
    )

    private val replyContext = TaskSessionReplyContext(
        accountUuid = "account-1",
        folderId = 1L,
        messageServerId = "msg-system-001",
        threadRootId = 100L,
        anchorTimestamp = 1_742_000_000_000,
    )

    val workspaceSummaries: List<TaskWorkspaceSummary> = listOf(
        TaskWorkspaceSummary(
            key = workspaceKey,
            title = "android_task_manager",
            subtitle = "feature/taskmail",
            backendSet = setOf(TaskMailBackend.Codex),
            activeSessionId = "session_001",
            sessionCount = 1,
            sessions = listOf(
                TaskSessionSummary(
                    key = TaskSessionKey(
                        sessionId = "session_001",
                        threadId = "thread_001",
                    ),
                    sessionName = "Build TaskMail Phase 1",
                    status = TaskMailSessionStatus.WaitingUser,
                    backend = TaskMailBackend.Codex,
                    lastSummary = "Parser layer is complete. Waiting to continue UI work.",
                    lastUpdatedAt = 1_742_000_000_000,
                    pendingQuestion = true,
                ),
            ),
        ),
    )

    val questionSessionDetail = TaskSessionDetail(
        key = TaskSessionKey(
            sessionId = "session_001",
            threadId = "thread_001",
        ),
        workspace = workspaceKey,
        sessionName = "Build TaskMail Phase 1",
        backend = TaskMailBackend.Codex,
        status = TaskMailSessionStatus.WaitingUser,
        repoPath = "E:/projects/android_task_manager",
        workdir = "feature/taskmail",
        lastSummary = "Parser layer is complete. Waiting to continue UI work.",
        question = TaskQuestionCapsule(
            questionId = "question_001",
            questionText = "Should I proceed with the workspace and detail screens next?",
            choices = listOf("yes", "no"),
        ),
        replyContext = replyContext,
        timeline = listOf(
            TaskTimelineItem(
                id = "timeline_001",
                timestamp = 1_742_000_000_000,
                direction = TaskTimelineDirection.System,
                statusLabel = TaskMailStatusLabel.Question,
                summary = "Need confirmation before continuing the UI layer.",
                body = plainTextBody(
                    text = "Parser layer is complete. Waiting for the next implementation step.",
                ),
            ),
        ),
    )

    val plainTextStatusSessionDetail = TaskSessionDetail(
        key = TaskSessionKey(
            sessionId = "session_status_001",
            threadId = "thread_status_001",
        ),
        workspace = workspaceKey,
        sessionName = "TaskMail Plain-Text Status Mail",
        backend = TaskMailBackend.Codex,
        status = TaskMailSessionStatus.Done,
        repoPath = "E:/projects/android_task_manager",
        workdir = "feature/taskmail",
        lastSummary = "Validation completed without any remaining parser drift.",
        replyContext = replyContext,
        timeline = listOf(
            TaskTimelineItem(
                id = "timeline_status_001",
                timestamp = 1_742_010_000_000,
                direction = TaskTimelineDirection.System,
                statusLabel = TaskMailStatusLabel.Done,
                summary = "Validation completed without any remaining parser drift.",
                body = plainTextBody(
                    text = """
                        Validation completed without any remaining parser drift.
                        The plain-text read path remains authoritative.
                    """.trimIndent(),
                ),
            ),
        ),
    )

    val richInlinePngSessionDetail = TaskSessionDetail(
        key = TaskSessionKey(
            sessionId = "session_png_001",
            threadId = "thread_png_001",
        ),
        workspace = workspaceKey,
        sessionName = "TaskMail HTML Inline PNG Preview",
        backend = TaskMailBackend.Codex,
        status = TaskMailSessionStatus.Running,
        repoPath = "E:/projects/android_task_manager",
        workdir = "feature/taskmail",
        lastSummary = "Attached the refreshed chart preview for review.",
        replyContext = replyContext.copy(messageServerId = "msg-system-png"),
        timeline = listOf(
            TaskTimelineItem(
                id = "timeline_png_001",
                timestamp = 1_742_020_000_000,
                direction = TaskTimelineDirection.System,
                statusLabel = TaskMailStatusLabel.Running,
                summary = "Attached the refreshed chart preview for review.",
                body = richBody(
                    plainText = "Attached the refreshed chart preview for review.",
                    document = TaskRichTextDocument(
                        blocks = listOf(
                            TaskRichTextBlock.Heading(
                                level = 2,
                                inlines = listOf(TaskRichTextInline.Text("Latest chart preview")),
                            ),
                            TaskRichTextBlock.Paragraph(
                                inlines = listOf(
                                    TaskRichTextInline.Text("Attached the refreshed workspace chart as an inline PNG."),
                                ),
                            ),
                            TaskRichTextBlock.InlineImage(
                                attachmentId = "attachment_png_001",
                                contentId = "chart-preview",
                                altText = "Workspace chart preview",
                                mimeType = "image/png",
                            ),
                        ),
                    ),
                ),
                attachments = listOf(
                    TaskMessageAttachment(
                        id = "attachment_png_001",
                        displayName = "workspace_chart.png",
                        contentType = "image/png",
                        sizeBytes = 98_304L,
                        isInline = true,
                        isImage = true,
                        contentId = "chart-preview",
                        internalUriString = "content://taskmail/preview/workspace_chart.png",
                        accountUuid = "account-1",
                        folderId = 1L,
                        messageServerId = "msg-system-png",
                        partId = 1L,
                        isContentAvailable = true,
                    ),
                ),
            ),
        ),
    )

    val richStaticSvgSessionDetail = TaskSessionDetail(
        key = TaskSessionKey(
            sessionId = "session_svg_001",
            threadId = "thread_svg_001",
        ),
        workspace = workspaceKey,
        sessionName = "TaskMail HTML Static SVG Preview",
        backend = TaskMailBackend.Codex,
        status = TaskMailSessionStatus.Done,
        repoPath = "E:/projects/android_task_manager",
        workdir = "feature/taskmail",
        lastSummary = "Static SVG formula preview is attached below for the completed run.",
        replyContext = replyContext.copy(messageServerId = "msg-system-svg"),
        timeline = listOf(
            TaskTimelineItem(
                id = "timeline_svg_001",
                timestamp = 1_742_030_000_000,
                direction = TaskTimelineDirection.System,
                statusLabel = TaskMailStatusLabel.Done,
                summary = "Static SVG formula preview is attached below for the completed run.",
                body = richBody(
                    plainText = "Static SVG formula preview is attached below for the completed run.",
                    document = TaskRichTextDocument(
                        blocks = listOf(
                            TaskRichTextBlock.Heading(
                                level = 2,
                                inlines = listOf(TaskRichTextInline.Text("Formula output")),
                            ),
                            TaskRichTextBlock.Paragraph(
                                inlines = listOf(
                                    TaskRichTextInline.Text(
                                        "The backend produced a static SVG preview for the latest result.",
                                    ),
                                ),
                            ),
                            TaskRichTextBlock.InlineImage(
                                attachmentId = "attachment_svg_001",
                                contentId = "formula-preview",
                                altText = "Static SVG formula preview",
                                mimeType = "image/svg+xml",
                                isSvg = true,
                            ),
                        ),
                    ),
                ),
                attachments = listOf(
                    TaskMessageAttachment(
                        id = "attachment_svg_001",
                        displayName = "formula_preview.svg",
                        contentType = "image/svg+xml",
                        sizeBytes = 12_288L,
                        isInline = true,
                        isImage = true,
                        contentId = "formula-preview",
                        internalUriString = "content://taskmail/preview/formula_preview.svg",
                        accountUuid = "account-1",
                        folderId = 1L,
                        messageServerId = "msg-system-svg",
                        partId = 2L,
                        isContentAvailable = true,
                    ),
                ),
            ),
        ),
    )

    val richUnmatchedImageFallbackSessionDetail = TaskSessionDetail(
        key = TaskSessionKey(
            sessionId = "session_fallback_001",
            threadId = "thread_fallback_001",
        ),
        workspace = workspaceKey,
        sessionName = "TaskMail HTML External Image Fallback",
        backend = TaskMailBackend.Codex,
        status = TaskMailSessionStatus.Done,
        repoPath = "E:/projects/android_task_manager",
        workdir = "feature/taskmail",
        lastSummary = "External image references fell back to safe text " +
            "without blank content.",
        replyContext = replyContext.copy(messageServerId = "msg-system-fallback"),
        timeline = listOf(
            TaskTimelineItem(
                id = "timeline_fallback_001",
                timestamp = 1_742_040_000_000,
                direction = TaskTimelineDirection.System,
                statusLabel = TaskMailStatusLabel.Done,
                summary = "External image references fell back to safe text " +
                    "without blank content.",
                body = richBody(
                    plainText = "External image references fell back to safe text " +
                        "without blank content.",
                    document = TaskRichTextDocument(
                        blocks = listOf(
                            TaskRichTextBlock.Heading(
                                level = 2,
                                inlines = listOf(TaskRichTextInline.Text("Remote preview fallback")),
                            ),
                            TaskRichTextBlock.Paragraph(
                                inlines = listOf(
                                    TaskRichTextInline.Text(
                                        "Android ignored the unmatched external image reference " +
                                            "and kept the detail readable.",
                                    ),
                                ),
                            ),
                            TaskRichTextBlock.UnsupportedHtml(
                                fallbackText = "External chart preview unavailable.",
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    val sessionDetails: List<TaskSessionDetail> = listOf(
        questionSessionDetail,
        plainTextStatusSessionDetail,
        richInlinePngSessionDetail,
        richStaticSvgSessionDetail,
        richUnmatchedImageFallbackSessionDetail,
    )

    private fun plainTextBody(text: String): TaskMessageBody {
        return TaskMessageBody(
            plainTextFallback = text,
        )
    }

    private fun richBody(
        plainText: String,
        document: TaskRichTextDocument,
    ): TaskMessageBody {
        return TaskMessageBody(
            plainTextFallback = plainText,
            renderMode = TaskBodyRenderMode.RichText,
            richDocument = document,
        )
    }
}
