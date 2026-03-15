# TaskMail Android Phase 2

This document defines the next implementation phase after the current Phase 1.5 debug-validation milestone.

## Date

- Last updated: 2026-03-14

## Phase Goal

Phase 2 turns TaskMail from a debug-only prototype into a formally reachable in-app feature.

Expected user outcome:

- users can open TaskMail from inside the app
- TaskMail uses the already-implemented real mail repository
- workspace and session detail remain read-focused
- the feature is available without adb or the debug-only activity

## Key Decisions

### Use `FeatureLauncher` as the formal host

Phase 2 should use the existing launcher stack instead of introducing a new production `TaskMailActivity`.

Reasoning:

- it fits the current app architecture better
- it avoids creating a parallel host stack just for TaskMail
- it keeps navigation aligned with other feature entry points

### Keep `TaskMailDebugActivity`

The debug activity remains useful and should stay in place for validation and smoke testing.

### Do not change startup routing

App startup should continue to land in mail as it does today.

TaskMail should be discoverable from a formal in-app entry point, not by replacing the app's main startup destination.

### Reuse the existing data stack

Phase 2 is not a repository rewrite.

It should continue to use:

- `LegacyTaskMailMessageSource`
- `LegacyTaskMailBodyExtractor`
- `DefaultTaskMailRepository`
- existing logical session merge behavior from Phase 1.5

## Scope

### In Scope

- formal TaskMail entry point inside the app
- formal host wiring through `FeatureLauncher`
- production Koin/module wiring for TaskMail
- production dependency scope updates for Thunderbird and K-9
- navigation and back-stack behavior for `workspace -> detail`
- regression verification for grouping, scrolling, and real-data display

### Out of Scope

- reply sending
- slash commands
- markdown rich rendering
- search or filter UI
- notification work
- changing startup destination to TaskMail
- deep integration into the normal message list UI

## Workstreams

### 1. Formal Host Integration

Deliver TaskMail through the existing launcher flow.

Checklist:

- [ ] add `FeatureLauncherTarget.TaskMail`
- [ ] point it to `TaskMailRoute.Workspace.route()`
- [ ] register TaskMail routes inside `FeatureLauncherNavHost`
- [ ] ensure `onBack` returns cleanly from detail to workspace and from workspace out of the launcher host
- [ ] keep deep-link navigation compatible with existing `TaskMailRoute`

Likely files:

- `feature/launcher/src/main/kotlin/app/k9mail/feature/launcher/FeatureLauncherTarget.kt`
- `feature/launcher/src/main/kotlin/app/k9mail/feature/launcher/navigation/FeatureLauncherNavHost.kt`
- `feature/taskmail/api/src/main/kotlin/net/thunderbird/feature/taskmail/api/TaskMailRoute.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/navigation/DefaultTaskMailNavigation.kt`

### 2. Production DI and Wiring

Move TaskMail from debug-only app wiring to formal app wiring.

Checklist:

- [ ] bind `TaskMailNavigation` to `DefaultTaskMailNavigation`
- [ ] include `taskMailModule` in formal app wiring
- [ ] verify no forbidden module-boundary violations are introduced
- [ ] keep debug wiring consistent with production wiring

Likely files:

- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/TaskMailModule.kt`
- `app-common/src/main/kotlin/net/thunderbird/app/common/feature/AppCommonFeatureModule.kt`
- app-specific module wiring files in `app-thunderbird` and `app-k9mail`

### 3. Dependency Scope Updates

TaskMail must be available outside debug-only builds for the formal entry path to work.

Checklist:

- [ ] promote TaskMail dependency scope in Thunderbird build config as needed
- [ ] promote TaskMail dependency scope in K-9 build config as needed
- [ ] confirm production code does not reference debug-only sources

Likely files:

- `app-thunderbird/build.gradle.kts`
- `app-k9mail/build.gradle.kts`

### 4. Drawer Entry

Expose TaskMail from a user-facing navigation entry point.

Checklist:

- [ ] add a `Tasks` entry to the drawer UI
- [ ] add the event/effect needed to launch TaskMail
- [ ] route entry clicks to `FeatureLauncherTarget.TaskMail`
- [ ] keep drawer close behavior consistent with current folder/account actions
- [ ] add minimal strings and icon treatment

Likely files:

- `feature/navigation/drawer/dropdown/src/main/kotlin/net/thunderbird/feature/navigation/drawer/dropdown/ui/DrawerContract.kt`
- `feature/navigation/drawer/dropdown/src/main/kotlin/net/thunderbird/feature/navigation/drawer/dropdown/ui/DrawerViewModel.kt`
- `feature/navigation/drawer/dropdown/src/main/kotlin/net/thunderbird/feature/navigation/drawer/dropdown/ui/DrawerContent.kt`

### 5. Navigation and UX Verification

Formal host integration must not regress existing behavior.

Checklist:

- [ ] verify `Drawer -> Tasks -> Workspace -> Detail`
- [ ] verify back navigation from detail to workspace
- [ ] verify back navigation from workspace exits the launcher host cleanly
- [ ] verify scrolling still works in the formal host
- [ ] verify repeated physical mail threads do not appear as duplicated logical sessions

### 6. Regression Tests and Validation

Phase 2 should keep using both fast tests and replay-based validation.

Checklist:

- [ ] keep repository regression tests green
- [ ] keep JSON replay validation usable
- [ ] add at least one host-entry or navigation-focused regression test if practical
- [ ] update validation docs if the launch path changes

Relevant existing validation assets:

- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/data/DefaultTaskMailRepositoryTest.kt`
- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/validation/`
- `docs/TASKMAIL-DEBUG-VALIDATION.md`

## Recommended Build Order

### Step 1

Formalize host entry first.

- add `FeatureLauncherTarget.TaskMail`
- register TaskMail in `FeatureLauncherNavHost`
- verify manual deep-link and host navigation still work

### Step 2

Move DI and dependency scope to formal app wiring.

- bind TaskMail navigation in Koin
- include TaskMail modules in non-debug runtime
- compile both apps

### Step 3

Add the drawer entry.

- wire click event
- open TaskMail via launcher target
- verify back stack and drawer behavior

### Step 4

Run regression and smoke validation.

- repository tests
- JSON replay validation
- app compile/build
- device smoke test when available

## Risks and Guardrails

### Risk: Reintroducing duplicate sessions

Guardrail:

- do not switch grouping back to raw physical thread grouping
- keep the logical `session_id -> thread_id -> physical fallback` merge behavior

### Risk: Host and debug paths diverge

Guardrail:

- keep `TaskMailRoute` as the common route contract
- keep the debug host working as a fallback validation path

### Risk: Scope creep into Phase 3 work

Guardrail:

- defer send/reply, markdown rendering, search, and richer task controls
- keep Phase 2 focused on reachability and host integration

## Definition of Done

- [ ] TaskMail can be opened from inside Thunderbird and K-9 without adb
- [ ] the feature is hosted through the formal app navigation path
- [ ] workspace and detail continue using real mail data
- [ ] logical session grouping still behaves correctly
- [ ] scrolling works in both workspace and detail
- [ ] Thunderbird and K-9 debug builds compile and assemble successfully
- [ ] debug validation docs remain accurate

## Suggested Verification Commands

```powershell
.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest
.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest --tests "net.thunderbird.feature.taskmail.internal.validation.*"
.\gradlew.bat :app-thunderbird:compileFossDebugKotlin
.\gradlew.bat :app-k9mail:compileFossDebugKotlin
.\gradlew.bat :app-thunderbird:assembleFossDebug
.\gradlew.bat :app-k9mail:assembleFossDebug
```

If a device is available, also run the smoke checklist in `docs/TASKMAIL-DEBUG-VALIDATION.md`.

## Next Phase

Once Phase 2 has been validated on device, the next implementation document is:

- `docs/TASKMAIL-ANDROID-PHASE3.md`

Phase 3 should focus on:

- session detail reply interaction
- pending-question choice replies
- in-thread status query using `/status`
- keeping the existing formal host path stable while adding interaction

Markdown rendering should remain deferred to Phase 4.
