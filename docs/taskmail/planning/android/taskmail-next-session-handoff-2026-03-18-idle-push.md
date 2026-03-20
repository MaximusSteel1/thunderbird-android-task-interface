# TaskMail Next Session Handoff - 2026-03-18 Idle Push

## Scope

P0 migration away from TaskMail foreground polling on the tasks surfaces and toward mailbox-level IMAP IDLE push.

Why this slice is now highest priority:

- user-reported tasks-page instability is severe
- current TaskMail foreground refresh still performs repeated `checkMail(...)`
- local-store observation already exists, so the 10-second loop is the highest-value load source to remove first

## Read First

1. `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
2. `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
3. `docs/TASKMAIL-DEBUG-VALIDATION.md`
4. `docs/TASKMAIL-MAIL-RULES.md`
5. `docs/taskmail/planning/android/taskmail-idle-push-migration-plan-v0.1.md`
6. `docs/taskmail/planning/android/taskmail-foreground-scoped-live-refresh-plan-v0.1.md`
7. `docs/taskmail/planning/android/taskmail-refresh-live-update-plan-v0.1.md`

## Current Decision Boundary

- remove TaskMail-specific foreground polling first
- keep observer-driven local reloads
- keep manual refresh as fallback in the first implementation unless product direction changes
- reuse the existing mail push stack instead of inventing TaskMail-private push plumbing
- treat "default accounts still have push disabled folders" as an explicit implementation requirement, not as a footnote
- treat "IMAP backend" and "server actually supports IDLE" as separate checks

## Current Blocker

The locally configured user mailbox was probed on 2026-03-18 with successful IMAP login plus `CAPABILITY`.

Result:

- the current user mailbox server did not advertise `IDLE`

Implication:

- a true TaskMail IDLE implementation is blocked for the current local user mailbox
- do not proceed with an IDLE-only code migration in this environment unless the mailbox/provider path changes
- if product still needs a same-session freshness improvement on this mailbox, that is a different design problem from
  IMAP IDLE push

## Next Steps

1. Remove workspace/detail foreground ticker wiring and the related TaskMail contract/ViewModel code.
2. Add TaskMail push-readiness wiring for the resolved sender account.
3. Keep the first push scope narrow:
   - single resolved sender account only
   - push-capable backend only
   - Inbox-first enablement only
4. Decide and implement the fallback behavior for:
   - ambiguous sender-account state
   - non-push-capable accounts
   - inability to enable push on the scoped folder
5. Re-run focused TaskMail tests and narrow quality checks before widening anything repo-wide.

## This Session

- Code changes: none
- Validation: none
- Documentation:
  - added `docs/taskmail/planning/android/taskmail-idle-push-migration-plan-v0.1.md`
  - added this handoff note
  - updated both notes with the confirmed 2026-03-18 local user-mailbox `IDLE` blocker
