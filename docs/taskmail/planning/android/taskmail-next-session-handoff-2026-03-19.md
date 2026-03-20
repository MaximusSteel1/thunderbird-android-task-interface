# TaskMail Next Session Handoff - 2026-03-19

## Scope

TaskMail rich-text detail body Batch 1/2 plus Batch 3, a minimal Batch 4 renderer, and Batch 5 preview/test corpus are
now implemented and narrowly validated, including a focused live-device inline-image placeholder pass plus fresh-build
controlled device-preview validation for static SVG and unmatched-image fallback.

The current frozen direction is:

- controlled rich-text rendering in Task Detail, not a whole-page WebView
- `text/plain` remains the protocol truth source
- Android consumes `article.task-mail` as the HTML body unit
- inline body images bind through `cid:` to attachment `contentId`
- static attached `image/svg+xml` follows the existing inline-image path
- workspace/session summary stays plain-text summary driven
- current runtime now projects supported HTML into `TaskRichTextDocument` and renders supported blocks in detail
- inline image/static SVG content currently renders as a controlled placeholder card, not a true visual preview

## Read First

1. `docs/TASKMAIL-MAIL-RULES.md`
2. `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
3. `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
4. `docs/taskmail/planning/android/taskmail-rich-text-body-model-draft.md`
5. `docs/taskmail/planning/android/taskmail-rich-text-body-implementation-checklist.md`
6. `docs/taskmail/planning/android/taskmail-rich-text-protocol-freeze-note.md`
7. `E:\projects\mail_based_task_manager\docs/current/pc_mail_output_protocol.md`
8. `E:\projects\mail_based_task_manager\docs/current/multimedia_mail_protocol.md`

## Next Steps

1. Decide whether inline image/SVG should remain placeholder-only on Android or grow into true attachment preview now
   that live `thread_071` plus controlled fresh-build static SVG / unmatched-fallback device checks are closed.
2. Widen broader live-mailbox device validation beyond the current `thread_071` placeholder pass and controlled preview
   samples before attempting any richer renderer expansion.
3. Widen repository/UI tests around unsupported HTML, unmatched/external image fallback, and attachment-row parity if
   drift appears.
4. Do not widen into WebView, formula-specific models, or summary/list rich rendering during the next coding slice.

## This Session

- Code changes:
  - preserved optional `htmlBody` in `TaskMailEnvelope` and `TaskMailMessage`
  - updated `LegacyTaskMailMessageSource` to extract only a real `text/html` MIME part
  - widened `TaskMessageBody` to the new source/model boundary
  - projected supported `article.task-mail` HTML into `TaskRichTextDocument` inside the repository
  - widened detail UI state/timeline rendering to prefer controlled rich-text blocks with plain-text fallback preserved
  - restricted inline image projection to exact `cid:` / `contentId` matches and degraded unmatched/external images to
    safe fallback text
  - added preview corpus for plain-text status, inline PNG, static SVG-rich Task Detail, and unmatched-image fallback
    Task Detail samples
  - added focused regression coverage proving workspace/session summaries stay plain-text based when detail bodies carry
    supported HTML
  - added a debug-only Task Detail preview deep-link path for controlled on-device renderer validation without relying on
    live mailbox corpus
- Validation:
  - `.\gradlew.bat :feature:taskmail:internal:spotlessApply`
  - `.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest :feature:taskmail:internal:detekt :feature:taskmail:internal:lintDebug :feature:taskmail:internal:spotlessCheck --continue`
  - `.\gradlew.bat :app-thunderbird:assembleFossDebug`
  - focused live-device validation on the existing `net.thunderbird.android.debug` install against
    `shaking_table_executor -> thread_071`, confirming the current inline-image placeholder path and unchanged
    plain-text workspace/session summaries
  - later, after user-approved uninstall of the incompatible debug install, the freshly built APK was installed
    manually on-device after `adb install` hit `INSTALL_FAILED_USER_RESTRICTED`, and controlled preview deep links
    `app://taskmail/preview/detail/static-svg` plus
    `app://taskmail/preview/detail/unmatched-image-fallback` were validated on the latest build
- Documentation:
  - froze Android rich-text consumer assumptions in planning docs and authority docs
  - aligned PC-side current docs for `article.task-mail`, `cid:` / `contentId`, and static SVG expectations
  - updated Android current-status and validation-ledger so Batch 1/2, Batch 3, the minimal Batch 4 renderer, and Batch
    5 preview/test corpus are recorded as implemented and narrowly validated, including the focused live-device
    placeholder result plus fresh-build controlled device-preview evidence
