# TaskMail Debug Validation

This document captures the current debug-only validation path for the Android TaskMail prototype.

## Goal

Use the existing debug activity to verify that:

- TaskMail can open from a deep link
- The workspace screen loads real TaskMail data from the repository
- Session detail can open from the workspace list
- Body extraction and grouping look reasonable with real mail data

## Debug Packages

- Thunderbird debug: `net.thunderbird.android.debug`
- K-9 Mail debug: `com.fsck.k9.debug`

## Build Commands

Use the narrowest build that matches the app you want to validate:

```powershell
.\gradlew.bat :app-thunderbird:assembleFossDebug
.\gradlew.bat :app-k9mail:assembleFossDebug
```

## Validation Replay

If you want to debug parser and grouping behavior without a device, run the JSON replay validation suite:

```powershell
.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest --tests "net.thunderbird.feature.taskmail.internal.validation.*"
```

The validation sources live in:

- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/validation/`

This suite replays exported mail JSON through the repository and prints simulated workspace and session output.

## Launch Commands

### Open TaskMail Workspace via deep link

Thunderbird:

```powershell
adb shell am start `
  -a android.intent.action.VIEW `
  -d "app://taskmail/workspace" `
  net.thunderbird.android.debug
```

K-9 Mail:

```powershell
adb shell am start `
  -a android.intent.action.VIEW `
  -d "app://taskmail/workspace" `
  com.fsck.k9.debug
```

### Open TaskMail Debug Activity directly

Thunderbird:

```powershell
adb shell am start `
  -n "net.thunderbird.android.debug/net.thunderbird.feature.taskmail.internal.debug.TaskMailDebugActivity"
```

K-9 Mail:

```powershell
adb shell am start `
  -n "com.fsck.k9.debug/net.thunderbird.feature.taskmail.internal.debug.TaskMailDebugActivity"
```

## Smoke Checklist

### Workspace

- App opens into the TaskMail workspace screen without crashing
- Existing TaskMail threads appear as grouped workspaces
- Empty states are understandable when no TaskMail mail exists
- Sessions are ordered by most recent activity
- Repeated physical mail threads for the same logical TaskMail session do not appear as duplicate sessions
- Workspace screen can scroll vertically when content exceeds one screen

### Session Detail

- Tapping a session opens detail successfully
- Timeline items show user and system messages in reasonable order
- User replies do not show large quoted-mail tails
- `TASK-STATE` and `TASK-QUESTION` capsules are not rendered verbatim in the message body
- Session detail screen can scroll vertically when timeline content exceeds one screen
- Timeline attachments show filename, type hints, and inline-image badges when present
- Tapping `Open` on a timeline attachment launches a viewer or image app when one is available
- Tapping `Save` on a timeline attachment can export the file through the Android document picker
- Reply composer can add one or more files and send attachment-only replies

### Data Quality Checks

- Workspace grouping follows `workspace_id`, then falls back to repo/workdir data
- Session grouping follows `session_id`, then falls back to thread id
- Messages with missing full body can still surface useful preview text
- Non-TaskMail mail should not appear in the TaskMail workspace
- Timeline attachment actions should only appear for real local-mail attachments, not synthetic placeholder rows

## Known Limitations

- This path is debug-only and is not yet linked from the production app shell
- Real data quality still depends on local message availability and sync state
- Deep-link validation is intended for prototype verification, not final product UX
- Timeline currently renders in chronological order from older to newer messages
- Console output from the JSON validation suite may still show text encoding issues for some Chinese content
