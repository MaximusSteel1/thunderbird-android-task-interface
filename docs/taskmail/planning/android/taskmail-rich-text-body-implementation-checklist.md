# TaskMail Rich Text Body Implementation Checklist

Updated: 2026-03-19

## Status

This document is an active implementation-reference checklist for the next Android TaskMail detail body slice.

Primary inputs:

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/TASKMAIL-MAIL-RULES.md`
- `docs/taskmail/planning/android/taskmail-rich-text-body-model-draft.md`
- `docs/taskmail/planning/android/thunderbird_task_detail_dev_plan_aligned.md`
- `E:\projects\mail_based_task_manager\docs/current/pc_mail_output_protocol.md`
- `E:\projects\mail_based_task_manager\docs/current/multimedia_mail_protocol.md`
- `E:\projects\mail_based_task_manager\docs/plans/p9_html_mail_projection_plan.md`

This checklist does not create a new protocol.
It translates the already chosen Android-side model direction into bounded implementation work.

## Scope

This slice includes:

- preserving `htmlBody` in the Android read path
- widening the body/timeline domain model for controlled rich text
- resolving inline image/static SVG references through current attachment metadata
- adding a first-round detail renderer that prefers rich text and falls back to plain text
- adding preview/test coverage for plain text, inline images, and static SVG

This slice does not include:

- WebView-based task detail rendering
- workspace/session summary rich rendering
- changing reply semantics
- changing state/question/run-result parsing authority
- formula-specific protocol fields or Android math layout
- cross-workspace/session-switch UI work

## Mandatory Guardrails

- `text/plain` remains the fact source for protocol parsing.
- `text/html` is a display input only.
- summary/list/header surfaces stay plain-text summary driven.
- static SVG travels through the same inline-image path as other previewable attachments.
- unsupported or unsafe HTML must degrade to plain-text rendering, not to broken UI.

## Stop Rules

Stop and document the blocker before widening scope if any of the following happen:

- the current local-message read path cannot reliably expose the HTML part
- `cid:` references cannot be matched back to current attachment metadata without heuristic guesswork
- the proposed body model starts forcing changes into workspace/session summary models
- the implementation starts drifting toward a whole-page WebView instead of controlled rich-text blocks

Do not paper over missing HTML by trying to reconstruct rich text from plain text.

Implementation pitfall now confirmed in this repository:

- do not use `BodyTextExtractor.getBodyTextFromMessage(..., SimpleMessageFormat.HTML)` to populate `htmlBody`
- that helper will synthesize HTML from `text/plain` when no real `text/html` part exists
- for this slice, `htmlBody` must mean a real `text/html` MIME part only

## Batch 1: Source Preservation

Goal:

- keep both plain text and HTML available after mail read so later layers do not need MIME re-access.

Files to inspect/change first:

- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/parser/TaskMailEnvelope.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/data/TaskMailMessageSource.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/data/LegacyTaskMailMessageSource.kt`

Checklist:

- [x] widen `TaskMailEnvelope` to carry `htmlBody`
- [x] widen `TaskMailMessage` to carry `htmlBody`
- [x] preserve current `rawBodyText` path for protocol parsing fallback
- [x] extract HTML from the local message without breaking the current plain-text path
- [x] keep attachment metadata, especially `contentId`, intact for later inline resolution

Done when:

- repository mapping can see both `rawBodyText` and optional `htmlBody`
- older/plain-text-only mail still works unchanged

## Batch 2: Domain Model Freeze

Goal:

- freeze the Kotlin model boundary before writing renderer code.

Files to inspect/change:

- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/model/TaskMessageBody.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/model/TaskTimelineItem.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/model/TaskSessionDetail.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/model/TaskSessionSummary.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/model/TaskWorkspaceSummary.kt`

Recommended additions:

- `TaskBodyRenderMode`
- `TaskRichTextDocument`
- `TaskRichTextBlock`
- `TaskRichTextInline`

Checklist:

- [x] replace the plain-text-only `TaskMessageBody` shape with the richer body model from the draft
- [x] keep `TaskTimelineItem` pointing at `TaskMessageBody`
- [x] keep `TaskSessionSummary` unchanged
- [x] keep `TaskWorkspaceSummary` unchanged
- [x] do not introduce `MathBlock`; use `InlineImage(isSvg = true)` instead

Done when:

- the domain model can represent rich text, inline images, and plain-text fallback without touching summary models

## Batch 3: Repository Projection

Goal:

- map mail source data into the new body model while keeping parsing truth unchanged.

Files to inspect/change:

- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/data/DefaultTaskMailRepository.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/data/LegacyTaskMailBodyExtractor.kt`
- new helper under `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/data/` or `domain/`

Checklist:

- [x] keep detector/state/question parsing bound to plain text
- [x] continue deriving timeline summary from state/plain text, not HTML
- [x] build `TaskMessageBody` with `plainTextFallback` always present
- [x] set `renderMode = PlainTextOnly` when HTML is absent or cannot be safely projected
- [x] project supported HTML into `TaskRichTextDocument`
- [x] resolve inline images through existing attachment `contentId`
- [x] treat externally delivered files as non-inline for the current message body

Done when:

- repository can emit mixed timeline items where some are plain text only and others are rich text with fallback

## Batch 4: UI State And Renderer

Goal:

- render the new body model in detail without changing the rest of TaskMail UI shape.

Files to inspect/change:

- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/TaskSessionDetailViewModel.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/TaskSessionDetailUiState.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/TaskSessionDetailContent.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/component/TimelineMessageCard.kt`
- new renderer components under `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/component/`

Checklist:

- [x] widen UI state so timeline rows can carry rich body data, not only `plainText`
- [x] keep the existing summary/status/attachment layout stable where possible
- [x] add controlled block renderers for paragraph, heading, quote, code, list, table, divider, inline image
- [x] keep unsupported blocks on a safe fallback path
- [x] keep plain-text rendering available as the universal fallback
- [x] do not introduce a TaskMail WebView

Done when:

- detail can render supported rich bodies and safely degrade unsupported bodies to plain text

## Batch 5: Preview And Test Corpus

Goal:

- lock the new model and renderer with representative local examples before device-only validation.

Files to inspect/change:

- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/preview/TaskMailPreviewData.kt`
- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/data/`
- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/`

Checklist:

- [x] add preview data for plain-text-only status mail
- [x] add preview data for HTML mail with inline PNG
- [x] add preview data for HTML mail with static SVG formula-like output
- [x] add repository/projection tests for HTML-present and HTML-absent bodies
- [x] add UI tests for rich-text rendering fallback behavior
- [x] add tests proving summaries still remain plain-text based

Done when:

- model, mapping, and first-round renderer are covered by focused local tests and preview data

## Batch 6: Verification

Run the narrowest relevant checks first:

- [x] `.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest`
- [x] `.\gradlew.bat :feature:taskmail:internal:detekt`
- [x] `.\gradlew.bat :feature:taskmail:internal:lintDebug`
- [x] `.\gradlew.bat :feature:taskmail:internal:spotlessCheck`

Then expand only if the slice is stable and not blocked by unrelated failures.

Manual/device validation should specifically confirm:

- HTML body is readable in Task Detail
- plain-text-only messages still render correctly
- inline image preview works
- static SVG falls back cleanly if inline preview cannot be shown
- summary/workspace cards remain unchanged

Current closeout status:

- confirmed on 2026-03-19 for readable Task Detail HTML, the current inline-image placeholder text path on live
  `thread_071`, unchanged plain-text workspace/session cards, fresh-build controlled device preview for static SVG
  placeholder rendering, and fresh-build controlled device preview for unmatched-image safe text fallback
- still open for true inline image preview and broader live-mailbox rich-text device coverage

## One-Line Execution Rule

Freeze the source/body/timeline model first, then repository projection, then UI renderer, and only after that widen
validation.
