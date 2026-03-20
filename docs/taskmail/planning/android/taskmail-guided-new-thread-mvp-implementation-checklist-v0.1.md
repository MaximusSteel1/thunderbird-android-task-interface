# TaskMail Guided New-Thread MVP Implementation Checklist (v0.1)

Updated: 2026-03-17

## Status

This document is now a historical/reference implementation checklist.

It originally served as the concrete Slice 2 implementation note for the guided new-thread MVP before implementation
landed.

- `docs/taskmail/planning/android/taskmail-next-development-plan-v0.3.md`

That slice has now landed in the repository. This file remains useful only as implementation rationale and validation
history.

It translates the Guided New-Thread MVP into:

- UI field specification
- local validation rules
- canonical mail serialization rules
- implementation checklist
- test and smoke checklist

## Inputs

This checklist is based on:

- `docs/TASKMAIL-MAIL-RULES.md`
- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/taskmail/planning/android/taskmail-next-development-plan-v0.3.md`
- `docs/taskmail/planning/android/taskmail-guided-new-thread-mvp-implementation-map-v0.1.md`
- `docs/taskmail/planning/android/taskmail-dual-mailbox-android-adjustments-v0.1.md`
- `E:\projects\mail_based_task_manager\docs/current/mail_protocol.md`
- `E:\projects\mail_based_task_manager\docs/current/android_reply_method_rules.md`
- `E:\projects\mail_based_task_manager\mail_runner\parser.py`
- `E:\projects\mail_based_task_manager\mail_runner\models.py`

## Scope

This MVP includes:

- one dedicated Android TaskMail first-task screen
- canonical first-task mail generation
- minimum required fields for safe mobile use
- collapsed advanced fields
- conditional sender-account selection when TaskMail is not already account-scoped
- explicit dual-mailbox send-target rules

This MVP does not include:

- `[SYNC]` discovery UI
- automatic `Repo:` prefill from discovery results
- recent repositories or favorites
- workdir discovery
- attachments on first-task mail
- dedicated UI for the broader TaskMail protocol superset

## Entry Recommendation

Recommended MVP entry points:

- primary entry: one explicit `New task` action inside the TaskMail workspace
- secondary entry: the same action exposed from the TaskMail empty state

Do not do this in MVP:

- hijack the app's generic compose flow
- add hidden deep-link-only entry as the product path
- scatter multiple inconsistent entry points across unrelated mail screens

## Screen Model

The screen should stay intentionally small.

Recommended visible sections:

1. sender-account resolution row
2. backend selection
3. `Repo:` input
4. `Task:` editor
5. editable subject preview / title row
6. collapsed advanced options
7. send button plus concise mailbox routing note

The UI goal is not to expose every protocol knob.
The UI goal is to make the canonical first-task path hard to get wrong on mobile.

## Field Specification

### Required User Inputs

| Field | UI treatment | Local rule | Wire result |
| --- | --- | --- | --- |
| `Send from` | conditional account row or selector | auto-select when exactly one finished setup account exists; require explicit selection when multiple accounts exist; block send when no sending account is available | selected outbound account only; not serialized into body |
| Backend | segmented choice or radio choice: `OpenCode` / `Codex` | required | subject prefix `[OC]` or `[CX]` |
| `Repo:` | single-line text field with paste-friendly behavior | required after trim | `Repo: <value>` |
| `Task:` | multi-line text area | required after trim | `Task:` block |

### Sender Account Resolution

The current TaskMail host is not account-scoped:

- `TaskMailRoute.Workspace` carries no account argument
- workspace/session aggregation currently reads across all finished setup accounts

Therefore the new-task composer cannot assume one active mailbox account from navigation alone.

Recommended MVP rule:

- if zero finished setup accounts are available, block send and show account-setup guidance
- if exactly one finished setup account is available, auto-select it and show a read-only summary row
- if multiple finished setup accounts are available, show an explicit `Send from` selector and require a user choice

Do not silently pick an arbitrary account when multiple accounts exist.

### Derived but Protocol-Significant Subject Title

The first-task subject still needs human-readable subject text after `[OC]` or `[CX]`.

Current high-level planning did not model this as a separate required field, but the parser still treats subject text as
its own protocol input.

Recommended MVP behavior:

- derive subject title from the first non-empty line of `Task:`
- show the derived value as an editable single-line `Title` or `Subject preview`
- block send if the final subject title is empty after trimming

Recommended rule:

- auto-derive by default
- allow user override when the derived first line is too long or too rough
- do not force a separate required title field before the user can type `Task:`

Wire result:

- subject becomes `[OC] <title>` or `[CX] <title>`

### Advanced Collapsed Fields

| Field | UI treatment | Local rule | Wire rule |
| --- | --- | --- | --- |
| `Workdir:` | optional single-line text | trim; blank = omitted | emit only when non-blank |
| `Mode:` | optional enum control inside advanced section | allowed values: `modify`, `analysis_only` | omit when left at default `modify`; emit only for non-default values |
| `Timeout:` | optional positive integer field in minutes | must parse to integer > 0 | emit only when non-blank |
| `Permission:` | optional enum control | allowed values: `default`, `highest` | omit when default behavior is intended; emit `Permission: highest` when chosen |
| `Profile:` | optional single-line text | trim; blank = omitted | emit only when non-blank |
| `Acceptance:` | optional multi-line list editor | blank allowed | emit `Acceptance:` block only when at least one non-blank line exists |

Notes:

- `Mode:` currently has only two current values in backend models: `modify` and `analysis_only`
- `Permission:` currently allows only `default` and `highest`
- `Acceptance:` can serialize as one line per item with `- ` prefixes in MVP

## Local Validation Rules

Minimum send-time validation:

- backend selected
- sending account resolved or explicitly selected
- `Repo:` non-empty after trim
- `Task:` non-empty after trim
- final subject title non-empty after trim
- `Timeout:` either blank or a positive integer
- `Mode:` if present must be one of `modify` or `analysis_only`
- `Permission:` if present must be one of `default` or `highest`

Recommended validation behavior:

- validate inline while editing
- keep the primary action disabled only for hard blockers
- do not silently rewrite invalid advanced values into something else

## Serialization Contract

### Subject

The composer must serialize:

- `[OC] <subject_title>` for OpenCode
- `[CX] <subject_title>` for Codex

Where `subject_title` is:

- the current editable title value if the user changed it
- otherwise the derived first non-empty line from `Task:`

### Body

Canonical body order:

1. `Repo:`
2. optional `Workdir:`
3. optional `Timeout:`
4. optional `Mode:`
5. optional `Profile:`
6. optional `Permission:`
7. blank line
8. `Task:`
9. task body
10. optional blank line plus `Acceptance:` block

Recommended example with minimum fields:

```text
Repo: E:\projects\android_task_manager

