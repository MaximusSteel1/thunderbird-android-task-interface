# TaskMail Next Session Handoff (2026-03-21, Phase 2 direct outbound `new task` implementation)

## Current Decision

As of 2026-03-21, the first Android-side Phase 2 direct outbound `new task` slice is now implemented and closed with
both narrow automated validation and live-device validation for the current `new task` scope.

Current reading:

- Android-side direct `new task` send over relay is now in repository
- the first direct business slice is still only `new task`
- reply, `/status`, and direct read-side work remain outside this first slice
- adjacent PC or VPS acceptance for `phase2-direct-outbound-contract-v1` `action = new_task` is now present in the
  adjacent repository
- formal Android `new task` now has live proof for accepted direct ingress, fallback-to-mail, and hard rejection with
  draft retention against the current relay endpoint
- the current engineering target is no longer handshake or negative-path debugging for this slice; the next decision is
  whether to widen scope beyond `new task` while keeping reply, `/status`, and read-side direct work explicitly out of
  scope until a later phase

## Read First

1. `docs/taskmail/planning/android/taskmail-phase2-direct-outbound-contract-v0.1.md`
2. `E:\projects\mail_based_task_manager\docs\plans\phase2_direct_outbound_contract_v1.md`
3. `E:\projects\mail_based_task_manager\docs\plans\vps_relay_deploy_runbook.md`
4. `docs/taskmail/planning/android/taskmail-android-public-plaintext-direct-connect-plan-v0.1.md`
5. `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
6. `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
7. `docs/TASKMAIL-MAIL-RULES.md`

## What Changed This Session

- Android relay protocol support now includes `packet` encoding, `packet_ack` decoding, and classified relay error
  handling for business-packet send
- Android now has a direct `new task` sender that maps the current `TaskMailNewTaskDraft` to
  `phase2-direct-outbound-contract-v1`
- the formal `TaskNewTaskViewModel` flow now prefers direct send after `hello_ack`, falls back to mail on
  fallback-classified direct failures, and stops on hard direct rejection without silent fallback
- rejected `packet_ack` handling is now also hardened so an optional ack-level `error_code`, or the same hard-rejection
  code preserved as an `error_message` prefix, is treated as direct rejection rather than silent mail fallback
- focused tests were added for relay protocol, relay client, direct sender, and ViewModel routing
- narrow Android validation was re-run cleanly:
  - `.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest`
  - `.\gradlew.bat :feature:taskmail:internal:detekt`
  - `.\gradlew.bat :feature:taskmail:internal:lintDebug`

## 2026-03-21 Live Preflight Snapshot

The next manual smoke is no longer blocked on adjacent PC or VPS implementation.

Current preflight snapshot:

- attached device `24090RA29C` is visible over adb and Thunderbird debug is installed
- device-side saved relay token fingerprint is `6f05b17d957d`
- live relay `http://124.223.41.153:8787/healthz` now returns:
  - `status = ok`
  - `tls_enabled = false`
  - `taskmail_direct_ingress_enabled = true`
  - `auth.transport_token_id = 6f05b17d957d`
- saved Android relay config still has `Use TLS = true`, so the next direct bootstrap would fail until that saved
  config is corrected

Important current-code nuance:

- saved `relay_enabled = false` on-device is not the practical blocker for formal `new task` smoke
- current `TaskNewTaskViewModel` bootstrap path loads saved relay host / port / token config and does not require the
  debug-screen `Relay enabled` switch to be on before attempting direct bootstrap
- the actionable fix before smoke is to save the relay config again with `Use TLS` turned off for the current endpoint

## 2026-03-21 Live Smoke Result

The first real-device smoke after that TLS correction produced a mixed but useful result.

Confirmed:

- retained debug relay bootstrap reached live `hello_ack`
- the formal TaskMail `New task` screen accepted a manual send titled `Phase2 direct smoke`
- adjacent PC runtime created `thread_082`, ran it to `DONE`, and returned
  `PHASE2_DIRECT_SMOKE_20260321`
