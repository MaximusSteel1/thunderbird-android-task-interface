# TaskMail Next Session Handoff (2026-03-20, refactor closeout)

## Scope boundary

The Android split-architecture refactor main path is largely landed.

This note records what is still not fully closed before Android should describe the refactor as completely settled.

It does not redefine protocol authority.

## Read first

1. `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
2. `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
3. `docs/TASKMAIL-MAIL-RULES.md`
4. `docs/task_manager_migration_development_schedule.md`
5. `docs/taskmail/planning/android/taskmail-next-session-handoff-2026-03-20-relay-vps-bootstrap.md`

## What is already true

- Android read-side layering now has explicit ingress / parser / sync seams.
- `UnifiedMessage` local persistence is on the main read path.
- `Tasks` and session detail both load local data first instead of blocking on sync before every entry.
- Session-detail snapshots now rebuild incrementally first and only fall back to full rebuild for bootstrap / recovery.
- Reply, new-task, and project-sync request flows now route through transport seams while email remains the only
  production transport.

## Closeout items still open

1. Workspace summary projection
   Detail snapshot storage is now ahead of workspace. Decide whether workspace should stay as cached-message
   aggregation or move to a dedicated incremental summary snapshot before calling the Android read-side refactor fully
   closed.
2. Strict delta fetch
   `fetchSince()` is still effectively a narrow recent-window scan plus thread expansion, not a true strict cursor
   delta query.
3. Foreground polling policy
   The foreground-only 10-second refresh loop is still present. The remaining product decision is whether to keep it,
   relax it, or remove it in favor of local mail-store change observation plus manual refresh.
4. Validation closeout
   Focused TaskMail-internal checks are in place, but repo-wide quality gates, `connectedAndroidTest`, and fresh
   device/manual smoke for the migrated local-first behavior are still open.
5. Relay/VPS transport
   Relay/VPS is now the next major engineering stream, but Android mail behavior remains the production baseline until a
   separate relay slice lands and is revalidated.

## Practical next steps

1. Finish the remaining Android closeout decisions above.
2. Keep email behavior stable while those closeout items are resolved.
3. Then begin the first Android relay/VPS slice as a transport-integration effort, not as a protocol rewrite.

## Validation performed in this session

- none

## Code changes performed in this session

- none
- documentation only
