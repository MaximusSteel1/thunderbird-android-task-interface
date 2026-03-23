# TaskMail Debug 验证路径

本文记录 Android TaskMail 仍保留的 debug-host 验证路径。

请与 `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md` 配合阅读：

- formal launcher 与 drawer entry 现在已经属于真实 TaskMail 表面的一部分
- 当你需要隔离 TaskMail internals 时，debug activity 仍然是有价值的 focused validation / fallback 路径

## 文档维护约定

- 本文件主要承载调试路径、设备路径与易复现 pitfall，不承担实现状态 authority
- Markdown 编码、换行与文件结尾遵循仓库 `.editorconfig`：`utf-8`、`lf`、保留 final newline
- 后续新增或更新说明默认使用中文；报错原文、命令行、包名、类名与 deep link 保持原文

## 目标

使用现有 debug activity 主要验证以下事项：

- TaskMail 可以通过 deep link 打开
- workspace screen 会从 repository 加载真实 TaskMail 数据
- session detail 可以从 workspace 列表打开
- body extraction 与 grouping 在真实邮件数据上看起来合理

## Debug 包名

- Thunderbird debug: `net.thunderbird.android.debug`
- K-9 Mail debug: `com.fsck.k9.debug`

## 构建命令

优先使用与你要验证的 app 对应的最窄构建命令：

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

## ADB Path Pitfall

On this workstation, fresh PowerShell shells used for TaskMail automation may not have `adb` on `PATH` even when the
Android SDK is already installed locally.

- symptom: `adb devices` fails immediately with `adb : The term 'adb' is not recognized ...`
- trigger: running device commands from a shell that has not imported Android SDK `platform-tools` into `PATH`
- current best understanding: the local SDK exists, but this shell profile does not add it automatically
- workaround: use the known local binary directly:

```powershell
& 'C:\Users\Administrator\AppData\Local\Android\Sdk\platform-tools\adb.exe' devices -l
```

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

## PowerShell Binary ADB Pull Pitfall

On this workstation, PowerShell redirection can also corrupt raw binary SQLite payloads read through
`adb exec-out run-as ... cat ...`.

- symptom: the pulled file exists with the expected size, but SQLite readers fail immediately with errors such as
  `sqlite3.DatabaseError: file is not a database`
- trigger: using PowerShell redirection on raw binary output, for example
  `adb exec-out run-as net.thunderbird.android.debug cat databases/preferences_storage > local.db`
- cause: this redirection path can wrap the raw stream into text-transformed output instead of preserving the original
  SQLite bytes
- workaround: use `cmd /c` for the redirection step, or first write the binary file on-device and then `adb pull` it
  back to the workstation

## Project Sync File Debug Logging

When you need a durable on-device trace for `Project list` / `[SYNC]` investigation, use the retained
`project-sync-debug.log` switch on the debug relay screen instead of relying only on transient logcat windows.

- entry: open `TaskMail relay debug`, turn on `Project sync debug file logging`, then tap `Save`
- output path: `/sdcard/Android/data/net.thunderbird.android.debug/files/taskmail-debug/project-sync-debug.log`
- current default: off
- intended scope: focused investigation of `direct accepted` / `mail fallback` / `waiting for canonical reply`
  sequencing on a real device
- current payload policy: keep only timestamps plus non-PII identifiers such as `requestId`, `receiptId`,
  `transportMessageId`, and error class / error message; do not treat this file as a place to add mailbox addresses,
  tokens, or message content
- recommended workflow: enable only for the specific repro window, pull the file immediately after reproduction, then
  turn it off again and delete the old file if you no longer need it

## Project Sync File Debug Logging Verification Note

Later on 2026-03-23, the attached Thunderbird debug device verified both sides of this switch:

- when the switch is enabled, `Project list -> Sync project list` writes `project-sync-debug.log` with `[SYNC]`
  request/ack/waiting/follow-up/result events
- after the switch is saved back to off and the old file is deleted, repeating `Sync project list` no longer recreates
  `project-sync-debug.log`

Current best reading:

- this is a retained debug-only capability, not part of the product-facing TaskMail surface
- the switch is reliable enough to keep as an important recurring investigation tool for future `[SYNC]` live-debug
  sessions

