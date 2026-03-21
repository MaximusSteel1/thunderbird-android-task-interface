# TaskMail Android Public Plaintext Direct-Connect Plan (v0.1)

Updated: 2026-03-21

## Status

This is the current Android-side staged execution plan for the public-IP plaintext direct-connect direction.

It translates:

- `docs/taskmail/planning/android/taskmail-android-public-plaintext-direct-connect-authority-v0.1.md`

into concrete Android-side phases.

It does not replace:

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/TASKMAIL-MAIL-RULES.md`

Those files still describe the current mail-first implementation and evidence.

## Purpose

This plan answers one question:

> Given the new decision that Android public plaintext direct-connect becomes the intended main path, what staged work
> should happen next without pretending the repository already implements that path today?

## Planning Position

The current Android-side planning position is now:

- direct-connect is the intended primary path
- the current mail path remains required fallback
- current code is still largely mail-first
- the plan must bridge from the current implementation to the new direct path in reviewable slices

The plan is intentionally pragmatic.
It prefers reusing the currently landed relay bootstrap seams over waiting for a later clean-room API-first redesign.

## Direction Summary

The chosen operational baseline for the direct path is:

- public host/IP
- configured port
- plaintext `http/ws`
- `/healthz` for diagnostics
- `/relay` for connection bring-up and later business traffic
- token-based auth
- mail fallback preserved during rollout

The exact Android-side Phase 0 freeze for this baseline is recorded in:

- `docs/taskmail/planning/android/taskmail-phase0-public-plaintext-baseline-v1.md`

## What Is No Longer The Active Plan

This plan no longer assumes:

- Android direct-connect must wait for DNS/TLS closeout
- direct-connect must stay debug-only
- Android must remain mail-first until a later API-only phase
- raw relay growth is automatically forbidden

## Phase Map

- Phase 0: Direction reset and baseline freeze
- Phase 1: Bootstrap promotion and reusable connection seam
- Phase 2: Direct outbound action bridge
- Phase 3: Direct inbound update bridge
- Phase 4: Dual-stack parity and primary-path switch
- Phase 5: Long-term default hardening

## Current Phase Status

- Phase 0 is closed for the Android-side planning layer.
- Phase 1 is the current active Android implementation-planning phase.

## Phase 0: Direction Reset And Baseline Freeze

### Goal

Make the planning layer consistent before implementation starts.

### Deliverables

- new Android-side authority doc
- this staged plan
- README/index updates that demote the earlier mail-first/TLS-gated planning line
- one concise handoff note for the next implementation session
- one frozen baseline note:
  - `docs/taskmail/planning/android/taskmail-phase0-public-plaintext-baseline-v1.md`

### Closeout Condition

- no active Android-side planning doc still describes mail-first or TLS trust as the controlling direct-connect gate
- the new direct-connect main-path decision is explicit
- the direct-connect baseline is frozen in one reviewable Android-side note

## Phase 1: Bootstrap Promotion And Reusable Connection Seam

### Goal

Promote the existing relay debug/bootstrap seam into a reusable direct-connect foundation.

### Android Work

- extract or reuse a connection manager above the current debug screen
- keep host/port/token/use-plaintext config reachable from internal TaskMail flows
- support:
  - `healthz`
  - connect
  - `hello -> hello_ack`
  - connection-state visibility
- define explicit fallback behavior to mail when direct connect is unavailable

### Guardrails

- current debug screen may remain during transition, but the connection logic should stop being debug-screen-only
- do not remove the mail path

### Closeout Condition

- Android can establish the public plaintext relay session reliably enough to be reused outside the debug screen
- failure states are visible and can route back to mail fallback

## Phase 2: Direct Outbound Action Bridge

### Goal

Make the direct path capable of driving the highest-value outbound user actions while mail remains available as fallback.

### First-Scope Actions

- new task
- plain continuation reply
- `/status`
- `/pause`
- `/resume`
- `/end`

### Android Work

- define the first Android direct-connect outbound contract with PC/VPS
- serialize current TaskMail intent into direct payloads
- keep current mail compose path available as fallback for unsupported or failed direct actions
- record direct-send versus mail-fallback behavior explicitly in logs/debug surfaces

### Closeout Condition

- the first-scope outbound actions can travel over the direct path
- direct failures can fall back cleanly to the existing mail path

## Phase 3: Direct Inbound Update Bridge

### Goal

Make Android capable of consuming direct-side updates strongly enough to drive the existing TaskMail UI.

### Android Work

- define how direct updates map into:
  - workspace summaries
  - session detail timeline
  - question states
  - paused/running/done/failed status
- preserve the current local repository boundary where possible
- allow mail-derived state and direct-derived state to coexist during transition

### Closeout Condition

- Android can maintain useful read-side state from the direct path without dropping mail fallback

## Phase 4: Dual-Stack Parity And Primary-Path Switch

### Goal

Make direct-connect the practical primary path while still keeping rollback credibility.

### Android Work

- compare representative direct-path outcomes against the existing mail-derived outcomes
- define mismatch triage rules
- define rollback triggers that switch affected flows back to mail
- switch the primary route to direct-connect for the covered flows once parity is good enough

### Closeout Condition

- direct path is the default for covered flows
- mail fallback is still available and tested

## Phase 5: Long-Term Default Hardening

### Goal

Stabilize the chosen long-term default rather than treating direct-connect as a temporary experiment.

### Android Work

- document long-term token handling and rotation expectations
- harden reconnect and stale-session behavior
- close the biggest direct-vs-mail edge cases
- keep explicit operator and user-facing fallback behavior

### Closeout Condition

- the direct path is stable enough to be treated as the normal route
- the mail fallback remains a real operational escape hatch rather than a dead code path

## Recommended First Implementation Slice

The first implementation slice after this planning rewrite should be:

1. keep the existing mail-first UI and repository intact
2. promote the current relay bootstrap code into a reusable internal connection seam
3. add one narrow direct-send action path, preferably `new task` or `/status`
4. preserve mail fallback for everything else

This keeps the first step reversible.

## Deliberately Deferred

This plan does not require the first implementation slice to solve:

- a brand-new polished app-facing API design
- mail fallback removal
- full read-side cutover
- secrecy or transport-hardening decisions that contradict the chosen plaintext baseline

## Reference Boundary

Current repository state, fallback behavior, and executable evidence should now be read from:

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/TASKMAIL-MAIL-RULES.md`

The earlier Android-side intermediate planning set was pruned during the 2026-03-21 cleanup and is no longer meant to
serve as reference material inside this repository.
