# TaskMail Guided New-Thread MVP UI Spec (v0.1)

Updated: 2026-03-17

## Status

This document is now a historical/reference UI spec for the Guided New-Thread MVP that landed on 2026-03-17.

Do not treat `taskmail-next-development-plan-v0.3.md` as a current active plan. Use the current-status and
validation-ledger docs for the live baseline.

## Read First

- `docs/taskmail/planning/android/taskmail-guided-new-thread-mvp-implementation-checklist-v0.1.md`
- `docs/taskmail/planning/android/taskmail-guided-new-thread-mvp-implementation-map-v0.1.md`
- `docs/TASKMAIL-MAIL-RULES.md`
- `docs/architecture/ui-architecture.md`
- `docs/architecture/design-system.md`
- `docs/architecture/module-structure.md`

## Purpose

Replace the current generic first-task mail compose path with a dedicated TaskMail screen that:

- collects the minimum required fields safely on mobile
- keeps advanced fields available without making them mandatory
- serializes canonical first-task mail
- preserves the dual-mailbox send model

## Scope

This MVP covers:

- screen placement inside the TaskMail host
- screen layout and field list
- state, events, and effects
- validation and send behavior
- sender-account resolution when TaskMail is not already account-scoped
- serialization decisions that affect the UI

This MVP does not cover:

- `[SYNC]` discovery UI
- repo recents/favorites
- repo-path handoff from discovery results
- `Workdir:` discovery
- full protocol-superset controls

## Placement and Navigation

### Recommended Route

Add a dedicated API route such as:

- `TaskMailRoute.NewTask`

Keep the route in `feature:taskmail:api` and the screen implementation in `feature:taskmail:internal`.

### Recommended Entry Points

For MVP, prefer entry points inside the existing TaskMail workspace host:

- top app bar action on the workspace screen
- empty-state CTA when no TaskMail sessions exist

Do not route this flow through generic mail compose.

## Screen Structure

The screen should follow the existing TaskMail screen pattern:

1. top app bar with title and back action
2. short helper copy explaining this creates a new TaskMail request
3. sender-account row when needed
4. required fields section
5. derived/editable subject title row
6. collapsed advanced section
7. primary send button and secondary helper text

### Sender Account Row

The current TaskMail host is not account-scoped, so the screen cannot infer a sender account from navigation alone.

MVP behavior:

- if zero finished setup accounts exist, show a blocking account-setup state instead of a sendable form
- if exactly one finished setup account exists, preselect it and show a concise read-only `Send from` row
- if multiple finished setup accounts exist, show a required sender-account selector above the form

Do not auto-pick an arbitrary account when multiple accounts are available.

### Required Fields Section

Display these controls above the fold:

- sender account selector when multiple eligible accounts exist
- backend selector
- `Repo:`
- `Task:`

### Subject Title Row

Display one editable single-line row for the human-readable subject title.

Behavior:

- derive the initial value from the first non-empty line of `Task:`
- allow user override when the derived title is too rough or too long
- keep the row visible instead of hiding subject generation behind send-time magic
- do not force the user to fill a separate title before they can begin typing `Task:`

### Advanced Section

Keep advanced fields behind an expand/collapse affordance:

- `Workdir:`
- `Mode:`
- `Timeout:`
- `Permission`
- `Profile:`
- `Acceptance:`

Default state for MVP:

- collapsed on first load

## Field Specification

| Field | Control | Required | Validation | Serialization |
| --- | --- | --- | --- | --- |
| `Send from` | conditional account row or selector | required when multiple eligible accounts exist | must resolve to one finished setup account before send | transport only; not serialized into body |
| Backend | segmented choice / chips | yes | one of `[OC]` or `[CX]` must be selected | subject prefix |
| `Repo:` | single-line text | yes | non-blank after trim | `Repo: <value>` |
| `Task:` | multiline text | yes | non-blank after trim | `Task:` block preserving line breaks |
| Title / subject preview | single-line text derived from `Task:` until overridden | yes | non-blank after trim | subject text after `[OC]` or `[CX]` |
| `Workdir:` | single-line text | no | none in MVP | omit when blank |
| `Mode:` | enum choice: `Modify (default)` / `Analysis only` | no | maps only to `modify` or `analysis_only` | omit for `modify`; emit `Mode: analysis_only` otherwise |
| `Timeout:` | numeric text | no | positive integer when present | omit when blank |
| `Permission` | two-state choice: backend default / highest | no | UI only supports omit or `highest` in MVP | omit or `Permission: highest` |
| `Profile:` | single-line text | no | none in MVP | omit when blank |
| `Acceptance:` | multiline text | no | blank allowed | `Acceptance:` block with one `- ` item per non-blank line |

## Field Behavior Notes

### `Send from`