## Install Note

Before device smoke, check whether the device already has an older local debug build installed.

- if Android rejects the new APK because the installed package uses a different or too-old signing key, uninstall the existing debug app first and then install the newly built APK

## Debug Keystore Drift Pitfall

On this workstation, switching `ANDROID_USER_HOME` between `E:\projects\android_task_manager\.android-user` and
`C:\Users\Administrator\.android` changes which local `debug.keystore` Gradle uses for debug APK signing.

- symptom: `.\gradlew.bat :app-thunderbird:installFullDebug` fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE` even
  though the package name matches the build already installed on the phone
- trigger: trying to upgrade an existing debug install that was signed with the other local debug keystore
- cause: Android treats those two local debug keystores as different signers
- workaround: for in-place upgrade, build and install with the same `ANDROID_USER_HOME` that produced the currently
  installed package, or uninstall before switching debug keystores

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

## Formal Host Cold-Start Pitfall

On this workstation, the formal TaskMail launcher host cannot be cold-started directly from adb in the same way as the
debug deep-link host.

- symptom: `adb shell am start -W -n net.thunderbird.android.debug/app.k9mail.feature.launcher.FeatureLauncherActivity`
  fails with `SecurityException: Permission Denial`, or an adb launch through `MainActivity` lands in
  `MessageHomeActivity` instead of reopening the formal `Tasks` flow
- trigger: trying to automate formal-host process-death or recent-tasks cold-start validation only through adb start
  commands
- cause: `FeatureLauncherActivity` is not exported, and the current `MainActivity` / startup routing does not recreate
  the same TaskMail host flow from arbitrary route data
- workaround: for formal-host cold-start smoke, relaunch Thunderbird from the desktop launcher and manually enter
  `Tasks`; keep debug deep links for debug-host-only validation

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

## 2026-03-21 Phase 2 Direct-Smoke Preflight Note

On 2026-03-21, a fresh preflight pass from the current workstation plus the attached device model `24090RA29C`
confirmed that the Phase 2 direct `new task` smoke boundary moved again.

That preflight established all of the following:

- the attached device is visible over adb as `Y5BYVCU4PF4PINJR`
- Thunderbird debug package `net.thunderbird.android.debug` is installed on-device
- the app's persisted TaskMail relay config already contains:
  - `host = 124.223.41.153`
  - `port = 8787`
  - `path = /relay`
  - a non-empty relay transport token whose Android-side fingerprint is `6f05b17d957d`
  - `TaskMail bot mailbox = sgjcc@qq.com`
- live relay `http://124.223.41.153:8787/healthz` returns:
  - `status = ok`
  - `tls_enabled = false`
  - `taskmail_direct_ingress_enabled = true`
  - `auth.transport_token_id = 6f05b17d957d`

The current blocker for the next direct smoke is now narrower than before:

- the saved Android relay config still has `Use TLS = true`
- the live relay currently reports `tls_enabled = false`
- current Android bootstrap/direct-send attempts will therefore fail until the saved device config is updated to use
  plaintext HTTP / WS for this endpoint

One subtle but important current-code reading also matters here:

- the saved `taskmail.relay_enabled` flag is currently `false` on-device
- current formal `new task` bootstrap/direct-send does not gate on that flag; it loads the saved host / port / path /
  token config and attempts direct bootstrap based on actual configuration presence
- in practice, for the next smoke the meaningful device-side fix is turning `Use TLS` off and saving the relay config

## 2026-03-21 Phase 2 Direct-Smoke Result

Later on 2026-03-21, after `Use TLS` was turned off and the relay config was saved again on-device, the retained
debug-host path and the formal host produced a narrower but more actionable smoke result.

Confirmed results:

- the debug relay screen reached live `hello_ack` successfully against `ws://124.223.41.153:8787/relay`
- the formal TaskMail host accepted a manual `New task` submission titled `Phase2 direct smoke`
- the adjacent PC runtime completed that task to `DONE` and returned the expected reply token
  `PHASE2_DIRECT_SMOKE_20260321`
- the formal TaskMail workspace on-device later showed the completed session and token summary

What this smoke did **not** prove:

- live direct `new task` packet acceptance from the formal Android flow

