# TaskMail Next Session Handoff (2026-03-20, workspace local-first fix)

## Scope closed in this session

- `TaskWorkspaceViewModel` no longer waits for cache sync before showing cached workspace snapshots on a fresh
  `Tasks` entry.
- `SyncTaskMailCache` now runs on `Dispatchers.IO`, so cache sync, snapshot rebuild, and file-backed repository access
  no longer execute on the UI coroutine path by default.

## Pitfall recorded

- Symptom: entering `Tasks` a second time could still show a long blank loading state, and automatic refresh felt
  visibly janky.
- Trigger: a new `TaskWorkspaceViewModel` instance always treated `LoadData` as a first load and blocked on
  `syncTaskMailCache()` before reading local snapshots.
- Confirmed cause: workspace snapshot reads were sequenced after sync, and sync itself was rebuilding session details
  and hitting file-backed cache repositories on the caller context.
- Practical avoidance: keep workspace entry local-first. Show cached summaries immediately when present, then run sync in
  the background; keep cache rebuild work off the main/UI path.

## Read first

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/TASKMAIL-MAIL-RULES.md`
- `docs/taskmail/planning/android/taskmail-refresh-live-update-plan-v0.1.md`

## Validation performed

- `.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest :feature:taskmail:internal:detekt :feature:taskmail:internal:lintDebug :feature:taskmail:internal:spotlessCheck`
- `.\gradlew.bat :feature:taskmail:internal:spotlessApply`

## Remaining follow-up

- Re-smoke the device path for `Tasks` second entry and foreground auto-refresh to confirm the blank interval and jank
  are materially reduced.
- If detail page or project sync still show similar pauses, apply the same "snapshot first, sync after" rule there
  instead of reintroducing sync-before-read timing.
