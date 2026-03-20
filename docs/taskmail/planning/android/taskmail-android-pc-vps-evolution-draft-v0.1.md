# TaskMail Android / PC / VPS Evolution Draft (v0.1, historical)

Updated: 2026-03-20

## Status

This file is now a historical draft snapshot.

Its macro-planning role has been superseded by:

- `docs/taskmail/planning/android/taskmail-android-pc-vps-evolution-authority-v1.md`
- `E:\projects\mail_based_task_manager\docs\plans\android_pc_vps_evolution_authority.md`

Keep this file only as background context for how the first draft was framed.

## Read First

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/TASKMAIL-MAIL-RULES.md`
- `docs/taskmail/planning/android/taskmail-relay-vps-android-development-plan-v0.1.md`
- `E:\projects\mail_based_task_manager\docs\current\android_runner_communication_contract.md`
- `E:\projects\mail_based_task_manager\docs\plans\connection_layer_target_plan.md`

## Current Baseline

The currently aligned near-term state is:

- Android remains a mail-first TaskMail client
- Android relay code is debug-only bootstrap tooling
- the stable live connection target is `PC -> VPS`
- VPS relay is transport/control plane, not Android business API
- mail remains the Android-facing compatibility surface

## Longer-Term Target

The longer-term target is not "Android uses raw `/relay`."

The longer-term target is:

- Android talks to a documented app-facing API
- that API is expressed in task/session/reply/artifact/update semantics
- VPS becomes the public access layer for that API
- PC remains the execution engine and a producer/consumer of canonical work events
- mail falls back to compatibility and rollback duty instead of being the primary Android workflow

Practical interpretation:

- Android should eventually ask for "create task", "list sessions", "send reply", and "download artifact"
- Android should not eventually manage `hello`, `packet_ack`, relay transport tokens, or raw relay packet semantics as
  app behavior

## Architectural Direction

The intended steady-state layering is:

1. Android app-facing API client
2. VPS app-facing API and realtime/update surface
3. VPS session/event projection and attachment access layer
4. PC execution engine and command/event bridge
5. legacy mail compatibility path

Important constraint:

The first direct Android API should still allow PC to remain execution truth.

That is the lowest-risk path because it avoids forcing task execution truth to migrate to VPS at the same time that the
Android protocol changes.

## Evolution Phases

### Phase 1 - Stabilize The Current Mail-First System

Android:

- keep normal TaskMail behavior mail-first
- keep relay bootstrap internal and debug-only
- continue mail-side quality and compatibility work

PC:

- stabilize the required `PC -> VPS` live connection
- harden reconnect, ack, resend, and fallback behavior
- keep current mail output behavior transport-agnostic

VPS:

- provide one stable public host
- provide stock-client-trusted TLS
- keep `https /healthz` and `wss /relay` externally coherent

Exit gate:

- Android sees no user-facing protocol change when PC delivery moves between direct email and relay

### Phase 2 - Add A VPS Session Projection Layer

Android:

- keep local repository boundaries clean enough to consume a future API without rewriting the whole UI

PC:

- emit stable canonical events about sessions, messages, status changes, questions, and artifacts

VPS:

- persist those events
- build queryable session/message projections
- expose internal/admin views first, before public Android APIs

Exit gate:

- VPS can answer read-side questions about current sessions without taking over task execution truth

### Phase 3 - Define The First App-Facing API

Android:

- remain mail-first in production
- prepare an internal API client layer

PC:

- accept VPS-routed high-level commands
- continue publishing canonical session updates

VPS:

- define high-level endpoints such as:
  - create task
  - list sessions
  - get session detail
  - send reply or control command
  - list and fetch artifacts
  - subscribe to updates
- define auth, identity, and permission boundaries

Exit gate:

- the new API is described in business semantics, not relay transport semantics

### Phase 4 - Run Dual Stack In Shadow Or Limited Beta

Android:

- add API read path first
- compare API results with mail-derived local state
- keep mail as fallback

PC:

- handle API-originated commands idempotently
- keep event delivery reliable under reconnect and replay conditions

VPS:

- harden auth
- harden sync/replay/dedupe
- harden artifact access
- support realtime updates and resume after reconnect

Exit gate:

- API reads and writes match existing mail behavior closely enough for real user testing

### Phase 5 - Make The API The Primary Android Path

Android:

- make the app-facing API the default read/write path
- retain mail fallback until rollout confidence is high

PC:

- continue as execution engine unless a later separate plan changes execution truth

VPS:

- operate as the public Android entry layer
- preserve compatibility and rollback hooks during migration

Exit gate:

- Android can complete its primary task/session workflow without depending on mail as the normal path

## Responsibility Split

Android owns:

- user experience
- local repository and UI adaptation
- migration safety on device
- compatibility behavior during dual-stack phases

PC owns:

- task execution truth for the current intended path
- canonical event production
- command handling and execution-side idempotency

VPS owns:

- public reachability
- authentication boundary
- projection/query layer
- artifact access layer
- realtime/public API continuity

## Major Open Decisions

These must be resolved before a real direct Android API can ship:

- auth model for Android users/devices
- canonical event schema between PC and VPS
- exact session/message projection shape on VPS
- attachment and artifact upload/download policy
- offline and reconnect semantics for Android
- rollback and fallback rules when API and mail disagree
- whether execution truth should remain on PC indefinitely or move later in a separate plan

## What Must Not Happen

- do not treat raw `/relay` as the Android product protocol
- do not let Android depend on relay transport credentials as shipped behavior
- do not attempt API cutover before VPS has a real projection/read model
- do not move execution truth and Android protocol at the same time unless a separate architecture decision explicitly
  accepts that risk

## Immediate Cross-Repo Next Steps

1. PC/VPS close public DNS and TLS readiness
2. PC define the first canonical event set it can emit consistently
3. VPS define the smallest useful session projection model
4. Android stay mail-first and avoid new raw-relay expansion
5. all three sides agree on a first app-facing API scope before any Android direct-connect implementation starts

## Success Condition

This draft has served its purpose when all three codebases can work toward the same answer:

- near term: stable mail-first Android plus stable `PC -> VPS`
- medium term: VPS-backed queryable session projection
- long term: Android direct app-facing API without exposing raw relay transport as the product contract
