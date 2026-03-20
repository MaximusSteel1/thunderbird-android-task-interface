# TaskMail Interface Extraction Inventory (v0.1)

Updated: 2026-03-20

## Status

This document is a planning/reference companion to:

- `docs/task_manager_migration_tasklist.md`
- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/TASKMAIL-MAIL-RULES.md`

It does not change current TaskMail protocol authority.

Its purpose is narrower:

> For the current Android repository, which interfaces should be kept, which new interfaces must be extracted, and which existing types should be split before email stops being the architectural center of the feature?

This is still documentation work only. It does not require code changes in the same session.

## 1. Scope and Guardrails

This inventory assumes the same phase boundary as `docs/task_manager_migration_tasklist.md`:

- email remains the only formal production transport in this phase
- current TaskMail mail behavior remains compatible
- no relay protocol implementation is added in this phase
- the goal is transport decoupling and read-path stabilization, not a feature rewrite

This document also follows ADR-0009:

- new contracts should start in `:feature:taskmail:internal` unless another module truly needs them
- only stable, intentionally shared contracts should later move to `:feature:taskmail:api`

## 2. Current Repository Shape

The current Android TaskMail implementation already has some useful seams, but the most important read-path boundaries are
still collapsed.

### 2.1 Current Read Path

Current effective chain:

`LegacyTaskMailMessageSource -> DefaultTaskMailRepository -> GetTaskWorkspaceSummaries/GetTaskSessionDetail -> ViewModel -> UI`

Main observations:

- `LegacyTaskMailMessageSource` is not just a transport ingress
- it already loads mail-store bodies and attachments and returns `TaskMailMessage`, which is a mail-specific hybrid model
- `DefaultTaskMailRepository` is not just a repository
- it also performs logical-session grouping, dedupe, parsing-dependent aggregation, reply-context resolution, and timeline projection
- refresh remains effectively full-source reload oriented
- there is no local persisted `UnifiedMessage` contract or sync cursor boundary yet

### 2.2 Current Write Path

Current reply path:

`TaskSessionDetailViewModel -> SendTaskMailReply -> TaskMailReplySender -> TaskMailReplySourceMessageLoader + TaskMailMimeMessageFactory + TaskMailMimeMessageSender`

Current new-task path:

`TaskNewTaskViewModel -> SendTaskMailNewTask -> TaskMailNewTaskSender -> TaskMailSenderAccountSource + TaskMailNewTaskMimeMessageFactory + TaskMailMimeMessageSender`

Current project-sync path:

`TaskProjectSyncViewModel -> RequestTaskMailProjectSync/GetLatestTaskMailProjectSyncResult -> TaskMailProjectSyncRepository`

Main observations:

- reply and new-task already have business-action interfaces
- their implementations are still mail-shaped underneath, which is acceptable for this phase
- `TaskMailProjectSyncRepository` currently mixes outbound request sending and inbound mail-result reading in one contract

### 2.3 Current Sync and Observation Path

Current chain:

- `RefreshTaskMail -> TaskMailSyncRequester`
- `ObserveTaskMailStoreChanges -> TaskMailStoreChangeObserver`
- `TaskWorkspaceViewModel` and `TaskSessionDetailViewModel` each contain their own refresh orchestration

Main observation:

- current sync helpers exist, but there is no single `MessageSyncCoordinator` boundary that owns cursor logic, incremental
  upsert sequencing, and local cache update policy

## 3. Extraction Principle

Do not turn every helper into an interface.

In this phase, an interface is justified when it separates one of these boundaries:

- transport ingress from parsing
- parsing from persistence
- persistence from feature read-model projection
- business action from concrete transport send mechanics
- sync coordination from ViewModel logic

If a type is still just a local algorithm, mapper, parser helper, or builder with one expected implementation, keep it
concrete for now.

## 4. Interfaces That Already Exist and Should Be Kept

These contracts already provide useful seams and should be preserved, even if some of them are later renamed or narrowed.

### 4.1 Existing Feature-Level Contracts Worth Keeping

| Current contract | Keep? | Why |
| --- | --- | --- |
| `TaskMailRepository` | Yes, but narrow later | It is already the feature read-model boundary used by read use cases. The interface can stay, but its implementation must stop talking directly to transport-shaped sources. |
| `TaskMailProjectSyncRepository` | Temporarily | It is a usable feature contract today, but it mixes request and read concerns and should be split in a later batch. |
| `TaskMailBotMailboxSettingsRepository` | Yes | It is already a stable settings-facing business contract. |
| `TaskMailReplySender` | Yes | It is already a business-level reply action boundary. |
| `TaskMailNewTaskSender` | Yes | It is already a business-level new-task action boundary. |

### 4.2 Existing Adapter Contracts Worth Keeping

| Current contract | Keep? | Why |
| --- | --- | --- |
| `TaskMailSyncRequester` | Yes, as an adapter | It already isolates the "request local mail sync" action from ViewModels. |
| `TaskMailStoreChangeObserver` | Yes, as an adapter | It already isolates local mail-store change observation. |
| `TaskMailSenderAccountSource` | Yes | Sender-account lookup is already isolated from UI. |
| `TaskMailDestinationAddressProvider` | Yes | Current bot-mailbox target lookup is already isolated. |
| `TaskMailReplyAttachmentResolver` | Yes | Attachment preparation is already isolated from ViewModel logic. |
| `TaskMailTimelineAttachmentHandler` | Yes | Attachment open/save mechanics are already isolated from ViewModel logic. |
| `TaskMailForegroundRefreshTickerFactory` | Yes | Foreground refresh timing is already isolated from ViewModels. |
| `TaskMailSettingsStorage` | Yes | Storage detail is already hidden under a settings contract. |

### 4.3 Existing Mail-Specific Contracts That Should Stay Internal

These are valid adapter seams, but they are mail-specific and should not be mistaken for the future transport-neutral
boundary:

- `TaskMailMessageSource`
- `TaskMailMimeMessageFactory`
- `TaskMailNewTaskMimeMessageFactory`
- `TaskMailMimeMessageSender`
- `TaskMailReplySourceMessageLoader`

They should remain internal mail-adapter contracts in the first refactor pass.

## 5. Interfaces That Must Be Extracted in the First Refactor Batch

This is the minimum new interface set needed to make the architecture transport-ready without replacing email behavior.

### 5.1 `MessageIngress<RawMessage>`

Layer:

- `data/ingress`

Responsibility:

- fetch raw transport messages
- support `fetchLatest()` and `fetchSince(cursor)` style access
- avoid building TaskMail UI/read models directly
- support a small steady-state recent-message window for Task list refresh instead of broad default mailbox scans

Why it must exist:

- current `TaskMailMessageSource` already mixes ingress and parsing
- future relay support needs a transport seam before any TaskMail-specific normalization happens
- local persistence only reduces mailbox pressure if ingress can stop default 100/250-message style refresh scans in steady
  state

Current split source:

- `LegacyTaskMailMessageSource`

First implementation:

- `EmailIngress`

Future implementation:

- `RelayIngress` stub only in this phase

### 5.2 `IncomingMessageParser<RawMessage, UnifiedMessage>`

Layer:

- `data/parser`

Responsibility:

- convert raw transport payload into a unified persisted message model
- own transport-specific parsing and normalization
- compute stable IDs, timestamps, content hash, and parser version

Why it must exist:

- the current read path jumps too quickly from mail-store access into TaskMail-specific models
- Android needs one normalization step before repository and UI logic

Current split source:

- `LegacyTaskMailMessageSource`
- `TaskMailMessageDetector`
- `LegacyTaskMailBodyExtractor`
- parser helpers under `domain/parser`

First implementation:

- `EmailMessageParser`

Future implementation:

- `RelayPacketParser`

### 5.3 `UnifiedMessageRepository`

Layer:

- `data/repository/local`

Responsibility:

- persist normalized messages
- dedupe and upsert by stable business key
- expose query access for higher-level read-model projection
- keep local cache concerns out of ViewModels

Why it must exist:

- the new migration phase explicitly depends on local `UnifiedMessage` persistence
- current `DefaultTaskMailRepository` is doing repository work and view-model-oriented aggregation at the same time

Current split source:

- `DefaultTaskMailRepository`

First backing store:

- local Android persistence introduced in this refactor phase

### 5.4 `MessageSyncStateRepository`

Layer:

- `data/repository/local`

Responsibility:

- store sync cursor and parser version state
- scope cursor by source and scope key
- keep cursor management out of ViewModels

Why it must exist:

- incremental refresh is a core goal of this phase
- current implementation has no durable cursor/state boundary for incremental upsert

Current gap:

- no equivalent contract exists yet

### 5.5 `MessageSyncCoordinator`

Layer:

- `sync`

Responsibility:

- orchestrate `ingress -> parser -> repository -> cursor update`
- coordinate incremental sync and fallback full refresh behavior
- integrate transport-triggered refresh with local cache policy
- keep sequencing and retry policy out of ViewModels
- decide when steady-state recent-window sync is enough and when bootstrap/recovery backfill is required

Why it must exist:

- current refresh logic is duplicated across ViewModels
- `RefreshTaskMail` and `ObserveTaskMailStoreChanges` are useful adapters, but they are not a full coordinator boundary

Current split source:

- refresh logic in `TaskWorkspaceViewModel`
- refresh logic in `TaskSessionDetailViewModel`
- `RefreshTaskMail`
- `ObserveTaskMailStoreChanges`

First implementation note:

- in the first pass it can still rely on `TaskMailSyncRequester` and `TaskMailStoreChangeObserver`
- the important change is that ViewModels stop owning sync flow decisions
- the initial steady-state Task list policy can target a recent raw-message window of `10`, with wider backfill reserved
  for cache-miss or cursor-recovery paths

### 5.6 `TaskSessionReadRepository` or a Narrowed `TaskMailRepository`

Layer:

- `domain/readmodel`

Responsibility:

- build TaskMail feature read models such as workspace, session list, and detail
- read only from `UnifiedMessageRepository`
- remain transport-agnostic

Why it must exist:

- even after `UnifiedMessageRepository` lands, the UI still needs a TaskMail-specific projection layer
- the feature should not expose `UnifiedMessage` directly to Compose ViewModels

Current mapping decision:

- the project can keep the existing interface name `TaskMailRepository` to reduce churn
- the key requirement is not the name change
- the key requirement is that its implementation stops depending on mail ingress directly

Current split source:

- `DefaultTaskMailRepository`

## 6. Interfaces That Should Be Extracted in the Second Refactor Batch

These are important for future relay/VPS support, but they do not need to block the first read-side cache refactor.

### 6.1 `TaskActionTransport`

Purpose:

- send business actions through a transport-neutral boundary

Why later:

- current write paths already have acceptable business-level seams through `TaskMailReplySender` and
  `TaskMailNewTaskSender`
- the first structural risk is still on the read side

Current mail-shaped sources:

- `TaskMailReplySender` implementation stack
- `TaskMailNewTaskSender` implementation stack
- outbound send part of `DefaultTaskMailProjectSyncRepository`

First implementation:

- `EmailTaskActionTransport`

Future implementation:

- `RelayTaskActionTransport`

### 6.2 `ProjectDiscoveryRequester`

Purpose:

- send the project-list bootstrap request independently of where results are later read from

Why later:

- current `TaskMailProjectSyncRepository` is workable for the current mail-only phase
- but it mixes outbound request transport and inbound result lookup

### 6.3 `ProjectDiscoveryResultReader`

Purpose:

- read the latest project-discovery result independently of how the request was sent

Why later:

- this allows `[SYNC]` style discovery to stop being mail-store-specific when a relay path appears

### 6.4 `TaskTransportConfigRepository`

Purpose:

- store transport-level configuration without tying higher layers to a mail-only setting shape

Why later:

- current bot-mailbox storage is adequate for the mail-only phase
- future dual-transport support will likely require a neutral config boundary above mail-specific settings

## 7. Existing Types That Should Be Split or Retired

The following current types should not survive unchanged if the refactor is serious.

| Current type | Problem | Target outcome |
| --- | --- | --- |
| `TaskMailMessageSource` | Too generic a name for a mail-specific hybrid source; it already returns parsed TaskMail-shaped data | Replace with `MessageIngress` + `IncomingMessageParser` and keep any email-specific source under `EmailIngress` |
| `LegacyTaskMailMessageSource` | Collapses mail fetch, body loading, attachment loading, and message normalization | Break apart into ingress and parser responsibilities |
| `DefaultTaskMailRepository` | Mixes transport-normalization assumptions, aggregation, dedupe, reply-context derivation, and UI-facing read-model projection | Split into local normalized storage plus transport-agnostic read-model projection |
| `TaskMailProjectSyncRepository` | Mixes outbound request send and inbound result read | Later split into requester and result reader |

## 8. Types That Should Stay Concrete in This Phase

Do not over-abstract these yet:

- `TaskMailRichTextProjector`
- `TaskMailProjectSyncResultParser`
- `TaskMailReplySubjectBuilder`
- `TaskMailMessageDetector`
- `TaskStateCapsuleParser`
- `TaskQuestionCapsuleParser`
- `LegacyTaskMailBodyExtractor`
- individual UI mappers inside ViewModels

Reason:

- these are still local algorithms or mail-parser helpers
- creating interfaces for them now would add indirection without actually separating architectural layers

## 9. Recommended Refactor Order

1. Keep existing reply/new-task mail behavior unchanged.
2. Introduce `MessageIngress` and `IncomingMessageParser`.
3. Introduce local `UnifiedMessageRepository` and `MessageSyncStateRepository`.
4. Introduce `MessageSyncCoordinator`.
5. Re-implement `TaskMailRepository` as a read-model projection over unified local data.
6. Move ViewModel refresh logic behind the coordinator.
7. Only after the read side is stable, introduce `TaskActionTransport` and split project discovery transport from result reading.

## 10. Bottom Line

The minimum meaningful extraction is not "add more interfaces around the existing mail repository".

The minimum meaningful extraction is:

- split ingress from parsing
- split normalized persistence from feature read-model projection
- split sync orchestration from ViewModels
- keep current outbound mail send contracts working while read-side decoupling lands first

If those boundaries are not created, email remains the architectural center even if some names are changed.
