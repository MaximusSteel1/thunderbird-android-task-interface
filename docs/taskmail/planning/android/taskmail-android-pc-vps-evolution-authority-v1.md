# TaskMail Android / PC / VPS Evolution Authority (v1)

Updated: 2026-03-20

## Status

This is the current macro planning authority for cross-repo Android / PC / VPS evolution work.

It governs:

- the intended longer-term direction
- the gating required before Android direct-connect work can begin
- the interpretation of current versus future work across the three codebases

It does not replace current protocol or implementation-truth documents such as:

- `docs/TASKMAIL-MAIL-RULES.md`
- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `E:\projects\mail_based_task_manager\docs/current\android_runner_communication_contract.md`
- `E:\projects\mail_based_task_manager\docs/current\mail_protocol.md`

## Purpose

This document exists to keep all three repositories aligned on one path:

- near term: stable mail-first Android plus stable `PC -> VPS`
- medium term: VPS-backed projection/query capability
- longer term: Android direct app-facing API

The key rule is that Android should prepare for the future path without treating that future path as implementation
ready before the required cross-repo gates are closed.

## Current Fixed Assumptions

These assumptions are fixed for current planning unless later authority docs reopen them:

1. Android production behavior remains mail-first for now.
2. The stable live connection target is `PC -> VPS`.
3. VPS relay remains transport/control plane in the current phase.
4. Raw `/relay` is not the Android product protocol.
5. Mail remains the Android-facing compatibility surface in the current phase.
6. A future Android direct path, if built, must be a new app-facing API expressed in business semantics.

Current planning also assumes a single primary user path.

That assumption is useful for scoping the near-term system, but it must not be misread as a permanent product law.

## Longer-Term Target

The intended longer-term product shape is:

- Android talks to an app-facing API in task/session/reply/artifact/update semantics
- VPS provides the public API, auth boundary, projection/query layer, and update continuity
- PC remains the execution engine and exchanges canonical commands/events with VPS
- mail becomes compatibility and fallback infrastructure rather than the primary Android workflow

This means the future Android path is not:

- relay transport token management
- `hello` / `packet` / `ack` relay semantics
- `/healthz` or `/readyz` as business APIs
- raw relay packet structures as app-facing state

## Cross-Repo Gates Before Android Direct-Connect Work

Android direct-connect or API-primary implementation must not be treated as ready until these are closed:

1. the current `PC -> VPS` connection is stable under reconnect, resend, and ack conditions
2. public DNS and stock-client-trusted TLS are closed for the VPS path
3. the first canonical PC -> VPS event set is explicitly frozen
4. the first VPS session/message projection model is explicitly frozen
5. the first app-facing API scope is agreed in business semantics
6. auth, artifact, rollback, and reconnect rules are explicit enough for real mobile behavior

If these gates are not closed, future Android API phases remain planning direction only.

## Phase Interpretation

### Phase 1 - Current Active Work

Android:

- keep production behavior mail-first
- keep relay bootstrap/probing internal and debug-only
- continue mail-side compatibility and quality work

PC:

- stabilize `PC -> VPS`
- harden reconnect, ack, resend, and fallback

VPS:

- harden the public host, DNS, and TLS
- keep `https /healthz` and `wss /relay` coherent

### Phase 2 - Projection Preparation

PC:

- emit canonical events for session lifecycle, visible messages, state changes, questions, and artifacts

VPS:

- persist those events
- build the first stable projection/read model

Android:

- keep seams clean enough to consume future API-backed data without rewriting the UI

Phase 2 is not Android product cutover work.

### Phase 3 - API Definition And Internal Preparation

VPS:

- define the first app-facing API in business semantics

PC:

- accept high-level VPS-routed commands
- keep publishing canonical updates

Android:

- internal API client abstractions are allowed
- repository or view-model seams are allowed
- production API-first behavior is not allowed yet

### Phase 4 - Dual Stack

Android:

- API read path first
- comparison against mail-derived state
- keep mail fallback

PC and VPS:

- harden idempotency, replay, auth, artifact access, and update resume behavior

### Phase 5 - API-Primary Android

This phase is acceptable intended direction, but it is not a current build target.

Android should not begin product cutover planning here until earlier gates are explicitly closed.

## Guardrails

- do not expand raw relay logic into shipped Android product behavior
- do not let future API preparation weaken current mail-rule correctness
- do not assume projection fields or event schemas that PC/VPS have not frozen
- do not start Android direct-connect cutover before fallback and rollback rules are explicit
- do not combine "move execution truth" and "replace Android protocol" into the same migration without a separate
  architecture decision

## Immediate Cross-Repo Next Steps

1. PC/VPS close public DNS and TLS readiness
2. PC freeze the first canonical event set it can emit consistently
3. VPS freeze the first useful session/message projection model
4. Android keep mail-first behavior authoritative and avoid new raw-relay expansion
5. all three sides agree on the first app-facing API slice before implementation begins

## Superseded Documents

This document supersedes the macro-planning role previously held by:

- `docs/taskmail/planning/android/taskmail-android-pc-vps-evolution-draft-v0.1.md`
- `docs/taskmail/planning/android/taskmail-android-pc-vps-evolution-review-note-v0.1.md`
- `E:\projects\mail_based_task_manager\docs/plans/android_pc_vps_evolution_draft.md`

Those files remain useful as historical inputs, but this file is the current Android-side authority for macro
cross-repo evolution planning.
