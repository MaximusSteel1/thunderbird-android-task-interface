# TaskMail Android Phase 3

This document defines the next implementation phase after Phase 2 host integration has been validated.

## Date

- Last updated: 2026-03-14

## Current Baseline

This document assumes the following Phase 2 baseline is already in place:

- TaskMail can be opened from the formal in-app entry point
- TaskMail is hosted through `FeatureLauncher`
- TaskMail reads real mail data through the existing repository stack
- `workspace -> detail -> workspace` navigation is stable
- device smoke validation has passed for the current read-only flow

Current implementation baseline:

- `feature:taskmail:api`
- `feature:taskmail:internal`
- `LegacyTaskMailMessageSource`
- `LegacyTaskMailBodyExtractor`
- `DefaultTaskMailRepository`
- formal launcher routing
- formal drawer entry
- interactive session detail UI with reply composer, quick actions, and refresh
- real TaskMail reply sending wired through the existing mail send stack inside `feature/taskmail/internal`

Protocol authority remains:

- `docs/TASKMAIL-MAIL-RULES.md`

If this document conflicts with the current mail protocol, update `docs/TASKMAIL-MAIL-RULES.md` first and then align this file.

## Current Implementation Status

As of 2026-03-14, the main Phase 3 code path is implemented and buildable.

Work package snapshot:

| Work package | Status | Notes |
| --- | --- | --- |
| WP1 - Reply anchor and domain model | Implemented | `replyContext` is derived from the newest logical-session message; mixed-account sessions disable reply. |
| WP2 - Reply contract and fake sender | Implemented | Internal sender seam, request/result models, and `SendTaskMailReply` are in place. |
| WP3 - Detail MVI and state handling | Implemented | Draft, send, `/status`, question-choice, error, and refresh flows are wired. |
| WP4 - Detail UI upgrade | Implemented | Detail screen uses a scaffold-style layout with a sticky composer and quick actions. |
| WP5 - Real sender integration | Implemented | Production sender stays in `feature/taskmail/internal` and reuses the existing mail send stack. |
| WP6 - Validation and smoke | In progress | Narrow unit tests plus both app compile/assemble flows have passed; device smoke and repo-wide quality tasks remain pending. |

Pending validation outside the current implementation pass:

- on-device smoke validation
- `connectedAndroidTest`
- `lint`
- `detekt`
- `spotlessCheck`

## Phase Goal

Phase 3 turns TaskMail from a read-only viewer into a lightweight interaction surface for an existing task thread.

Expected user outcome:

- the user can open a TaskMail session detail page
- the user can read the latest state, summary, and pending question
- the user can send a plain-text reply from inside TaskMail
- the user can answer a pending question with a quick action
- the user can send a status query without leaving TaskMail
- the session remains anchored to the existing mail thread instead of creating a new thread

One-sentence summary:

> Phase 3 adds reply interaction and status query to the existing session detail flow, while keeping TaskMail aligned with the current mail-thread protocol.

## What Phase 3 Is Not

Phase 3 is not a general-purpose mail compose rewrite.

This phase should not introduce:

- a new standalone mail protocol
- rich text or Markdown rendering
- attachments
- full traditional quoted-history reply behavior for every TaskMail action
- full slash-command UI coverage
- new task creation from TaskMail Android UI
- search and filter UI
- notifications or background polling
- a replacement for the existing generic mail composer

Those concerns remain later-phase work.

## Protocol Assumptions

Phase 3 should follow the current rules already documented in `docs/TASKMAIL-MAIL-RULES.md`.

The important constraints are:

- normal natural-language reply means "continue the current session"
- if the current session is waiting for user input, the user may answer directly without adding `/resume`
- slash commands already exist on the protocol side, but Android Phase 3 should expose only the small subset needed for the interaction loop
- reply must stay in the existing session thread and must not fork a new session
- Android must not invent a new standalone status subject or a new session subject

For Phase 3, the explicitly supported outgoing bodies should be:

| Action | Outgoing body |
| --- | --- |
| Plain-text reply | The user-entered text only |
| Question choice reply | The selected choice text only when exactly one pending question is active |
| Status query | `/status` |

