# TaskMail Android Next Development Plan (v0.3)

Updated: 2026-03-17

## Status

This document is now a historical planning snapshot.

Do not treat it as the current integrated Android TaskMail plan.

Why it is historical now:

- the guided new-thread, bootstrap discovery, repo handoff, and runtime bot-mailbox slices described here have already
  landed in the repository
- current implementation truth now lives in `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- current validation truth now lives in `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- upcoming cross-repo protocol drift is now driven more by the current PC-side canonical docs and
  `E:\projects\mail_based_task_manager\docs\plans\coding_backlog.md` than by this March 2026 ordering note

This file is still useful for understanding the original near-term ordering and rationale from the 2026-03-17 planning
pass.

## Inputs

This plan is based on:

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/TASKMAIL-DEBUG-VALIDATION.md`
- `docs/TASKMAIL-MAIL-RULES.md`
- `docs/taskmail/planning/android/taskmail-refresh-live-update-plan-v0.1.md`
- `docs/taskmail/planning/android/taskmail-dual-mailbox-android-adjustments-v0.1.md`
- `E:\projects\mail_based_task_manager\docs/current/mail_protocol.md`
- `E:\projects\mail_based_task_manager\docs/current/android_reply_method_rules.md`
- `E:\projects\mail_based_task_manager\state.md`

## Planning Resolution for `[SYNC]`

Cross-repo docs are not perfectly aligned right now:

- `docs/current/mail_protocol.md` describes `[SYNC]` as a current first-mail control action
- `state.md` records live mailbox validation for `[SYNC]`
- the older planning note `docs/plans/project_folder_sync_entry_plan.md` still says the feature is planning-only

This plan follows the current-layer documents and treats `[SYNC]` as protocol-available now.

That means the Android blocker is no longer "wait for `[SYNC]` to exist at all".
The remaining work is Android-side UI, mailbox/result handling, and cross-doc cleanup.

## Current Product Boundary

TaskMail Android remains a thin mail control-plane client.

That still means:

- Session UI is allowed
- Bootstrap UI is allowed
- Android-private protocol or task-manager duplication is still deferred

The boundary change in this version is ordering, not architecture:

- guided new-thread creation now moves earlier because it materially improves correctness and replaces the current
  high-friction generic compose path
- bootstrap discovery still matters, but it becomes a repo-path assist layer rather than a mandatory gate before any
  new-thread UI can start

## Current Priorities

### Priority 1: Targeted Validation Guardrail

Immediate work should begin with a narrow guardrail closeout for the already-implemented refresh/live-update and
dual-mailbox slices.

This is intentionally narrower than the old v0.2 "finish refresh first" framing.

Goal:

- resolve or explicitly accept the current refresh-warning test blocker
- close the highest-risk device smoke gaps that would make new-thread work hard to validate on top of the current
  baseline

This priority does not require waiting for a full repo-wide quality freeze before new UI work starts.

### Priority 2: Guided New-Thread MVP

After the narrow validation guardrail, Android should implement a dedicated first-task screen that serializes canonical
first-task mail.

This MVP should replace the current generic compose route even if `Repo:` is still entered manually in the first version.

Goal:

- reduce first-task formatting mistakes on mobile
- make backend selection and required fields explicit
- preserve canonical mail output and dual-mailbox send rules

### Priority 3: Android Bootstrap Discovery UI

After the Guided New-Thread MVP, Android should add a dedicated bootstrap discovery path for `[SYNC]`.

Goal:

- let the user discover allowed project roots from Android
- keep `[SYNC]` outside TaskMail session/detail projection
- provide a mobile-friendly folder-list result view

### Priority 4: Discovery-to-Composer Handoff

Once both surfaces exist, Android should connect them deliberately.

Goal:

- allow a `[SYNC]` result to seed or prefill `Repo:` in the guided new-thread screen
- improve first-task ergonomics without inventing a new protocol

This remains a later slice because it is an integration convenience layer, not the minimum correctness boundary.

### Priority 5: Compatibility and Quality Freeze

After the MVP composer and bootstrap discovery path are in place, close the cycle with widened validation and doc
alignment.

Work areas:

- protocol-focused tests
- narrow and widened Gradle validation as appropriate
- targeted device smoke for first-task creation and `[SYNC]`
- documentation updates across Android and PC-side current docs where needed

## Recommended Slices

### Slice 1: Targeted Validation Guardrail

Goal:

- close the smallest set of blockers needed before layering new UI work on the current TaskMail baseline

Work areas:

- decide whether to fix or explicitly accept `TaskWorkspaceScreenKtTest.kt:152`
- re-run the highest-value refresh/live-update smoke gaps
- re-run the highest-value dual-mailbox coexistence and `[SYNC]` exclusion smoke gaps

Exit condition:

- refresh/live-update and dual-mailbox slices have an explicit go/no-go interpretation for new UI work
- the remaining validation debt is documented instead of implicit

### Slice 2: Guided New-Thread MVP

Goal:

- add a dedicated Android first-task screen that outputs canonical first-task mail

Minimum surface:

- backend selection: `[OC]` or `[CX]`
- `Repo:`
- `Task:`

Advanced collapsed options:

- `Workdir:`
- `Mode:`
- `Timeout:`
- `Permission:`
- `Profile:`
- `Acceptance:`

Hard requirements:

- send from the user mailbox account
- target the configured TaskMail bot mailbox
- reuse the existing mail send stack
- derive or expose a non-empty subject title for `[OC] <title>` / `[CX] <title>`
- do not invent an Android-only protocol

Concrete implementation checklist:

- `docs/taskmail/planning/android/taskmail-guided-new-thread-mvp-implementation-checklist-v0.1.md`

Concrete UI spec:

- `docs/taskmail/planning/android/taskmail-guided-new-thread-mvp-ui-spec-v0.1.md`

Code-readiness implementation map:

- `docs/taskmail/planning/android/taskmail-guided-new-thread-mvp-implementation-map-v0.1.md`

Exit condition:

- Android no longer depends on generic mail compose for first-task creation
- generated mail matches canonical first-task mail structure

### Slice 3: Android Bootstrap Discovery UI

Goal:

- add the Android entry, result handling, and rendering for `[SYNC]`

Hard requirements:

- `[SYNC]` reply stays outside TaskMail session/detail projection
- result rendering is understandable on mobile
- the flow does not create or fake a TaskMail session

Exit condition:

- the user can trigger discovery and inspect project-folder results from Android

### Slice 4: Discovery-to-Composer Handoff

Goal:

- reduce repo-path typing after discovery results exist

Candidate scope:

- copy path from discovery result into the composer
- explicit "use this repo" handoff into the guided new-thread screen

Exit condition:

- discovery meaningfully lowers path-entry friction for first-task creation

### Slice 5: Compatibility and Quality Freeze

Goal:

- ensure the new composer and bootstrap layers do not regress current TaskMail behavior

Work areas:

- protocol-focused tests for first-task mail serialization and dual-mailbox targeting
- TaskMail-internal quality checks
- targeted device smoke for new-thread and `[SYNC]`
- doc alignment across status, ledger, handoff, and planning docs

## Explicitly Deferred

The following remain out of scope for this near-term plan:

- Android-private control protocol
- direct task creation from `[SYNC]` without canonical first-task mail
- recursive repository browsing in v1
- arbitrary path entry into sync discovery
- full dedicated Android UI for every existing TaskMail protocol command
- complete platform-front-end behavior on Android

## Suggested Execution Order

1. Close the targeted validation guardrail.
2. Implement the Guided New-Thread MVP.
3. Implement Android bootstrap discovery UI for `[SYNC]`.
4. Add discovery-to-composer handoff for repo-path assist.
5. Perform compatibility and quality freeze.

## Documentation Follow-up

When implementation advances, update:

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/TASKMAIL-DEBUG-VALIDATION.md`
- `docs/TASKMAIL-MAIL-RULES.md`

If the older PC-side planning note for `[SYNC]` remains in the tree, update it or clearly mark it as historical so it
does not continue to contradict the current protocol/state docs.