- Base the option list on finished setup accounts only.
- When only one eligible account exists, preselect it and avoid extra taps.
- When multiple eligible accounts exist, require explicit selection before send.
- When no eligible account exists, replace the sendable state with account-setup guidance.
- The chosen account affects transport only and must not change the serialized subject/body.

### Backend

- User-facing labels may be human-friendly.
- Wire output must still be canonical `[OC]` or `[CX]`.

### `Repo:`

- Preserve the user-entered path as text.
- Trim leading/trailing whitespace before validation and serialization.
- Do not attempt path existence validation on-device in MVP.

### `Task:`

- Support multiline input.
- Preserve line breaks in the serialized `Task:` block.

### Title / Subject Preview

- Keep this field synchronized with the first non-empty line of `Task:` until the user edits it directly.
- Once the user overrides the title, stop auto-replacing it from later `Task:` edits.
- Block send if the final title is blank after trimming.
- Serialize the subject as `[OC] <title>` or `[CX] <title>`.

### `Mode:`

- The UI should expose only the current supported values from backend models: `modify` and `analysis_only`.
- User-facing labels may be friendlier than wire values.
- When the user leaves the control at the default `modify`, omit the field instead of serializing `Mode: modify`.

### `Permission`

For first-task mail, omission and explicit `default` resolve to the same backend behavior.

Therefore the MVP UI should expose:

- `Use backend default`
- `Highest`

When `Use backend default` is selected, omit the field instead of serializing `Permission: default`.

### `Acceptance:`

- Support multiline input.
- Treat each non-blank input line as one acceptance item.
- Serialize the block with one canonical `- ` item per non-blank line.
- Preserve user-entered content on-screen even if the serializer normalizes list prefixes.

## State Model

Recommended state fields:

- `availableSenderAccounts`
- `selectedSenderAccountId`
- `senderAccountBlockingError`
- `selectedBackend`
- `repoPath`
- `taskText`
- `subjectTitle`
- `isSubjectTitleEdited`
- `workdir`
- `mode`
- `timeoutText`
- `permissionSelection`
- `profile`
- `acceptanceText`
- `isAdvancedExpanded`
- `isSending`
- `fieldErrors`
- `sendError`

Recommended event set:

- `BackClicked`
- `SenderAccountSelected`
- `BackendSelected`
- `RepoChanged`
- `TaskChanged`
- `SubjectTitleChanged`
- `AdvancedToggleClicked`
- `WorkdirChanged`
- `ModeChanged`
- `TimeoutChanged`
- `PermissionChanged`
- `ProfileChanged`
- `AcceptanceChanged`
- `SendClicked`
- `DismissSendError`

Recommended effects:

- `NavigateBack`
- `ShowMessage`

## Validation Rules

### Local Validation

The send action should fail locally when:

- no eligible sender account can be resolved
- backend is not selected
- `Repo:` is blank
- `Task:` is blank
- title / subject preview is blank
- `Timeout:` is present but not a positive integer
- `Mode:` is not one of `modify` or `analysis_only`
- `Permission` is not one of backend default or `highest`

### Validation Presentation

- show field-level errors inline where practical
- keep the screen scroll position stable where possible
- preserve all user input on validation failure

## Send Behavior

### Transport Rules

The screen must send first-task mail as:

- selected user mailbox account -> configured bot-mailbox address
- canonical first-task subject/body
- no reply headers

### Success Behavior

On send success:

- stop the loading state
- emit a one-time success message
- navigate back to the TaskMail workspace
- do not navigate directly to session detail

Recommended helper copy:

- "Task request sent. It will appear after the first TaskMail status mail arrives."

### Failure Behavior

On send failure:

- preserve all field values
- preserve advanced-section state
- show an actionable send error

## Serialization Rules the UI Must Respect

The screen must output canonical first-task mail:

- subject becomes `[OC] <title>` or `[CX] <title>`
- title comes from the editable subject row, which defaults to the first non-empty line of `Task:`
- selected sender account affects transport only and is not serialized into the body
- body begins with required fields
- blank optional fields are omitted
- `Mode:` is omitted for the default `modify`
- `Permission:` is omitted for backend-default behavior
- `Task:` remains a multiline block and `Acceptance:` serializes as a multiline list block when used
- no Android-only JSON, attachments, or special metadata are introduced

## Design System and Accessibility Constraints

- Build the screen with Compose and the existing design system.
- Prefer the same screen/content split used by other TaskMail screens.
- Keep the form vertically scrollable.
- Ensure the primary send button is reachable on smaller screens.
- Use accessible labels for backend selection, advanced toggle, and send button.
- Do not rely on placeholder text as the only label.

## Non-Goals

This MVP spec intentionally does not define:

- bootstrap discovery result rendering
- clipboard/repo handoff from `[SYNC]`
- recent repositories
- workdir discovery UI
- dedicated controls for `/new`, `/rerun`, `/kill`, or other broader protocol actions

Those belong to later slices.