Important notes:

- do not append `TASK-STATE` or `TASK-QUESTION` blocks in Android-generated replies
- do not auto-prefix question answers with `/resume`
- do not expose `/new`, `/sessions`, `/rerun`, or `/kill` in the first Phase 3 UI
- if a session is waiting on multiple pending questions, follow `docs/TASKMAIL-MAIL-RULES.md` and use a structured answer body instead of a one-tap single-choice reply
- production send already uses standard reply semantics at the mail-header level:
  - reply recipients
  - normalized reply subject
  - `In-Reply-To`
  - `References`
- production body composition currently uses `QuotedTextMode.NONE`, so the outgoing body is new text only and does not append the anchor message body
- if reply-body quoting is added later for UX parity with traditional mail clients, limit it to:
  - plain-text replies only
  - a minimal plain-text quote of the newest anchor message
  - no full-thread history dump
- question-choice replies and `/status` should remain quote-free even if free-text quoting is added later
- multi-question structured answer support is now part of the protocol surface; read-side compatibility should land before any Android-side multi-question reply UI

## Core Product Decision

### Session detail becomes the interaction hub

Phase 3 should keep TaskMail interaction centered on the session detail page.

Reasoning:

- the detail page already contains state, summary, question, and timeline
- the user needs thread context while replying
- reply affordances belong next to the current question and state, not in the workspace summary list

This means:

- workspace stays summary-oriented
- detail becomes "read + respond"

## Key Technical Decisions

### 1. Mail remains the control plane

TaskMail Android still communicates by replying to the existing mail thread.

Phase 3 must reuse the existing mail sending stack instead of introducing a second SMTP path.

### 2. Reply target must come from the logical session, not from the UI route alone

Current detail routes only carry:

- `sessionId`
- `threadId`

That is not enough to send a reply. To reply safely, Android also needs a concrete reply anchor.

The reply anchor should be selected from the newest message inside the current logical session and should provide enough information to reconstruct a message reference.

The existing stable reply identity in the codebase is `MessageReference(accountUuid, folderId, uid)`.

So the minimum send-capable reply context is:

- `accountUuid`
- `folderId`
- `messageServerId`

Optional diagnostic metadata may also carry:

- `threadRootId`
- `anchorTimestamp`

This context already exists in the raw TaskMail message layer:

- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/data/TaskMailMessageSource.kt`

### 3. Logical session reply target selection must be explicit

Current TaskMail grouping intentionally merges messages by:

1. `session_id`
2. fallback `thread_id`

That means one logical session may contain more than one physical mail thread.

For outgoing reply actions, Phase 3 should use this rule:

1. collect all messages that belong to the current logical session
2. sort by `(timestamp, messageServerId)` ascending
3. choose the newest message as the reply anchor
4. derive reply context from that message

This keeps replies attached to the latest active physical thread in the current logical session.

### 4. Mixed-account sessions must be treated as unsafe

If a logical session contains messages from multiple `accountUuid` values, Android should not guess which identity to use for reply.

Phase 3 should therefore use this guardrail:

- if all messages in the session belong to the same account, reply is allowed
- if multiple account UUIDs are present, disable reply and show a clear "reply unavailable" explanation

This is safer than silently replying from the wrong account.

### 5. No optimistic fake timeline item as the source of truth

After send succeeds, UI may show transient feedback such as:

- "Reply sent"
- "Waiting for refresh"

But the canonical timeline should still come from repository reload.

Phase 3 should not permanently inject a fake timeline item into the session detail state and treat it as authoritative.

### 6. Full generic composer is optional fallback, not the primary Phase 3 path

The existing legacy reply path can already open `MessageCompose` from a `MessageReference`.

Relevant code today:

- `legacy/ui/legacy/src/main/java/com/fsck/k9/activity/compose/MessageActions.kt`
- `legacy/ui/legacy/src/main/java/com/fsck/k9/activity/MessageCompose.java`

However, that path does not currently expose a stable, narrow prefill contract for TaskMail-specific quick actions such as:

- selected-choice answer
- `/status`

So the recommended primary plan for Phase 3 is:

- TaskMail-native reply UI
- TaskMail-specific sender boundary
- reuse existing send infrastructure underneath

If needed later, opening the full generic composer can still be added as a secondary advanced action.

### 7. Reply threading semantics and quoted-body UX are separate decisions

Phase 3 should distinguish these two concerns:

- thread correctness
- body presentation

Thread correctness is mandatory and already comes from standard reply semantics such as:

- reply recipients
- reply subject normalization
- `In-Reply-To`
- `References`
- `MessageReference`-anchored sending

Quoted body text is a UX choice, not the primary source of thread linkage.

Current implementation stance:

- all three supported actions send only their action body
- no quoted body is appended automatically

Recommended follow-up stance if we later want closer parity with a traditional mail-client reply experience:

- add a minimal plain-text quote only for free-text replies
- keep question-choice replies quote-free
- keep `/status` replies quote-free
- never append the entire thread history

## Scope

### In Scope

- session detail layout enhancement
- reply draft state in session detail
- plain-text reply from session detail
- quick reply for `TASK-QUESTION` choices
- status query quick action using `/status`
- reply context extraction from repository/domain layer
- TaskMail-specific send contract and use case
- send success and failure feedback
- detail refresh after send
- targeted tests and validation docs

### Out of Scope

- workspace-level reply entry
- `/new`
- `/sessions`
- `/rerun`
- `/kill`
- attachments
- HTML compose
- Markdown rendering
- slash-command palette UI
- notification work
- background polling and push-like sync behavior
- replacing the existing mail compose experience for the rest of the app

## User Experience Plan

### 1. Detail layout

Current detail screen is a vertically scrollable column with a simple back text action.

Phase 3 should upgrade this into a more structured screen:

- top app bar
- manual refresh action
- scrollable content area
- sticky bottom reply composer

Recommended visual structure:

```text
TaskSessionDetail
|- Top app bar
|  |- Back
|  \- Refresh
|- Scrollable content
|  |- SessionHeader
|  |- TaskStateCard
|  |- PendingQuestionCard
|  |- TimelineList
|  \- Error/Loading states
\- Bottom reply surface
   |- Draft input
   |- Send button
   |- /status quick action
   \- Choice quick actions when question exists
```

### 2. Reply composer states

The reply composer should explicitly model these states:

- idle
- sending
- send failed with draft preserved
- send succeeded and refresh running

Behavior expectations:

- send button disabled while sending
- draft preserved on failure
- refresh failure must not wipe already loaded detail content
- success feedback should be short and non-blocking

### 3. Question answering

If a `TASK-QUESTION` exists:

- the question card remains read-only
- each choice is actionable
- tapping a choice sends the choice text as the outgoing body

Phase 3 should prefer one-tap send for these choices only if:

- reply context is available
- the session is not already sending another reply

Otherwise:

- the choice action should be disabled or fall back to inserting the choice into the draft field

### 4. Status query

Add a minimal quick action for status query.

Behavior:

- visible when reply is available
- sends `/status`
- keeps the user on the same session detail screen
- reloads detail after send

Phase 3 should not expose the rest of the slash command set in the first cut.

## Data and Domain Changes

### 1. Extend `TaskSessionDetail`

Current `TaskSessionDetail` already carries:

- session metadata
- status
- summary
- question
- timeline

Phase 3 should extend it with reply capability metadata.

Recommended addition:

- `replyContext: TaskSessionReplyContext?`

Suggested new model:

```kotlin
internal data class TaskSessionReplyContext(
    val accountUuid: String,
    val folderId: Long,
    val messageServerId: String,
    val threadRootId: Long? = null,
    val anchorTimestamp: Long? = null,
)
```

Likely file:

- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/model/TaskSessionReplyContext.kt`

Likely modified file:

- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/model/TaskSessionDetail.kt`

### 2. Repository responsibility

`DefaultTaskMailRepository` should become the place that decides:

- whether reply is available
- which message is the reply anchor
- whether the session is safe to reply to

This keeps reply-target logic inside the same layer that already owns logical grouping.

Likely modified files:

- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/data/DefaultTaskMailRepository.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/data/TaskMailMessageSource.kt`

