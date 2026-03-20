# TaskMail Android Relay/VPS Development Plan (v0.1)

Updated: 2026-03-20

## Status

This is an agent-facing Android execution note for relay/VPS work.

As of the 2026-03-20 cross-repo alignment pass, this file no longer treats raw Android relay send/receive as the
target-state product direction.

Current checkpoint:

- `Slice A` code is landed as debug-only bootstrap tooling
- narrow module validation is green
- live relay verification is blocked by VPS TLS trust-chain issues on stock Android
- the older staged idea of continuing into Android raw relay business slices is now paused

This file is not current protocol authority.

Current authority remains:

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/TASKMAIL-MAIL-RULES.md`
- `docs/taskmail/planning/android/taskmail-android-pc-vps-evolution-authority-v1.md`
- `E:\projects\mail_based_task_manager\docs/current\mail_protocol.md`
- `E:\projects\mail_based_task_manager\docs\current\android_runner_communication_contract.md`
- `E:\projects\mail_based_task_manager\docs\plans\connection_layer_target_plan.md`

## Purpose

This document exists to answer one execution question:

> What relay/VPS work is still valid for Android after the PC-side connection-layer target was clarified?

The answer is narrower than this file's earlier version:

- Android may keep a debug-only bootstrap probe for bring-up and diagnostics
- Android should not continue growing toward raw `/relay` as its application protocol in the current repository phase
- the stable live connection target belongs to `PC -> VPS`, not `Android -> VPS`

## Final Target Alignment

Android has no architectural objection to the current PC-side target-state draft in
`E:\projects\mail_based_task_manager\docs\plans\connection_layer_target_plan.md`.

For current cross-repo work, the aligned target is:

1. Android remains mail-first.
2. PC -> VPS is the required stable live connection.
3. VPS relay remains transport/control plane only in this phase.
4. Android-facing compatibility remains the mail contract.
5. Android must not depend on raw relay transport as a product boundary.
6. Android must not require relay transport credentials in shipped behavior.
7. If Android later needs direct VPS connectivity, it must be a new documented app-facing API, not a reuse of raw
   `/relay`.

This means the earlier idea of gradually moving Android business send/receive onto raw relay is not the current target
and must not be implemented by default.

## Hard Boundaries

These are non-negotiable unless the current authority docs are updated first.

- Android-facing TaskMail semantics remain mail-compatible.
- Android reads from the user mailbox and sends to the bot mailbox in the current phase.
- Relay/VPS is infrastructure that may affect delivery transport, not the Android app protocol.
- Relay code must stay in `:feature:taskmail:internal` for now.
- Debug tooling must not be mistaken for product architecture.
- Do not add a product-facing relay settings surface.
- Do not make Android business behavior depend on `/relay`, `/healthz`, or current VPS host/port details.
- Do not commit relay credentials, SSH keys, or server secrets into the Android repository.
- Do not let future work resume the old raw-relay `Slice B-E` plan without an explicit cross-repo authority update.

## Current Repository Checkpoint

As of 2026-03-20:

- the Android repository has a debug-only relay probe screen
- the probe can persist relay config, run `healthz`, and perform `hello -> hello_ack`
- device smoke confirmed the route opens and missing-token validation works
- live TLS validation on device currently fails with
  `java.security.cert.CertPathValidatorException: Trust anchor for certification path not found`

The confirmed current blocker is VPS/public-endpoint TLS trust, not Android route wiring and not token mismatch.

## What Android Relay Work Is Still Valid

### 1. Debug-Only Bootstrap And Diagnostics

This remains valid:

- debug-only relay config
- debug-only `healthz` probe
- debug-only WebSocket `hello -> hello_ack`
- connection-state visibility for bring-up
- recording live validation findings in Android docs

Why it is still useful:

- it helps verify VPS/public endpoint readiness from a stock Android client
- it exposes TLS and routing problems early
- it does not require changing the Android mail-facing product contract

### 2. Evidence Gathering For Cross-Repo Readiness

This remains valid:

- using Android debug probes to confirm whether public DNS/TLS/WSS are actually Android-ready
- documenting failures such as trust-chain or host-identity issues
- pausing Android relay follow-on work when the blocker is clearly outside Android code

## What Is No Longer The Active Android Plan

The following earlier staged items are now paused and should be treated as historical unless later authority docs
reopen them:

- an Android `TaskActionTransport` designed primarily to route business actions onto raw relay
- Android `NewTask` over raw relay as the next default slice
- Android inbound raw relay shadow receive into unified-message cache
- Android `Reply` over raw relay

These items were useful as a temporary refactoring thought process, but they are not the agreed current cross-repo
target.

## Immediate Next Step

Do not start a new Android raw-relay business slice.

The next relay-related Android work should be limited to:

1. keep `Slice A` debug-only and internal
2. re-run live device bootstrap only after PC/VPS side fixes public DNS/TLS trust issues
3. record the outcome in validation and handoff docs
4. otherwise return Android development effort to mail-side features and compatibility work

## Anti-Drift Rules

If later implementation work starts drifting, stop and re-read this section.

- If a proposed change makes Android treat `/relay` as its default app protocol, stop.
- If a proposed change requires Android to hold relay transport credentials in normal product flow, stop.
- If a proposed change starts implementing outbound or inbound raw relay business traffic as the next slice, stop.
- If a proposed change turns the debug relay page into a product-facing settings or transport switcher, stop.
- If a proposed change treats `/healthz` as an app-facing API instead of a diagnostic probe, stop.
- If a proposed change weakens TLS verification on Android to compensate for VPS certificate issues, stop.

## Historical Note

Earlier on 2026-03-20, this file described a staged Android raw-relay roadmap:

- `Slice A`: bootstrap
- `Slice B`: action transport abstraction
- `Slice C`: `NewTask` over relay
- `Slice D`: inbound relay shadow receive
- `Slice E`: `Reply` over relay

That sequence should now be treated as superseded by the cross-repo target-state alignment captured above.

If future cross-team work explicitly introduces a new Android app-facing VPS API, write a new plan instead of reviving
the old raw-relay slices from this document.
