# TaskMail Rich Text Body Model Draft

## Status

- Date: 2026-03-19
- Scope: Android-side model draft for TaskMail detail rich-text body rendering
- Layer: implementation-reference draft under `docs/taskmail/planning/android/`
- Current authority remains:
  - `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
  - `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
  - `docs/TASKMAIL-MAIL-RULES.md`
  - current PC-side protocol docs in `E:\projects\mail_based_task_manager\docs/current/`

This document does not redefine protocol authority.
It freezes a practical Android-side model shape for the next implementation slice.

## Purpose

Define the minimal Android model changes needed to support:

- controlled rich-text rendering in TaskMail detail,
- inline image and static SVG preview inside the detail timeline,
- continued plain-text parsing authority for protocol facts,
- future evolution without pulling workspace/session summary models into the same refactor.

## Current Baseline

Current repository behavior is still plain-text-first in the read path:

- `TaskMailEnvelope` only carries `plainTextBody`
- `TaskMailMessage` only carries `rawBodyText`
- `TaskMessageBody` only carries `plainText` plus `markdownCandidate`
- timeline rendering uses plain-text body only
- workspace/session summary cards only consume `lastSummary`

This means HTML is currently lost too early in the Android read path for a controlled rich-text renderer to use it.

## Scope Boundary

This draft is intentionally narrow.

It changes the body/timeline path for Task Detail.
It does **not** change the authority or shape of:

- `TaskWorkspaceSummary`
- `TaskSessionSummary`
- `lastSummary`
- state-capsule parsing
- question-capsule parsing
- reply serialization truth rules

Summary/list/header surfaces remain plain-text summary driven.

## Key Decisions

1. Android detail uses controlled rich-text rendering, not a whole-page WebView.
2. `text/plain` remains the protocol truth source for parsing, routing, question handling, and state extraction.
3. `text/html` is a display input only.
4. Formula support does not introduce a Math model in v1.
5. Formula-like output is expected to arrive from the PC side as static SVG and travel through the same inline-image
   path as other attached previewable images.
6. Existing attachment identity remains the anchor for inline previews; no separate Android-only asset registry is
   introduced in this slice.

## Proposed Model Changes

### 1. Source envelope should preserve HTML

Current `TaskMailEnvelope` should be widened from plain text only to dual-body input:

```kotlin
internal data class TaskMailEnvelope(
    val messageId: String,
    val subject: String,
    val fromAddress: String,
    val timestamp: Long,
    val inReplyTo: String? = null,
    val references: List<String> = emptyList(),
    val plainTextBody: String,
    val htmlBody: String? = null,
)
```

Rules:

- `plainTextBody` remains mandatory for Android protocol parsing.
- `htmlBody` is optional and may be absent on older or plain-text-only mail.
- missing or invalid HTML must not block timeline creation.

### 2. Raw message model should preserve the same dual-body boundary

`TaskMailMessage` should keep both bodies so repository/timeline mapping can decide how to render without re-reading MIME:

```kotlin
internal data class TaskMailMessage(
    val accountUuid: String,
    val folderId: Long,
    val messageServerId: String,
    val threadRootId: Long,
    val timestamp: Long,
    val subject: String,
    val rawBodyText: String,
    val htmlBody: String? = null,
    val internetMessageId: String? = null,
    val attachments: List<TaskMessageAttachment> = emptyList(),
    val detection: TaskMailDetection,
    val isFromCurrentUser: Boolean,
)
```

Rules:

- `rawBodyText` remains the existing plain-text extraction result used by detector/body fallback.
- `htmlBody` is the raw HTML part preserved for controlled rich-text projection.
- attachment metadata remains the bridge for `cid:` and inline-image resolution.

### 3. `TaskMessageBody` should become a renderable body model

Current `TaskMessageBody` is too narrow for controlled rich text and future inline SVG.

Recommended replacement:

```kotlin
internal data class TaskMessageBody(
    val plainTextFallback: String,
    val renderMode: TaskBodyRenderMode,
    val richDocument: TaskRichTextDocument? = null,
    val sourceHtml: String? = null,
)

internal enum class TaskBodyRenderMode {
    PlainTextOnly,
    RichText,
}
```

Rules:

- `plainTextFallback` is always the safe fallback body for rendering and debugging.
- `renderMode` decides whether the UI tries rich rendering.
- `richDocument` is optional and only populated after successful HTML-to-model projection.
- `sourceHtml` may be retained short-term for debugging and parity testing, but UI should render `richDocument`, not raw
  HTML.

### 4. Rich document should be structured, not HTML-string driven

Recommended document shape:

