# TaskMail Next-Session Handoff - 2026-03-20 Inline Image Follow-up

## Scope Boundary

This follow-up closed the Android repository/UI work for two TaskMail detail issues seen in live-device screenshots:

- duplicate timeline image attachments when one physical part appeared through both inline and attachment paths
- placeholder-only inline preview for raster images even when Android already had a local attachment content URI

Static SVG was kept on the controlled placeholder/fallback path in this slice.

## Read First

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/TASKMAIL-MAIL-RULES.md`
- `E:\projects\mail_based_task_manager\_tmp_live_mail_runner\tasks\thread_072\mail\raw_038.json`

## What Changed

- repository timeline mapping now filters/deduplicates timeline attachments before detail projection
- raster inline images now render from the attachment `internalUriString` when available
- static SVG remains fallback-only
- focused repository, ViewModel, and screen tests were added for:
  - attachment deduplication
  - raster inline preview path
  - SVG fallback path
  - `image/svg` compatibility for SVG recognition

## Validation Performed

- `.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest --continue`
- `.\gradlew.bat :feature:taskmail:internal:detekt --continue`
- `.\gradlew.bat :feature:taskmail:internal:lintDebug --continue`

All three passed on 2026-03-20.

`.\gradlew.bat :feature:taskmail:internal:spotlessCheck --continue` is still blocked by existing module-wide
line-ending / formatting drift outside this slice.

## Next Concrete Steps

1. Build and install a fresh Thunderbird debug APK containing this follow-up.
2. Re-run the live-thread smoke on the same TaskMail message with one PNG and one SVG attachment.
3. Confirm the attachment section shows exactly two attachments, not duplicated PNG/SVG rows.
4. Confirm the PNG inline preview now renders as a real bitmap preview in `Inline Previews`.
5. Confirm SVG still shows the controlled fallback card and does not blank, overflow, or crash.
6. Re-check whether `Open` still resolves to an undesirable external app choice on that device; treat that as a
   separate follow-up from the inline-preview fix if it still reproduces.

## Current Open Risk

The repository/UI path is now different from the last live-device screenshot evidence. Fresh device validation is still
required before this rich-text image slice can be called closed.
