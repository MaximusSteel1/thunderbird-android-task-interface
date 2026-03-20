# TaskMail Next-Session Handoff (2026-03-20 local cache)

## Current decision boundary

This session moved Android TaskMail read-side persistence from planning into code:

- `UnifiedMessage` is now persisted locally under `feature:taskmail:internal`
- the default `TaskMailMessageSource` no longer reads directly from `EmailIngress`
- the new path is `EmailIngress -> TaskMailCacheSyncCoordinator -> file-backed cache -> CachedTaskMailMessageSource -> DefaultTaskMailRepository`

The current implementation intentionally keeps `DefaultTaskMailRepository` projection logic intact and changes only the
source of messages feeding that projection.

## Read first

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/TASKMAIL-MAIL-RULES.md`
- `docs/task_manager_migration_tasklist.md`
- `docs/taskmail/planning/android/taskmail-interface-extraction-inventory-v0.1.md`
- `docs/task_manager_migration_development_schedule.md`

## What landed this session

- added `FileBackedUnifiedMessageRepository`
- added `FileBackedMessageSyncStateRepository`
- added `EmailIngressPayloadJsonCodec`
- added `TaskMailCacheSyncCoordinator`
- added `CachedTaskMailMessageSource`
- wired TaskMail DI to use the cached source by default
- preserved `LegacyTaskMailMessageSource` as a non-default adapter
- added unit coverage for bootstrap sync and steady-state incremental sync

## Important behavior now

- first read bootstraps the local cache from a wide local-mail scan
- once sync state exists, steady-state reads use incremental sync with the recent-window default of `10`
- `EmailIngress.fetchSince()` still falls back to `fetchLatest(recentMessageLimit)` for now, so the cursor is persisted
  and consumed by the coordinator contract, but true cursor-aware mailstore delta still remains follow-up work

## Next concrete steps

1. Move workspace/detail refresh and store-change handling onto explicit cache sync semantics instead of relying on
   repository read-triggered incremental sync.
2. Decide whether the file-backed cache remains the durable implementation for this phase or whether it should migrate
   into a stronger storage layer before relay transport work begins.
3. Tighten bootstrap/recovery policy so "wide scan" becomes a controlled window with explicit fallback rules rather than
   `Int.MAX_VALUE`.
4. Update current-status / validation-ledger docs after manual smoke confirms the local-cache path behaves correctly on
   device.

## Validation this session

Code changes were made in this session.

Validation completed:

- `.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest`
- `.\gradlew.bat :feature:taskmail:internal:detekt`
- `.\gradlew.bat :feature:taskmail:internal:lintDebug`
- `.\gradlew.bat :feature:taskmail:internal:spotlessCheck`