### 3. Send contract

Introduce a narrow sending contract instead of binding UI directly to legacy compose APIs.

Suggested interface:

```kotlin
internal interface TaskMailReplySender {
    suspend fun send(request: TaskMailReplyRequest): TaskMailReplyResult
}
```

Suggested request model:

```kotlin
internal data class TaskMailReplyRequest(
    val context: TaskSessionReplyContext,
    val body: String,
    val kind: TaskMailReplyKind,
)
```

Suggested result model:

```kotlin
internal data class TaskMailReplyResult(
    val isSuccess: Boolean,
    val errorMessage: String? = null,
)
```

Suggested kind enum:

- `FreeText`
- `QuestionChoice`
- `StatusQuery`

Likely files:

- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/reply/TaskMailReplySender.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/reply/TaskMailReplyRequest.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/reply/TaskMailReplyResult.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/reply/TaskMailReplyKind.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/usecase/SendTaskMailReply.kt`

Implementation note:

- prefer keeping the sender interface inside `feature:taskmail:internal`
- keep the real send implementation, TaskMail-specific mapping, validation, and feature-scoped DI wiring inside `feature:taskmail:internal`
- only lift a minimal seam to `feature:taskmail:api` if another feature actually needs to call TaskMail send behavior
- do not move TaskMail UI logic into `app-common`
- the currently confirmed reply anchor type in the codebase is `app.k9mail.legacy.message.controller.MessageReference`
- `legacy/ui/legacy/.../MessageActions.kt` can open a reply composer from `MessageReference`, but it does not provide a narrow TaskMail quick-action contract for prefilled one-tap bodies
- `app-common` should remain the wiring/composition layer only:
  - it may import a TaskMail module or bind an interface to an implementation
  - it should not own TaskMail-specific abstractions or business semantics
- if TaskMail eventually needs a shared lower-level mail submission capability, that capability should live in an appropriate shared mail/core area rather than in `app-common`

### 4. UI state

`TaskSessionDetailUiState` should be extended with interaction state.

Suggested additions:

- `draftText: String`
- `isSending: Boolean`
- `sendError: String?`
- `canReply: Boolean`
- `canQueryStatus: Boolean`
- `replyUnavailableReason: String?`

Likely modified file:

- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/TaskSessionDetailUiState.kt`

## MVI Plan

Current detail contract only supports:

- load detail
- back

Phase 3 should extend it to support reply interaction.

Suggested new events:

- `DraftChanged(text)`
- `SendReplyClicked`
- `SendChoiceClicked(choice)`
- `StatusQueryClicked`
- `RefreshClicked`
- `DismissSendError`

Suggested new effects, only if needed:

- `ReplySent`
- `ShowMessage(text)`

However, keep effects minimal. If state alone is enough, prefer state-driven UI.

Likely modified files:

- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/TaskSessionDetailContract.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/TaskSessionDetailViewModel.kt`

## Refresh Strategy

Phase 3 must define a clear post-send refresh behavior.

Recommended behavior:

1. user sends reply
2. send use case completes successfully
3. UI enters a short "waiting for refresh" state
4. detail reload runs immediately
5. if refresh succeeds, updated canonical session state replaces the old state

Important constraints:

- do not clear existing detail before refresh unless a full reload is truly necessary
- if refresh fails, keep the previous detail visible and show a non-destructive error
- workspace auto-refresh is optional in Phase 3 and should not block detail interaction work

Recommended interpretation:

- session detail is the primary interactive truth surface for Phase 3
- workspace summary may remain eventually consistent until the next explicit reload

## Recommended Implementation Order

### Step 0: Freeze protocol assumptions

- re-check `docs/TASKMAIL-MAIL-RULES.md`
- confirm that question answers may be plain-text replies
- confirm `/status` remains the chosen quick command
- avoid implementing any undocumented answer capsule format

### Step 1: Add reply context to domain and repository

- add `TaskSessionReplyContext`
- extend `TaskSessionDetail`
- update `DefaultTaskMailRepository` to select reply anchors
- disable reply for mixed-account sessions

### Step 2: Add sender seam and fake implementation

- define `TaskMailReplySender`
- define request/result models
- add `SendTaskMailReply`
- create fake sender for tests before wiring the real send path

### Step 3: Upgrade detail MVI

- extend `TaskSessionDetailContract`
- add reply draft state
- add send and refresh handling to `TaskSessionDetailViewModel`
- keep failure handling draft-safe

### Step 4: Upgrade detail UI

- refactor detail screen to a scaffold-style layout
- add sticky reply composer
- add `/status` quick action
- add question choice actions
- keep timeline and state cards intact

### Step 5: Wire the real send implementation

- bind `TaskMailReplySender` to the existing mail send stack
- ensure no duplicate send path is introduced
- verify the outgoing reply stays in the current thread

### Step 6: Validate and document

- repository tests
- ViewModel tests
- detail UI interaction tests
- device smoke tests
- update this document with any final seam adjustments

## Execution Checklist

This section is the recommended implementation checklist for turning Phase 3 into reviewable work packages.

Status snapshot:

- Gate 0 decisions have been frozen in code and documentation for the current implementation pass.
- Work Packages 1 through 5 are implemented in code.
- Work Package 6 is partially complete:
  - narrow unit tests have passed
  - both app compile flows have passed
  - both app assemble flows have passed
  - device smoke and repo-wide quality tasks remain pending

### Gate 0: Freeze implementation seams

- [ ] Re-check `docs/TASKMAIL-MAIL-RULES.md` and confirm there is still no Android-specific answer capsule format to generate.
- [ ] Freeze the sender ownership model before writing production send code:
  - [ ] real sender stays in `feature/taskmail/internal`
  - [ ] `app-common` only imports/binds it
  - [ ] no TaskMail-specific seam is added to `app-common`
  - [ ] only move a minimal contract to `feature/taskmail/api` if a second feature actually needs it
- [ ] Freeze the minimum `TaskSessionReplyContext` fields:
  - [ ] required: `accountUuid`, `folderId`, `messageServerId`
  - [ ] optional: `threadRootId`, `anchorTimestamp`
- [ ] Freeze the disabled-reply UX copy for:
  - [ ] mixed-account sessions
  - [ ] missing reply anchor
- [ ] Freeze post-send behavior:
  - [ ] short success feedback
  - [ ] immediate detail reload
  - [ ] preserve existing detail content if refresh fails

### Work Package 1: Reply anchor and domain model

Target files:

- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/model/TaskSessionDetail.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/model/TaskSessionReplyContext.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/data/DefaultTaskMailRepository.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/data/TaskMailMessageSource.kt`
- preview and fake data files touched by the new model shape

Checklist:

- [ ] Add `TaskSessionReplyContext`.
- [ ] Extend `TaskSessionDetail` with `replyContext`.
- [ ] Select the newest logical-session message as the reply anchor.
- [ ] Keep reply-anchor selection independent from route params alone.
- [ ] Disable reply when one logical session contains multiple `accountUuid` values.
- [ ] Keep `replyContext == null` when no valid anchor can be derived.
- [ ] Update preview/fake data to include reply-capable and reply-disabled examples.

Done when:

- [ ] the repository returns a stable `replyContext` for normal sessions
- [ ] mixed-account sessions are explicitly non-replyable
- [ ] existing read-only detail data still renders unchanged

Test gate:

- [ ] add/update repository tests for newest-message anchor selection
- [ ] add/update repository tests for mixed-account disable behavior
- [ ] add/update repository tests for null-anchor fallback behavior

### Work Package 2: Reply contract and fake sender

Target files:

- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/reply/TaskMailReplySender.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/reply/TaskMailReplyRequest.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/reply/TaskMailReplyResult.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/reply/TaskMailReplyKind.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/usecase/SendTaskMailReply.kt`

Checklist:

- [ ] Add `TaskMailReplyKind`.
- [ ] Add `TaskMailReplyRequest`.
- [ ] Add `TaskMailReplyResult`.
- [ ] Add `TaskMailReplySender`.
- [ ] Add `SendTaskMailReply`.
- [ ] Add a fake sender for tests before wiring the real implementation.
- [ ] Keep the contract internal unless a real cross-feature caller appears.
- [ ] Lock the exact outgoing body mapping:
  - [ ] free text -> raw draft text only in the current implementation
  - [ ] question choice -> selected choice text only, with no quoted body
  - [ ] status query -> `/status`, with no quoted body

Done when:

- [ ] ViewModel and domain code can depend on the sender seam without knowing any legacy send classes
- [ ] the fake sender is sufficient to drive success and failure tests

Test gate:

- [ ] add unit tests for exact outgoing body generation
- [ ] add unit tests for success and failure result propagation

### Work Package 3: Detail MVI and state handling

Target files:

- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/TaskSessionDetailContract.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/TaskSessionDetailViewModel.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/TaskSessionDetailUiState.kt`
- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/TaskSessionDetailViewModelTest.kt`

Checklist:

- [ ] Extend the contract with draft, send, refresh, and dismiss-error events.
- [ ] Extend state with `draftText`, `isSending`, `sendError`, `canReply`, `canQueryStatus`, and `replyUnavailableReason`.
- [ ] Preserve loaded detail content during send and refresh.
- [ ] Preserve draft text on send failure.
- [ ] Block send actions when reply is unavailable.
- [ ] Trigger immediate detail reload after successful send.
- [ ] Keep one-time effects minimal and prefer state-driven UI.

Done when:

- [ ] the ViewModel supports `load`, `refresh`, `draft changed`, `send free text`, `send choice`, and `send /status`
- [ ] send failure does not clear the draft or wipe the loaded detail

Test gate:

- [ ] add tests for draft update
- [ ] add tests for send success
- [ ] add tests for send failure with draft preserved
- [ ] add tests for blocked send when `replyContext == null`
- [ ] add tests for `/status` dispatch
- [ ] add tests for question-choice dispatch
- [ ] add tests for refresh-after-send behavior

### Work Package 4: Detail UI upgrade

Target files:

- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/TaskSessionDetailContent.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/component/TaskReplyComposer.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/component/PendingQuestionCard.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/component/TimelineList.kt`
- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/TaskSessionDetailScreenKtTest.kt`

Checklist:

- [ ] Replace the temporary text-only layout with a scaffold-style detail screen.
- [ ] Add a top app bar with back and refresh actions.
- [ ] Keep the main content vertically scrollable.
- [ ] Add a sticky bottom reply composer.
- [ ] Add a dedicated `/status` quick action.
- [ ] Add question-choice quick actions when a pending question exists.
- [ ] Disable send controls while sending.
- [ ] Show non-destructive failure feedback without blanking the screen.
- [ ] Use existing design-system components instead of introducing raw Material components outside the design system modules.

Done when:

- [ ] reply-capable sessions visibly expose the composer
- [ ] reply-disabled sessions show a clear reason instead of dead controls
- [ ] the timeline, header, and state cards remain intact

Test gate:

- [ ] add UI tests for composer visibility
- [ ] add UI tests for disabled state while sending
- [ ] add UI tests for question-choice visibility
- [ ] add UI tests for `/status` visibility
- [ ] add UI tests for failure feedback without clearing content

### Work Package 5: Real sender integration

Target files:

- `feature/taskmail/internal` sender, mapper, validator, and DI wiring files
- `feature/taskmail/api` only if a minimal public seam is truly needed
- `app-common` binding/import points
- any existing shared mail send infrastructure files needed by the internal implementation

Checklist:

- [ ] Keep the production `TaskMailReplySender` implementation in `feature/taskmail/internal`.
- [ ] Keep TaskMail-specific mapping and validation in `feature/taskmail/internal`.
- [ ] Let `app-common` only import the TaskMail module or bind interface to implementation.
- [ ] Do not define or move TaskMail-specific abstractions into `app-common`.
- [ ] If a `MessageReference` bridge is used, map from `TaskSessionReplyContext` to `MessageReference(accountUuid, folderId, messageServerId)`.
- [ ] Do not rely on `MessageCompose.ACTION_REPLY` as the primary Phase 3 path unless there is a proven narrow contract for TaskMail quick-action bodies.
- [ ] Reuse the existing send infrastructure instead of introducing a second SMTP path.
- [ ] If a reusable lower-level mail submission capability is missing, add it in the appropriate shared mail/core area instead of `app-common`.
- [ ] Ensure the reply stays attached to the referenced message thread.
- [ ] Preserve standard reply headers and recipient semantics (`Re:`, reply recipients, `In-Reply-To`, `References`).
- [ ] Ensure the correct account identity is used.
- [ ] Keep the current body composition quote-free unless a follow-up explicitly adds minimal free-text-only quoting.
- [ ] Do not log reply body text, question text, or selected choice text.
- [ ] Bind the real sender in Koin and keep tests using the fake sender.

Done when:

- [ ] production code can send the three supported reply kinds
- [ ] TaskMail UI code does not need to know about composer activity details
- [ ] `app-common` still acts only as composition root, not as TaskMail abstraction owner
- [ ] no duplicate send path has been introduced

Test gate:

- [ ] add focused mapping tests for request -> reply anchor translation
- [ ] run compile checks for both app targets after wiring the real binding

### Work Package 6: Validation, smoke, and documentation

Checklist:

- [ ] Run repository and ViewModel unit tests.
- [ ] Run TaskMail UI tests.
- [ ] Compile Thunderbird debug.
- [ ] Compile K-9 debug.
- [ ] Assemble both debug APKs if compile passes.
- [ ] Run on-device smoke validation when a device is available.
- [ ] Update this document with any seam changes discovered during implementation.
- [ ] Update validation guidance if the user-visible flow or failure behavior changes.

Verification commands:

```powershell
.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest --tests "*DefaultTaskMailRepositoryTest"
.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest --tests "*TaskSessionDetailViewModelTest"
.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest --tests "net.thunderbird.feature.taskmail.internal.ui.detail.*"
.\gradlew.bat :app-thunderbird:compileFossDebugKotlin
.\gradlew.bat :app-k9mail:compileFossDebugKotlin
.\gradlew.bat :app-thunderbird:assembleFossDebug
.\gradlew.bat :app-k9mail:assembleFossDebug
```

Current verification snapshot from the latest implementation pass:

- passed:
  - `.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest --tests "net.thunderbird.feature.taskmail.internal.domain.reply.RealTaskMailReplySenderTest"`
  - `.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest --tests "net.thunderbird.feature.taskmail.internal.domain.usecase.SendTaskMailReplyTest" --tests "net.thunderbird.feature.taskmail.internal.data.DefaultTaskMailRepositoryTest" --tests "net.thunderbird.feature.taskmail.internal.ui.detail.*"`
  - `.\gradlew.bat :app-thunderbird:compileFossDebugKotlin`
  - `.\gradlew.bat :app-k9mail:compileFossDebugKotlin`
  - `.\gradlew.bat :app-thunderbird:assembleFossDebug`
  - `.\gradlew.bat :app-k9mail:assembleFossDebug`
- pending:
  - on-device smoke validation
  - `.\gradlew.bat connectedAndroidTest`
  - `.\gradlew.bat lint`
  - `.\gradlew.bat detekt`
  - `.\gradlew.bat spotlessCheck`

### Suggested PR slices

- PR 1: reply anchor/domain model + repository tests
- PR 2: sender seam + fake sender + ViewModel state handling
- PR 3: detail UI upgrade + UI tests
- PR 4: real sender integration + validation/docs

## Likely File Touchpoints

Primary Phase 3 files are expected to include:

- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/model/TaskSessionDetail.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/model/TaskSessionReplyContext.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/data/DefaultTaskMailRepository.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/data/TaskMailMessageSource.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/usecase/SendTaskMailReply.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/TaskSessionDetailContract.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/TaskSessionDetailViewModel.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/TaskSessionDetailUiState.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/TaskSessionDetailContent.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/component/PendingQuestionCard.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/component/TimelineList.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/component/TaskReplyComposer.kt`

