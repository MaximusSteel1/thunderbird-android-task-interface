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

    val richExternalDeliveriesSessionDetail = TaskSessionDetail(
        key = TaskSessionKey(
            sessionId = "session_external_delivery_001",
            threadId = "thread_external_delivery_001",
        ),
        workspace = workspaceKey,
        sessionName = "TaskMail External Delivery Sample",
        backend = TaskMailBackend.Codex,
        status = TaskMailSessionStatus.Done,
        repoPath = "E:/projects/android_task_manager",
        workdir = "feature/taskmail",
        lastSummary = "External delivery links remain readable on Android.",
        replyContext = replyContext.copy(messageServerId = "msg-system-external-delivery"),
        timeline = listOf(
            TaskTimelineItem(
                id = "timeline_external_delivery_001",
                timestamp = 1_742_050_000_000,
                direction = TaskTimelineDirection.System,
                statusLabel = TaskMailStatusLabel.Done,
                summary = "External delivery links remain readable on Android.",
                body = richBody(
                    plainText = "External delivery links remain readable on Android.",
                    document = TaskRichTextDocument(
                        blocks = listOf(
                            TaskRichTextBlock.Heading(
                                level = 2,
                                inlines = listOf(TaskRichTextInline.Text("Reply")),
                            ),
                            TaskRichTextBlock.Paragraph(
                                inlines = listOf(
                                    TaskRichTextInline.Text(
                                        "APK smoke reply body.",
                                    ),
                                ),
                            ),
                            TaskRichTextBlock.Heading(
                                level = 2,
                                inlines = listOf(TaskRichTextInline.Text("Artifacts")),
                            ),
                            TaskRichTextBlock.BulletList(
                                items = listOf(
                                    listOf(
                                        TaskRichTextBlock.Paragraph(
                                            inlines = listOf(
                                                TaskRichTextInline.Text("thunderbird-taskmail-foss-debug.apk"),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                            TaskRichTextBlock.Heading(
                                level = 2,
                                inlines = listOf(TaskRichTextInline.Text("External Deliveries")),
                            ),
                            TaskRichTextBlock.BulletList(
                                items = listOf(
                                    listOf(
                                        TaskRichTextBlock.Paragraph(
                                            inlines = listOf(
                                                TaskRichTextInline.Link(
                                                    text = "thunderbird-taskmail-foss-debug.apk",
                                                    href = "https://mailbot-1412015279.cos.ap-shanghai.myqcloud.com/" +
                                                        "mail-runner/thread_072/20260320_001950_88c5/" +
                                                        "thunderbird-taskmail-foss-debug.apk.bin",
                                                ),
                                                TaskRichTextInline.Text(
                                                    ": Delivered via COS (46.7 MB, expires " +
                                                        "2026-03-26T16:25:17+00:00)",
                                                ),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    val richAttachmentNoticesSessionDetail = TaskSessionDetail(
        key = TaskSessionKey(
            sessionId = "session_attachment_notice_001",
            threadId = "thread_attachment_notice_001",
        ),
        workspace = workspaceKey,
        sessionName = "TaskMail Attachment Notice Sample",
        backend = TaskMailBackend.Codex,
        status = TaskMailSessionStatus.Done,
        repoPath = "E:/projects/android_task_manager",
        workdir = "feature/taskmail",
        lastSummary = "Attachment notice copy remains readable on Android.",
        replyContext = replyContext.copy(messageServerId = "msg-system-attachment-notice"),
        timeline = listOf(
            TaskTimelineItem(
                id = "timeline_attachment_notice_001",
                timestamp = 1_742_060_000_000,
                direction = TaskTimelineDirection.System,
                statusLabel = TaskMailStatusLabel.Done,
                summary = "Attachment notice copy remains readable on Android.",
                body = richBody(
                    plainText = "Attachment notice copy remains readable on Android.",
                    document = TaskRichTextDocument(
                        blocks = listOf(
                            TaskRichTextBlock.Heading(
                                level = 2,
                                inlines = listOf(TaskRichTextInline.Text("External Deliveries")),
                            ),
                            TaskRichTextBlock.BulletList(
                                items = listOf(
                                    listOf(
                                        TaskRichTextBlock.Paragraph(
                                            inlines = listOf(
                                                TaskRichTextInline.Link(
                                                    text = "app-thunderbird-foss-debug-20260320-taskmail-" +
                                                        "detail-incremental.apk",
                                                    href = "https://mailbot-1412015279.cos.ap-shanghai.myqcloud.com/" +
                                                        "mail-runner/thread_075/20260320_162935_094f/" +
                                                        "app-thunderbird-foss-debug-20260320-taskmail-detail-" +
                                                        "incremental.apk.bin",
                                                ),
                                                TaskRichTextInline.Text(
                                                    ": Delivered via COS (46.8 MB, expires " +
                                                        "2026-03-27T08:32:04+00:00)",
                                                ),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                            TaskRichTextBlock.Heading(
                                level = 2,
                                inlines = listOf(TaskRichTextInline.Text("Attachment Notices")),
                            ),
                            TaskRichTextBlock.BulletList(
                                items = listOf(
                                    listOf(
                                        TaskRichTextBlock.Paragraph(
                                            inlines = listOf(
                                                TaskRichTextInline.Text(
                                                    "COS default domain blocks direct APK distribution. " +
                                                        "The external download uses a .bin object name; " +
                                                        "rename the downloaded file back to " +
                                                        "app-thunderbird-foss-debug-20260320-taskmail-" +
                                                        "detail-incremental.apk if needed.",
                                                ),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    val richFailedLongErrorSessionDetail = TaskSessionDetail(
        key = TaskSessionKey(
            sessionId = "session_failed_long_error_001",
            threadId = "thread_failed_long_error_001",
        ),
        workspace = workspaceKey,
        sessionName = "TaskMail Failed Long Error Sample",
        backend = TaskMailBackend.Codex,
        status = TaskMailSessionStatus.Failed,
        repoPath = "E:/projects/android_task_manager",
        workdir = "feature/taskmail",
        lastSummary = "Long failure output remains readable on Android.",
        replyContext = replyContext.copy(messageServerId = "msg-system-failed-long-error"),
        timeline = listOf(
            TaskTimelineItem(
                id = "timeline_failed_long_error_001",
                timestamp = 1_742_070_000_000,
                direction = TaskTimelineDirection.System,
                statusLabel = TaskMailStatusLabel.Failed,
                summary = "Long failure output remains readable on Android.",
                body = richBody(
                    plainText = "Long failure output remains readable on Android.",
                    document = TaskRichTextDocument(
                        blocks = listOf(
                            TaskRichTextBlock.Heading(
                                level = 2,
                                inlines = listOf(TaskRichTextInline.Text("Failure Output")),
                            ),
                            TaskRichTextBlock.Paragraph(
                                inlines = listOf(
                                    TaskRichTextInline.Text(
                                        "Representative failed-mail content stays scannable without " +
                                            "collapsing the detail view.",
                                    ),
                                ),
                            ),
                            TaskRichTextBlock.CodeBlock(
                                languageHint = "text",
                                text = """
                                    Error: Codex Exec exited with code 1: 2026-03-18T12:23:54.044393Z ERROR codex_core::models_manager::manager: failed to refresh available models: timeout waiting for child process to exit
                                    Reading prompt from stdin...
                                    2026-03-18T12:23:59.102450Z ERROR codex_core::models_manager::manager: failed to refresh available models: timeout waiting for child process to exit

                                        at CodexExec.run (file:///E:/projects/mail_based_task_manager/scripts/codex_sdk_sidecar/node_modules/@openai/codex-sdk/dist/index.js:280:15)
                                        at process.processTicksAndRejections (node:internal/process/task_queues:103:5)
                                        at async Thread.runStreamedInternal (file:///E:/projects/mail_based_task_manager/scripts/codex_sdk_sidecar/node_modules/@openai/codex-sdk/dist/index.js:78:24)
                                        at async main (file:///E:/projects/mail_based_task_manager/scripts/codex_sdk_sidecar/dist/index.js:151:22)
                                """.trimIndent(),
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
        richExternalDeliveriesSessionDetail,
        richAttachmentNoticesSessionDetail,
        richFailedLongErrorSessionDetail,
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
