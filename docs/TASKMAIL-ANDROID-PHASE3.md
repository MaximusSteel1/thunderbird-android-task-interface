# TaskMail Android Phase 3

This document now serves as a historical/reference document for the interaction-focused phase of TaskMail Android.

Current status authority:

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`

Protocol authority:

- `docs/TASKMAIL-MAIL-RULES.md`

Validation evidence authority:

- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`

## Date

- Last updated: 2026-03-16

## Document Role

Use this file for:

- understanding what “Phase 3” originally meant
- understanding which interaction goals are already baseline in the repository
- identifying what still remains validation closeout instead of new implementation

Do not use this file alone to answer either of these questions:

- “What is currently implemented right now?”
- “What has already been fully validated?”

## Original Phase Goal

Phase 3 turned TaskMail from a read-only viewer into a lightweight interaction surface for an existing task thread.

Expected user outcome:

- the user can read the latest state, summary, and pending questions
- the user can reply from inside TaskMail
- the user can trigger `/status` without leaving TaskMail
- the user stays anchored to the existing mail thread instead of forking a new one

## Current Alignment Summary

As of the 2026-03-16 documentation alignment pass, substantial Phase 3 surface is already present in the repository.

### Interaction Surface Already Present in Repository

- reply context derived from the newest logical-session message
- mixed-account or missing-anchor sessions disabling reply
- session detail reply composer
- plain-text replies
- reply attachments
- single-question quick answers
- multi-question structured `Answers:` template replies
- `/status`
- `paused` session send paths that prepend `/resume`
- real sender integration via the existing mail send stack
- timeline attachment metadata plus `Open` / `Save` actions

### Validation Evidence Already Recorded

The validation ledger already records executable evidence for:

- reply sender unit tests
- `/status` dispatch
- paused-session `/resume` body serialization
- single-question quick-answer behavior
- multi-question structured-answer gating
- reply attachment selection and attachment-only continuation behavior
- detail-screen UI state handling
- Thunderbird and K-9 `fossDebug` assembles

### What Is Still Not Closed for Phase 3

Phase 3 should now be read as “implemented in repository, not fully validation-closed.”

The remaining closeout items are:

- live end-to-end mail sending re-smoke
- device/manual validation for attachments, paused sessions, and multi-question flows
- repo-wide quality gates outside the narrow TaskMail slice

## Current Repository-Aligned Phase 3 Definition

When discussing “Phase 3” now, the accurate meaning is:

- session detail is a real interaction hub
- Android can reply within the existing TaskMail thread
- Android can handle paused-state send semantics
- Android can distinguish single-question and multi-question waits
- Android can attach files to replies where protocol rules allow it

What Phase 3 no longer means:

- “reply interaction is still only a design sketch”
- “attachments are only future work”
- “multi-question support is read-only only”

## Protocol Boundary for Phase 3

The current PC-side protocol surface is still broader than the Android explicit UI surface.

Android should therefore be read as:

- protocol-compatible
- intentionally narrow in explicit controls

### Explicit UI Surface That Exists Today

- plain-text reply
- reply attachments
- single-question quick answers
- multi-question structured answers
- `/status`
- paused-session resume semantics

### Protocol Capabilities Not Yet Exposed as Dedicated Android Controls

- `Permission`
- `Profile`
- `Timeout`
- `Mode`
- `/pause`
- `/new`
- `/sessions`
- `/rerun`
- `/kill`

This is a deliberate scope boundary, not a contradiction in protocol.

## Remaining Phase 3 Closeout Work

The most useful closeout work is now:

- validate detail reply flows on-device
- validate attachment open/save and reply-attachment flows on-device
- confirm paused-session and multi-question UX copy is understandable in live use
- keep protocol docs and Android docs synchronized when the mail control plane changes

## Documentation Guidance

If the interaction surface changes, update these files together:

1. `docs/TASKMAIL-MAIL-RULES.md`
2. `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
3. `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
4. `docs/TASKMAIL-ANDROID-PHASE3.md`

in that order.

## Recommended Interpretation

The safest current interpretation is:

- Phase 3 implementation surface exists in the repository
- that surface already includes attachments, paused-session handling, and structured multi-question replies
- full device/manual confidence and repo-wide quality closure are still pending
