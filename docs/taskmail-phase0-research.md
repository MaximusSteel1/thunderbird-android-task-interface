# Task Mail Phase 0 Research

## Scope

This document records the Phase 0 repository survey for adding a Task Mail feature to this Thunderbird for Android fork.

Phase 0 goals:

- Identify the best integration points in the current codebase
- Propose module boundaries for the new feature
- Clarify how normal mail and task mail should be separated
- List the concrete files likely to be touched in later phases

This phase does not implement the feature.

> Status note (2026-03-13): This document is preserved as the original Phase 0 survey record.
> The repository now already contains a Phase 1 TaskMail skeleton under `feature/taskmail`.
> For the current Android implementation status see `docs/TASKMAIL-ANDROID-PHASE1.md`.
> For the next execution plan see `docs/TASKMAIL-ANDROID-PHASE1-5.md`.

## Local repository status

- At the time of the original Phase 0 survey, the workspace was a local source snapshot of the fork.
- In the current workspace, the repository already has local `.git` metadata and a landed `feature/taskmail` Phase 1
  implementation.
- Treat the implementation status in this document as historical unless explicitly marked otherwise.

Fork URL:

- `https://github.com/MaximusSteel1/thunderbird-android-task-interface`

## Documents reviewed

- `README.md`
- `docs/CONTRIBUTING.md`
- `docs/architecture/adr/0009-api-internal-split.md`
- `docs/architecture/ui-architecture.md`
- `AGENTS.md`

## High-level conclusion

The current repository structure matches the expected Thunderbird for Android modular architecture at the integration
points that matter for this feature.

The recommended Phase 0 outcome is unchanged in principle, but is now confirmed against the local code snapshot:

- Add a new isolated feature area:
  - `:feature:taskmail:api`
  - `:feature:taskmail:internal`
- Keep task-specific business logic out of `legacy:*` unless there is no reasonable alternative
- Add a `Tasks` entry to the existing navigation drawer action area
- Open a dedicated Task Mail host screen from that entry
- Keep normal mail list and normal message view unchanged
- Perform task state parsing and markdown rendering only inside the Task Mail feature

## Repository findings

## 1. Module structure is compatible with a new feature

The project uses a clear feature/core modular structure in `settings.gradle.kts`.

Relevant existing feature patterns:

- `:feature:mail:message:list:api`
- `:feature:mail:message:list:internal`
- `:feature:mail:message:reader:api`
- `:feature:mail:message:reader:impl`
- `:feature:navigation:drawer:api`
- `:feature:navigation:drawer:dropdown`

Relevant ADR:

- `docs/architecture/adr/0009-api-internal-split.md`

Implication:

- A new `taskmail` feature should follow the same API/internal split
- Public contracts belong in `:feature:taskmail:api`
- Implementation, UI, data mapping, and parsing belong in `:feature:taskmail:internal`
- DI binding should happen in `app-common` or app modules, not inside unrelated features

## 2. App entry and wiring

Observed files:

- `app-common/src/main/kotlin/net/thunderbird/app/common/MainActivity.kt`
- `app-common/src/main/kotlin/net/thunderbird/app/common/feature/AppCommonFeatureModule.kt`
- `app-common/src/main/kotlin/net/thunderbird/app/common/feature/MessageListLauncher.kt`

Current flow:

- `MainActivity` delegates startup to `StartupRouter`
- `MessageListLauncher` launches `MessageHomeActivity`
- `app-common` is the correct place for dependency wiring and launcher bindings

Implication:

- If Task Mail exposes a launcher contract, `app-common` is the right composition point to bind it
- This fits the existing repository architecture and ADR rules

## 3. Normal mail home screen host

Observed file:

- `legacy/ui/legacy/src/main/java/com/fsck/k9/activity/MessageHomeActivity.kt`

Confirmed responsibilities of `MessageHomeActivity`:

- Hosts the navigation drawer
- Hosts the normal message list
- Hosts the normal message view container
- Handles split view and single-pane behavior
- Handles folder opening, thread opening, search, and message navigation

Important local evidence:

- The drawer is initialized in `initializeDrawer()` and `initializeFolderDrawer()`
- `DropDownDrawer` is wired with folder and settings actions
- The activity remains a large legacy host for mail browsing behavior

Implication:

- Task Mail should not be implemented by deeply modifying the normal mail host in Phase 1
- The safest path is to launch a separate Task Mail host screen from the drawer

## 4. Navigation drawer is the best Task entry point

Observed files:

- `feature/navigation/drawer/dropdown/src/main/kotlin/net/thunderbird/feature/navigation/drawer/dropdown/DropDownDrawer.kt`
- `feature/navigation/drawer/dropdown/src/main/kotlin/net/thunderbird/feature/navigation/drawer/dropdown/ui/DrawerContract.kt`
- `feature/navigation/drawer/dropdown/src/main/kotlin/net/thunderbird/feature/navigation/drawer/dropdown/ui/setting/FolderSettingList.kt`
- `feature/navigation/drawer/dropdown/src/main/res/values/strings.xml`
- `legacy/ui/legacy/src/main/java/com/fsck/k9/activity/MessageHomeActivity.kt`