The strongest current evidence is:

- live relay `/healthz` still showed `taskmail_direct_ingress_enabled = true`
- around the formal send, relay `session_count` increased but `packet_count` stayed unchanged
- adjacent PC runtime stored `thread_082/mail/raw_001.json` as a real inbound `[CX] Phase2 direct smoke` mail from the
  user's mailbox to the bot mailbox
- that first mail did not carry the direct-bridge marker `X-TaskMail-Direct: 1`
- a separate no-side-effect live `/relay` probe using the same saved device token later received:
  - `hello_ack` for the websocket handshake
  - `error code = invalid_payload` for a deliberately malformed Phase 2 `new_task` packet

Current best reading:

- the live relay direct handler is present and reachable
- this smoke completed through formal Android mail fallback rather than accepted direct packet ingress
- the next debugging target is the formal Android direct-send path itself:
  either it never sent the business packet after `hello_ack`, or it received a pre-accept relay rejection and silently
  fell back to mail

## 2026-03-21 Phase 2 Direct-Smoke Closure

Later on 2026-03-21, after reinstalling a fresh Thunderbird debug build signed with the device-compatible local debug
keystore and rerunning a second formal-host smoke titled `Phase2 direct smoke B`, the accepted direct-ingress boundary
closed.

Confirmed results:

- device logcat from `OkHttpRelayConnectionClient` recorded:
  - `Sending relay packet packetId=android-taskmail:new-task:req_894649456f184a50a4a641a2c01d006b`
  - `Received relay packet ack for packetId=android-taskmail:new-task:req_894649456f184a50a4a641a2c01d006b`
- live relay `/healthz` after the send reported:
  - `status = ok`
  - `taskmail_direct_ingress_enabled = true`
  - `tls_enabled = false`
  - `session_count = 8`
  - `packet_count = 4`
- adjacent runtime created `thread_083`
- `thread_083/mail/raw_001.json` stored the first ingress as `[CX] Phase2 direct smoke B` with direct-bridge headers:
  - `X-TaskMail-Direct: 1`
  - `X-TaskMail-Relay-Packet-Id: android-taskmail:new-task:req_894649456f184a50a4a641a2c01d006b`
  - `X-TaskMail-Relay-Request-Id: req_894649456f184a50a4a641a2c01d006b`
- adjacent runtime then completed that thread to `DONE`, and `thread_083/thread_state.json` stored
  `PHASE2_DIRECT_SMOKE_20260321_B` as the final summary

Current best reading:

- formal Android `new task` now has live proof of accepted `packet -> packet_ack` ingress against the current relay
- the first `[CX]` mail seen by the PC runtime in this path is the expected direct-bridge artifact, not a fallback user
  mail
- later TaskMail status/result delivery still remains on the retained mail path today
- reply, `/status`, and read-side direct transport remain outside the validated scope

## 2026-03-22 Phase 4 Relay Re-Provision / Direct Closeout Note

Later on 2026-03-22, a focused follow-up on the same attached device re-provisioned the saved relay config after an
earlier reinstall had left the formal host in `not_configured`.

That pass verified all of the following:

- the saved Android relay config was brought back to the current live plaintext boundary:
  - `host = 124.223.41.153`
  - `port = 8787`
  - `path = /relay`
  - `useTls = false`
  - bot mailbox `sgjcc@qq.com`
  - relay transport token fingerprint `6f05b17d957d`
- the retained debug relay screen again reached:
  - `Healthz -> status=ok | ... | token_id=6f05b17d957d`
  - `Connect -> connection=connected`
- after force-stop plus desktop-launch return into the formal host, a fresh `New task`
  `Phase 4 direct parity 20260322 C` surfaced user-visible `[Relay]`
- the adjacent PC runtime then closed that same run on `thread_095`, where
  `runs/20260322_163746_d160/canonical_summary.json` records:
  - `ingress_type = direct_bridge`
  - `request_id = req_f8f52bd45be6445185c0553b4b248fb0`
  - `terminal_mail_subject = [DONE][S:thread_095] Phase 4 direct parity 20260322 C`

Current best reading:

- the debug-host path is still a valid retained preflight route for relay `Healthz` plus `hello_ack`
- the formal host now also has a fresh relay-accepted sample after live re-provision, not only the earlier 2026-03-21
  direct smoke
- later status/result delivery still remains mail-based today; this note does not widen the validated scope beyond the
  current `new task` direct boundary

## 2026-03-21 Phase 2 Hard-Reject Stale-APK Pitfall

在 2026-03-21 的 hard-rejection smoke 里，live relay 已经返回了正确的 `error_code = invalid_payload`，但设备第一次
仍然错误地回退到了 mail。

- symptom: 期望出现本地 `TaskMail send failed`，实际却又生成了一封新的 `[CX]` mail，并在 PC 侧创建了 `thread_085`
- trigger: Android 代码已经加入 ack-level `error_code` hard-rejection 分类后，设备仍在运行旧 APK
- cause: 手机上的 APK 还不包含新的 `packet_ack.error_code` 拒绝分类逻辑
- workaround: 先重装当前最新 debug APK，再确认 logcat 里的 rejected `packet_ack` 已经带出 `code=invalid_payload`

## 2026-03-21 Phase 2 Negative-Path Smoke Closure

Later on 2026-03-21, after the live relay exposed `taskmail_direct_negative_hook_enabled = true`, the retained
device-validation path closed the two remaining negative branches for the current Phase 2 `new task` slice.

Confirmed results:

- fallback smoke `Phase2 fallback smoke A` produced:
  - Android logcat showing a relay `packet` send followed by rejected `packet_ack`
  - live relay `/healthz` advancing `packet_count` from `6` to `7`
  - adjacent runtime creating `thread_084`, whose first ingress is a real inbound user-mail `[CX]` message rather than
    a direct-bridge mail
  - adjacent runtime later completing that thread with `PHASE2_FALLBACK_SMOKE_20260321`
- the first hard-rejection smoke `Phase2 hard reject smoke A` was invalidated by the stale-APK pitfall above, because
  the device still fell back to mail and created `thread_085`
- after reinstalling the latest debug APK, hard-rejection smoke `Phase2 hard reject smoke B` produced:
  - Android logcat `Relay packet ack rejected ... code=invalid_payload`
  - live relay `packet_count` advancing again without any new adjacent-runtime thread beyond `thread_085`
  - the device staying on `New task`, preserving the draft, and showing inline `TaskMail send failed`

Current best reading:

- the current Phase 2 `new task` slice now has live proof for accepted direct ingress, fallback-to-mail, and hard
  rejection without silent fallback
- later TaskMail status/result delivery still remains on the retained mail path today
- reply, `/status`, and read-side direct transport remain outside the validated scope

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

On debug builds, these deep links currently land in `TaskMailDebugActivity` because the debug manifest declares the `app://taskmail/*` intent filter. Use the in-app drawer `Tasks` entry when you need to validate the formal launcher host on a device; for cold-start/process-death smoke, relaunch from the desktop launcher first instead of treating this deep link as an equivalent substitute.

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
- Formal-host cold-start validation is still launcher-first/manual-`Tasks`; adb deep links and direct activity starts do not recreate the same host boundary
- Some real-message datasets may still surface noisy fallback text if the stored mail body lacks clean reply/capsule boundaries
- Console output from the JSON validation suite may still show text encoding issues for some Chinese content
- Some devices may refuse to install a freshly built debug APK over an older local install if the signing key changed; uninstall the old package first in that case

## 2026-03-21 Phase 3 Detail Live-Smoke Note

在 `2026-03-21` 的 Phase 3 `detail` live smoke 里，`thread_086 / phase3-detail-q-20260321_194446-211b05`
给出了一个比“Android 没刷新”更具体的结论。

这轮确认了：

- 设备上的 `TaskMail relay debug` 可以成功连上 `ws://124.223.41.153:8787/relay`，`hello_ack` 正常返回
- Android `detail` 页面里的 durable mail 路径仍然可用，因为手动 `pull-to-refresh` 后可以看到
  `Running -> WaitingUser -> Done`
- 但在保持 `detail` 打开的窗口里，没有观察到预期的 direct `session_snapshot` / `session_update`

