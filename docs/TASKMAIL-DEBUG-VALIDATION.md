# TaskMail Debug Validation

This document captures the retained debug-host validation path for Android TaskMail.

Use it together with `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`:

- formal launcher and drawer entry are now part of the real TaskMail surface
- the debug activity remains valuable as a focused validation and fallback path when you want to isolate TaskMail internals

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

If your local debug validation depends on a build-time default TaskMail bot-mailbox target instead of a prewritten
storage key, rebuild with:

```powershell
.\gradlew.bat :app-thunderbird:assembleFossDebug -PtaskmailBotMailboxAddress="<bot mailbox>"
```

## Java Pitfall

On this workstation, fresh PowerShell shells can still resolve Gradle to Java 11 from
`C:\Users\Administrator\.gradle\gradle.properties`.

When that happens, TaskMail build or validation reruns fail before they start with a JVM-version error even though this
repository requires Java 21.

Before running TaskMail Gradle commands here, set:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
```

## ADB Sandbox Pitfall

When TaskMail device inspection is launched from the Codex sandbox on this workstation, `adb` can fail immediately with:
`Cannot mkdir 'C:\Users\CodexSandboxOffline\.android': Permission denied`.

- symptom: even simple commands such as `adb devices`, `adb shell dumpsys ...`, or `uiautomator dump` fail before they
  reach the attached phone
- trigger: running `adb` inside the restricted Codex sandbox user context
- cause: this `adb` build still tries to initialize state under the sandbox user's home `.android` directory, which is
  not writable in that context
- workaround: rerun device-inspection `adb` commands outside the sandbox / with escalated permissions instead of treating
  the failure as a device-disconnect issue

## Device Input Pitfall

On the current attached Xiaomi/MIUI device, repeated `adb shell input text ...` attempts during TaskMail form entry can
unexpectedly foreground `com.android.quicksearchbox/.SearchActivityTransparent` instead of staying inside Thunderbird.

For guided new-thread smoke on this device, prefer:

- adb for launch, install, dumpsys, screenshots, and UI dumps
- manual user entry for the actual `Repo:` / `Task:` text when long-form input is required

## PowerShell Screenshot Pitfall

On this workstation, PowerShell redirection can corrupt raw `adb exec-out screencap -p` output.

- symptom: the pulled screenshot exists but image decoders fail with errors such as `Out of memory` or unsupported/corrupt
  PNG reads
- trigger: using `adb exec-out screencap -p > local-file.png` from PowerShell
- cause: PowerShell redirection is not preserving the raw screencap byte stream reliably for this path
- workaround: write the screenshot on-device first with `adb shell screencap -p /sdcard/...`, then `adb pull` it to the
  workstation before inspection or conversion

## Install Note

Before device smoke, check whether the device already has an older local debug build installed.

- if Android rejects the new APK because the installed package uses a different or too-old signing key, uninstall the existing debug app first and then install the newly built APK

## Relay Bootstrap Warm-Start Pitfall

On the current attached Xiaomi/MIUI device, immediately firing a TaskMail relay debug deep link after reinstall can
leave the user looking at an already-running Thunderbird task instead of the new relay screen.

- symptom: the relay deep link command returns `Starting: Intent ...`, but the device does not visibly land on the new
  `TaskMail relay debug` screen
- trigger: launching `app://taskmail/debug/relay` while an older Thunderbird debug task is still warm in the foreground
- current best understanding: the warm task stack can keep the previous host surface visible long enough to confuse the
  smoke step even though the package resolves the deep link correctly
- workaround: `adb shell am force-stop net.thunderbird.android.debug` first, then cold-start the relay route with
  `adb shell am start -W -a android.intent.action.VIEW -d "app://taskmail/debug/relay" net.thunderbird.android.debug`

## 2026-03-20 Relay Bootstrap Device Note

Later on 2026-03-20, a focused real-device smoke pass on attached Android device model `24090RA29C` advanced the new
relay bootstrap debug path from code-only status into initial executable evidence.

That pass verified:

- the latest Thunderbird `fossDebug` APK installs cleanly over the current local debug package
- after force-stop plus cold-start deep-link launch, `app://taskmail/debug/relay` resumes
  `net.thunderbird.feature.taskmail.internal.debug.TaskMailDebugActivity`
- the new `TaskMail relay debug` screen is visible on-device
- with no relay transport token configured, tapping `Connect` surfaces the expected local validation error:
  `Relay host, port, and transport token are required.`

This pass did not yet verify live `Healthz` or `hello -> hello_ack`, because no valid relay transport token was
available during the smoke session.

Later in the same 2026-03-20 relay bootstrap smoke, after a valid relay transport token became available and `Use TLS`
was enabled on-device, the relay debug path advanced one step further:

- the earlier plain-HTTP `unexpected end of stream` failure was confirmed to be a transport mismatch, not a bad token,
  because the relay currently reports `tls_enabled = true`
- the next `Healthz` attempt then failed with
  `java.security.cert.CertPathValidatorException: Trust anchor for certification path not found.`

At that point, the Android-side blocker was no longer route wiring or token entry. The active blocker was TLS trust:
the relay certificate chain presented to the Android client was not trusted by the device.

## 2026-03-16 Slice1 Note

The slice1 follow-up on 2026-03-16 added automated formal-host regression coverage in:

- `feature/launcher/src/test/kotlin/app/k9mail/feature/launcher/navigation/FeatureLauncherNavHostTaskMailFlowTest.kt`

That test now covers:

- TaskMail workspace deep-link entry through the formal launcher host
- workspace -> detail navigation
- detail -> workspace back behavior
- workspace back-stack exit staying scoped to the TaskMail launcher flow

