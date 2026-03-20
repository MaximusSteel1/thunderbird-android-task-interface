# TaskMail Next Session Handoff (2026-03-20, Android relay execution)

## Scope of this session

This session did not add more Android relay code.

It aligned the Android-side relay plan with the latest PC-side connection-layer target and froze the current boundary:

- Android remains mail-first in the current phase
- PC -> VPS is the stable live connection target
- the current Android relay screen is debug-only bring-up tooling
- do not continue toward Android raw relay business send/receive by default

## Read first

1. `docs/taskmail/planning/android/taskmail-relay-vps-android-development-plan-v0.1.md`
2. `docs/taskmail/planning/android/taskmail-android-pc-vps-evolution-authority-v1.md`
3. `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
4. `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
5. `docs/TASKMAIL-DEBUG-VALIDATION.md`
6. `docs/TASKMAIL-MAIL-RULES.md`
7. `E:\projects\mail_based_task_manager\docs\current\android_runner_communication_contract.md`
8. `E:\projects\mail_based_task_manager\docs\plans\connection_layer_target_plan.md`

## Current decisions

- keep the landed Android relay bootstrap screen and client as debug-only tooling
- do not implement Android raw relay business slices from the older staged plan
- do not make Android depend on relay transport token or `/relay` as a product contract
- use Android relay bootstrap only to validate public endpoint readiness from a stock Android client
- treat mail compatibility as the only current Android-facing compatibility surface

## Current validation state

- narrow Android relay/bootstrap code is already landed
- debug route launch has been smoke-checked on device
- missing-token validation has been smoke-checked on device
- live TLS-enabled device probe currently fails with
  `java.security.cert.CertPathValidatorException: Trust anchor for certification path not found`

Confirmed current understanding:

- the live blocker is VPS/public endpoint TLS trust
- the live blocker is not Android route wiring
- the live blocker is not token mismatch

## Next concrete steps

1. do not start `Slice B` or any later raw-relay Android business slice
2. wait for PC/VPS-side public DNS/TLS hardening
3. once PC/VPS reports a stock-Android-trusted public endpoint, re-run Android debug relay smoke:
   - open `app://taskmail/debug/relay`
   - save debug relay config
   - run `Healthz`
   - run `Connect`
   - verify `hello -> hello_ack`
4. record the result in Android validation docs
5. keep normal Android feature work on the mail-side roadmap unless a new cross-repo authority update says otherwise

## Code and validation status in this session

- code changes: docs only
- Gradle validation: not run
- live relay validation: not re-run
- device validation: not re-run

## Remaining caveats

- the current relay bootstrap code is intentionally not part of normal TaskMail product flow
- the current relay bootstrap code must not be used as justification to resume Android raw relay architecture work
- Android should not weaken TLS verification to compensate for VPS certificate issues

## Why this note exists

The earlier Android relay execution note still pointed toward `Slice B-E` as if Android raw relay expansion were the
active plan.

That is no longer the agreed cross-repo target.

This handoff exists so the next session does not accidentally continue with:

- Android `TaskActionTransport` work meant for raw relay migration
- Android `NewTask` over raw relay
- Android relay ingress into unified-message cache
- Android `Reply` over raw relay
