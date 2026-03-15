# TaskMail Android Current Status

This document captures the current state of the Android TaskMail prototype after the latest Phase 1.5 work.

## Date

- Last updated: 2026-03-14

## Current Outcome

Phase 1.5 is now in a usable debug-validation state:

- real TaskMail mail is loaded from the local mail store
- `workspace -> session -> detail` is backed by the real repository instead of the in-memory fake
- debug builds can open TaskMail with the `app://taskmail/workspace` deep link
- the debug TaskMail screens can scroll on device
- logical TaskMail sessions are merged across multiple physical mail threads

This is still a debug-only prototype. It is not yet wired into the production app shell or drawer entry.

## What Is Implemented

### Real Mail Bridge

- `LegacyTaskMailMessageSource` reads candidate mail threads from the legacy mail store
- `LegacyTaskMailBodyExtractor` extracts displayable plain text for user and system messages
- `DefaultTaskMailRepository` aggregates messages into TaskMail workspaces, sessions, and timeline items

### UI Wiring

- `TaskWorkspaceViewModel` and `TaskSessionDetailViewModel` now use injected real repository wiring
- debug Koin modules for Thunderbird and K-9 include the TaskMail module
- `TaskMailDebugActivity` can consume the debug deep link and hand it to `NavController`

### Debug Validation Path

- debug manifest exposes `app://taskmail/...`
- debug APKs can be installed and opened without adding a production entry point
- a dedicated validation document exists at `docs/TASKMAIL-DEBUG-VALIDATION.md`

### Scrolling

- workspace screen supports vertical scrolling
- session detail screen supports vertical scrolling

### Logical Session Merge

The repository no longer treats each physical mail thread as a separate TaskMail session by default.

Current grouping behavior is:

- first collect messages from physical mail threads
- then merge them into logical TaskMail sessions by:
  - `session_id` when available
  - otherwise `thread_id`
  - otherwise physical mail thread fallback

This was added because validation data showed that one TaskMail session can span multiple physical mail conversations.

## Validation Assets

### JSON Validation Suite

A JSON-based validation harness is available at:

- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/validation/`

Key files:

- `TaskMailValidationRunner.kt`
- `JsonMessageSource.kt`
- `ThreadResolver.kt`
- `UiSimulator.kt`

Purpose:

- replay exported mail JSON through the same repository logic used by the feature
- inspect workspace, session, and timeline grouping
- debug parser and aggregation problems without needing a device

### Repository Regression Tests

Important repository tests currently include:

- workspace grouping and ordering
- session detail body extraction
- thread-id fallback
- merging multiple physical mail threads into one logical TaskMail session

## Useful Commands

### Run repository tests

```powershell
.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest --tests "net.thunderbird.feature.taskmail.internal.data.DefaultTaskMailRepositoryTest"
```

### Run JSON validation replay

```powershell
.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest --tests "net.thunderbird.feature.taskmail.internal.validation.*"
```

### Build debug APKs

```powershell
.\gradlew.bat :app-thunderbird:assembleFossDebug
.\gradlew.bat :app-k9mail:assembleFossDebug
```

## Debug APK Outputs

Current build outputs:

- `app-thunderbird/build/outputs/apk/foss/debug/app-thunderbird-foss-debug.apk`
- `app-k9mail/build/outputs/apk/foss/debug/app-k9mail-foss-debug.apk`

## Known Limitations

- TaskMail is still debug-only and not exposed from the production app shell
- timeline display order is currently chronological from older to newer messages
- ordering is more stable than before, but could still be improved further with better tie-break and UX decisions
- validation console output currently has encoding issues for some Chinese text
- markdown rendering and reply sending are still future phases

## Recommended Next Step

The next most valuable step is to improve timeline presentation and ordering behavior:

- confirm whether detail should stay old-to-new or switch to new-to-old
- tighten timestamp tie-break rules where needed
- continue validating against exported JSON and on-device real mail

Formal entry planning is now captured separately in `docs/TASKMAIL-ANDROID-PHASE2.md`.