随后用同一个已保存的 Android relay config，在工作站侧直接做 websocket probe，向 live relay 发送
`subscribe_session_detail`，订阅参数使用了当前 live thread 的 canonical locator：

- `workspace_id = workspace_d0a3ad8a2abc`
- `repo_path = E:\projects\android_task_manager`
- `workdir = .`
- `session_id = thread_086`
- `thread_id = thread_086`

probe 结果是：

- `hello_ack` 成功
- `packet_ack.accepted = false`
- `error_code = session_not_found`
- `error_message = could not resolve a session for the requested workspace/session locator`

当前最佳理解是：

- live relay 本身可达，transport token 也有效
- 但当前 VPS/live relay 所使用的 session registry / task-root 并不包含这条本地 PC live smoke 线程
  `thread_086`
- 因此这轮 Android `detail` 没收到 direct live update，当前更像是 relay-side session resolution blocker，
  还不能把问题直接归到 Android `timeline merge` / projector / ViewModel

同一轮还确认了一个次级现象：

- `E:\projects\mail_based_task_manager\_tmp_live_mail_runner\tasks\thread_086\mail\raw_004.json`
  里的 `[QUESTION]` mail 自身就包含两段 `---TASK-QUESTION-BEGIN--- ... ---TASK-QUESTION-END---`
- 所以 `detail` 里出现的 duplicate pending question，更像 mail body duplication / extractor 输入问题，
  不是这轮 direct merge 新引入的重复

下一步建议顺序：

1. 先让 live relay 能 resolve 当前 smoke thread，或者改用 relay 已知 task-root 下已存在的 thread 做订阅
2. 再重做 Android `detail` live smoke，确认 `session_snapshot/session_update -> projector -> detail merge`
   是否真的通
3. 在 direct live-update blocker 清掉之前，不要把这轮失败读成 Android Phase 3 merge 回归

## 2026-03-21 Phase 3 Detail Live-Smoke Retest

在同一天稍后的 retest 里，前一轮 `session_not_found` blocker 已经不再成立。

- 工作站侧用同一组 relay 配置重做 `subscribe_session_detail` probe 时，live relay 对 canonical locator
  返回了 `packet_ack.accepted = true`，并立即下发 `session_update(update_type = session_snapshot)`。
- 这说明前一轮卡住的问题已经从“relay 不能 resolve 当前 live session”收缩到“Android 设备侧 detail
  实际是靠 direct ws 还是靠 durable mail sync 推进”。

随后用新的 live thread `thread_087 / phase3-detail-live-20260321_202113-1dbf6b` 做了真实设备 retest，
这轮 QUESTION mail 要求回复 token `BAF751DE25`。

- Android detail 在不手动 `pull-to-refresh` 的前提下，先停在 `WaitingUser`
- reply mail 发出后，detail 自然推进到 `Running`
- 再经过后续后台同步，detail 自然推进到 `Done`
- 最终 summary 正常显示 `QUESTION_FLOW_OK | BAF751DE25`

本轮抓到的关键时间点是：

- `20:27:32`：设备 logcat 出现 `RealImapConnection` 对 `thread_087` 的 `[ACCEPTED]` / `[RUNNING]` fetch
- `+20s` UI dump：detail 已从 `WaitingUser` 进入 `Running`
- `20:28:37`：设备 logcat 出现 `RealImapConnection` 对 `thread_087` 的 `[DONE]` fetch
- 再等一个自然处理窗口后，detail UI 进入 `Done`

当前最稳妥的解释是：

- Phase 3 `detail` 页在真实设备上已经重新证明了“无手动刷新也能跟着 live mail 自然推进到终态”
- 这轮没有再复现前一轮的 relay `session_not_found` blocker
- 但从设备 logcat 能明确看到的是 IMAP fetch，不是 direct websocket `session_update` 日志
- 因此这轮更适合作为“detail auto-refresh closeout”证据，而不是“direct ws path 优先于 durable mail path”
  的最终证明

仍然保留的次级现象没有变化：

- `thread_087` 的 `[QUESTION]` mail UI 里仍显示 duplicate pending question
- 这与前一轮 `thread_086` 一样，更像 question mail 原文 / extractor 输入问题，而不像这次 Phase 3 merge
  新引入的重复
