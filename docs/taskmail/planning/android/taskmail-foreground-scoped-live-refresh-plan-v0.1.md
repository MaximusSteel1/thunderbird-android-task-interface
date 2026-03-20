# TaskMail Foreground Scoped Live Refresh Plan (v0.1)

Updated: 2026-03-18

## Status

This document is now a historical planning snapshot and implementation-reference note.

Do not treat it as the current implementation or validation authority.

Why it is historical now:

- the foreground-only 10-second account-scoped refresh loop described here has already landed in the repository for the
  TaskMail workspace and session-detail surfaces
- current implementation truth now lives in `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- current validation truth now lives in `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`

This file is still useful for understanding the design boundary and rationale behind that refresh model.

This document does not replace the current implementation-status or validation-status documents:

- implementation truth remains in `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- validation truth remains in `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`

## Inputs

This plan is based on:

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/TASKMAIL-DEBUG-VALIDATION.md`
- `docs/TASKMAIL-MAIL-RULES.md`
- `docs/taskmail/planning/android/taskmail-refresh-live-update-plan-v0.1.md`
- `docs/taskmail/planning/android/taskmail-dual-mailbox-android-adjustments-v0.1.md`
- `E:\projects\mail_based_task_manager\docs/current/mail_protocol.md`
- user clarification recorded on 2026-03-18:
  - Android has only the single user mailbox account logged in for this flow
  - Android is not expected to log into the bot mailbox as a second local account
  - the target interval is 10 seconds
  - the target surfaces are TaskMail workspace and Task session detail

## Problem Statement

Current TaskMail live freshness is already better than the original baseline:

- workspace supports pull-to-refresh
- manual refresh can trigger real mail sync
- workspace and detail reload when the local mail store changes
- detail reload preserves in-progress draft text and selected reply attachments

However, the current refresh path is still broader than needed for this user flow:

- TaskMail refresh is currently implemented as all-account mail sync
- there is no foreground-only loop that keeps TaskMail fresh while the user stays on workspace or detail
- the current product goal is not global IMAP push; it is near-live updates while the user is actively looking at TaskMail

## Decision Summary

This slice should follow these decisions:

- do not add or widen global background push behavior
- do not assume Android logs into the bot mailbox
- treat the desired mail as bot-generated replies arriving in the single logged-in user mailbox
- add foreground-only scoped sync while TaskMail workspace or session detail is visible
- use a 10-second interval for the first implementation
- reuse existing local-store observation as the primary UI freshness trigger
- optimize for the current single-account TaskMail flow without silently inventing new multi-account workspace behavior

## Product Boundary

The required product outcome is:

- while the user keeps the TaskMail workspace open, newer bot-generated TaskMail mail should appear without requiring a
  manual pull gesture
- while the user keeps a TaskMail session detail screen open, newer bot-generated TaskMail mail should appear in the
  timeline without requiring a manual refresh tap
- leaving those screens should stop the extra refresh behavior

This slice is intentionally not:

- a full global IMAP push redesign
- a dedicated Android login flow for the bot mailbox
- a change to TaskMail protocol, reply semantics, or dual-mailbox routing rules
- a promise that every mail account in the app will participate in the new 10-second loop

## Current Baseline

The repository already contains the following useful pieces:

- TaskMail workspace and detail view models observe local mail-store changes and reload their own screen data
- session detail preserves draft text and selected attachments across same-session reloads
- TaskMail screen entry currently happens through a one-time `LoadData` / `LoadDetail` event from Compose
- TaskMail refresh currently delegates to a `TaskMailSyncRequester` that triggers all-account `checkMail(...)`
- project-sync code already demonstrates account resolution patterns via TaskMail sender-account infrastructure

The practical consequence is:

- the UI freshness half of the problem is mostly solved already
- the missing half is a scoped foreground sync trigger with lifecycle control

## Recommended Sync Scope

### v0.1 Scope Decision

The first implementation should use **account-scoped** sync, not folder-scoped sync.

Rationale:

- the current user flow involves one logged-in user mailbox account for TaskMail
- account-scoped sync is already supported by the legacy mail layer
- folder-scoped sync would require additional folder-target plumbing that is not necessary for the current low-risk goal
- workspace does not currently expose account identifiers in its UI model, so folder-level targeting would not reduce
  complexity for the list screen

This means the first slice should narrow from:

- all accounts

to:

- the single resolved TaskMail sender/read account

This is sufficient for the current requested flow and keeps the change smaller than a folder-targeted design.

### Detail-Screen Target Resolution

The detail screen should resolve its sync target from the loaded session detail reply context when available.

Preferred target for this slice:

- `replyContext.accountUuid`

If detail has no reply context:

- skip the automatic foreground loop for that detail screen
- keep current read-only load behavior

### Workspace Target Resolution

The workspace screen has no session-selected account context.

For this slice it should resolve its target from the TaskMail sender-account source:

- if exactly one TaskMail sender account is available, use that account
- if account resolution is ambiguous, do not silently start a multi-account 10-second loop

This matches the current user clarification while avoiding a silent behavior change for unhandled future multi-account
workspace cases.

## Recommended Implementation Shape

### 1. Introduce Scoped Sync Requesting

TaskMail should gain a scoped sync request path that can target one resolved account.

Recommended shape:

- evolve the TaskMail sync requester so it can request sync for a resolved `accountUuid`
- resolve the concrete legacy account through existing TaskMail sender-account infrastructure
- call legacy `MessagingController.checkMail(account = resolvedAccount, ignoreLastCheckedTime = true, useManualWakeLock = true, notify = false, ...)`

This keeps the new behavior aligned with existing mail sync semantics while avoiding all-account fan-out.

### 2. Keep Background Auto-Refresh Separate from User-Visible Refresh State

The 10-second foreground loop should not behave like repeated manual pull-to-refresh.

Recommended behavior:

- background loop triggers the scoped sync request
- successful new mail arrival is surfaced through the existing local-store observer path
- background loop does not continuously toggle the visible refresh spinner
- background loop does not repeatedly surface refresh-error banners every 10 seconds

Manual refresh entry points may reuse the same scoped sync machinery later, but the first requirement here is foreground
freshness without noisy UI churn.

### 3. Add Lifecycle Start/Stop Events

Workspace and detail should explicitly model foreground refresh start/stop.

Recommended shape:

- add start/stop events to workspace and detail contracts
- dispatch them from the Compose screens through lifecycle-aware effects
- keep the loop owned by the corresponding ViewModel, not by a global service

This keeps the slice local to TaskMail UI behavior.

### 4. Add a ViewModel-Owned Foreground Loop

Each relevant ViewModel should own a cancellable coroutine job for foreground sync.

Required rules:

- start when the screen becomes visible
- stop immediately when the screen leaves the foreground
- use a 10-second interval
- keep at most one sync request in flight
- if a tick occurs while a sync is still running, skip that tick rather than queueing overlapping work
- after failure, wait for the next normal interval instead of retrying aggressively

### 5. Preserve Existing Local-Store Observation

The existing local-store observation path should remain the primary screen reload trigger after background sync.

Recommended behavior:

- background sync itself should not force a visible full reload cycle when no local mail change occurred
- if new mail lands, the existing observer-driven reload updates the screen
- detail reload must continue to preserve draft text and selected reply attachments

### 6. Keep Manual Refresh Functional

This slice should not regress the existing user-triggered refresh affordances.

Minimum expectation:

- workspace pull-to-refresh still works
- detail manual refresh still works

Implementation note:

- if manual refresh can safely reuse the new scoped account path for the single-account case, that is acceptable
- if doing so creates avoidable ambiguity for the current multi-account workspace behavior, keep manual refresh semantics
  unchanged in this slice and scope the new account-targeting path to foreground auto-refresh first

## Screen-Specific Plan

### Workspace

Workspace should gain:

- foreground start/stop signaling from the screen host
- account resolution for the single TaskMail sender/read account
- a background 10-second sync loop while visible
- no continuous refresh spinner for the background loop
- existing observer-driven summary reloads after local mail changes

Workspace should not gain in this slice:

- an account selector
- per-workspace account affinity in the UI model
- global auto-refresh while the user is elsewhere in the app

### Session Detail

Session detail should gain:

- foreground start/stop signaling from the screen host
- a background 10-second sync loop while visible
- account resolution from `replyContext.accountUuid`
- continued observer-driven timeline reloads after local mail changes
- preserved draft text and selected attachments during observer-driven reloads

Session detail should not gain in this slice:

- a dedicated folder-scoped sync engine
- user-visible repeated refresh banners for every loop failure

## Likely Touched Areas

The exact type names can still change, but implementation will likely touch:

- `feature:taskmail:internal` TaskMail sync requester abstractions
- `feature:taskmail:internal` sender-account resolution reuse
- `feature:taskmail:internal` workspace contract, screen, and ViewModel
- `feature:taskmail:internal` session-detail contract, screen, and ViewModel
- `feature:taskmail:internal` Koin wiring for any new use case or helper
- focused unit tests in TaskMail workspace/detail test suites

## Risks and Constraints

### 1. Multi-Account Workspace Ambiguity

The current request is explicitly single-account, but the product surface can exist in wider account setups.

Risk:

- silently converting workspace into an implicit multi-account 10-second sync loop would increase cost and change behavior

Guardrail:

- only enable workspace foreground auto-refresh when the TaskMail account target is unambiguous

### 2. UI Noise

A repeated visible refresh spinner or error banner every 10 seconds would be a poor UX.

Guardrail:

- keep background auto-refresh separate from user-visible manual refresh state

### 3. Overlapping Sync

Repeated ticks can create overlapping sync calls if the network is slow.

Guardrail:

- enforce one in-flight sync per screen loop

### 4. Draft Preservation

Detail auto-refresh must keep draft text and selected attachments stable.

Guardrail:

- reuse the existing observer-driven detail reload path rather than inventing a new detail state replacement path

### 5. Validation Debt

Current TaskMail docs still record open device evidence gaps for:

- confirmed post-sync workspace/session summary freshness
- detail auto-refresh while editing
- broader refresh failure warning closeout

This slice should add new focused validation, not assume those gaps are already closed.

## Validation Plan

### Unit and ViewModel Coverage

Add or update tests for:

- workspace foreground loop starts when visible and stops when hidden
- workspace foreground loop does not run when no unique account can be resolved
- detail foreground loop starts when visible and stops when hidden
- detail foreground loop skips when reply context has no account target
- foreground sync does not overlap when a previous tick is still running
- new local mail still reloads workspace summaries
- new local mail still reloads detail while preserving draft text and selected attachments

### Narrow Gradle Tasks

Start with:

- `.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest`
- `.\gradlew.bat :feature:taskmail:internal:detekt`
- `.\gradlew.bat :feature:taskmail:internal:lintDebug`

If the broader module test suite is still blocked by the existing workspace refresh-warning assertion, record that
explicitly rather than widening into unrelated fixes.

### Manual Smoke

Targeted smoke for this slice should include:

1. Open TaskMail workspace and keep it visible while backend feedback arrives; verify updated workspace/session summaries
   appear without a pull gesture.
2. Open TaskMail session detail and keep it visible while backend feedback arrives; verify new timeline/status mail
   appears without a manual refresh tap.
3. Keep a partially written draft plus one selected attachment on detail; verify a later local mail update does not clear
   either.
4. Leave workspace/detail and verify the extra refresh loop stops instead of continuing app-wide.

## Explicit Non-Goals

This slice does not include:

- global background IMAP push redesign
- bot-mailbox local-account support on Android
- folder-scoped sync orchestration
- dedicated multi-account workspace refresh UI
- TaskMail protocol changes
- repo-wide mail sync architecture changes outside the TaskMail slice

## Recommended Execution Order

1. Introduce the account-scoped TaskMail sync request path.
2. Add workspace and detail foreground lifecycle events plus cancellable loop ownership.
3. Reuse the existing local-store observer path for UI freshness.
4. Add focused tests for loop start/stop, scope resolution, and non-overlap.
5. Run narrow validation tasks and document any remaining known blockers.

## Documentation Follow-Up

When implementation begins or lands, update:

1. `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
2. `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
3. this planning note
