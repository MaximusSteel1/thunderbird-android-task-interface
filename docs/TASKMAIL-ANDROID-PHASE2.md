# TaskMail Android Phase 2

This document now serves as a historical/reference document for the formal-host integration phase of TaskMail Android.

Current status authority:

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`

Validation evidence authority:

- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`

## Date

- Last updated: 2026-03-16

## Document Role

Use this file for:

- understanding what “Phase 2” originally meant
- understanding which formal-entry goals are now baseline in the repository
- identifying what still belongs to host / navigation closeout instead of interaction work

Do not use this file alone to answer either of these questions:

- “What is currently implemented right now?”
- “What has already been fully validated?”

## Original Phase Goal

Phase 2 turned TaskMail from a debug-only prototype into a formally reachable in-app feature.

Expected user outcome:

- users can open TaskMail from inside the app
- TaskMail uses the real repository-backed read path
- the feature is available without adb or debug-only routing
- debug host remains available as a fallback validation path

## Current Alignment Summary

As of the 2026-03-16 documentation alignment pass, the core Phase 2 surface is no longer future work only. It is repository baseline.

### Phase 2 Scope That Is Already Present in Repository

- `FeatureLauncherTarget.TaskMail`
- TaskMail route registration in `FeatureLauncherNavHost`
- `TaskMailNavigation` / `DefaultTaskMailNavigation`
- `taskMailModule` included from `app-common`
- drawer-level `Tasks` entry plumbing
- shared `TaskMailRoute` contract across formal host and debug host
- retained `TaskMailDebugActivity`

### Phase 2 Validation Evidence Already Recorded

The validation ledger already records executable evidence for:

- launcher route contract tests
- drawer event / effect plumbing tests
- drawer-to-launcher TaskMail handoff
- debug-host back-stack fallback
- Thunderbird and K-9 `fossDebug` assembles

### What Is Still Not Closed for Phase 2

Phase 2 should now be read as “implemented in repository, not fully validation-closed.”

The remaining closeout items are:

- broader device/manual smoke for formal entry and back stack
- confirmation that drawer click-through is stable on-device
- repo-wide quality tasks outside the narrow TaskMail slice

## Current Repository-Aligned Phase 2 Definition

When discussing “Phase 2” now, the accurate meaning is:

- TaskMail is formally reachable in-app
- TaskMail is hosted through the normal launcher/navigation stack
- TaskMail drawer entry exists
- debug host remains as a fallback validation surface

What Phase 2 no longer means:

- “formal entry is still only a future checklist item”
- “TaskMail only exists behind debug deep links”

## What Still Belongs Outside Phase 2

These areas are no longer useful to discuss as Phase 2 scope:

- reply interaction semantics
- structured multi-question answers
- attachment send/open/save behavior
- paused-session resume behavior
- richer protocol/UI boundary decisions

Those belong to current status, protocol docs, or Phase 3 reference material.

## Documentation Guidance

If the formal host path changes, update these files together:

1. `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
2. `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
3. `docs/TASKMAIL-ANDROID-PHASE2.md`

in that order.

## Recommended Interpretation

The safest current interpretation is:

- Phase 2 implementation surface exists in the repository
- executable evidence exists for the narrow launcher / drawer / build path
- full manual/device confidence for the formal entry path is still pending