```kotlin
internal data class TaskRichTextDocument(
    val blocks: List<TaskRichTextBlock>,
)

internal sealed interface TaskRichTextBlock {
    data class Paragraph(val inlines: List<TaskRichTextInline>) : TaskRichTextBlock
    data class Heading(
        val level: Int,
        val inlines: List<TaskRichTextInline>,
    ) : TaskRichTextBlock
    data class Quote(val blocks: List<TaskRichTextBlock>) : TaskRichTextBlock
    data class CodeBlock(
        val languageHint: String? = null,
        val text: String,
    ) : TaskRichTextBlock
    data class BulletList(val items: List<List<TaskRichTextBlock>>) : TaskRichTextBlock
    data class OrderedList(val items: List<List<TaskRichTextBlock>>) : TaskRichTextBlock
    data class Table(
        val headers: List<List<TaskRichTextInline>>,
        val rows: List<List<List<TaskRichTextInline>>>,
    ) : TaskRichTextBlock
    data class InlineImage(
        val attachmentId: String? = null,
        val contentId: String? = null,
        val altText: String? = null,
        val caption: String? = null,
        val mimeType: String? = null,
        val isSvg: Boolean = false,
    ) : TaskRichTextBlock
    data object Divider : TaskRichTextBlock
    data class UnsupportedHtml(val fallbackText: String) : TaskRichTextBlock
}

internal sealed interface TaskRichTextInline {
    data class Text(val text: String) : TaskRichTextInline
    data class Strong(val text: String) : TaskRichTextInline
    data class Emphasis(val text: String) : TaskRichTextInline
    data class Code(val text: String) : TaskRichTextInline
    data class Link(
        val text: String,
        val href: String,
    ) : TaskRichTextInline
}
```

Important v1 rule:

- no dedicated `MathInline` or `MathBlock` is introduced
- static SVG formula output rides through `InlineImage`

### 5. Timeline model changes stay local to detail

`TaskTimelineItem` keeps its role, but `body` now points at the richer model:

```kotlin
internal data class TaskTimelineItem(
    val id: String,
    val timestamp: Long,
    val direction: TaskTimelineDirection,
    val statusLabel: TaskMailStatusLabel? = null,
    val summary: String? = null,
    val body: TaskMessageBody,
    val attachments: List<TaskMessageAttachment> = emptyList(),
)
```

This is intentionally a local detail/timeline change.
`TaskSessionSummary` and `TaskWorkspaceSummary` remain plain-text summary models.

### 6. Existing attachment model is already sufficient for v1

Current `TaskMessageAttachment` already carries the most important identity needed for inline preview:

- `id`
- `contentId`
- `contentType`
- `isInline`
- `isImage`

That is sufficient for first-round HTML image/SVG projection.

No formula-specific attachment model is needed in this slice.

## Parsing And Rendering Rules

### Parsing truth rules

- session detection stays bound to `plainTextBody`
- state-capsule parsing stays bound to `plainTextBody`
- question-capsule parsing stays bound to `plainTextBody`
- run-result parsing stays bound to plain-text machine blocks
- HTML must never become the only fact source

### HTML-to-model projection rules

- rich-text projection runs only after protocol detection/parsing has already succeeded
- unsupported or unsafe HTML falls back to `PlainTextOnly`
- unknown tags may be dropped or mapped to `UnsupportedHtml`, but must not break the whole mail item
- remote images are not rendered inline
- `cid:` images must resolve through current attachment metadata
- externally delivered files are not inline body images for the current mail

### Rendering rules

- detail UI renders `richDocument` when `renderMode == RichText`
- if rich rendering fails, fall back to `plainTextFallback`
- inline SVG uses the same image block path as other previewable attachments
- summary/list/header surfaces continue to display plain-text summary only

## Explicit Non-Changes

This draft does not introduce:

- a TaskMail WebView page
- Markdown as an Android truth format
- HTML-driven state parsing
- formula-specific protocol fields
- cross-workspace/session summary rich rendering

## Recommended First Implementation Order

1. preserve `htmlBody` in `TaskMailEnvelope` and `TaskMailMessage`
2. widen `TaskMessageBody` and local timeline mapping
3. map HTML into `TaskRichTextDocument` for a narrow supported subset
4. keep `TaskSessionSummary` / `TaskWorkspaceSummary` unchanged
5. add preview/test corpus with plain text, inline images, and static SVG
6. add the Compose renderer after the model boundary is stable

## Done When

- Android no longer drops HTML at the source/message boundary
- Task Detail can carry a structured rich-text document plus plain-text fallback
- inline image and static SVG preview can be represented without introducing a Math model
- protocol truth remains anchored to plain text
- workspace/session summary models remain untouched by the body-rendering slice