- the formal TaskMail workspace on-device displayed the completed session

Not yet closed:

- accepted direct `packet -> packet_ack` ingress from the formal Android flow

Current evidence points away from PC or VPS readiness as the blocker:

- live `/healthz` around the formal send still reported `taskmail_direct_ingress_enabled = true`
- relay `session_count` increased but `packet_count` stayed flat
- adjacent runtime stored the first ingress as a real inbound `[CX]` mail from the user's mailbox rather than a
  direct-bridge message
- a separate live `/relay` probe using the same saved device token received:
  - `hello_ack`
  - `error code = invalid_payload` for a deliberately malformed Phase 2 packet

Current best reading:

- live direct handler is online
- the formal Android flow still completed through mail fallback
- next debugging should focus on why formal direct-send does not reach accepted packet ingress:
  - no business packet sent after `hello_ack`, or
  - pre-accept relay rejection that currently falls back to mail without durable enough diagnostics

## 2026-03-21 Live Smoke Closure

Later on 2026-03-21, after reinstalling a fresh Thunderbird debug build signed with the device-compatible local debug
keystore and rerunning a second formal-host smoke titled `Phase2 direct smoke B`, the accepted direct-ingress boundary
closed for the current `new task` scope.

Confirmed:

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
- `thread_083/mail/raw_001.json` stored the first ingress as `[CX] Phase2 direct smoke B` with the direct-bridge
  headers:
  - `X-TaskMail-Direct: 1`
  - `X-TaskMail-Relay-Packet-Id: android-taskmail:new-task:req_894649456f184a50a4a641a2c01d006b`
  - `X-TaskMail-Relay-Request-Id: req_894649456f184a50a4a641a2c01d006b`
- adjacent runtime completed the task to `DONE`, and `thread_083/thread_state.json` stored
  `PHASE2_DIRECT_SMOKE_20260321_B` as the final summary

Current best reading:

- live accepted direct ingress for formal Android `new task` is now closed
- the first `[CX]` mail seen by the adjacent runtime in this path is the expected direct-bridge artifact, not a user
  mail fallback
- later TaskMail status/result delivery still remains mail-based today
- reply, `/status`, and read-side direct transport remain out of scope for this phase

## 2026-03-21 Negative-Path Smoke Closure

Later on 2026-03-21, after the live relay exposed `taskmail_direct_negative_hook_enabled = true`, the remaining two
negative branches for the current Phase 2 `new task` slice were also closed on-device.

Confirmed:

- fallback smoke `Phase2 fallback smoke A` produced:
  - Android logcat showing a relay `packet` send followed by rejected `packet_ack`
  - live relay `packet_count` advancing from `6` to `7`
  - adjacent runtime creating `thread_084` from a real inbound user-mail `[CX]` message rather than a direct-bridge
    message
  - adjacent runtime completing `thread_084` with `PHASE2_FALLBACK_SMOKE_20260321`
- the first hard-rejection smoke `Phase2 hard reject smoke A` initially appeared to fail because the device was still
  running a stale APK that lacked the new ack-level `error_code` classification and therefore fell back to mail,
  creating `thread_085`
- after reinstalling the latest debug APK, hard-rejection smoke `Phase2 hard reject smoke B` produced:
  - Android logcat `Relay packet ack rejected ... code=invalid_payload`
  - live relay `packet_count` advancing again without any new adjacent-runtime thread beyond `thread_085`
  - the device staying on `New task`, preserving the draft, and showing inline `TaskMail send failed`

Current best reading:

- the current Phase 2 `new task` slice is now live-validated on all three intended branches:
  - accepted direct ingress
  - fallback-classified direct failure routing back to mail
  - hard direct rejection stopping locally without silent fallback
