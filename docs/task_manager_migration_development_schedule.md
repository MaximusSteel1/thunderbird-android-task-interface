# Task Manager Migration Development Schedule

Updated: 2026-03-20

## Status

This document converts `docs/task_manager_migration_tasklist.md` into a practical development schedule.

It is a planning/reference document only.

It does not change current TaskMail protocol authority and does not replace:

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/TASKMAIL-MAIL-RULES.md`

## 1. Purpose

The migration tasklist already freezes the technical scope.

This schedule answers a different question:

> In what order should the refactor be developed so current email behavior stays usable, Android gets local `UnifiedMessage`
> persistence early, and future relay/VPS support becomes a layering problem instead of a rewrite?

## 2. Planning Assumptions

This schedule assumes all of the following remain true:

- email remains the only production transport during this migration phase
- Android local persistence for `UnifiedMessage` is first-phase work, not a later optimization
- current TaskMail reply/new-task behavior must keep working while refactoring lands
- no relay protocol, WebSocket, background push, or UI redesign is added in this schedule
- once local persistence exists, steady-state Task list refresh should stop depending on broad mailbox fetch windows such as
  100 or 250 messages

The schedule is written for a conservative single-developer serial path first.

Where safe parallelism exists, it is called out explicitly.

## 3. Development Strategy

The overall strategy is:

1. Finish the documentation and boundary freeze first.
2. Land Android read-side decoupling and local cache early, because that is both a hard phase requirement and the
   highest current architectural risk.
3. Land PC outbound layering in parallel or immediately after the Android cache skeleton is stable.
4. Stabilize the email-compatible version before any relay/VPS implementation work starts.

This means the schedule is not "PC first, Android later" and not "Android only".

It is:

- Android read-side first for risk reduction
- PC outbound layering next to remove transport coupling
- shared stabilization after both sides are internally split

## 4. Milestone Overview

| Milestone | Status | Main outcome | Typical effort |
| --- | --- | --- | --- |
| M0. Scope and interface freeze | Done | tasklist, interface inventory, and phase constraints are explicit | completed in the current documentation pass |
| M1. Android read-side skeleton | Next | ingress/parser/local repository/sync contracts exist on paper and then in code skeleton form | 2-4 focused sessions |
| M2. Android local cache vertical slice | Next | `UnifiedMessage` local persistence and local-first detail refresh path land | 3-5 focused sessions |
| M3. PC outbound skeleton | Next | render/packet/transport layering exists without changing current email behavior | 2-4 focused sessions |
| M4. Android repository migration closeout | Later | workspace/session/detail stop depending on raw email structures directly | 2-4 focused sessions |
| M5. PC packet adoption and send journal | Later | main outbound path uses packet stage and records delivery attempts | 2-3 focused sessions |
| M6. Email-compatible stabilization | Later | focused regression, quality reruns, and device/mailbox smoke close the phase | 2-4 focused sessions |

These are sequencing units, not fixed calendar dates.

## 5. Detailed Milestones

### M0. Scope and Interface Freeze

Status:

- complete in this documentation pass

Artifacts already produced:

- `docs/task_manager_migration_tasklist.md`
- `docs/taskmail/planning/android/taskmail-interface-extraction-inventory-v0.1.md`

What M0 resolved:

- this phase does not replace email
- Android local `UnifiedMessage` persistence is mandatory now
- first-batch interfaces are explicit
- second-batch relay-facing interfaces are explicitly deferred

Exit condition:

- no more ambiguity about phase scope or first-batch interface boundaries

### M1. Android Read-Side Skeleton

Why this comes first:

- Android local cache is a hard phase requirement
- current read path is the most collapsed part of the feature
- this work can begin without waiting for PC packet work to fully land

Primary deliverables:

- define and land the code skeletons for:
  - `MessageIngress`
  - `EmailIngress`
  - `IncomingMessageParser`
  - `EmailMessageParser`
  - `UnifiedMessageRepository`
  - `MessageSyncStateRepository`
  - `MessageSyncCoordinator`
- decide whether the feature keeps the name `TaskMailRepository` for the read-model projection boundary
- map current `TaskMailMessageSource` and `DefaultTaskMailRepository` into split responsibilities

Not yet required in M1:

- full relay support
- full migration of all screens to the new local cache path
- packet-aware cross-transport dedupe perfection

Exit condition:

- Android read-side contracts exist and the target dependency direction is enforceable:
  `ingress -> parser -> repository -> sync -> viewmodel -> ui`

### M2. Android Local Cache Vertical Slice

Why this is second:

- the new interfaces are only useful once one real local-first path exists
- detail view is the smallest meaningful user-facing slice for proving the architecture

Primary deliverables:

- local persisted `UnifiedMessage` entity, DAO, and storage
- local persisted sync state / cursor storage
- initial upsert and dedupe rules
- detail screen reads local data first, then runs incremental sync
- ViewModel no longer owns cursor logic directly
- steady-state Task list / workspace refresh uses a small recent raw-message window plus cursor-based increment
- the initial steady-state recent window can be `10` raw messages
- wider backfill is reserved for bootstrap/recovery cases instead of normal refresh

Recommended first vertical slice:

- start with session detail
- then widen to workspace/session summary once the detail path is stable

Exit condition:

- opening a session detail can render from local persisted data before incremental sync finishes
- refresh no longer requires a full historical re-parse every time
- normal Task list refresh no longer depends on the default broad mailbox fetch window

### M3. PC Outbound Skeleton

Why this can run in parallel with late M1 or early M2:

- its contract boundary is already frozen by the migration tasklist
- it does not need to change the Android-facing protocol in this phase

Primary deliverables:

- `TaskRunPacket`
- `TaskStatePatch`
- `TransportReceipt`
- `ResultNormalizer`
- `HtmlRenderer`
- `HtmlEnvelopeBuilder`
- `TaskRunPacketBuilder`
- `PacketIdGenerator`
- `OutboundTransport`
- `OutboundDispatcher`

Main rule:

- email send behavior keeps working exactly as before from the user's point of view

Exit condition:

- PC-side business logic no longer sends mail directly
- transport no longer owns business HTML generation

### M4. Android Repository Migration Closeout

Why this is after M2:

- once one local-first vertical slice works, the rest of the read path can migrate with lower risk

Primary deliverables:

- workspace/session summary reads from the local unified-message-backed projection
- `TaskMailRepository` implementation stops depending on raw email source shapes directly
- project-wide ViewModel refresh logic is reduced behind coordinator-driven flow

Recommended order inside M4:

1. session detail closeout
2. workspace/session summary migration
3. project-sync reader alignment if needed

Exit condition:

- `workspace -> session -> detail` is fully backed by the new local-first read path

### M5. PC Packet Adoption and Send Journal

Why this is after the skeleton:

- once layering exists, packet and journal adoption becomes mechanical instead of speculative

Primary deliverables:

- main outbound path builds a stable packet before transport
- email send code moves behind `EmailTransport`
- one journal record is produced for each send attempt

Exit condition:

- packet stage is on the main path
- outbound send attempts are traceable without relying on transport internals alone

### M6. Email-Compatible Stabilization

Why this is its own milestone:

- the phase is not complete when the abstractions exist
- the phase is complete only when the email-compatible version is still usable and validated

Primary deliverables:

- focused regression fixes
- narrow and widened validation reruns
- mailbox/device smoke for the migrated behavior
- explicit documentation closeout if implementation details drifted

Exit condition:

- the acceptance criteria in `docs/task_manager_migration_tasklist.md` are met

## 6. Parallelism Rules

Safe parallelism:

- M3 can start once M0 is done and M1 contract naming is stable enough
- late M1 and early M3 can proceed in parallel because Android read-side cache work does not require PC packet
  implementation to be finished

Unsafe parallelism:

- do not start relay/VPS implementation before M6
- do not widen Android UI migrations before one local-first detail slice is stable
- do not begin packet/journal stabilization before the PC skeleton exists

## 7. Verification Cadence

Each milestone should end with the narrowest relevant checks before moving on.

### Android-side default checks during M1, M2, and M4

- `.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest`
- `.\gradlew.bat :feature:taskmail:internal:detekt`
- `.\gradlew.bat :feature:taskmail:internal:lintDebug`

### PC-side equivalent expectation during M3 and M5

- run the narrowest unit and lint/test slice in the adjacent PC-side repository
- keep validation evidence separate from Android validation evidence

### Wider stabilization checks in M6

- rerun the relevant TaskMail narrow checks
- rerun app assemble paths that touch the feature
- perform targeted mailbox/device smoke where automation is not enough

## 8. Recommended Next Coding Order

If implementation starts immediately after this schedule, the next coding order should be:

1. Android M1 skeletons
2. Android M2 detail vertical slice
3. PC M3 outbound skeleton
4. Android M4 read-path closeout
5. PC M5 packet + journal closeout
6. M6 stabilization

This order intentionally front-loads the Android cache requirement while still moving PC transport decoupling in the same
phase.

## 9. Explicit Do-Not-Advance Gates

Do not advance to the next milestone if any of the following are still false:

- M1 -> M2 gate:
  Android read-side boundaries are still vague or still collapse ingress and parser into one type
- M2 -> M4 gate:
  there is still no real local persisted `UnifiedMessage` path in a user-facing screen
  or normal Task list refresh still depends on default 100/250-style mailbox scans
- M3 -> M5 gate:
  PC business logic still directly owns mail send behavior
- M5 -> M6 gate:
  packet stage is not yet on the real send path
- M6 -> relay/VPS work:
  current email-compatible version is not yet stable enough to serve as the baseline

## 10. Bottom Line

The planned phase sequence is:

- freeze boundaries
- land Android local unified-message cache early
- land PC outbound layering in the same phase
- finish with one stable email-compatible split architecture

Only after that should relay/VPS work become active implementation rather than planning.

## 11. Current 2026-03-20 Checkpoint

The Android side has now advanced materially beyond the original M1/M2 wording.

The current repository checkpoint is best understood as:

- Android read-side skeleton: landed
- Android `UnifiedMessage` local cache vertical slice: landed on the main read path
- local-first workspace and session-detail entry timing: landed
- incremental-first session-detail snapshot rebuild: landed
- Android send-side first transport seams: landed, with email still serving as the only production transport

What is still not fully closed inside the Android slice:

1. workspace summary projection still needs a final closeout decision and may still warrant its own dedicated
   incremental snapshot layer
2. steady-state sync still uses a narrow recent-window strategy instead of a strict cursor-native delta fetch
3. the foreground-only 10-second refresh loop still remains a product/performance policy decision
4. repo-wide and device/manual validation for the migrated local-first behavior are still incomplete

Practical implication:

- the split-architecture refactor main path is largely complete on Android
- the next major engineering stream can now move toward relay/VPS transport work
- however, the Android closeout items above should remain explicitly tracked instead of being treated as already solved
