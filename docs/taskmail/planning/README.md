# TaskMail Planning And History Archive

This directory is not the source of current TaskMail implementation-truth or mail-protocol authority.

Use the following documents first:

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/TASKMAIL-MAIL-RULES.md`
- `docs/TASKMAIL-DEBUG-VALIDATION.md`

## Current Active Planning Docs

The current active planning exceptions in this tree are:

- `docs/taskmail/planning/android/taskmail-android-public-plaintext-direct-connect-authority-v0.1.md`
- `docs/taskmail/planning/android/taskmail-android-public-plaintext-direct-connect-plan-v0.1.md`
- `docs/taskmail/planning/android/taskmail-phase0-public-plaintext-baseline-v1.md`
- `docs/taskmail/planning/android/taskmail-phase2-direct-outbound-contract-v0.1.md`
- `docs/taskmail/planning/android/taskmail-next-session-handoff-2026-03-21-reply-direct-send-seam.md`

Use the public-plaintext direct-connect authority for the current frozen macro direction.
Use the public-plaintext direct-connect plan as the current Android-side staged execution plan.
Use the Phase 0 baseline note for the exact public-IP, port, endpoint, and fallback freeze.
Use the Phase 2 direct outbound contract mirror for the first direct `new task` payload and fallback freeze.
Use the latest handoff note only for short-lived continuation context.

As of 2026-03-21, earlier Android-side intermediate planning docs, completed handoff notes, and the older
mail-first/TLS-gated planning line have been intentionally pruned from this directory.
The adjacent PC-side authority pair and coordinated phase documents have since been rewritten around the same public
plaintext direction and can now be used as cross-repo phase references.

Do not use any of these planning docs to override the current Android protocol/implementation authority documents listed
above.

Use most other files under `docs/taskmail/planning/` only as historical or implementation-reference material.

## Still Useful As Reference

### Platform Historical / Broader Context

- [Task Manager Platform Design (v0.2)](platform/task-manager-platform-design-v0.2.md)
- [Task Manager Platform Delivery (v0.2)](platform/task-manager-platform-delivery-v0.2.md)
- [Task Manager Tool Interface Spec (v0.1)](platform/task-manager-tool-interface-spec-v0.1.md)
- [Task Manager Implementation Roadmap (v0.1)](platform/task-manager-implementation-roadmap-v0.1.md)
- [Task Manager Schemas (v0.1)](platform/task-manager-schemas-v0.1.md)

## Cleanup Note

Most older Android-side planning and handoff notes were intentionally removed during the 2026-03-21 cleanup.
Current implementation truth should be reconstructed from:

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/TASKMAIL-MAIL-RULES.md`

Platform planning imports are still retained for broader context, but they are not the active Android/PC roadmap.

## Notes

- `android/` contains Android-specific historical planning and implementation-reference documents.
- `platform/` contains broader Task Manager platform context from the imported planning set.
- `platform/` files should not be treated as the active Android/PC TaskMail roadmap unless a newer authority doc explicitly promotes them again.
- `platform/schemas/task-manager-schemas-v0.1/` contains the extracted JSON Schema package from the provided zip archive.