Task:
Audit the new TaskMail screen flow and list only shipping blockers.
```

Recommended example with advanced fields:

```text
Repo: E:\projects\android_task_manager
Workdir: feature/taskmail/internal
Timeout: 120
Mode: analysis_only
Permission: highest

Task:
Audit the new TaskMail screen flow and list only shipping blockers.

Acceptance:
- Do not modify code
- Provide a concise risk list
```

### Omission Rules

To preserve current protocol/default semantics, the composer should omit:

- `Workdir:` when blank
- `Timeout:` when blank
- `Mode:` when left at default `modify`
- `Permission:` when default behavior is intended
- `Profile:` when blank
- `Acceptance:` when empty

## Send-Target and Account Rules

The Guided New-Thread MVP must preserve the dual-mailbox model:

- send from the user mailbox account
- send to the configured TaskMail bot-mailbox address

Current repository reality matters here:

- TaskMail workspace is not currently scoped to one mailbox account
- cross-account workspace loading means "current account" is not a stable implementation input

Implementation rule for sender-account resolution:

- query finished setup accounts from Android account storage
- auto-select only when exactly one eligible user-mailbox account exists
- require explicit user selection when multiple eligible accounts exist
- fail with a user-visible blocking state when no eligible sending account exists

Implementation rule for destination resolution:

- do not infer the destination from "current account address" or "self"
- reuse the TaskMail configuration source for bot-mailbox resolution
- fail with a user-visible send error if the bot-mailbox target is missing or invalid

## Recommended UI Copy

Recommended primary action:

- `Send task`

Recommended advanced-section label:

- `Advanced options`

Recommended routing helper copy:

- `TaskMail requests are sent from this account to your configured TaskMail service address.`

Recommended subject helper copy:

- `The title is used in the mail subject and session list. By default it comes from the first line of Task.`

## ViewModel / State Checklist

- define a dedicated screen state for the Guided New-Thread MVP
- load and expose the available sender-account options separately from form draft state
- auto-select the only eligible sender account without requiring extra user action
- require explicit sender-account choice when multiple eligible accounts exist
- keep required-field state separate from advanced-field state
- compute subject preview from `Task:` when the user has not manually overridden it
- freeze the manual title override once the user edits it explicitly
- expose validation errors as field-level state
- expose send-in-progress and send-failure state
- keep successful-send completion path explicit so the screen can close or reset intentionally

## Implementation Checklist

### Navigation

- add one TaskMail-scoped `New task` route
- wire it from workspace action and empty-state CTA
- keep the route inside TaskMail host/navigation, not generic mail compose navigation

### UI

- build the screen with required fields first
- add editable subject preview/title row
- add collapsed advanced section
- add clear send/error/loading states
- keep the layout mobile-first and paste-friendly for `Repo:`

### Domain / Serialization

- add a dedicated first-task subject/body serializer for new-task mail
- do not reuse reply serialization code paths
- centralize omission rules so UI and tests do not drift

### Send Path

- resolve the sending account from the eligible finished setup account set, not from a reply anchor
- resolve the destination from TaskMail bot-mailbox configuration
- send a new non-reply TaskMail message through the existing mail send stack
- surface missing/invalid bot-mailbox configuration as a hard failure

### Validation and Errors

- block send on missing required fields
- block send on invalid advanced values
- show send failure without destroying the user's typed content
- keep advanced values visible after validation failure

## Automated Test Checklist

- serializer builds `[OC]` and `[CX]` subjects correctly
- serializer derives title from the first non-empty `Task:` line
- serializer prefers explicit user title override over derived title
- serializer omits untouched optional fields correctly
- serializer emits `Mode: analysis_only` when selected
- serializer emits `Permission: highest` when selected
- validation rejects blank backend, `Repo:`, `Task:`, or final title
- validation rejects missing sender-account selection when multiple accounts exist
- validation rejects invalid timeout, mode, or permission
- sender-account loading exposes zero-account, single-account, and multi-account states clearly
- send path targets bot mailbox instead of self/current-account heuristics
- send path reports missing or invalid bot-mailbox configuration
- ViewModel preserves typed content after send failure

## Manual Smoke Checklist

1. Open the new task screen from the TaskMail workspace.
2. Verify sender-account behavior for zero, one, and multiple configured mailbox accounts.
3. Send a minimal `[OC]` task and verify the outgoing subject/body are canonical.
4. Verify the title defaults from the first `Task:` line and remains editable.
5. Send with `Mode: analysis_only` and verify the raw outgoing body includes that field.
6. Send with `Permission: highest` and verify the raw outgoing body includes that field.
7. Leave advanced fields untouched and verify omitted fields stay omitted in the raw outgoing body.
8. Verify the message is sent from the selected user mailbox account to the configured bot mailbox address.
9. Break or remove bot-mailbox configuration and verify the send path fails clearly without losing draft content.

## Explicit Non-Goals

- bootstrap discovery via `[SYNC]`
- repo-result handoff into the composer
- recent repositories or favorites
- attachments for first-task mail
- platform-wide generic compose replacement

## Follow-Up Docs

When implementation starts or decisions change, keep these aligned:

- `docs/taskmail/planning/android/taskmail-next-development-plan-v0.3.md`
- `docs/taskmail/planning/android/taskmail-guided-new-thread-mvp-implementation-map-v0.1.md`
- `docs/TASKMAIL-MAIL-RULES.md`