Current drawer action area already includes:

- Sync account
- Manage folders
- Sync all accounts
- Settings

Why this is the best Phase 2 entry point:

- It is already designed for global app actions, not folder content
- It avoids changing message list semantics or folder hierarchy semantics
- It keeps normal mail entry unchanged
- It minimizes risk to the legacy host

Recommended drawer integration:

- Add a new drawer event such as `OnTasksClick`
- Add a new drawer effect such as `OpenTasks`
- Add a new item in `FolderSettingList`
- Add a new string resource for `Tasks`
- Wire the new callback in `DropDownDrawer`
- Handle the launch in `MessageHomeActivity`

## 5. Normal message list should not be reused as-is for Task Mail UI

Observed files:

- `feature/mail/message/list/api/...`
- `feature/mail/message/list/internal/...`
- `legacy/ui/legacy/src/main/java/com/fsck/k9/ui/messagelist/MessageListFragmentBridgeContract.kt`

Key observation:

- The normal mail list is not a simple independent screen that can be lightly repurposed
- It is still bridged through legacy hosting and carries folder/search/thread assumptions
- Its UI model is oriented around normal mail fields, not task-specific metadata

Task Mail list needs additional fields such as:

- backend
- latestStatus
- latestSummary
- messageCount
- unreadCount

Implication:

- Reuse data access patterns where helpful
- Do not force Task Mail into `MessageListFragment` or `MessageListFragmentBridgeContract`
- Build a focused Task Mail list screen in the new feature module

## 6. Normal message detail should stay untouched

Observed files:

- `legacy/ui/legacy/src/main/java/com/fsck/k9/ui/messageview/MessageViewContainerFragment.kt`
- `legacy/ui/legacy/src/main/java/com/fsck/k9/ui/messageview/MessageViewFragment.kt`
- `legacy/ui/legacy/src/main/java/com/fsck/k9/ui/messageview/MessageContainerView.kt`

Key observation:

- Normal message reading still flows through legacy view code
- `MessageContainerView` is built around `WebView` and HTML/plain rendering
- The message view stack also includes attachments, crypto, printing, copy/move/refile, and other mail-specific logic

Implication:

- Do not add task markdown rendering to the normal mail reading stack
- Do not change normal message body rendering globally
- Build a separate Task Mail detail screen with task-specific rendering

This is directly aligned with the product requirement:

- markdown is only for task mail
- normal mail experience should not change

## 7. Existing Compose host pattern can be reused conceptually

Observed files:

- `feature/launcher/src/main/kotlin/app/k9mail/feature/launcher/FeatureLauncherActivity.kt`
- `feature/launcher/src/main/kotlin/app/k9mail/feature/launcher/FeatureLauncherTarget.kt`
- `feature/launcher/src/main/kotlin/app/k9mail/feature/launcher/FeatureLauncherExternalContract.kt`

This repository already has a pattern where:

- a feature gets its own host activity
- deep links or route targets are used
- app wiring remains in composition modules

Implication:

- Task Mail can follow a similar feature-host pattern
- For Phase 2, a dedicated `TaskMailActivity` remains the simplest path
- A route-based launcher can be added later if desired

## 8. Historical note: no existing taskmail implementation was found during Phase 0

At the time of the original Phase 0 survey, searches in the local repository found no existing implementation for:

- `TaskMail`
- `taskmail`
- `TASK-STATE`
- `Markwon`

Implication:

- The feature can be introduced cleanly as a new area
- There is no local half-implementation to preserve or refactor first

Current note (2026-03-13):

- This is no longer true for the current workspace.
- `feature/taskmail:api` and `feature/taskmail:internal` now exist and should be treated as the implementation baseline
  for all follow-up work.

## Recommended feature design

## Module proposal

Add:

- `:feature:taskmail:api`
- `:feature:taskmail:internal`

Recommended package roots:

- `net.thunderbird.feature.taskmail`
- `net.thunderbird.feature.taskmail.internal`

## API module responsibilities

Suggested contents for `:feature:taskmail:api`:

- `TaskThreadSummary`
- `TaskMessageDetail`
- `TaskStateCapsule`
- `TaskThreadDetail`
- `RenderMode`
- public repository/query contracts
- launcher or navigation contract if needed by other modules

## Internal module responsibilities

Suggested contents for `:feature:taskmail:internal`:

- `TaskThreadDetector`
- `TaskStateCapsuleParser`
- `TaskMessageExtractor`
- `TaskMarkdownRenderer`
- repository implementation
- mappers and use cases
- list/detail screen UI
- ViewModels
- Activity or navigation host

## Recommended data flow

```text
local mail/thread data
  -> task thread detector
  -> message text extraction
  -> state capsule parsing
  -> task repository
  -> list/detail use cases
  -> task list/detail ViewModels
  -> task mail UI
```