- later TaskMail status/result delivery still remains mail-based today
- reply, `/status`, and read-side direct transport remain out of scope for this phase

## Next Concrete Steps

1. treat the current Phase 2 `new task` slice as closed for its intended scope:
   - accepted direct ingress is live-validated
   - fallback-to-mail is live-validated
   - hard rejection with draft retention is live-validated
2. keep capturing the live relay preflight for any future smoke:
   - relay host and port
   - TLS on or off, and whether Android saved config matches live `tls_enabled`
   - `taskmail_direct_ingress_enabled`
   - `taskmail_direct_negative_hook_enabled` when negative-path validation is in play
   - transport token fingerprint match
3. if the next phase starts, freeze the next contract boundary before code:
   - keep reply, `/status`, and read-side work on the current mail path until the later phase explicitly widens scope
   - do not widen the current slice retroactively just because `new task` is now closed

## 建议的下一阶段规划

建议把下一阶段收窄为“send-side session actions 扩展”，而不是马上把整个 TaskMail transport 改成 direct-first。

建议目标：

- 在保持 current read-side / status-result delivery 继续走 mail 的前提下，把 direct outbound 从 `new task` 扩到
  session-detail 里的 `reply` 和 `/status`
- 复用已经验证过的 bootstrap、`packet` / `packet_ack`、fallback-classification、hard-rejection 停止语义
- 不在这一阶段引入 direct read-side、push/streaming session update、或新的 relay presence 语义

建议顺序：

1. 先冻结下一版 contract，再写代码
   - 优先决定是扩展现有 `phase2-direct-outbound-contract-v1`，还是单独起一个 session-actions contract
   - 先写清 `reply` / `/status` 的 payload、错误分类、mail fallback matrix、以及 hard rejection 边界
2. Android 先做 send-side seam 复用
   - 把 current `new task` direct-send orchestration 提炼成可复用 coordinator
   - 接到 `TaskSessionDetailViewModel` / reply send path，而不是重新复制一套 direct-send 流程
3. 先覆盖最容易回归的 reply semantics
   - plain-text reply
   - single-question quick answer
   - multi-question `Answers:`
   - paused-session `/resume`
   - attachment-only continuation
   - `/status`
4. PC/VPS 保持 bridge 思路，不提前改 read-side
   - 先把 direct ingress 的 session actions 安全桥接回现有 mail control plane
   - status/result 回流、thread projection、mail parsing 继续走现有 mail path
5. 验证按“小而硬”的矩阵收口
   - narrow tests + `:feature:taskmail:internal:testDebugUnitTest`
   - `:feature:taskmail:internal:detekt`
   - `:feature:taskmail:internal:lintDebug`
   - live smoke 至少覆盖 success / fallback / hard rejection 三类结果

明确暂缓：

- direct read-side transport
- direct status/result delivery back to Android
- workspace / session live push
- project-list / bootstrap discovery 的额外范围扩张
- 为了下一阶段而回头重构当前已经关闭的 `new task` slice

如果下一阶段真的启动，建议先从 `reply` 起步，再补 `/status`；不要同时把 reply attachments、multi-question、
paused resume、`/status` 全部一起上线到 live relay。

## Scope Boundary Reminder

Do not widen the first implementation slice to reply, `/status`, or direct read-side state.

Do not rewrite current Layer 1 mail behavior docs into future tense until code actually changes.

## Session Summary

Current session result:

- code changes performed: yes
- validation performed: yes
- artifact produced:
  - Android-side Phase 2 direct outbound `new task` implementation plus narrow verification
  - four live-device smoke snapshots:
    - the first showed end-to-end completion through mail fallback and narrowed the debugging target
    - the second closed accepted direct `packet -> packet_ack` ingress for the formal Android `new task` flow
    - the third closed fallback-classified direct failure routing back to mail
    - the fourth, after reinstalling the latest APK, closed hard direct rejection without silent mail fallback
