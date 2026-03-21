# TaskMail Next Session Handoff (2026-03-21, public plaintext direct-connect reset)

## Current Decision

As of 2026-03-21, the Android-side planning direction has been reset to:

- Android public direct-connect as the intended main TaskMail path
- public plaintext transport accepted as the chosen baseline
- mail retained as fallback

The Android-side planning closeout for Phase 0 is now in place.
This is no longer doc-only.
The Android-side Phase 1 bootstrap-manager, bootstrap-classification, and `new task` bootstrap-reuse slices are now in
repository. The Android-side repository dependency that kept bootstrap trapped behind the debug surface is no longer the
main blocker, but direct business transport and live plaintext validation are still not closed.

## Read First

1. `docs/taskmail/planning/android/taskmail-android-public-plaintext-direct-connect-authority-v0.1.md`
2. `docs/taskmail/planning/android/taskmail-android-public-plaintext-direct-connect-plan-v0.1.md`
3. `docs/taskmail/planning/android/taskmail-phase0-public-plaintext-baseline-v1.md`
4. `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
5. `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
6. `docs/TASKMAIL-MAIL-RULES.md`

## What Changed This Session

- planning README was updated to point at the new active planning docs
- a new Android-side authority doc was added for the public plaintext direct-connect decision
- a new staged plan was added for the same direction
- a compact Phase 0 baseline note was added to freeze the exact Android-side public plaintext endpoint shape
- the Android-side planning layer now treats Phase 0 as closed and Phase 1 as active
- a reusable `RelayBootstrapManager` was added above the current relay debug ViewModel
- the current debug relay screen now delegates config load/save plus `healthz` / connect / disconnect through that
  manager instead of owning those lower-level dependencies directly
- structured `RelayBootstrapStatus` / `RelayBootstrapResult` models were added so Android relay bootstrap now classifies
  `hello_ack` and representative failure outcomes in repository code instead of relying only on raw exception strings
- the bootstrap manager now exposes that classification path and focused manager coverage locks representative
  `not_configured`, `invalid_http_response`, `token_id_mismatch`, `unauthorized`, and `hello_ack` outcomes
- the formal `new task` ViewModel now reuses that bootstrap result before mail send, records the last bootstrap
  classification, disconnects temporary `hello_ack` preflight connections, and emits an explicit mail-fallback success
  message when direct bootstrap is unavailable
- focused manager coverage landed and clean narrow Gradle validation was re-run

## What Did Not Change

- no direct business-action transport was added
- no current-status or validation-ledger facts were rewritten into future tense
- no new device validation was run for the live plaintext VPS path

## Next Concrete Steps

1. keep the frozen Phase 0 baseline stable unless a later authority doc explicitly changes it
2. freeze the first Android direct outbound contract for `new task` with the adjacent PC/VPS repo
3. replace the current `new task` preflight-plus-mail path with a real direct-send path that still preserves mail
   fallback on non-success bootstrap or send failures
4. decide the next covered direct action after `new task`:
   - `/status`
   - plain continuation reply
5. add device/manual validation for the live public plaintext path once the first real direct outbound slice lands
6. preserve mail fallback from the first direct implementation slice onward

## Scope Boundary Reminder

The older mail-first/TLS-gated Android planning docs were pruned during the 2026-03-21 cleanup.
Do not attempt to recover direction from deleted intermediate planning notes; use the new authority/plan pair plus the
current implementation-truth docs instead.