## Separation between normal mail and task mail

Recommended rule set:

- Normal mail entry keeps using the existing message list and message view flow
- Task Mail entry loads only threads matching the detector rules
- Task parsing happens only in Task Mail flows
- Markdown rendering happens only in Task Mail detail
- If a task message fails to parse, the fallback is plain text inside Task Mail detail
- If a message is not a task message, it stays on the normal mail path

## Recommended host strategy

Preferred Phase 2 host:

- `TaskMailActivity`

Why:

- Keeps separation from the large legacy `MessageHomeActivity`
- Reduces coupling with split view and normal mail back stack behavior
- Fits the repository preference for isolated feature UI
- Keeps markdown code out of the normal mail stack

Alternative that is possible but not recommended for v1:

- Embed a task fragment inside `MessageHomeActivity`

Why not recommended now:

- Higher coupling to legacy navigation and back stack behavior
- More risk of side effects on the normal mail experience

## Concrete file touchpoints for later phases

## Likely files for Phase 1

- `settings.gradle.kts`
- `feature/taskmail/api/build.gradle.kts`
- `feature/taskmail/internal/build.gradle.kts`
- `feature/taskmail/api/src/main/kotlin/...`
- `feature/taskmail/internal/src/main/kotlin/...`
- `feature/taskmail/internal/src/test/kotlin/...`

## Likely files for Phase 1.5

- `feature/taskmail/internal/build.gradle.kts`
- `feature/taskmail/internal/src/main/kotlin/.../data/...`
- `feature/taskmail/internal/src/main/kotlin/.../domain/repository/...`
- `feature/taskmail/internal/src/main/kotlin/.../ui/workspace/...`
- `feature/taskmail/internal/src/main/kotlin/.../ui/detail/...`
- `feature/taskmail/internal/src/test/kotlin/.../data/...`
- `feature/taskmail/internal/src/test/kotlin/.../domain/...`
- `docs/TASKMAIL-ANDROID-PHASE1-5.md`

## Likely files for Phase 2

- `settings.gradle.kts`
- `app-common/src/main/kotlin/net/thunderbird/app/common/feature/AppCommonFeatureModule.kt`
- `feature/navigation/drawer/dropdown/src/main/kotlin/net/thunderbird/feature/navigation/drawer/dropdown/DropDownDrawer.kt`
- `feature/navigation/drawer/dropdown/src/main/kotlin/net/thunderbird/feature/navigation/drawer/dropdown/ui/DrawerContract.kt`
- `feature/navigation/drawer/dropdown/src/main/kotlin/net/thunderbird/feature/navigation/drawer/dropdown/ui/DrawerViewModel.kt`
- `feature/navigation/drawer/dropdown/src/main/kotlin/net/thunderbird/feature/navigation/drawer/dropdown/ui/setting/FolderSettingList.kt`
- `feature/navigation/drawer/dropdown/src/main/res/values/strings.xml`
- `legacy/ui/legacy/src/main/java/com/fsck/k9/activity/MessageHomeActivity.kt`
- `feature/taskmail/api/src/main/kotlin/...`
- `feature/taskmail/internal/src/main/...`

## Likely files for Phase 3

- `feature/taskmail/internal/src/main/kotlin/.../ui/detail/...`
- `feature/taskmail/internal/src/main/kotlin/.../ui/list/...`

## Likely files for Phase 4

- `feature/taskmail/internal/build.gradle.kts`
- `feature/taskmail/internal/src/main/kotlin/.../render/TaskMarkdownRenderer.kt`
- `feature/taskmail/internal/src/test/kotlin/.../render/...`

## Phase 0 acceptance summary

Phase 0 is considered complete with the following confirmed:

- The local repository is suitable for a new `taskmail` feature area
- The best user entry point is the navigation drawer action area
- The best implementation strategy is a separate Task Mail host screen
- Normal mail list and normal mail detail should remain unchanged
- Parsing and markdown logic should live in the new feature, not in legacy mail view code
- Later phase file touchpoints are now identified from the actual repository

Current note (2026-03-13):

- The architectural conclusions above still stand.
- The implementation baseline has moved on to “Phase 1 skeleton complete, Phase 1.5 pending”.

## Local limitations found during Phase 0 (historical)

- The repository in that Phase 0 workspace was a source snapshot without `.git` history
- The default shell environment in this Codex session still resolves `java` to Java 17 unless `JAVA_HOME` is set
  explicitly for the command
- JDK 21 is installed locally at `C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot`
- Gradle works correctly when this JDK 21 path is injected into the command environment
- `.\gradlew.bat help` was verified successfully with JDK 21
- A wider probe using `.\gradlew.bat :app-thunderbird:assembleDebug --dry-run` timed out, so full build readiness is
  not yet confirmed

These limitations do not block repository survey work. For implementation and testing, use JDK 21 explicitly in the
command environment or restart the terminal after updating system environment variables.
