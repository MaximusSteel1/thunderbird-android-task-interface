# TaskMail Idle Push Migration Plan (v0.1)

Updated: 2026-03-18

## Status

This is a new planning note for replacing TaskMail foreground polling with IMAP IDLE-backed updates.

It does not replace the current implementation or validation authority:

- implementation truth remains in `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- validation truth remains in `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`

## Inputs

This plan is based on:

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/TASKMAIL-DEBUG-VALIDATION.md`
- `docs/TASKMAIL-MAIL-RULES.md`
- `docs/taskmail/planning/android/taskmail-refresh-live-update-plan-v0.1.md`
- `docs/taskmail/planning/android/taskmail-foreground-scoped-live-refresh-plan-v0.1.md`
- current TaskMail workspace/detail refresh code in `feature:taskmail:internal`
- current Android mail push infrastructure in `legacy:core`, `legacy:common`, and `legacy:mailstore`

## Problem Statement

The current TaskMail tasks surface is doing more work than necessary while visible:

- workspace and session detail both start a foreground-scoped 10-second refresh loop
- each loop tick eventually calls legacy `checkMail(...)`
- local mail-store observation already exists and already reloads TaskMail UI when new local mail arrives

This means the current foreground loop is not the only freshness mechanism. It is an extra sync driver layered on top of
observer-driven reloads.

Given the current user report of frequent crashes while pulling tasks, the highest-priority product direction is:

1. remove TaskMail-specific automatic polling
2. replace it with mailbox-level idle push where the mail stack already supports it

## Current Baseline

Repository inspection confirms all of the following are true now:

- TaskMail workspace uses `TaskMailForegroundRefreshLifecycleEffect` and a ViewModel-owned 10-second ticker
- TaskMail session detail uses the same lifecycle/ticker pattern
- both ViewModels still keep local `MessageListRepository` observation active through `ObserveTaskMailStoreChanges`
- manual refresh is still available through existing TaskMail UI actions
- the sync path used by TaskMail foreground refresh currently calls `MessagingController.checkMail(...)` through
  `LegacyTaskMailSyncRequester`
- the app already contains a real global push stack:
  - `PushController`
  - `AccountPushController`
  - `PushService`
  - IMAP backend push support through `ImapBackendFactory`
- push is only active when:
  - the backend is push-capable
  - at least one remote folder has `isPushEnabled = true`
- default account setup still uses `folderPushMode = NONE`, so switching TaskMail away from polling without handling push
  readiness would silently remove live updates for many accounts

Repository inspection also exposed one important implementation pitfall:

- `MessagingController.isPushCapable()` is optimistic at the backend-type layer
- the IMAP backend reports `isPushCapable = true`
- real IMAP IDLE support is still decided later by server capabilities inside `RealImapFolderIdler`
- if the connected IMAP server does not advertise `IDLE`, the pusher returns `NOT_SUPPORTED`

So "the account is IMAP" and "the account can actually use IDLE push" are not equivalent in this codebase.

## 2026-03-18 Local User-Mailbox Probe

On 2026-03-18, the local user-mailbox config documented by the adjacent TaskMail workspace was probed directly via IMAP
login plus `CAPABILITY`.

Observed result:

- SSL connect succeeded
- login succeeded
- the server capability list did **not** include `IDLE`

Practical interpretation:

- the current locally configured user mailbox is not a viable target for a true IMAP IDLE implementation
- implementing a TaskMail page-scoped IDLE client against this mailbox now would be expected to fail or degrade
- for this local environment, an IDLE-only migration should stop until the mailbox/provider path changes or the product
  accepts a non-IDLE fallback strategy

This check was protocol-level and environment-specific. It does not prove that every mailbox of the same provider behaves
identically, but it is decisive for the currently configured local user mailbox used in this workspace.

## Planning Assumption

For this migration, "remove automatic refresh" means:

- remove TaskMail-specific foreground polling

It does not automatically mean:

- remove explicit user-triggered manual refresh
- remove observer-driven reloads after local mail changes

The first implementation should keep manual refresh as a fallback unless product direction changes again.

## Decision Summary

### 1. Remove TaskMail-Specific Foreground Polling

TaskMail should stop owning its own 10-second foreground sync loop.

That means removing the TaskMail-only foreground ticker and the ViewModel logic that repeatedly requests mail sync while
workspace or detail is on screen.

### 2. Keep Observer-Driven UI Reloads

TaskMail should continue to treat local mail-store change observation as the main UI freshness trigger.

This is already the correct update boundary after:

- incoming pushed mail
- manual sync
- local outgoing mail writes

### 3. Reuse Existing Mail Push Infrastructure

TaskMail should not invent a private push service, private socket, or protocol extension.

The migration should reuse the existing account/folder push stack already present in the mail app.

### 4. Push Readiness Must Be Handled Explicitly

This is the key non-obvious pitfall for the migration:

- current global push infrastructure exists
- but default accounts still do not have push enabled folders

So "remove polling and rely on idle push" is incomplete unless the implementation also decides how TaskMail ensures push
is actually active for the mailbox path it depends on.

## Recommended v0.1 Scope

The first idle-push slice should stay deliberately narrow:

