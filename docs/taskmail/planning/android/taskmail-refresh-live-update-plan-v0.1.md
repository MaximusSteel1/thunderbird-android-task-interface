# TaskMail Refresh and Live Update Plan (v0.1)

Updated: 2026-03-16

## Status

This document is now a historical/reference plan.

Most of the slice described here has landed in the repository. Remaining gaps are validation and closeout items tracked
in `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md` and `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`.

## Purpose

This document captures the focused next development slice for Android TaskMail:

- add pull-to-refresh on the TaskMail workspace screen
- trigger a real mail sync from the server
- update workspace cards after sync completes
- keep workspace and session detail screens up to date when the local mail store changes

This is a planning document. It does not change the current implementation-status or validation-status documents.

## Scope

This slice includes:

- workspace pull-to-refresh
- manual mail sync initiated from TaskMail workspace
- workspace card refresh after sync
- automatic workspace refresh when local TaskMail mail changes
- automatic session-detail refresh when local TaskMail mail changes
- state handling so refresh does not blank out already loaded UI

This slice does not include:

- TaskMail protocol changes
- dedicated UI for new TaskMail commands or protocol fields
- new-thread creation UI
- account-specific or folder-specific sync targeting
- repo-wide sync architecture changes outside the TaskMail feature

## Development Mode

This slice should be developed as a single focused workstream.

The separate "new thread" work should remain a later follow-up slice. The main reason is not Git isolation but debug reliability: both efforts are likely to touch TaskMail workspace/detail flows, local mail timing, and live-mail validation paths. Keeping this slice isolated reduces cross-feature uncertainty while sync and refresh behavior is being stabilized.

## Current Baseline

The current TaskMail implementation has these relevant properties:

- workspace data is read from the local mail store through the TaskMail repository
- workspace currently performs a one-time load on screen entry
- session detail has a local refresh path, but it does not trigger remote mail sync
- TaskMail reads across finished-setup accounts and aggregates logical sessions from local mail data
- the local mail infrastructure already exposes message-list change listeners
- the Compose design system already contains a reusable `PullToRefreshBox`

The practical consequence is:

- adding only a manual re-query is not sufficient
- adding only a sync trigger is also not sufficient

The feature needs both:

1. a user-triggered sync path
2. an automatic local-data observation path

## Goals

### Product Goals

- A user can pull down on the TaskMail workspace to refresh mail.
- That gesture triggers a real server sync rather than only re-reading cached TaskMail data.
- Workspace cards update after new TaskMail mail arrives.
- Session detail updates automatically when new timeline mail arrives or local state changes.
- Refresh should feel additive, not destructive: existing content should remain visible while refresh is running.

### Engineering Goals

- Reuse existing mail infrastructure where possible.
- Keep changes inside TaskMail internal modules and existing legacy mail interfaces.
- Preserve current API/internal module boundaries.
- Keep validation focused on narrow TaskMail and related host modules.

## Non-Goals

- Introducing a new TaskMail-specific sync backend
- Changing TaskMail subject, reply, or session protocol rules
- Building a dedicated account selector for TaskMail refresh
- Solving the future "new thread" UX in the same implementation slice

## Recommended Implementation

### 1. Workspace Pull-to-Refresh

The TaskMail workspace screen should adopt the existing design-system pull-to-refresh container.

Expected behavior:

- pull gesture is available when the workspace list is visible
- refreshing state shows a progress indicator
- existing workspace cards remain visible during refresh
- refresh errors do not clear existing content

### 2. Manual Sync Strategy

The first implementation should use all-account manual mail sync with notifications disabled.

Recommended behavior:

- trigger a manual `checkMail(account = null, ignoreLastCheckedTime = true, useManualWakeLock = true, notify = false, ...)`
- treat this as a TaskMail-level refresh action, not as a dedicated sync policy engine

Rationale:

- TaskMail currently aggregates sessions across all finished-setup accounts
- workspace cards do not currently expose a reliable account-scoped refresh target
- correctness is more important than premature optimization for the first version
- matching message-list manual refresh semantics with `notify = false` avoids unnecessary notification noise from a TaskMail-triggered gesture

### 3. Local Mail Change Observation

TaskMail should observe local message-list changes and react by re-reading TaskMail repository data.

Recommended behavior:

- workspace observes relevant local mail-store change events while active
- session detail observes relevant local mail-store change events while active
- on change, each screen reloads its own local TaskMail view data

This observation path should become the primary source of UI freshness after sync, reply send, and background local updates.

### 4. State Model

The state model should distinguish:

- initial loading
- content loaded
- content refreshing
- refresh failure with stale content retained
- hard load failure with no content available

This is important because pull-to-refresh should not regress the screen into a blank loading or empty state when data already exists.

### 5. Detail-Screen Rules

Automatic detail refresh should update:

- timeline items
- task status
- pending-question state
- reply availability and helper copy

Automatic detail refresh should not overwrite:

- current draft text
- selected reply attachments
- temporary send-error state unless a new user action clears it

The detail screen already has a post-send local refresh path. That can remain, but local mail change observation should become the main freshness mechanism.

## Proposed Internal Design Shape

The exact type names can still change, but the implementation will likely need:

- a TaskMail-scoped use case to trigger manual refresh
- a TaskMail-scoped observer around local message-list change events
- workspace state support for `isRefreshing`
- detail state support for background refresh without wiping user draft state

Likely touched areas:

- `feature:taskmail:internal` workspace UI and ViewModel
- `feature:taskmail:internal` detail ViewModel
- `feature:taskmail:internal` module wiring
- TaskMail-local wrappers around existing mail infrastructure

## Risks and Constraints

### Sync Completion Semantics

All-account `checkMail()` completion semantics need to be handled carefully so the refresh indicator ends at the right time.

### Repeated Local Change Events

Local message-list notifications may arrive multiple times during one sync session. The UI layer should be robust against duplicate refresh triggers.

### Draft Preservation

Detail auto-refresh must not wipe a partially written reply or selected attachments.

### Scope Creep

This slice should not absorb the new-thread feature. That work belongs to a later follow-up once refresh behavior is stable and validated.

## Validation Plan

### Unit and ViewModel Coverage

Add or update tests for:

- manual refresh use case success and failure handling
- workspace refresh state transitions
- workspace auto-refresh after local message-list change
- detail auto-refresh after local message-list change
- detail draft and attachment preservation across auto-refresh

### Narrow Gradle Tasks

Start with:

- `.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest`
- `.\gradlew.bat :feature:taskmail:internal:detekt`
- `.\gradlew.bat :feature:taskmail:internal:lintDebug`

Then widen only if needed to cover related host wiring.

### Manual Smoke

Minimum smoke expectations for this slice:

1. Open TaskMail workspace through the formal in-app path.
2. Pull to refresh on workspace and verify the indicator appears.
3. Confirm new TaskMail mail pulled from the server updates workspace cards.
4. Open session detail and confirm new local mail updates the timeline without losing an in-progress draft.
5. Send a reply, keep the user on TaskMail, and verify the resulting local mail change updates workspace/detail state.

## Follow-up After Implementation

After code and validation land, update these documents:

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/TASKMAIL-DEBUG-VALIDATION.md`

`docs/TASKMAIL-MAIL-RULES.md` should not be updated for this slice unless protocol behavior changes, which is not currently planned.