Depending on the final send seam, the real implementation may also touch:

- TaskMail DI wiring
- app-level binding points
- existing mail send infrastructure modules

But Phase 3 should avoid spreading TaskMail-specific UI logic outside the TaskMail feature.

## Testing Plan

### Repository and domain tests

Add or update tests for:

- selecting the newest logical-session message as reply anchor
- preferring logical-session newest message over thread fallback assumptions
- disabling reply when multiple accounts are present in one logical session
- preserving null reply context when no valid anchor exists
- generating exact outgoing body for:
  - free text
  - question choice
  - `/status`

Relevant test areas:

- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/data/DefaultTaskMailRepositoryTest.kt`
- new tests around `SendTaskMailReply`
- focused real-sender tests around reply header/body preparation

### ViewModel tests

Add tests for:

- draft update
- send success
- send failure with draft preserved
- send blocked when reply context is unavailable
- `/status` quick action dispatch
- question choice dispatch
- refresh after send

Relevant test area:

- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/TaskSessionDetailViewModelTest.kt`

### UI tests

Add tests for:

- reply composer visible when `canReply == true`
- reply composer disabled while sending
- choice actions visible when a question exists
- `/status` action visible when query is allowed
- failure message visible without clearing loaded detail

Relevant test area:

- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/`

## Device Smoke Checklist

When a real device is available, validate at least:

1. `Drawer -> Tasks -> Workspace -> Detail` still opens cleanly.
2. Typing a plain-text reply and sending does not exit TaskMail.
3. The session detail page stays on the same thread after send.
4. A pending question choice can be sent from detail.
5. `/status` can be sent from detail.
6. Send failure keeps the draft instead of clearing it.
7. Detail refresh after send does not blank the screen.
8. Returning from detail still goes back to workspace.

## Risks and Guardrails

### Risk: Reply attaches to the wrong physical thread

Guardrail:

- always select the newest message inside the current logical session
- do not derive the reply anchor from route params alone

### Risk: Reply uses the wrong account identity

Guardrail:

- disable reply for mixed-account logical sessions
- never silently choose an arbitrary account

### Risk: Android forks a new session accidentally

Guardrail:

- always send as a reply to an existing message reference
- never synthesize a brand-new TaskMail subject for Phase 3
- keep `/new` out of scope

### Risk: UI becomes dependent on generic composer behavior

Guardrail:

- keep TaskMail-native reply interaction as the primary path
- treat full composer handoff as optional fallback only

### Risk: Quoted history grows into noisy or privacy-unfriendly bodies

Guardrail:

- keep the current Phase 3 body composition limited to the new action body
- if quoting is added later, quote only the newest anchor message
- keep quoting plain-text only
- never append the full thread history automatically

### Risk: Protocol drift between Android and backend

Guardrail:

- `docs/TASKMAIL-MAIL-RULES.md` remains the source of truth
- update protocol docs before implementing new reply semantics

### Risk: Sensitive content leaks into logs

Guardrail:

- do not log reply body text
- do not log question text or selected choice content
- log only aggregate state transitions and non-PII diagnostics

## Definition of Done

Phase 3 is complete when all of the following are true:

- TaskMail session detail supports plain-text reply from inside the feature
- TaskMail session detail supports question-choice reply from inside the feature
- TaskMail session detail supports `/status` query from inside the feature
- reply target selection follows logical-session newest-message rules
- mixed-account sessions do not allow unsafe reply
- send failure preserves the draft
- detail refresh works after successful send
- standard reply headers keep the message anchored to the existing thread
- existing `workspace -> detail -> workspace` navigation remains stable
- targeted tests cover repository anchor selection, reply dispatch, and detail state handling
- Phase 3 validation guidance is documented

## Suggested Verification Commands

Run the narrowest relevant checks first:

```powershell
.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest
.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest --tests "*TaskSessionDetailViewModelTest"
.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest --tests "*DefaultTaskMailRepositoryTest"
.\gradlew.bat :app-thunderbird:compileFossDebugKotlin
.\gradlew.bat :app-k9mail:compileFossDebugKotlin
.\gradlew.bat :app-thunderbird:assembleFossDebug
.\gradlew.bat :app-k9mail:assembleFossDebug
```

If a device is available, also run the Phase 3 smoke checklist above.
