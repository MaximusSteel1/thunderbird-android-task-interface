# TaskMail Next Session Handoff - 2026-03-19 Project List Repo Prefill

## Scope

The `Project list / Use this repo / Repo:` prefill slice is now code-complete for both TaskMail hosts:

- opening `Project list` from `New task` still returns to the existing `New task` screen with `Repo:` prefilled
- opening `Project list` directly from the TaskMail workspace now pops `Project list`, opens `New task`, and then prefills `Repo:`

Current scope boundary:

- code and focused automated validation are closed for the formal host and internal host routing
- current manual/device smoke is still open

## Read First

1. `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
2. `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
3. `docs/TASKMAIL-DEBUG-VALIDATION.md`
4. `docs/TASKMAIL-MAIL-RULES.md`
5. `docs/taskmail/planning/android/taskmail-next-development-plan-v0.3.md`

## Next Steps

1. On a real device, from the formal TaskMail workspace host, open `Project list`, verify the project list renders, and
   confirm `Use this repo` returns to `New task` with editable `Repo:` prefill.
2. Re-smoke the existing `New task -> Choose from project list -> Use this repo` path on-device so both entry paths are
   covered manually.
3. Re-run formatting validation only after deciding how to handle the current worktree/module line-ending drift; this
   session's `spotlessCheck` was blocked by existing module-wide formatting differences outside this narrow slice.

## This Session

- Code changes: yes
  - added a workspace-level `Project list` entry and empty-state CTA
  - added workspace ViewModel/screen effect wiring for `Project list`
  - hardened repo-selection callbacks in `TaskMailNavHost` and `FeatureLauncherNavHost` so repo handoff works whether
    `Project list` was opened from `New task` or directly from workspace
  - added focused navigation/workspace regression tests for both branches
- Validation: yes
  - `.\gradlew.bat :feature:launcher:testDebugUnitTest :feature:taskmail:internal:testDebugUnitTest --continue`
  - `.\gradlew.bat :feature:launcher:detekt :feature:launcher:lintDebug :feature:taskmail:internal:detekt :feature:taskmail:internal:lintDebug --continue`
- Validation not closed:
  - real-device/manual smoke for the formal host `Project list` flow
  - `.\gradlew.bat :feature:launcher:spotlessCheck :feature:taskmail:internal:spotlessCheck --continue` because the
    current worktree already contains broader module formatting and line-ending drift outside this slice
