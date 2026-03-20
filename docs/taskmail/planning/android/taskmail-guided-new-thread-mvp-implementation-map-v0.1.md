# TaskMail Guided New-Thread MVP Implementation Map (v0.1)

Updated: 2026-03-17

## Status

This document is now a historical/reference implementation map.

It originally served as the pre-code bridge between:

- `docs/taskmail/planning/android/taskmail-next-development-plan-v0.3.md`
- `docs/taskmail/planning/android/taskmail-guided-new-thread-mvp-implementation-checklist-v0.1.md`
- `docs/taskmail/planning/android/taskmail-guided-new-thread-mvp-ui-spec-v0.1.md`

Those implementation decisions have now landed; keep this file only as rationale/reference.

## Repository Reality Snapshot

Current Android TaskMail structure already gives the MVP a clear landing zone:

- API routes currently expose only `TaskMailRoute.Workspace` and `TaskMailRoute.SessionDetail`
- TaskMail navigation is registered entirely inside `feature:taskmail`
- workspace UI already uses MVI and is the natural place for the `New task` entry
- reply sending already has a working transport stack:
  - `SendTaskMailReply`
  - `TaskMailReplySender`
  - `TaskMailMimeMessageFactory`
  - `TaskMailMimeMessageSender`
- mailbox reads currently aggregate TaskMail messages across all finished setup accounts

Two consequences matter for coding:

1. the new composer should stay inside `feature:taskmail` instead of routing through generic mail compose
2. sender-account selection cannot be inferred from TaskMail navigation alone because the current host is not account-scoped

## Locked Implementation Decisions

### 1. Route and Naming

Use `NewTask` naming in code for the first-task composer.

Reason:

- it matches the user-facing CTA
- it fits `TaskMailRoute.NewTask`
- it keeps code identifiers shorter than `new-thread` while remaining semantically correct

Recommended package:

- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/newtask/`

### 2. Sender-Account Policy

Do not rely on an implicit "current mailbox account" for the first implementation.

Current reasons:

- `TaskMailRoute.Workspace` carries no account argument
- `LegacyTaskMailMessageSource` scans all finished setup accounts
- the workspace screen is not bound to one mailbox context

MVP rule:

1. load all finished setup accounts that are eligible to send mail
2. if zero exist, render a blocking account-setup state
3. if one exists, preselect it and show a read-only `Send from` row
4. if multiple exist, show an explicit sender-account selector and require a user choice

Do not auto-pick an arbitrary account when multiple accounts exist.

### 3. New-Task Send Pipeline

Do not overload the reply pipeline for first-task mail.

Reason:

- reply sending requires a source message and reply headers
- first-task mail must not set `In-Reply-To` or `References`
- reusing `TaskMailReplyRequest` would force fake reply context into a non-reply action

Recommended boundary:

- keep reusing `TaskMailMimeMessageSender`
- keep reusing `TaskMailDestinationAddressProvider`
- add a dedicated new-task request, serializer, sender, and non-reply mime-message factory

### 4. Success and Failure Behavior

Lock these behaviors before coding:

- successful send returns to the TaskMail workspace
- successful send shows one transient confirmation message
- successful send does not navigate directly to session detail
- failed send preserves all form fields and advanced-state expansion
- missing sender account or invalid bot-mailbox configuration is a hard blocker, not a silent fallback

## File-Level Implementation Map

### API and Navigation

Update:

- `feature/taskmail/api/src/main/kotlin/net/thunderbird/feature/taskmail/api/TaskMailRoute.kt`
  - add `TaskMailRoute.NewTask`
- `feature/taskmail/api/src/test/kotlin/net/thunderbird/feature/taskmail/api/TaskMailNavigationTest.kt`
  - add route/base-path assertions for `NewTask`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/navigation/DefaultTaskMailNavigation.kt`
  - register the new route and screen
- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/navigation/TaskMailNavHostBackCallbackTest.kt`
  - no expected behavior change; update only if route handling adds back-stack-specific logic

Do not change:

- feature-launcher deep-link targets
- TaskMail host ownership

The MVP entry remains `TaskMailRoute.Workspace`; `NewTask` is an internal flow destination from there.

### Workspace Entry Wiring

Update:

- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/workspace/TaskWorkspaceContract.kt`
  - add `NewTaskClicked`
  - add `OpenNewTask`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/workspace/TaskWorkspaceViewModel.kt`
  - emit navigation effect for `NewTaskClicked`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/workspace/TaskWorkspaceScreen.kt`
  - accept `onNewTask`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/workspace/TaskWorkspaceContent.kt`
  - add top-app-bar action
  - add empty-state CTA
- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/ui/workspace/TaskWorkspaceViewModelTest.kt`
  - add effect test for new-task navigation
- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/ui/workspace/TaskWorkspaceScreenKtTest.kt`
  - add assertions for top-app-bar action and empty-state CTA

### New UI Package

Create:

- `ui/newtask/TaskNewTaskContract.kt`
- `ui/newtask/TaskNewTaskScreen.kt`
- `ui/newtask/TaskNewTaskContent.kt`
- `ui/newtask/TaskNewTaskViewModel.kt`
- `ui/newtask/TaskNewTaskUiState.kt` only if the state grows enough to justify a separate file

Recommended state responsibilities:

- sender-account availability and selection
- backend selection
- `Repo:` / `Task:` / title draft state
- advanced-field draft state
- validation errors
- send-in-progress
- send failure

Recommended effect responsibilities:

- `NavigateBack`
- `ShowMessage`

### Domain and Serialization

Create:

- `domain/model/TaskMailSenderAccount.kt`
  - UI-safe sender-account label model
- `domain/newtask/TaskMailNewTaskDraft.kt`
  - canonical form state for serialization/send
- `domain/newtask/TaskMailNewTaskRequest.kt`
  - selected account id plus canonical subject/body
- `domain/newtask/TaskMailNewTaskResult.kt`
  - success/failure result type
- `domain/newtask/TaskMailNewTaskBodySerializer.kt`
  - canonical first-task body serializer
- `domain/newtask/TaskMailNewTaskSubjectBuilder.kt`
  - `[OC] <title>` / `[CX] <title>` builder
- `domain/newtask/TaskMailNewTaskSender.kt`
  - sender interface
- `domain/usecase/GetTaskMailSenderAccounts.kt`
  - load eligible sender accounts
- `domain/usecase/SendTaskMailNewTask.kt`
  - serialize subject/body and delegate to sender

Do not extend:

- `TaskMailReplyRequest`
- `TaskMailReplyMode`
- `TaskMailReplyBodySerializer`

Those remain reply-only abstractions.

### Data and Transport

Create:

- `data/TaskMailSenderAccountSource.kt`
- `data/LegacyTaskMailSenderAccountSource.kt`
  - backed by `LegacyAccountDtoManager`
- `data/TaskMailNewTaskMimeMessageFactory.kt`
- `data/LegacyTaskMailNewTaskMimeMessageFactory.kt`
  - non-reply message builder
- `domain/newtask/RealTaskMailNewTaskSender.kt`
  - resolves the selected sender account, builds message, and sends it

Reuse:

- `TaskMailDestinationAddressProvider`
- `StorageBackedTaskMailDestinationAddressProvider`
- `TaskMailMimeMessageSender`
- `LegacyTaskMailMimeMessageSender`

Required transport differences from replies:

- no reply anchor lookup
- no `In-Reply-To`
- no `References`
- no quoted-body behavior
- `To` resolves only from TaskMail bot-mailbox configuration

### Dependency Injection

Update:

- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/TaskMailModule.kt`

Add bindings for:

- sender-account source
- new-task mime-message factory
- new-task sender
- new sender-account lookup and send use cases
- new-task ViewModel

Do not disturb existing reply bindings.

## Coding Order

Use this sequence to keep the change reviewable:

1. Add `TaskMailRoute.NewTask` and route tests.
2. Add workspace navigation event/effect and minimal entry wiring.
3. Add domain models plus subject/body serializers with unit tests.
4. Add sender-account source and new-task sender with unit tests.
5. Add `TaskNewTaskViewModel` tests and implement the ViewModel.
6. Add `TaskNewTaskContent`/screen tests and implement the UI.
7. Finish DI wiring and end-to-end navigation.
8. Run narrow verification and update status docs only after code exists.

This order keeps protocol serialization and sender-account policy testable before Compose work begins.

## Test Map

Create or update these tests during implementation:

### API / Navigation

- `feature/taskmail/api/src/test/kotlin/net/thunderbird/feature/taskmail/api/TaskMailNavigationTest.kt`
- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/ui/workspace/TaskWorkspaceViewModelTest.kt`
- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/ui/workspace/TaskWorkspaceScreenKtTest.kt`

### New Domain Tests

- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/domain/newtask/TaskMailNewTaskBodySerializerTest.kt`
- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/domain/newtask/TaskMailNewTaskSubjectBuilderTest.kt`
- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/domain/usecase/GetTaskMailSenderAccountsTest.kt`
- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/domain/usecase/SendTaskMailNewTaskTest.kt`
- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/domain/newtask/RealTaskMailNewTaskSenderTest.kt`

### New UI Tests

- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/ui/newtask/TaskNewTaskViewModelTest.kt`
- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/ui/newtask/TaskNewTaskScreenKtTest.kt`

### Data Tests

- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/data/LegacyTaskMailNewTaskMimeMessageFactoryTest.kt`
- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/data/LegacyTaskMailSenderAccountSourceTest.kt`

## Narrow Verification Plan

Before widening scope, the intended command set is:

1. `.\gradlew.bat :feature:taskmail:api:testDebugUnitTest`
2. `.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest`
3. `.\gradlew.bat :feature:taskmail:internal:detekt`
4. `.\gradlew.bat :feature:taskmail:internal:lintDebug`

If these pass and no unrelated blockers appear, then decide whether broader app-level verification is worth running for the slice.

## Out of Scope for This Coding Slice

- `[SYNC]` discovery UI
- repo-path handoff from discovery results
- recent repositories or favorites
- first-task attachments
- changing TaskMail session aggregation
- generic mail-compose integration

## Exit Condition for Documentation Phase

This planning track is ready to hand off to code when:

- route naming is fixed
- sender-account policy is fixed
- file-level change map is fixed
- test insertion points are fixed
- no remaining protocol ambiguity exists for first-task subject/body serialization

This document is intended to satisfy that boundary.