Later on 2026-03-16, a live device rerun on model `24090RA29C` confirmed that the real drawer `Tasks` entry launches `FeatureLauncherActivity`, reaches TaskMail workspace, opens detail, returns to workspace on the first back press, and exits back to `MessageHomeActivity` on the second back press.

That same device pass also confirmed an important debug-build routing detail: `app://taskmail/workspace` currently resolves to `TaskMailDebugActivity`, not the formal launcher host.

Later in the same 2026-03-16 session, after inbox sync pulled live mail for `thread_042 / android-reply-892553` onto the device, a direct debug-host session-detail reopen on `app://taskmail/session/thread_042/thread_042` confirmed two additional points:

- the debug host can reopen a real synced live session detail directly once the local mail store has the thread
- the dedicated `/status` action in TaskMail detail produces a new outgoing `/status` timeline row and mailbox-side reply `[STATUS][S:thread_042] android-reply-892553`

One subtlety matters here: live `/status` replies currently come back with status label `STATUS`, not a terminal label such as `DONE` or `FAILED`. If you reuse terminal-only mailbox polling helpers, they will observe the message and still time out unless they explicitly treat `STATUS` as success for this path.

That same debug-host validation path was then reused for three more live protocol checks once the relevant threads were synced locally:

- single-question live thread `thread_043 / android-singleq-3889adca` showed labeled quick answers `Ship it` and `Not yet`; tapping `Ship it` added outgoing canonical value `approve` in the timeline and mailbox-side evidence completed `ACCEPTED -> RUNNING -> DONE` with final reply `Approved.`
- multi-question live thread `thread_044 / android-multiq-60d0f4ab` did not show a quick-answer section and instead prefilled the composer with `Answers:` plus one line per question id: `entry_position:` and `icon_strings:`
- paused live thread `thread_042 / android-reply-892553` showed paused helper copy plus `Resume and send`; the raw user mail captured from the mailbox proves Android prepended `/resume` before the typed continuation text `Please continue with PAUSED_RESUME_F1B7`, after which the backend completed with `[DONE][S:thread_042] android-reply-892553`

Later in that same 2026-03-16 validation session, the debug host was reused on live thread `thread_026 / 时间线测试` to close the remaining slice1 attachment and historical-timeline checks:

- selecting local file `taskmail-attachment-smoke-20260316.txt` enabled attachment-only send and disabled `/status`; the outgoing `2026-03-16 19:34` timeline card appeared immediately, and mailbox-side raw user mail `Re: [DONE] [S:thread_026] 时间线测试` arrived with an empty body plus that single attachment
- the same `2026-03-16 19:34` outgoing attachment card exposed `Open` and `Save`; `Open` launched Android `ResolverActivity` through `ACTION_VIEW`, while `Save` launched DocumentsUI `PickActivity` through `ACTION_CREATE_DOCUMENT`
- historical reply-like `Re: [DONE][S:thread_026] 时间线测试` user mails at `23:24`, `22:49`, `18:56`, `18:34`, and `18:31` rendered as `Outgoing` cards rather than `System`
- raw mail files `raw_053.json` and `raw_049.json` in `E:\projects\mail_based_task_manager\_tmp_live_mail_runner\tasks\thread_026\mail` are `multipart/alternative` with `attachments: []`, while the matching device timeline cards showed no pseudo-attachment rows; `raw_033.json` remained the historical real-attachment case and surfaced only the CSV file `05_two_full_clf_top_shap.csv`
- the historical `18:56`, `18:34`, and `18:31` timestamps each appeared once in the device timeline, so the older duplicate outgoing-card regression did not reappear

This closes the slice1 manual smoke checklist items 9-13 for the current captured session.

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

On debug builds, these deep links currently land in `TaskMailDebugActivity` because the debug manifest declares the `app://taskmail/*` intent filter. Use the in-app drawer `Tasks` entry when you need to validate the formal launcher host on a device.

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
- Timeline items show newest messages first and keep user/system messages in a reasonable order
- User replies do not show large quoted-mail tails
- `TASK-STATE` and `TASK-QUESTION` capsules are not rendered verbatim in the message body
- Session detail screen can scroll vertically when timeline content exceeds one screen
- Timeline attachments show filename, type hints, and inline-image badges when present
- Tapping `Open` on a timeline attachment launches a viewer or image app when one is available
- Tapping `Save` on a timeline attachment can export the file through the Android document picker
- Reply composer can add one or more files and send attachment-only replies

### Data Quality Checks

- Workspace grouping prefers normalized `repo_path + workdir` when available, falls back to `workspace_id` when repo metadata is missing, and only then falls back to thread/session identity
- Session grouping follows `session_id`, then falls back to thread id
- Messages with missing full body can still surface useful preview text
- Non-TaskMail mail should not appear in the TaskMail workspace
- Timeline attachment actions should only appear for real local-mail attachments, not synthetic placeholder rows

## Known Limitations

- This path is still debug-only, but it is no longer the only way TaskMail is reachable in-app
- In debug builds, `app://taskmail/workspace` validates debug-host routing, not the formal launcher host routing
- Live `/status` replies currently use the label `STATUS`; polling helpers that only wait for terminal labels will need a path-specific exception
- Real data quality still depends on local message availability and sync state
- Formal launcher and drawer entry should be treated as the primary product-facing smoke path; use the debug activity when isolating TaskMail-specific issues
- Some real-message datasets may still surface noisy fallback text if the stored mail body lacks clean reply/capsule boundaries
- Console output from the JSON validation suite may still show text encoding issues for some Chinese content
- Some devices may refuse to install a freshly built debug APK over an older local install if the signing key changed; uninstall the old package first in that case