- target the TaskMail sender/read mailbox account only
- optimize first for the already-documented single-account TaskMail flow
- avoid silent multi-account push mutations in this first pass
- keep manual refresh as a fallback path

Recommended v0.1 rule:

- if TaskMail sender-account resolution yields exactly one finished-setup account and that backend is push-capable,
  ensure the Inbox remote folder for that account has push enabled
- if sender-account resolution is ambiguous or the backend is not push-capable, do not silently mutate multiple accounts;
  keep fallback behavior explicit instead

Why Inbox-first for v0.1:

- current TaskMail live-reply flow is documented as bot-generated replies landing in the logged-in user mailbox
- local outgoing mail does not need remote push because local-store observation already sees it
- the inbox path is the smallest practical first cut for replacing repeated `checkMail(...)`

This scope may need widening later if device or mailbox evidence shows that TaskMail replies regularly bypass Inbox for
the real user flow.

## Recommended Implementation Order

### Step 1: Delete TaskMail Foreground Polling

Remove the TaskMail-only foreground loop from both surfaces:

- workspace
- session detail

Expected code shape:

- remove `TaskMailForegroundRefreshLifecycleEffect` wiring from the TaskMail screens
- remove `ForegroundRefreshStarted` / `ForegroundRefreshStopped` contract events
- remove ViewModel-owned ticker jobs and ticker factory dependencies
- keep manual refresh actions intact
- keep local mail-store observation intact

### Step 2: Add TaskMail Push-Readiness Wiring

Add a TaskMail-scoped use case that bridges into existing mail push configuration rather than into repeated sync.

Expected responsibilities:

- resolve the TaskMail sender/read account
- check whether the account backend is push-capable
- inspect remote folder details through the existing folder repository
- enable push for the scoped remote Inbox folder when the v0.1 preconditions are satisfied
- avoid destructive changes to unrelated folder settings

This should be implemented as a small bounded TaskMail integration layer, not as a new global push architecture pass.

### Step 3: Keep Local Observation as the UI Reload Path

Do not replace the existing local-store observer with direct push callbacks in TaskMail UI.

Desired flow:

1. mailbox receives new mail through IMAP push
2. local mail store changes
3. existing TaskMail observer reloads workspace/detail data

This keeps TaskMail coupled to repository state, not to transport events.

### Step 4: Define Explicit Fallback Behavior

The migration should not leave the user with silent non-refreshing behavior.

At minimum, define how TaskMail behaves when:

- the backend is not push-capable
- sender-account resolution is ambiguous
- push cannot be enabled for the scoped folder

The fallback can stay simple in v0.1:

- preserve manual refresh
- keep current content stable
- surface a concise non-blocking explanation if live push is unavailable

## Validation Plan

### Focused Automated Coverage

Add or update focused tests for:

- workspace no longer starting foreground polling
- detail no longer starting foreground polling
- manual refresh still requesting sync when explicitly triggered
- push-readiness logic enabling the scoped Inbox folder only when preconditions are satisfied
- no-op behavior for ambiguous sender-account state
- no-op behavior for non-push-capable accounts
- observer-driven reload behavior remaining intact after local mail changes

### Narrow Gradle Tasks

Start with:

- `.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest`
- `.\gradlew.bat :feature:taskmail:internal:detekt`
- `.\gradlew.bat :feature:taskmail:internal:lintDebug`

If the push-readiness implementation touches other modules directly, widen only to the affected modules.

### Manual / Device Validation

Minimum smoke expectations for this slice:

1. Open TaskMail workspace and confirm it no longer drives periodic foreground sync while sitting idle.
2. Confirm manual refresh still works as a fallback.
3. With push-ready account/folder settings in place, keep TaskMail workspace open and verify new TaskMail mail appears
   without pull-to-refresh.
4. Keep a TaskMail session detail screen open and verify the timeline updates after new pushed mail lands.
5. Confirm local outgoing mail still appears without regression because local-store observation remains active.

## Risks and Constraints

### Push Is Not "Free" by Default

The main migration pitfall is operational, not conceptual:

- the app has push support
- but new/default account state still leaves push folders disabled

If this is not handled explicitly, the migration will look correct in code review while regressing live updates in
practice.

### Backend Capability Boundary

This plan assumes an IMAP push-capable mailbox path with actual server-side `IDLE` support.

If the user mailbox backend is not push-capable, or if the server login succeeds but does not advertise `IDLE`, TaskMail
cannot honestly claim idle-push live updates for that account.

### Inbox-Only Scope May Be Too Narrow

The first implementation should not silently expand into all folders.

But if real TaskMail traffic is regularly routed somewhere other than Inbox, this v0.1 scope will need a narrow follow-up
based on observed mailbox behavior, not guesswork.

### Multi-Account Ambiguity

The current TaskMail workspace does not have a robust multi-account push-target model.

This plan intentionally avoids silently mutating push settings across multiple candidate accounts in the first pass.

## Follow-up After Implementation

Once implementation and validation land, update:

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/TASKMAIL-DEBUG-VALIDATION.md`

If the final implementation changes the documented TaskMail operational rules around refresh/live-update prerequisites,
also update the closest durable TaskMail planning or setup note rather than leaving that pitfall only in a handoff.
