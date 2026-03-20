# TaskMail Android / PC / VPS Evolution Review Note (v0.1, historical)

Updated: 2026-03-20

## Status

This file is now a historical review note.

Its conclusions have been absorbed into:

- `docs/taskmail/planning/android/taskmail-android-pc-vps-evolution-authority-v1.md`
- `E:\projects\mail_based_task_manager\docs\plans\android_pc_vps_evolution_authority.md`

Keep this file only as background context for the review pass that tightened gating and phase interpretation.

## Purpose

Keep Android planning aligned with two facts at the same time:

1. the longer-term direction can include a real Android app-facing API,
2. the current Android product boundary is still mail-first until the cross-repo prerequisites are actually ready.

This note exists so Android does not overreact in either direction:

- not by treating raw relay as the product protocol,
- and not by assuming mail must remain the permanent primary path forever.

## Review Conclusion

Under the current single-user assumption, the macro evolution direction is acceptable for Android planning:

- near term: Android stays mail-first,
- medium term: Android can prepare for VPS-backed projection/query reads,
- longer term: Android may move to a separate app-facing API,
- raw relay still stays infrastructure rather than app protocol.

So the direction is acceptable.
What remains important is **gating**, not the direction itself.

## What Android Should Treat As Fixed For Now

### 1. Production Android Behavior Stays Mail-First

Until later cross-repo gates are closed, Android production behavior should continue to assume:

- read from the mail-derived TaskMail model,
- send through the current mail control-plane rules,
- keep relay probing or relay bootstrap work internal and debug-only.

This remains true even if Android starts preparing local seams for a later app-facing API.

### 2. Raw Relay Is Not The Android Product Surface

Android should continue to avoid product behavior that depends directly on:

- relay transport credentials,
- `hello` / `packet` / `ack` relay semantics,
- `/healthz` or `/readyz` as business APIs,
- relay packet transport details as app-facing state.

If Android later gains a direct connection, it should be through high-level task/session/reply/artifact/update
semantics, not through raw relay transport reuse.

### 3. Future API Preparation Is Allowed, But Cutover Is Not Yet Allowed

Android may prepare:

- an internal API client abstraction,
- repository/view-model seams that can later consume API-backed data,
- dual-stack comparison logic in principle.

Android should **not** treat API-first behavior as implementation-ready until the cross-repo prerequisites below are
closed.

## Cross-Repo Gates Android Should Wait For

Before Android direct-connect or API-primary work begins, Android planning should expect these repository-side gates to
exist:

1. the current `PC -> VPS` connection is stable under reconnect, resend, and ack conditions,
2. public DNS and trusted TLS are closed for the VPS path,
3. the first canonical PC -> VPS event set is explicitly frozen,
4. the first VPS session/message projection model is explicitly frozen,
5. the first app-facing API scope is agreed in business semantics,
6. auth, artifact, rollback, and reconnect rules are explicit enough to support real mobile behavior.

If those gates are not closed, Android should treat Phases 3-5 as planning direction only, not as implementation-ready
work.

## Android-Side Planning Interpretation

The macro phases should be interpreted like this:

- Phase 1 is active current work.
- Phase 2 is only safe once the event/projection contract is frozen.
- Phase 3 may start on interface design and internal seams, but not on production cutover.
- Phases 4-5 are acceptable intended direction, not current build targets.

This means Android can prepare for the future path without prematurely binding UI, repository, or sync behavior to an
API contract that does not yet exist.

## What Must Not Happen

- do not expand raw relay logic as shipped Android product behavior
- do not let "future API direction" weaken current mail-rule correctness
- do not assume projection fields or event schemas that the PC/VPS side has not frozen
- do not start direct-connect product cutover before fallback and rollback rules are explicit

## Immediate Android Implication

For the Android repository, the practical result is:

- keep current mail-first behavior authoritative,
- keep planning for a future API path,
- and wait for the PC/VPS side to freeze the first event/projection/API contracts before treating that future path as
  implementation-ready.

That is the narrowest interpretation that preserves both forward progress and protocol safety.
