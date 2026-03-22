# TaskMail Android 验证台账

本文记录 Android TaskMail 功能当前的验证证据。

请与下列文档配合阅读：

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`，用于实现状态
- `docs/TASKMAIL-MAIL-RULES.md`，用于协议 authority

跨仓库解读本台账时，应遵循 PC 侧当前 canonical docs：
`E:\projects\mail_based_task_manager\docs/current/`，尤其是
`E:\projects\mail_based_task_manager\docs/current/task_view_mail_parsing_rules.md`。

在相关内容真正进入 PC 侧 current docs，并且完成 Android 可执行验证之前，不要把
`E:\projects\mail_based_task_manager\docs/plans/coding_backlog.md` 里的 PC-side next-phase planning 直接当成
Android 行为的验证证据。

## 日期

- 最后更新：2026-03-22
- 本文件最近一次记录的可执行验证会话：2026-03-22

## 文档维护约定

- 本文件只回答“哪些能力现在有可执行验证证据”，不替代 current status 或 protocol authority
- Markdown 编码、换行与文件结尾遵循仓库 `.editorconfig`：`utf-8`、`lf`、保留 final newline
- 后续新增或更新说明默认使用中文；测试类名、Gradle 命令、路径和协议标识保持原文

## 目的

相比 current-status 文档，本文件回答的是一个更窄的问题：

> TaskMail Android 现在有哪些部分已经具备可执行验证证据，哪些部分仍然依赖历史结论或手工后续确认？

## 2026-03-22 Phase 4 New Task Durable Evidence Follow-up

Later on 2026-03-22, the first Android-side Phase 4 implementation slice turned the current `new task` direct-send
classification into durable local evidence instead of leaving it only in transient `TaskNewTaskViewModel` state.

That follow-up changed:

- new machine-readable `TaskMailDirectSendEvidence` / `TaskMailDirectOutcome` / `TaskMailDirectSwitchGate` models for
  `direct accepted`, `mail fallback`, and `hard rejection` outcomes
- `RunTaskMailDirectOrFallback` now returns explicit `outcome`, `switchGate`, `receiptId`, optional
  `transportMessageId`, fallback reason, and error-message evidence rather than only a bootstrap-status hint
- new `TaskMailNewTaskSendRecordRepository` plus file-backed JSON persistence for the latest `new task` send record per
  sender account
- `TaskNewTaskViewModel` now saves the latest direct-or-fallback evidence after each send attempt and rehydrates the
  latest persisted evidence when the selected sender account is resolved again
- focused coverage now locks:
  - machine-readable direct / fallback / hard-stop evidence mapping in
    `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/domain/usecase/RunTaskMailDirectOrFallbackTest.kt`
  - sender-account-scoped evidence rehydration plus post-send persistence in
    `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/ui/newtask/TaskNewTaskViewModelTest.kt`
  - file-backed latest-record persistence and reload behavior in
    `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/data/cache/FileBackedTaskMailNewTaskSendRecordRepositoryTest.kt`

The following narrow validation was re-run cleanly after that follow-up:

- `.\gradlew.bat -Dkotlin.compiler.execution.strategy=in-process :feature:taskmail:internal:testDebugUnitTest --tests "net.thunderbird.feature.taskmail.internal.domain.usecase.RunTaskMailDirectOrFallbackTest" --tests "net.thunderbird.feature.taskmail.internal.ui.newtask.TaskNewTaskViewModelTest" --tests "net.thunderbird.feature.taskmail.internal.data.cache.FileBackedTaskMailNewTaskSendRecordRepositoryTest"`
- `.\gradlew.bat -Dkotlin.compiler.execution.strategy=in-process :feature:taskmail:internal:detekt :feature:taskmail:internal:lintDebug`

This follow-up does **not** yet establish:

- export of that durable evidence into the shared Phase 4 `parity checklist`, `mismatch ledger`, or `rollback trigger`
  operational workflow
- new live-device smoke for reviewing the persisted evidence after process death or screen reload
- any widening of direct transport beyond the current `new task` scope

## 2026-03-22 Phase 4 New Task Three-Scenario Matrix Reconciliation

Later on 2026-03-22, the current Android-side `new_task` evidence was reconciled against the adjacent PC-side Phase 4
shared artifacts so the three intended scenarios now have an explicit Android-side matrix readout instead of only
scattered tests and smoke notes.

That follow-up changed:

- `docs/taskmail/planning/android/phase4_dual_stack_parity_checklist.md` now records the first Android-side matrix
  readout for:
  - `direct accepted`
  - `fallback_to_mail`
  - `hard_rejection_stop`
- `docs/taskmail/planning/android/phase4_mismatch_ledger.md` now includes a first Android-side readout and remains
  intentionally empty because no Android-side confirmed mismatch was evidenced in this pass
- `docs/taskmail/planning/android/phase4_rollback_trigger_note.md` now cites concrete Android evidence for:
  - bootstrap failure
  - ack classification drift
  - accepted without expected outcome
  - fallback path unavailable
  - summary outcome drift

This reconciliation reused already-recorded Android evidence from:

- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/domain/usecase/RunTaskMailDirectOrFallbackTest.kt`
- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/ui/newtask/TaskNewTaskViewModelTest.kt`
- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/data/relay/RelayTaskMailDirectNewTaskSenderTest.kt`
- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/data/relay/protocol/RelayProtocolJsonCodecTest.kt`
- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/data/cache/FileBackedTaskMailNewTaskSendRecordRepositoryTest.kt`
- the previously recorded 2026-03-21 live-device evidence for:
  - `thread_083` direct accepted
  - `thread_084` fallback-to-mail
  - hard reject smoke B after `thread_085`

The following narrow validation was re-run cleanly after this matrix-reconciliation follow-up:

- `.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest --tests "net.thunderbird.feature.taskmail.internal.domain.usecase.RunTaskMailDirectOrFallbackTest" --tests "net.thunderbird.feature.taskmail.internal.ui.newtask.TaskNewTaskViewModelTest" --tests "net.thunderbird.feature.taskmail.internal.data.relay.RelayTaskMailDirectNewTaskSenderTest" --tests "net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayProtocolJsonCodecTest" --tests "net.thunderbird.feature.taskmail.internal.data.cache.FileBackedTaskMailNewTaskSendRecordRepositoryTest"`

This follow-up does **not** yet establish:

- new live-device review of persisted latest evidence after screen reload or process death
- same-run Android / PC summary parity closeout on the shared artifacts
- any widening of direct transport beyond the current `new task` scope

## 2026-03-22 Phase 4 New Task Persisted-Evidence Review Surface

Later on 2026-03-22, the next Android-side Phase 4 follow-up turned the previously rehydrated latest
`TaskMailDirectSendEvidence` into a stable in-repo review surface on the formal `New task` screen.

That follow-up changed:

- the formal `TaskNewTaskContent` flow now renders a dedicated latest-evidence card when
  `state.lastDirectSendEvidence` is present
- new `TaskNewTaskDirectEvidenceCard` UI logic now shows the restored latest `outcome`, `switchGate`,
  `bootstrapStatus`, optional `requestId`, optional `receiptId`, optional `transportMessageId`, optional fallback
  reason, and optional error message in a reviewable form
- focused screen coverage now locks both:
  - accepted-result review with `requestId`, `receiptId`, and `transportMessageId`
  - rejected-result review with fallback reason and surfaced error text

The following narrow validation was re-run cleanly after this follow-up:

- `.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest --tests "net.thunderbird.feature.taskmail.internal.ui.newtask.TaskNewTaskScreenKtTest" --tests "net.thunderbird.feature.taskmail.internal.ui.newtask.TaskNewTaskViewModelTest"`
- `.\gradlew.bat :feature:taskmail:internal:detekt :feature:taskmail:internal:lintDebug`

This follow-up was automation-only in the current session because no attached Android device was available for the
live/manual closeout step.

This follow-up does **not** yet establish:

- live-device proof that the same review surface remains visible after actual screen reload or process death
- same-run Android / PC summary parity closeout on the shared artifacts
- any widening of direct transport beyond the current `new task` scope

## 2026-03-22 Phase 5 New Task `requestId` Bind Prep

在 2026-03-22 的后续窄实现中，Android 侧 accepted direct `new task` 结果把先前只在 packet 构造期生成的
`requestId` 真正贯通到了 durable evidence 链路。

That follow-up changed:

- `RelayTaskMailDirectNewTaskSender` now returns accepted direct `requestId` together with `receiptId` and optional
  `transportMessageId`
- `RunTaskMailDirectOrFallback`、`TaskNewTaskViewModel`、`TaskMailNewTaskSendRecordJsonCodec`、
  `FileBackedTaskMailNewTaskSendRecordRepository`、`TaskNewTaskDirectEvidenceCard` 现在都会保留并展示
  accepted direct `requestId`
- focused coverage now locks:
  - accepted-result request-id propagation in
    `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/data/relay/RelayTaskMailDirectNewTaskSenderTest.kt`
  - evidence mapping with `requestId` in
    `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/domain/usecase/RunTaskMailDirectOrFallbackTest.kt`
  - post-send persistence / rehydration of `requestId` in
    `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/ui/newtask/TaskNewTaskViewModelTest.kt`
  - latest-evidence card rendering of `requestId` in
    `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/ui/newtask/TaskNewTaskScreenKtTest.kt`
  - file-backed reload of accepted-direct `requestId` in
    `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/data/cache/FileBackedTaskMailNewTaskSendRecordRepositoryTest.kt`

The following narrow validation was re-run cleanly after that follow-up:

- `.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest --tests "net.thunderbird.feature.taskmail.internal.data.relay.RelayTaskMailDirectNewTaskSenderTest" --tests "net.thunderbird.feature.taskmail.internal.domain.usecase.RunTaskMailDirectOrFallbackTest" --tests "net.thunderbird.feature.taskmail.internal.ui.newtask.TaskNewTaskViewModelTest" --tests "net.thunderbird.feature.taskmail.internal.ui.newtask.TaskNewTaskScreenKtTest" --tests "net.thunderbird.feature.taskmail.internal.data.cache.FileBackedTaskMailNewTaskSendRecordRepositoryTest"`
- `.\gradlew.bat :feature:taskmail:internal:detekt :feature:taskmail:internal:lintDebug`

This follow-up does **not** yet establish:

- automatic binding from Android latest direct evidence to the later canonical mail terminal outcome
- any widening of direct transport beyond the current `new task` scope

## 2026-03-22 Phase 4 New Task Live Persisted-Evidence / Mail-Fallback Closeout

Later on 2026-03-22, the attached Android device was used to close the remaining live/manual boundary for the current
Phase 4 latest-evidence review surface on the formal `New task` host.

That follow-up established all of the following:

- after reinstall plus account reconfiguration, a live formal-host send titled `Phase 4 live review 20260322 A`
  surfaced explicit user-visible `[Mail fallback]` feedback rather than pretending the request took the direct path
- app-private send-record persistence under `files/taskmail/taskmail_new_task_send_records.json` stored the same run as:
  - `bootstrapStatus = not_configured`
  - `outcome = MailFallbackSucceeded`
  - `switchGate = FallbackRequired`
  - `fallbackReason = Relay host, port, and transport token are required.`
- reopening the formal `New task` page in the same session still showed the latest-evidence card with
  `Mail fallback succeeded`, `Fallback required`, `Not configured`, and the same fallback reason
- after clearing Thunderbird from recent tasks, relaunching from the desktop launcher, and manually re-entering
  `Tasks -> New task`, the same latest-evidence card was still present on the formal host
- mailbox-side truth also closed for the same run: the device inbox later showed
  `[DONE][S:thread_093] Phase 4 live review 20260322 A` with the expected reply token
  `PHASE4_LIVE_REVIEW_20260322_A`

Current best reading:

- the current formal-host latest-evidence review surface is no longer only an in-repo or same-process claim; it now has
  live-device proof across screen reload and recent-tasks cold start
- this specific live sample is a bootstrap-unavailable fallback case caused by missing relay config after reinstall, not
  a new regression in the accepted-direct path
- same-run Android / PC summary parity on the shared artifacts is still not fully closed just because one fallback
  sample now has better evidence

No additional Gradle rerun was required for this documentation-only closeout; it reused the focused automated coverage
already recorded above for the persisted-evidence review surface and direct / fallback classification.

## 2026-03-22 Phase 4 New Task Same-Run Summary Parity Readout

Later on 2026-03-22, the Android-side same-run summary parity readout tightened the shared Phase 4 matrix from
"all three rows still need Android / PC outcome reconciliation" into a state where the current `new task` direct /
fallback main rows all have retained positive samples.

That readout established:

- `thread_083` direct-accepted row now has a positive same-run parity sample:
  - Android retained workspace dump `_tmp_device/taskmail_phase2_negative_after_tap.xml` shows
    `Phase2 direct smoke B`, `Done`, and `PHASE2_DIRECT_SMOKE_20260321_B`
  - PC `thread_083/thread_state.json` and `mail/raw_004.json` both close on the same token
- `thread_093` bootstrap-unavailable fallback row now also has a positive same-run parity sample:
  - Android retained workspace dump `_tmp_device/current_taskmail_workspace2.xml` shows
    `Phase 4 live review 20260322 A`, `Done`, and `PHASE4_LIVE_REVIEW_20260322_A`
  - PC `thread_093/thread_state.json` and `mail/raw_004.json` both close on the same token
- `thread_094` fallback-to-mail replacement row now also has a positive same-run parity sample:
  - Android retained workspace dump `_tmp_device/phase4_fallback_after_refresh.xml` shows
    `Phase 4 fallback parity 20260322 B`, `Done`, and `PHASE4_FALLBACK_PARITY_20260322_B`
  - the latest app-private send record still stores the same run as
    `bootstrapStatus = not_configured` / `outcome = MailFallbackSucceeded` / `switchGate = FallbackRequired`
  - PC `thread_094/thread_state.json` and `mail/raw_004.json` both close on the same token
- `thread_084` should now be read as a historical retained-artifact gap, not an active blocker:
  - PC `thread_084/thread_state.json` and `mail/raw_004.json` already close on `PHASE2_FALLBACK_SMOKE_20260321`
  - the only retained Android live artifact currently on disk is `_tmp_device/taskmail_phase2_hardreject_prep.xml`,
    which still shows the earlier workspace card `Phase2 fallback smoke A / Unknown /` original prompt text
  - current best reading is that the retained Android terminal-summary sample for that older run was not preserved in
    this workspace, and the gap has now been superseded by the stronger `thread_094` same-run sample

This readout does **not** yet establish:

- automatic binding from Android latest direct evidence to the later canonical mail terminal outcome
- repeated real-run use of the now-documented manual `parity checklist -> mismatch ledger -> rollback trigger` flow
  beyond the current first fresh direct-accepted re-provision sample
- a confirmed mismatch that should already be promoted into `phase4_mismatch_ledger.md`

## 2026-03-22 Phase 4 Relay Re-Provision / Fresh Direct-Accepted Closeout

Later on 2026-03-22, after reinstall plus account reconfiguration had previously left the formal host in
`bootstrapStatus = not_configured`, the attached Android device was re-provisioned with the current live relay config
again and then used for one fresh direct-accepted formal-host sample.

That follow-up established all of the following:

- live VPS `http://124.223.41.153:8787/healthz` still returned:
  - `status = ok`
  - `tls_enabled = false`
  - `taskmail_direct_ingress_enabled = true`
  - `auth.transport_token_id = 6f05b17d957d`
- the device-side persisted TaskMail relay config was repaired back to the current live plaintext boundary:
  - `host = 124.223.41.153`
  - `port = 8787`
  - `path = /relay`
  - `useTls = false`
  - bot mailbox `sgjcc@qq.com`
  - non-empty relay transport token whose fingerprint again matched `6f05b17d957d`
- the retained debug-host relay screen then closed both bootstrap prechecks again on-device:
  - `Healthz` returned `status=ok | service=mail-runner-relay | listen=0.0.0.0:8787 | sessions=25 | packets=18 | token_id=6f05b17d957d`
  - `Connect` then reached `connection=connected` with a real `connection_id`
- after force-stop and desktop-launch re-entry into the formal host, a fresh `New task` titled
  `Phase 4 direct parity 20260322 C` surfaced explicit user-visible `[Relay]` feedback on send
- app-private send-record persistence under `files/taskmail/taskmail_new_task_send_records.json` stored that same run
  as:
  - `bootstrapStatus = hello_ack`
  - `outcome = DirectAccepted`
  - `switchGate = KeepDirectDefault`
  - `receiptId = relay-receipt:android-taskmail:new-task:req_f8f52bd45be6445185c0553b4b248fb0:03bed494`
  - `transportMessageId = <177416864698.472093.13590235740865598638@mail-runner.local>`
- the adjacent PC runtime closed that same run on `thread_095`, where:
  - `thread_state.json.last_summary = PHASE4_DIRECT_ACCEPT_20260322_C`
  - `mail/raw_001.json` stored the ingress mail as `[CX] Phase 4 direct parity 20260322 C`
  - `mail/raw_001.json` carries `X-TaskMail-Direct: 1`
  - `mail/raw_001.json` carries `X-TaskMail-Relay-Request-Id: req_f8f52bd45be6445185c0553b4b248fb0`
  - `mail/raw_001.json.message_id` matches Android `transportMessageId`
  - `runs/20260322_163746_d160/canonical_summary.json` now also closes the same run as:
    - `ingress_type = direct_bridge`
    - `ingress_message_id = <177416864698.472093.13590235740865598638@mail-runner.local>`
    - `request_id = req_f8f52bd45be6445185c0553b4b248fb0`
    - `terminal_mail_subject = [DONE][S:thread_095] Phase 4 direct parity 20260322 C`
  - `mail/raw_004.json` then closed mailbox-side on reply token `PHASE4_DIRECT_ACCEPT_20260322_C`

Current best reading:

- the current formal-host direct-accepted path is no longer represented only by the older `thread_083` sample; it now
  also has a fresh same-day sample after live relay re-provision on `thread_095`
- Android / PC same-run parity now has positive rows for both:
  - earlier accepted-direct proof (`thread_083`)
  - fresh accepted-direct proof after relay re-provision (`thread_095`)
- the shared-artifact manual consumption flow has now been exercised once with the newer PC-side
  `canonical_summary.json` artifact rather than relying only on manual `raw_001` / `raw_004` inspection
- later TaskMail status/result delivery still remains on the retained mail path today; this pass does not widen the
  validated direct scope beyond current `new task`

No additional Gradle rerun was required for this device/documentation closeout; it reused the already-recorded focused
automated coverage for direct classification, durable evidence persistence, and latest-evidence review.

## 2026-03-22 Phase 5 Fresh Closeout Workflow Reuse

在 2026-03-22 的后续 formal-host closeout 里，附着 Android 真机与当前 PC closeout helper 一起跑通了 freeze 之后第一条
fresh `daily_closeout_bundle` 复用。

这轮补充验证确认了：

- 一条新的 formal-host `New task` `Phase 5 fresh closeout 20260322 E` 在 PC 侧闭环为
  `thread_097 / runs/20260322_184156_afc8`
- PC `thread_state.json` 与 `canonical_summary.json` 都把同一 run 收敛到
  `PHASE5_FRESH_CLOSEOUT_20260322_E`
- Android app-private send-record 持久化 `files/taskmail/taskmail_new_task_send_records.json` 把同一 run 记录为：
  - `bootstrapStatus = hello_ack`
  - `outcome = DirectAccepted`
  - `switchGate = KeepDirectDefault`
  - `receiptId = relay-receipt:android-taskmail:new-task:req_c8c28c6db7394e3bbde73935461b38c9:232ada8a`
  - `transportMessageId = <177417609933.472093.14011859041567057941@mail-runner.local>`
- 但同次拉取到的 fresh Android latest send record 仍然 **没有** 带出 `requestId`
- Android app-private `taskmail_session_details.json` 仍把同一 run 闭环为：
  - `sessionId = thread_097`
  - `status = Done`
  - `lastSummary = PHASE5_FRESH_CLOSEOUT_20260322_E`
- 相邻 PC helper `scripts/build_taskmail_closeout_bundle.py` 随后写出了
  `thread_097/runs/20260322_184156_afc8/taskmail_daily_closeout_bundle.json`，其中：
  - all four required bundle-presence fields were `true`
  - `pc_canonical_outcome.ingress_type = direct_bridge`
  - `same_run_bind.effective_bind_level = transport_message_id`
  - `same_run_bind.matched_fields = ["transport_message_id", "last_summary"]`
  - `same_run_bind.strong_bind = true`
  - `same_run_bind.notes = ["android_request_id_missing"]`

当前最佳解读：

- 冻结后的 `daily_closeout_bundle` workflow 不再只是文档计划；`thread_097` 已提供一条 fresh formal-host rerun
- latest direct evidence 到 canonical outcome 已不再只是 summary-only weak bind，因为同一 run 已通过
  `transportMessageId <-> ingress_message_id` 完成强绑定
- 但这仍**没有**关闭目标中的 `request_id -> transportMessageId / ingress_message_id -> last_summary` 顺序，因为 fresh
  Android latest send record 仍未带出 `requestId`
- 因此当前 `new_task` switch-review 仍应保持 `not_ready_keep_mail_default`，直到后续 fresh sample 在设备上闭环
  `request_id`

这轮 device / documentation closeout 没有新增 Gradle 重跑；它复用了先前已记录的 Android focused coverage 与当前
PC-side closeout helper。

## 2026-03-22 Phase 5 Fresh `requestId`-First Bind Closeout

在 2026-03-22 的后续验证里，先安装了当前仓库构建出的 formal-host APK，然后又跑了一条新的 fresh direct-accepted sample，
用来确认 Android latest send evidence 是否终于在设备上带出 `requestId`，并按目标顺序闭环。

这轮补充验证确认了：

- `.\gradlew.bat :app-thunderbird:installFullDebug` 已把当前仓库的 formal-host build 装到附着设备
- 随后新的 formal-host `New task` `Phase 5 fresh closeout 20260322 F` 在 PC 侧闭环为
  `thread_098 / runs/20260322_190954_7e1f`
- Android app-private `taskmail_new_task_send_records.json` 的最新记录现在已带出：
  - `bootstrapStatus = hello_ack`
  - `outcome = DirectAccepted`
  - `switchGate = KeepDirectDefault`
  - `requestId = req_2a1e0790bdd54194bdce17e96c378e5e`
  - `receiptId = relay-receipt:android-taskmail:new-task:req_2a1e0790bdd54194bdce17e96c378e5e:ddf5624a`
  - `transportMessageId = <177417776437.472093.5957744132826889915@mail-runner.local>`
- 同一 run 的 PC canonical outcome 记录为：
  - `ingress_type = direct_bridge`
  - `request_id = req_2a1e0790bdd54194bdce17e96c378e5e`
  - `ingress_message_id = <177417776437.472093.5957744132826889915@mail-runner.local>`
  - `last_summary = PHASE5_FRESH_CLOSEOUT_20260322_F`
  - `terminal_mail_subject = [DONE][S:thread_098] Phase 5 fresh closeout 20260322 F`
- 同一 run 的 `taskmail_daily_closeout_bundle.json` 现已记录：
  - `android_latest_send_evidence.selection = request_id`
  - `same_run_bind.effective_bind_level = request_id`
  - `same_run_bind.matched_fields = ["request_id", "transport_message_id", "last_summary"]`
  - `same_run_bind.strong_bind = true`
  - `same_run_bind.notes = []`

当前最佳解读：

- 当前 formal-host build 上，Android latest send evidence 到 PC canonical outcome 的目标顺序
  `request_id -> transportMessageId / ingress_message_id -> last_summary` 已在 fresh sample 上闭环
- 先前 `thread_097` 暴露的 `android_request_id_missing` 已不再成立；它更像是旧安装包 / 旧设备构建状态下的 live gap
- 因此当前 `new_task` switch-review 已具备进入 `ready_for_direct_default_review` 的证据条件
- 这仍不等于“立即切换 direct-default 已被授权”；mail fallback 仍必须保持可执行，`reply` / `/status` 仍不进入当前 review scope

这轮验证没有新增生产代码，也没有新增测试代码；新增的是 `installFullDebug` 安装和后续 formal-host / helper closeout。

## 2026-03-22 Phase 5 `new_task` Activation / Config Verdict

在 2026-03-22 的同日后续判断里，Android 侧把 rollout / activation note 留下的更窄问题真正收口到了代码与可执行验证：

> 当前 Android 是否还需要单独的 `new_task` flow-scoped activation / config change，才能把 guarded
> `direct-default` activation 边界表达清楚？

这轮补充判断确认了：

- 在这轮判断发生的那个时点，`TaskNewTaskViewModel` 仍是 formal-host `new_task` business send 唯一进入
  `RunTaskMailDirectOrFallback.execute(...)` 的代码路径；direct bootstrap、direct send、mail fallback 与 hard
  rejection stop 只在这条 `new_task` flow 上发生
- 在这轮判断发生的那个时点，`TaskSessionDetailViewModel` 里的 `reply`、quick answer 与 `/status` 仍继续通过
  `SendTaskMailReply` 走 mail 语义，尚未复用 `RunTaskMailDirectOrFallback` 或新的 post-creation session-action sender
- 当前保存的 `taskmail.relay_enabled` 字段仍主要服务于 retained debug config / debug UI；formal `new task`
  direct bootstrap 与 direct detail observer 的实际 gate 仍是 relay config 是否满足 `isConfigured()`，而不是该 flag

当前最佳解读：

- 当前 Android 实现已经把 guarded direct-default 的 business activation 边界天然限制在 formal-host `new_task`
  flow，本轮没有发现会把 `reply` / `/status` 顺手拉进 direct-default 的发送 gate
- 因此当前结论是：`no_additional_new_task_activation_config_needed`
- 在这个结论下，在当时那个时点，下一步不是新增 Android flag / config 或再做一轮最小生产实现，而是把 verdict
  同步回 authority / handoff，并继续保持：
  - mail fallback 可执行
  - hard rejection = local stop + draft retention
  - `reply` / `/status` 留在当前 scope 外

The following narrow validation was re-run cleanly after this verdict:

- `$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot'; .\gradlew.bat :feature:taskmail:internal:testDebugUnitTest :feature:taskmail:internal:detekt :feature:taskmail:internal:lintDebug`

This follow-up made no Android production or test code change.

This follow-up does **not** yet establish:

- authorization to switch the production default immediately
- any new formal-host live rollout closeout, because no Android runtime behavior changed in this pass

## 2026-03-22 Phase 5 `reply` / `/status` Guarded Direct Slice Validation Note

同日更晚的 Phase 5 实现 follow-up 又把 `reply` / `/status` 的 current reading 往前推进了一步，但推进方式仍然是
guarded slice，而不是 current protocol/default 宣告。

这轮窄验证确认了：

- canonical `workspace_id` 现已从 workspace/detail route 贯通到 `TaskMailRoute.SessionDetail`、`TaskSessionKey`、
  session-detail cache JSON、snapshot-backed summary key，以及 repository / cache 的 legacy key 兼容读取
- Android 现已新增 `RelayTaskMailDirectSessionActionSender` 与 `SendTaskMailDirectSessionAction`，并按 shared
  `post_creation_session_action_contract_v1` 冻结的 packet wrapper 覆盖：
  - `current-session plain reply`
  - `current-session /status`
- `TaskSessionDetailViewModel` 现在会在以下条件同时满足时，复用 `RunTaskMailDirectOrFallback` 进入 guarded direct lane：
  - current detail route 能提供 canonical `workspace_id + session_id`
  - 动作属于 current-session plain reply 或 current-session `/status`
- 下列行为仍继续保持 mail path，没有被偷偷并进这个 direct slice：
  - quick answer
  - multi-question `Answers:`
  - paused `/resume`
  - attachment-bearing continuation
- UI 读法也保持受控：
  - direct accepted 只表示已进入 direct lane
  - 最终 user-visible outcome 仍由 canonical mail truth layer 收敛

本轮已执行并通过的窄验证包括：

- `.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest --tests "net.thunderbird.feature.taskmail.internal.data.relay.RelayTaskMailDirectSessionActionSenderTest" --tests "net.thunderbird.feature.taskmail.internal.domain.usecase.RunTaskMailDirectOrFallbackTest" --tests "net.thunderbird.feature.taskmail.internal.ui.detail.TaskSessionDetailViewModelTest" --tests "net.thunderbird.feature.taskmail.internal.ui.detail.TaskSessionDetailWorkdirDisplayTest"`
- `.\gradlew.bat :feature:taskmail:internal:detekt :feature:taskmail:internal:lintDebug`

这些 focused tests 现在锁定了：

- post-creation session-action relay packet wrapper 与 ack/error classification
- plain reply direct-eligible / fallback / hard-stop 分流
- `/status` direct-eligible 分流
- canonical `workspace_id + session_id` 缺失时继续保持 mail path

当前仍需保留的验证边界：

- 这轮只关闭了 Android-side focused unit / quality evidence，不等于 live mailbox / closeout 已完成
- 更宽的 `:feature:taskmail:internal:testDebugUnitTest` 仍受本地环境依赖影响；`TaskMailValidationRunner` 读取相邻
  PC 仓缺失的 `scripts/test_fetch_latest_100.json` 时会失败，因此不应把那条失败误判为本轮 guarded slice 回归

## 2026-03-23 Phase 5 `reply` / `/status` Durable Session-Action Evidence Slice

在 2026-03-23 的后续 Batch D 首段实现里，Android 侧把 `reply` / `/status` guarded direct lane 的 latest result
从 transient ViewModel state 推进到了可持久化、可重建、可 review 的 session-action evidence。

That follow-up changed:

- new `TaskMailSessionActionSendRecord` model plus `TaskMailSessionActionSendRecordRepository`
- new file-backed `TaskMailSessionActionSendRecordJsonCodec` and
  `FileBackedTaskMailSessionActionSendRecordRepository`
- new `GetLatestTaskMailSessionActionSendRecord` and `RecordTaskMailSessionActionSendRecord` use cases
- `TaskSessionDetailViewModel` now saves the latest guarded direct `reply` / `/status` result after each direct-or-fallback
  attempt and rehydrates that record when the current detail target is loaded again
- `TaskSessionDetailContent` now exposes a dedicated latest direct evidence review card for the current session target

本轮已执行并通过的窄验证包括：

- `.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest --tests "net.thunderbird.feature.taskmail.internal.data.cache.FileBackedTaskMailSessionActionSendRecordRepositoryTest" --tests "net.thunderbird.feature.taskmail.internal.ui.detail.TaskSessionDetailViewModelTest" --tests "net.thunderbird.feature.taskmail.internal.ui.detail.TaskSessionDetailScreenKtTest"`
- `.\gradlew.bat :feature:taskmail:internal:detekt :feature:taskmail:internal:lintDebug`

这些 focused tests 现在锁定了：

- latest session-action record 现按 canonical `workspace_id + session_id` 选取；`thread_id` 仅作 supporting identity，
  不会反向变成 latest-record 主键
- guarded plain reply 与 guarded `/status` 的 direct accepted / fallback / hard-stop 结果，都会更新同一条 latest direct
  evidence 读面，而不是只停留在临时 UI toast / 文案
- direct accepted 仍只表示“已进入 direct lane”；detail review surface 继续把 direct result 与最终 canonical mail
  outcome 分离呈现
- quick answer、multi-question `Answers:`、paused `/resume`、attachment continuation 与 targeted-session variant
  仍没有被偷偷并进这一批 durable evidence scope

This follow-up does **not** yet establish:

- live-device proof that the latest direct evidence card remains visible and accurate after actual screen reload or
  recent-tasks cold start
- same-run Android / PC shared-artifact closeout that binds the new session-action send record to canonical mail outcome
- live mailbox proof that accepted direct `reply` and accepted direct `/status` converge to canonical mail outcomes under
  the current formal-host path
- any promotion of `reply` / `/status` into current protocol authority or direct-default behavior

## 2026-03-23 Phase 5 Reply/Status Live Closeout

2026-03-23 这轮 closeout 在当前安装的 `net.thunderbird.android.debug` 上补齐了第一组 formal-host live/manual 证据。开始前先处理了一个设备坑：手机上旧的 `net.thunderbird.android.debug` 与当前工作站 debug keystore 签名不一致，必须先卸载，再用 `.\gradlew.bat :app-thunderbird:installFullDebug` 重装当前 build。重装后还需要通过 debug deep link `app://taskmail/debug/relay` 手动恢复 relay token，并确认 `Healthz` / `Connect` 都成功，之后再回正式 `Tasks` 做验证。

本轮 live/manual closeout 只保留了两个 v1 scope 样本：

- `thread_019 / Phase5 status closeout 20260323 A`
- `thread_020 / Phase5 reply closeout 20260323 B`

本轮新增或确认的实证如下：

- `thread_019` 在正式 `Tasks` detail 里点击 `/status` 后，Android 侧最新 `TaskMailSessionActionSendRecord` 记录为 `actionType=Status`、`target=workspace_cb2404bf828c/thread_019`、`bootstrapStatus=hello_ack`、`outcome=MailFallbackSucceeded`、`switchGate=FallbackRequired`
- 同一次 `/status` 动作在 PC mailbox 侧留下了用户入站 `raw_005.json`，并进一步收敛到 canonical `[STATUS][S:thread_019] Phase5 status closeout 20260323 A` `raw_006.json`
- `thread_019` detail 实际返回并重开后，`Latest direct result` 卡仍能恢复为 `Status query / Mail fallback succeeded / Fallback required / Hello ack`
- `thread_020` 在正式 `Tasks` detail 里发送 plain reply `PHASE5_REPLY_CLOSEOUT_20260323_B` 后，Android 侧最新记录为 `actionType=Reply`、`target=workspace_cb2404bf828c/thread_020`、`bootstrapStatus=hello_ack`、`outcome=MailFallbackSucceeded`、`switchGate=FallbackRequired`
- 同一次 plain reply 在 PC mailbox 侧先留下用户入站 `raw_005.json`，随后因原线程为 `FAILED` 触发 fresh recovery run `20260323_020701_9913`，最终收敛到 canonical `[DONE][S:thread_020] Phase5 reply closeout 20260323 B` `raw_008.json`
- `thread_020` 发送后曾瞬时出现 `Unable to load session`，但返回重开 detail 后 evidence 卡恢复；随后按 formal-host 要求从桌面图标冷启动 Thunderbird，再经 `Tasks` 回到 detail，卡片仍能恢复为 `Plain reply / Mail fallback succeeded / Fallback required / Hello ack`
- 为避免 closeout bundle 错过 Android 侧 `session_action` 记录，相邻 PC 仓 `mail_based_task_manager` 本轮还补了 `mail_runner/taskmail_closeout.py` 对 target-based Android 记录的选择逻辑与 `action_type` / `target_session_identity` 透传，并通过 `E:\projects\mail_based_task_manager\.venv\Scripts\python.exe -m pytest tests\test_taskmail_closeout.py`（`11 passed`）

当前结论：

- 设备侧 durable evidence / persistence blocker 已关闭：detail reload 与 formal-host desktop-launch cold start 都已有正向证据
- shared-artifact / strong-bind blocker 仍未关闭：Android fallback `TaskMailSessionActionSendRecord` 还缺 `requestId` / `transportMessageId`，PC 当前 post-creation fallback canonical artifacts 也还没有保留 `action_type`、`target_session_identity`、action-specific `ingress_message_id`

## 2026-03-21 Phase 0 Representative Consumer-Sample Follow-up

On 2026-03-21, the Android-side Phase 0 consumer-acceptance package advanced from an open sample manifest into
executable representative-sample coverage for three previously missing mail classes:

- `DONE` mail with `External Deliveries`
- mail with `Attachment Notices`
- `FAILED` mail with long error or code-like content

That follow-up added or revalidated all of the following:

- `TaskMailPreviewData` now includes representative detail samples for the three previously missing Phase 0 classes
- debug preview routing now resolves
  `app://taskmail/preview/detail/external-deliveries`,
  `app://taskmail/preview/detail/attachment-notices`, and
  `app://taskmail/preview/detail/failed-long-error`
- focused projector coverage in
  `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/data/TaskMailRichTextRepresentativeSampleTest.kt`
  now locks representative `article.task-mail` projection for `External Deliveries`, `Attachment Notices`, and
  `<pre>`-style failure output
- focused Robolectric detail coverage in
  `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/TaskSessionDetailRepresentativeSamplesTest.kt`
  now locks readable rendering for the same three representative samples
- the representative sample set is seeded from current PC-side outbound/raw mail examples, including
  `thread_072/raw_020.json`, `thread_075/raw_138.json`, and the current failed-mail class represented by
  `thread_051/raw_030.json`
- a clean rerun of `:feature:taskmail:internal:testDebugUnitTest`
- a clean rerun of `:feature:taskmail:internal:detekt`
- a clean rerun of `:feature:taskmail:internal:lintDebug`

This follow-up closes the Android-side representative sample gap for Phase 0 consumer acceptance. It does not replace
broader live-mailbox/device validation for richer rich-text corpus coverage, and it does not change the separate relay
TLS trust blocker recorded for Android / PC / VPS Phase 0 closeout.

## 2026-03-21 Phase 1 Relay Bootstrap Manager Follow-up

Later on 2026-03-21, the first Android-side Phase 1 implementation slice extracted the relay bootstrap orchestration out
of the debug ViewModel into a reusable internal manager layer.

That follow-up changed:

- new `RelayBootstrapManager` / `DefaultRelayBootstrapManager` orchestration above the current debug ViewModel
- `TaskMailRelayDebugViewModel` now delegates config load/save plus `healthz` / connect / disconnect actions through that
  manager instead of directly owning the three lower-level dependencies
- focused manager coverage in
  `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/data/relay/DefaultRelayBootstrapManagerTest.kt`

The following narrow validation was re-run cleanly after that refactor:

- `.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest`
- `.\gradlew.bat :feature:taskmail:internal:detekt`
- `.\gradlew.bat :feature:taskmail:internal:lintDebug`

This follow-up does **not** yet establish:

- direct business-action transport over relay
- new device validation for the plaintext live VPS path
- fallback routing from a formal TaskMail flow back to mail after direct-connect failure
- any claim that Android production transport is no longer mail-first today

## 2026-03-21 Phase 1 Relay Bootstrap Classification Follow-up

Later on 2026-03-21, the next Android-side Phase 1 follow-up added explicit bootstrap result classification aligned with
the current PC-side Phase 1 bootstrap vocabulary.

That follow-up changed:

- new `RelayBootstrapStatus` / `RelayBootstrapResult` models under the TaskMail internal relay/domain layer
- `RelayBootstrapManager` now exposes a `bootstrap(...)` path that probes `healthz`, attempts relay connect, and classifies
  the outcome into a reviewable result instead of leaving callers with raw exception text alone
- focused manager coverage now locks representative `not_configured`, `invalid_http_response`, `token_id_mismatch`,
  `unauthorized`, and `hello_ack` outcomes in
  `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/data/relay/DefaultRelayBootstrapManagerTest.kt`

The following narrow validation was re-run cleanly after that follow-up:

- `.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest`
- `.\gradlew.bat :feature:taskmail:internal:detekt`
- `.\gradlew.bat :feature:taskmail:internal:lintDebug`

This follow-up does **not** yet establish:

- a non-debug TaskMail caller reusing the bootstrap result above the retained debug surface
- direct business-action transport over relay
- new device validation for the plaintext live VPS path
- any claim that Android production transport is no longer mail-first today

## 2026-03-21 Phase 1 New Task Bootstrap Reuse Follow-up

Later on 2026-03-21, the next Android-side Phase 1 follow-up reused the structured relay bootstrap result in the formal
`new task` flow without cutting business traffic over to relay yet.

That follow-up changed:

- `TaskNewTaskViewModel` now preflights relay bootstrap through `RelayBootstrapManager` before the existing mail send path
- when bootstrap reaches `hello_ack`, the temporary preflight connection is immediately disconnected and the flow
  continues on the existing mail transport
- when bootstrap returns a non-success classification, the flow still sends the task request over mail and now emits an
  explicit `mail fallback` success message instead of pretending direct connect is already active
- `TaskNewTaskContract.State` now retains the last direct bootstrap classification for the most recent send attempt
- focused ViewModel coverage now locks bootstrap preflight reuse, `hello_ack` disconnect cleanup, and explicit
  mail-fallback success messaging in
  `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/ui/newtask/TaskNewTaskViewModelTest.kt`

The following narrow validation was re-run cleanly after that follow-up:

- `.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest`
- `.\gradlew.bat :feature:taskmail:internal:detekt`
- `.\gradlew.bat :feature:taskmail:internal:lintDebug`

This follow-up does **not** yet establish:

- direct business-action transport over relay for `new task`
- new device validation for the plaintext live VPS path
- any claim that Android production transport is no longer mail-first today

## 2026-03-21 Phase 2 New Task Direct Outbound Follow-up

Later on 2026-03-21, the first Android-side Phase 2 implementation slice replaced the earlier `hello_ack` preflight-only
behavior with a direct-first `new task` send path.

That follow-up changed:

- Android relay protocol support now includes relay `packet` encoding plus `packet_ack` decoding
- `RelayConnectionClient` / `OkHttpRelayConnectionClient` now support sending one business packet after `hello_ack` and
  classifying relay server `error` responses with transport-visible error codes
- new `RelayTaskMailDirectNewTaskSender` now maps `TaskMailNewTaskDraft` to the shared
  `phase2-direct-outbound-contract-v1` payload for `action = new_task`
- `TaskNewTaskViewModel` now prefers direct send after successful bootstrap, falls back to the current mail transport on
  fallback-classified direct failures, and stops on hard direct rejection without silent mail fallback
- focused relay protocol, relay client, direct sender, and ViewModel coverage now locks:
  - `packet` encoding
  - `packet_ack` decoding
  - direct payload mapping
  - accepted direct-send success without duplicate mail send
  - fallback-to-mail routing
  - hard direct rejection without silent fallback

The following narrow validation was re-run cleanly after that follow-up:

- `.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest`
- `.\gradlew.bat :feature:taskmail:internal:detekt`
- `.\gradlew.bat :feature:taskmail:internal:lintDebug`

This follow-up does **not** yet establish:

- the matching PC or VPS acceptance path for `phase2-direct-outbound-contract-v1` `action = new_task`
- new device validation for the plaintext live VPS direct-send path
- any claim that reply, `/status`, or read-side TaskMail updates no longer depend on mail today

## 2026-03-21 Phase 2 Packet Ack Rejection Classification Follow-up

Later on 2026-03-21, after the accepted direct-ingress smoke had already closed, a small Android-side compatibility
follow-up tightened direct rejection handling for the remaining `packet_ack.accepted = false` branch.

That follow-up changed:

- `RelayPacketAck` now decodes an optional ack-level `error_code`
- `RelayTaskMailDirectNewTaskSender` now treats `packet_ack.accepted = false` with:
  - hard-rejection `error_code` such as `invalid_payload`, `validation_failed`, or `unauthorized`, or
  - the same hard-rejection code carried as a prefix in `error_message`
  as `Rejected` rather than silently routing to mail fallback
- focused relay protocol and direct sender coverage now locks:
  - optional `packet_ack.error_code` decoding
  - ack-level hard rejection classification
  - preserved mail fallback for non-hard ack rejection

The following narrow validation was re-run cleanly after that follow-up:

- `.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest`
- `.\gradlew.bat :feature:taskmail:internal:detekt`
- `.\gradlew.bat :feature:taskmail:internal:lintDebug`

This follow-up does **not** yet establish:

- live negative-path smoke for ack-level hard rejection
- any change to the current mail-based status/result delivery path
- any widening of direct transport beyond the current `new task` scope

## 2026-03-21 Phase 2 Live Direct-Smoke Attempt

Later on 2026-03-21, the first focused live-device smoke against the current plaintext relay-ready endpoint closed the
basic end-to-end loop but did **not** close accepted direct-ingress validation for the formal Android `new task` flow.

Live-device and adjacent-runtime evidence established all of the following:

- retained debug relay bootstrap against `124.223.41.153:8787` reached live `hello_ack`
- the formal Android `New task` surface successfully submitted a live task titled `Phase2 direct smoke`
- adjacent PC runtime created `thread_082`, ran the task to `DONE`, and returned the expected token
  `PHASE2_DIRECT_SMOKE_20260321`
- the formal Android workspace later displayed the completed session and token summary

At the same time, the smoke also showed that the accepted-direct boundary is still open:

- live relay `/healthz` reported `taskmail_direct_ingress_enabled = true`
- around the formal send, relay `session_count` increased but `packet_count` did not
- adjacent runtime `thread_082/mail/raw_001.json` shows the first ingress as a real inbound `[CX]` mail from the
  user's mailbox to the bot mailbox rather than a direct-bridge mail
- that first ingress mail does not carry `X-TaskMail-Direct: 1`
- a separate no-side-effect relay probe using the same saved device token later confirmed that the live relay direct
  handler is actually present by returning `hello_ack` plus `invalid_payload` for a deliberately malformed Phase 2
  packet

The safest current interpretation is:

- live PC or VPS direct acceptance is no longer the blocker
- formal Android `new task` still fell back to mail in this smoke
- the next Android-side debugging target is to capture why the formal direct path does not reach accepted packet
  ingress under live conditions

## 2026-03-21 Phase 2 Live Direct-Smoke Closure

Later on 2026-03-21, after reinstalling a fresh Thunderbird debug build signed with the device-compatible local debug
keystore and rerunning a second formal-host smoke titled `Phase2 direct smoke B`, the accepted direct-ingress boundary
closed for the current Phase 2 `new task` slice.

Live-device, relay, and adjacent-runtime evidence established all of the following:

- device logcat from `OkHttpRelayConnectionClient` recorded both:
  - `Sending relay packet packetId=android-taskmail:new-task:req_894649456f184a50a4a641a2c01d006b`
  - `Received relay packet ack for packetId=android-taskmail:new-task:req_894649456f184a50a4a641a2c01d006b`
- live relay `/healthz` still reported `taskmail_direct_ingress_enabled = true`, `tls_enabled = false`, and advanced
  `packet_count` from the earlier `3` to `4` during the smoke
- adjacent PC runtime created `thread_083`
- `thread_083/mail/raw_001.json` stored the first ingress as `[CX] Phase2 direct smoke B` and carries the direct-bridge
  markers:
  - `X-TaskMail-Direct: 1`
  - `X-TaskMail-Relay-Packet-Id: android-taskmail:new-task:req_894649456f184a50a4a641a2c01d006b`
  - `X-TaskMail-Relay-Request-Id: req_894649456f184a50a4a641a2c01d006b`
- adjacent runtime then completed the task to `DONE`, and `thread_083/thread_state.json` stored the expected final
  summary token `PHASE2_DIRECT_SMOKE_20260321_B`

The safest current interpretation is:

- accepted direct `new task` ingress from the formal Android flow is now live-validated
- the first `[CX]` mail stored by the PC runtime is now confirmed to be the expected direct-bridge artifact rather than
  a user-mail fallback path
- later TaskMail status/result delivery still remains on the current mail path today
- reply, `/status`, and read-side direct transport still remain outside the current validated scope

## 2026-03-21 Phase 2 Live Negative-Path Closure

Later on 2026-03-21, after the live relay exposed `taskmail_direct_negative_hook_enabled = true`, the remaining two
Phase 2 negative branches for the current `new task` slice were closed on a real device.

Live-device, relay, and adjacent-runtime evidence established all of the following:

- the live relay `/healthz` snapshot now exposed `taskmail_direct_negative_hook_enabled = true`
- the fallback smoke titled `Phase2 fallback smoke A` produced:
  - Android logcat showing a relay `packet` send followed by rejected `packet_ack`
  - live relay `packet_count` advancing from `6` to `7`
  - adjacent runtime `thread_084` created from a real inbound `[CX]` mail rather than a direct-bridge mail
  - `thread_084/thread_state.json` storing the expected final token `PHASE2_FALLBACK_SMOKE_20260321`
- the first hard-rejection smoke titled `Phase2 hard reject smoke A` still fell back to mail and created `thread_085`,
  but that result was traced to a stale APK on-device rather than a relay/runtime bug:
  - the live relay already returned `error_code = invalid_payload`
  - the phone was still running an older APK that lacked the new ack-level hard-rejection classification patch
- after reinstalling the latest Thunderbird debug APK, the second hard-rejection smoke titled
  `Phase2 hard reject smoke B` produced:
  - Android logcat showing `Relay packet ack rejected ... code=invalid_payload`
  - live relay `packet_count` advancing again without any new adjacent-runtime thread beyond `thread_085`
  - the device staying on `New task`, keeping the draft content, and surfacing inline `TaskMail send failed`

The safest current interpretation is:

- the current Phase 2 `new task` slice now has live evidence for all three intended branches:
  - accepted direct ingress
  - fallback-classified direct failure routing back to mail
  - hard direct rejection stopping locally without silent mail fallback
- later TaskMail status/result delivery still remains on the current mail path today
- reply, `/status`, and read-side direct transport still remain outside the current validated scope

## 2026-03-20 Slice A Relay Bootstrap Follow-up

Later on 2026-03-20, the new Android relay bootstrap slice advanced from narrow unit/quality validation into focused
real-device smoke on attached Android device model `24090RA29C`.

That follow-up verified:

- the latest Thunderbird `fossDebug` APK installs cleanly on-device
- after an explicit `am force-stop` plus cold-start deep-link launch, `app://taskmail/debug/relay` resolves to
  `TaskMailDebugActivity` on the device
- the new `TaskMail relay debug` surface is visible on-device
- with no transport token configured, the relay screen blocks `Connect` locally and shows the expected validation
  message `Relay host, port, and transport token are required.`

That same follow-up did not verify live relay connectivity yet:

- no valid relay transport token was available during the device session
- `Healthz`
- live `hello -> hello_ack`

Later in the same 2026-03-20 device follow-up, once a valid relay transport token was supplied and the user retried the
probe with `Use TLS` enabled, the evidence boundary advanced again:

- the earlier plain-HTTP `unexpected end of stream` failure was confirmed to come from using non-TLS transport against a
  relay that currently reports `tls_enabled = true`
- the next TLS `Healthz` attempt failed with
  `java.security.cert.CertPathValidatorException: Trust anchor for certification path not found.`

This means the current live relay blocker is TLS trust / certificate chain validation on Android, not missing token
entry or missing relay-route wiring.

## 2026-03-20 Slice6 Inline Attachment Preview / Dedup Follow-up

Later on 2026-03-20, a focused TaskMail rich-text detail follow-up addressed two concrete Android-side issues observed
from live-device screenshots on a real TaskMail thread:

- timeline attachments could duplicate when the same physical image part surfaced through both attachment and inline
  projection paths
- `Inline Previews` still stayed on placeholder-only cards even when a raster image attachment already had a resolvable
  local content URI

That follow-up added or revalidated all of the following:

- timeline attachment projection now filters and deduplicates timeline attachments before repository detail mapping, so a
  single inline image part no longer renders twice in the attachment section
- rich-text detail rendering now uses the timeline attachment's local content URI for raster inline-image preview when a
  previewable attachment is available
- static SVG remains on the controlled placeholder/fallback path rather than trying to force a new SVG renderer in the
  same slice
- focused repository, ViewModel, and Robolectric screen coverage now locks the dedup path, raster-preview path, and SVG
  fallback path
- a clean rerun of `:feature:taskmail:internal:testDebugUnitTest`
- a clean rerun of `:feature:taskmail:internal:detekt`
- a clean rerun of `:feature:taskmail:internal:lintDebug`

This follow-up still does not add fresh device validation for the new raster inline-image preview path.

`spotlessCheck` for `:feature:taskmail:internal` remains blocked by existing module-wide line-ending / formatting drift
outside this slice, so no broad formatting sweep was applied as part of this follow-up.

## 2026-03-16 Slice1 Follow-up

The 2026-03-16 Android/PC documentation-alignment pass synchronized Android docs with the current PC-side canonical protocol.

The 2026-03-16 slice1 follow-up then added new executable evidence:

- a formal-host TaskMail flow regression test covering deep-link workspace entry, workspace to detail navigation, detail back to workspace, and workspace back-stack exit behavior
- a rerun of the narrow launcher / drawer / TaskMail / legacy unit-test slice
- a rerun of `:feature:taskmail:internal:detekt` and `:feature:taskmail:internal:lintDebug`
- a rerun of Thunderbird and K-9 `fossDebug` app assembles

Later on 2026-03-16, a real-device smoke pass on attached Android device model `24090RA29C` advanced the manual evidence boundary for the formal TaskMail entry flow.

That device pass verified:

- the Thunderbird debug APK installs and launches cleanly
- debug-build `app://taskmail/workspace` deep links are currently intercepted by `TaskMailDebugActivity`
- the real in-app drawer `Tasks` entry launches `app.k9mail.feature.launcher.FeatureLauncherActivity`
- the formal TaskMail host reaches the workspace screen, opens session detail, returns to workspace on the first back press, and exits back to `MessageHomeActivity` on the second back press instead of falling through to onboarding

Later in the same 2026-03-16 device session, a live-mail smoke pass against the adjacent PC-side workspace `E:\projects\mail_based_task_manager` advanced the evidence boundary again.

That live-mail pass verified:

- Thunderbird inbox sync on-device surfaces the real live thread `thread_042 / android-reply-892553`
- the formal TaskMail host opens that live session from the real drawer `Tasks` entry
- a plain-text continuation reply sent from TaskMail detail clears the composer only after success and stays anchored to `thread_042`
- mailbox-side evidence for that continuation shows `ACCEPTED -> RUNNING -> DONE` on `[S:thread_042] android-reply-892553`, with final reply text `ANDROID_FOLLOWUP_F0D2C7`
- a follow-up debug-host rerun of the same session confirms the dedicated `/status` button adds an outgoing `/status` timeline row and produces `[STATUS][S:thread_042] android-reply-892553`
- a live single-question session `thread_043 / android-singleq-3889adca` exposes labeled quick answers, and tapping `Ship it` adds an outgoing canonical `approve` row while mailbox-side evidence shows `ACCEPTED -> RUNNING -> DONE` with final reply `Approved.`
- a live multi-question session `thread_044 / android-multiq-60d0f4ab` does not expose quick-answer buttons and instead pre-fills the structured `Answers:` template with `entry_position:` and `icon_strings:`
- a live paused-session rerun on `thread_042` shows paused helper copy plus `Resume and send`, and the actual user mail sent from Android is `/resume` followed by the typed continuation text `Please continue with PAUSED_RESUME_F1B7`; mailbox-side evidence then shows `ACCEPTED -> RUNNING -> DONE` with final reply `PAUSED_RESUME_F1B7`
- a live `thread_026 / 时间线测试` rerun confirms attachment-only continuation on Android, working timeline `Open` / `Save` actions, reply-like `Re: [DONE]...` outgoing rendering, pseudo-attachment suppression for `multipart/alternative` mail without real files, and stable duplicate-collapse behavior around `18:56`, `18:34`, and `18:31`

## 2026-03-16 Slice2 Refresh / Live Update Follow-up

Later on 2026-03-16, the TaskMail refresh/live-update slice added new repository behavior and new narrow validation evidence.

That slice implemented:

- workspace pull-to-refresh on the current TaskMail screen
- a real manual all-account sync request before workspace reload
- automatic workspace reload when local TaskMail mail-store changes arrive
- automatic session-detail reload when local TaskMail mail-store changes arrive
- stale-content retention during refresh failures
- preservation of in-progress detail draft text and selected reply attachments across same-session auto-refresh

The executable evidence added in this follow-up is:

- focused ViewModel reruns for workspace manual refresh, refresh-failure stale-content retention, workspace auto-refresh after local mail change, and detail auto-refresh preserving draft/attachments
- a rerun of `:feature:taskmail:internal:detekt`
- a rerun of `:feature:taskmail:internal:lintDebug`

The earlier local Robolectric-artifact blocker is no longer the active issue for this area.

At the time of the 2026-03-16 slice2 follow-up, a later full rerun of `:feature:taskmail:internal:testDebugUnitTest`
executed `TaskWorkspaceScreenKtTest` and exposed an existing assertion failure in the refresh-warning UI coverage
instead.

No device smoke was performed for this refresh/live-update slice in the current session.

Later device install feedback also established one practical APK-install caveat for future smoke sessions:

- if Android reports that the currently installed app was signed differently or that the existing signature is too old to upgrade in place, uninstall the existing debug package before installing the newly built APK

## 2026-03-16 Slice3 Dual-Mailbox Follow-up

Later on 2026-03-16, the TaskMail dual-mailbox follow-up aligned the Android repository read path with the current PC-side
mailbox topology:

- user mailbox sends to bot mailbox
- Android remains a reply client on user-mailbox TaskMail traffic
- bot-mailbox copies should not become the dominant Android session/detail source when both mailboxes are configured locally
- `[SYNC]` remains a low-input bootstrap action outside TaskMail session projection

That follow-up did not introduce a new Android-only protocol. The reply path remained on the existing TaskMail mail control
plane, preserving normal reply headers and current TaskMail reply semantics.

The repository behavior added or revalidated in this follow-up is:

- repository session aggregation now prefers user-mailbox TaskMail messages when a bot-mailbox copy of the same session also
  exists locally
- the legacy single-mailbox fallback is preserved so older service-side-only setups still surface data
- `[SYNC]` mail is kept outside TaskMail session/detail projection
- focused repository/parser coverage now locks in the dual-mailbox preference and `[SYNC]` exclusion behavior

## 2026-03-17 Slice3 Dual-Mailbox Reply-Target Follow-up

Later on 2026-03-17, the TaskMail dual-mailbox follow-up was extended from repository read-path alignment into reply
transport targeting:

- TaskMail replies now resolve an explicit TaskMail `bot mailbox` destination from Android-side TaskMail configuration
- generic mail reply-recipient heuristics are no longer responsible for TaskMail `To` / `Cc`
- missing, invalid, or non-unique bot-mailbox configuration now fails the TaskMail send path instead of silently
  falling back to source-derived reply recipients

The repository behavior added or revalidated in this follow-up is:

- a TaskMail-level destination provider now exposes the current bot-mailbox target through hidden global storage key
  `taskmail.bot_mailbox_address`
- the TaskMail MIME reply factory now preserves `In-Reply-To`, `References`, and canonical subject identity tokens while
  forcing `To = bot mailbox` and `Cc = empty`
- focused unit coverage now locks in configured-target delivery plus fail-fast behavior for missing and invalid bot
  mailbox configuration
- narrow TaskMail-internal quality checks were re-run clean after the send-target changes

## 2026-03-17 Device Refresh / Live Update Follow-up

Later on 2026-03-17, a user-driven Android device follow-up advanced the manual evidence boundary for the
refresh/live-update slice without adding new code changes.

That device follow-up recorded:

- TaskMail workspace pull-to-refresh succeeds on the installed latest Android build while already loaded cards remain
  visible
- the same offline rerun does not currently surface a visible refresh warning even though loaded content stays on screen
- after sending a reply, Android can already show the newest local outgoing entry for the session
- the backend had not yet produced new server-side feedback during the observation window, so post-sync workspace/session
  summary freshness is still not closed from this pass

## 2026-03-17 Slice4 Guided New-Thread Follow-up

Later on 2026-03-17, the Guided New-Thread MVP follow-up advanced the Android first-task surface from planning-only docs
into targeted repository validation and one focused send-path behavior fix.

That follow-up revalidated or tightened all of the following:

- formal TaskMail-host navigation to a dedicated `NewTask` route
- sender-account handling for zero-account blocking, one-account auto-selection, and multi-account explicit selection
- editable title derivation from the first non-empty `Task:` line
- canonical first-task subject/body serialization, including omission of default optional fields and normalized
  `Acceptance:` items
- dedicated non-reply MIME building to the configured TaskMail bot mailbox without reply headers
- explicit surfacing of missing, invalid, or non-unique bot-mailbox configuration errors instead of collapsing those
  failures into one generic preparation message

The executable evidence added in this follow-up is:

- focused `testDebugUnitTest` reruns for new-task ViewModel, screen, serializer, MIME factory, and real sender behavior
- a clean rerun of `:feature:taskmail:internal:detekt`
- a clean rerun of `:feature:taskmail:internal:lintDebug`

Later on 2026-03-17, a first device smoke pass also advanced the evidence boundary for the guided new-thread slice.

That device follow-up verified:

- `app://taskmail/new-task` opens the dedicated first-task screen on the attached Android device
- the installed build shows the single-account read-only sender field
- `Repo:` and `Task:` input survive a send attempt, and `Title` still tracks the first non-empty `Task:` line on-device
- the send-failure banner on device surfaces the specific message `TaskMail bot mailbox is not configured.` instead of a
  generic preparation error
- the failed send path keeps the current draft visible instead of clearing the form

That same follow-up exposed one environment/configuration caveat rather than a new product bug:

- the initially installed debug build had no default TaskMail bot-mailbox destination configured, so the first real-device
  send attempt could only close the explicit failure-path smoke
- a later local rebuild injected a non-empty debug default bot mailbox and reinstalled Thunderbird

Later in the same 2026-03-17 session, a second guided new-thread device resend advanced the success-path evidence boundary:

- a manual resend of `Codex / Repo: E:/projects/android_task_manager / Task: Audit the new thread smoke TMFIX_0317A`
  reached the bot mailbox as canonical first-task mail with subject `[CX] Audit the new thread smoke TMFIX_0317A`
- the bot-mailbox copy kept the expected minimal canonical body:
  `Repo: E:/projects/android_task_manager` followed by `Task:` and the same one-line task text
- mailbox-side user replies for the Android-originated thread advanced through `[ACCEPTED][S:thread_054] ...`,
  `[RUNNING][S:thread_054] ...`, and `[DONE][S:thread_054] ...`
- `mail_runner.observe show-thread thread_054` ultimately reported `backend=codex`, `repo=E:/projects/android_task_manager`,
  `workdir=.`, `status=done`, `Latest Run Status=success`, and `Exit Code=0`
- the terminal summary for that smoke thread was `No repo files were modified.`
- this closes the minimal live first-task success path for single-account guided new-thread smoke, but multi-account sender
  selection and formal-host entry coverage are still pending

## 2026-03-17 Slice5 Runtime Bot-Mailbox Settings Follow-up

Later on 2026-03-17, the TaskMail bot-mailbox follow-up advanced Android from build-time-only fallback wiring into a
runtime settings path that users can update without reinstalling a debug APK.

That follow-up added or revalidated all of the following:

- a dedicated TaskMail `Settings` route and settings screen inside the formal TaskMail navigation surface
- a new `FeatureLauncherTarget.TaskMailSettings` entry point so the settings screen is reachable from Android
  `General settings`
- a TaskMail-specific settings repository and persistence layer for `taskmail.bot_mailbox_address`
- immediate visibility of saved bot-mailbox updates to TaskMail destination resolution, so reply and first-task sends do
  not depend on an app restart after saving
- explicit UI validation and save feedback for the single bot-mailbox address field

The executable evidence added in this follow-up is:

- focused `testDebugUnitTest` reruns for TaskMail API route contract, launcher target/intent/nav flow wiring, TaskMail
  settings storage/repository behavior, TaskMail settings ViewModel, and TaskMail settings screen behavior
- a clean rerun of `:feature:taskmail:api:detekt`
- a clean rerun of `:feature:taskmail:api:lintDebug`
- a clean rerun of `:feature:launcher:detekt`
- a clean rerun of `:feature:launcher:lintDebug`
- a clean rerun of `:feature:taskmail:internal:detekt`
- a clean rerun of `:feature:taskmail:internal:lintDebug`
- a clean rerun of `:legacy:ui:legacy:detekt`
- a clean rerun of `:legacy:ui:legacy:lintDebug`

No new device smoke was completed for this slice in the same session.

## 2026-03-18 Slice7 Multi-Question Send-Validation Follow-up

Later on 2026-03-18, the TaskMail structured-reply follow-up tightened Android local send gating for multi-question
waits so the client better matches the current PC-side Android reply guidance.

That follow-up added or revalidated all of the following:

- `TaskSessionDetailUiState.canSendReply()` now rejects multi-question sends when any required question remains
  unanswered
- structured drafts that contain unknown `question_id` values are blocked locally instead of being sent
- choice questions accept only canonical wire values, not user-facing display labels
- the legacy two-line `question_id:` then next-line answer format remains supported
- attachment-only multi-question replies remain blocked until valid structured answers are present

The executable evidence added in this follow-up is:

- focused `:feature:taskmail:internal:testDebugUnitTest` coverage for
  `TaskSessionDetailUiStateStructuredReplyValidationTest` plus the updated `TaskSessionDetailViewModelTest`
- a clean rerun of `:feature:taskmail:internal:testDebugUnitTest`
- a clean rerun of `:feature:taskmail:internal:detekt`
- a clean rerun of `:feature:taskmail:internal:lintDebug`

No new device smoke was completed for this slice in the same session.

## 2026-03-18 Device Runtime Bot-Mailbox Settings Follow-up

On 2026-03-18, a user-driven device rerun closed the happy-path smoke for the new Android settings route.

That follow-up verified:

- `General settings -> TaskMail bot mailbox` opens on-device in the installed Thunderbird debug APK
- saving a valid bot-mailbox address through that screen succeeds on-device
- after returning to TaskMail, the next send no longer depends on reinstalling or restarting the app
- the runtime-saved bot-mailbox value is therefore visible to the live send path, matching the focused repository and
  ViewModel coverage added in the prior slice

## 2026-03-18 Slice6 Foreground Refresh Follow-up

Later on 2026-03-18, the TaskMail foreground-refresh follow-up advanced the older refresh/live-update slice from
manual plus observer-driven freshness into a scoped foreground auto-refresh path for the TaskMail surfaces that are
already in the repository.

That follow-up added or revalidated all of the following:

- `TaskMailSyncRequester` now supports requesting sync for one resolved TaskMail account UUID while preserving the
  existing all-account manual-refresh path
- `LegacyTaskMailSyncRequester` now resolves a concrete sender/read account for scoped sync and fails fast when that
  account is no longer available
- workspace and detail contracts/screens now dispatch explicit lifecycle-driven foreground refresh start/stop events
- `TaskMailForegroundRefreshLifecycleEffect`, `TaskMailForegroundRefreshTickerFactory`, and
  `DefaultTaskMailForegroundRefreshTickerFactory` now back a cancellable 10-second loop owned by the corresponding
  TaskMail ViewModel
- workspace foreground refresh only runs when TaskMail sender-account resolution is unambiguous
- detail foreground refresh resolves its sync target from `replyContext.accountUuid`
- the background loop stays separate from user-visible manual refresh spinner/error state and still relies on the
  local mail-store observer path for visible UI reloads

The executable evidence added in this follow-up is:

- focused `:feature:taskmail:internal:testDebugUnitTest` reruns for `TaskWorkspaceViewModelTest` and
  `TaskSessionDetailViewModelTest`
- a clean rerun of `:feature:taskmail:internal:detekt`
- a clean rerun of `:feature:taskmail:internal:lintDebug`
- a later clean rerun of the full `:feature:taskmail:internal:testDebugUnitTest` suite, so the earlier
  `TaskWorkspaceScreenKtTest.kt:152` refresh-warning blocker no longer reproduces in the current executable evidence set

No new device smoke was completed for this slice in the same session.

## 2026-03-19 Rich-Text Body Documentation Alignment

On 2026-03-19, the TaskMail rich-text detail body slice advanced at the documentation/protocol boundary only.

This pass:

- froze Android-side consumer assumptions for HTML detail rendering
- aligned Android planning docs around a controlled rich-text body model
- promoted key freeze points into Android authority docs and current PC-side protocol docs

This pass did not add executable validation evidence.

No Kotlin/runtime code was changed for the rich-text renderer in this session, and no Gradle/device validation was run
for that slice.

## 2026-03-19 Rich-Text Body Batch 1 And Batch 2 Follow-up

Later on 2026-03-19, the TaskMail rich-text detail body slice advanced from documentation-only planning into the first
runtime implementation boundary.

That follow-up added or revalidated all of the following:

- `TaskMailEnvelope` now preserves optional `htmlBody`
- `TaskMailMessage` now preserves optional `htmlBody`
- `LegacyTaskMailMessageSource` now extracts only a real `text/html` MIME part instead of synthesizing HTML from
  `text/plain`
- `TaskMessageBody` now carries `plainTextFallback`, `renderMode`, optional `richDocument`, and optional `sourceHtml`
- repository timeline mapping now preserves `sourceHtml` while keeping `renderMode = PlainTextOnly`
- focused repository coverage now locks in that HTML can survive into the detail body model without changing current
  plain-text rendering behavior

The executable evidence added in this follow-up is:

- a clean rerun of `:feature:taskmail:internal:testDebugUnitTest`
- a clean rerun of `:feature:taskmail:internal:detekt`
- a clean rerun of `:feature:taskmail:internal:lintDebug`

This follow-up does not yet add UI/device validation for controlled HTML projection or rich rendering.

## 2026-03-19 Rich-Text Body Batch 3 And Minimal Batch 4 Follow-up

Later on 2026-03-19, the TaskMail rich-text detail body slice advanced from source/model preservation into controlled
repository projection plus a first-round detail renderer.

That follow-up added or revalidated all of the following:

- `DefaultTaskMailRepository` now projects supported `article.task-mail` HTML into `TaskRichTextDocument`
- `cid:` inline images now bind only through exact attachment `contentId` matching instead of filename/order guesses
- unmatched or external image references now degrade to safe fallback text rather than attempting inline preview
- detail UI state now carries `renderMode` plus optional `richDocument` for each timeline row
- the detail timeline now prefers controlled rich-text blocks for supported paragraph, heading, quote, code, list,
  table, divider, and inline-image placeholder content
- plain-text fallback remains the universal fallback when HTML is absent or cannot be safely projected
- focused repository and screen coverage now locks in `cid:` projection and rich-text rendering without regressing the
  rest of the detail surface

The executable evidence added in this follow-up is:

- a clean rerun of `:feature:taskmail:internal:testDebugUnitTest`
- a clean rerun of `:feature:taskmail:internal:detekt`
- a clean rerun of `:feature:taskmail:internal:lintDebug`
- a clean rerun of `:feature:taskmail:internal:spotlessCheck`

This follow-up still does not add device validation for rich-text body rendering, and inline image/SVG blocks still
render as controlled placeholders rather than true attachment previews.

## 2026-03-19 Rich-Text Body Batch 5 Preview And Summary Follow-up

Later on 2026-03-19, the TaskMail rich-text detail body slice advanced from the minimal Batch 4 renderer into the
planned Batch 5 preview corpus and summary/plain-text regression closeout.

That follow-up added or revalidated all of the following:

- `TaskMailPreviewData` now includes representative detail samples for plain-text-only status mail, HTML mail with inline
  PNG, and HTML mail with static SVG-rich output
- debug `TaskSessionDetailPreview` now exposes dedicated preview entries for the question state, plain-text status,
  inline PNG, and static SVG-rich samples
- focused repository coverage now proves workspace/session summaries remain plain-text based even when supported HTML is
  present on detail timeline mail
- the existing rich-text repository coverage now also asserts the projected detail timeline summary still comes from the
  plain-text path rather than the HTML-only content

The executable evidence added in this follow-up is:

- a clean rerun of `:feature:taskmail:internal:testDebugUnitTest`
- a clean rerun of `:feature:taskmail:internal:detekt`
- a clean rerun of `:feature:taskmail:internal:lintDebug`
- a clean rerun of `:feature:taskmail:internal:spotlessCheck`

This follow-up still does not add device validation for rich-text body rendering, and inline image/SVG blocks still
render as controlled placeholders rather than true attachment previews.

## 2026-03-19 Rich-Text Detail Live Device Placeholder Validation

Later on 2026-03-19, a focused live-device validation pass was completed for the current rich-text detail renderer on
the connected Xiaomi/MIUI device using the existing installed `net.thunderbird.android.debug` app state.

Important validation boundary for this pass:

- attempting `:app-thunderbird:installFossDebug` failed with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`
- the existing debug install was intentionally preserved so the already-synced live TaskMail corpus would not be wiped
- therefore this evidence reflects the currently installed debug app rather than a freshly installed APK from the
  current repo build

That device pass confirmed all of the following on live TaskMail data:

- opening the `shaking_table_executor` live session `thread_071` still reaches Task Detail without blank content or
  renderer failure
- the `2026-03-19 17:49` HTML-rich `DONE` message remains readable in Task Detail on-device
- inline body images currently surface through the expected placeholder text path rather than a true thumbnail preview:
  `Artifacts:`, `rotated.jpg (inline preview)`, `[Image Preview] rotated.jpg`, `rotated270.jpg (inline preview)`, and
  `[Image Preview] rotated270.jpg`
- workspace/session cards remain plain-text summary driven rather than switching to HTML-rich rendering
- ordinary non-inline attachment rows in the same session still render separately with `Open` and `Save` actions

This is focused but concrete device evidence for the current placeholder-only inline-image scope. It does not yet close
broader device coverage for static SVG fallback, unsupported-image edge cases, or a freshly installed current-build APK.

## 2026-03-19 Rich-Text Detail Fresh-Build Controlled Device Preview Validation

Later on 2026-03-19, a second device pass validated the latest repo build on the same connected Xiaomi/MIUI device
through controlled debug-preview routes rather than a live mailbox thread.

Important validation boundary for this pass:

- the previously preserved live-data install was explicitly removed after user approval because
  `INSTALL_FAILED_UPDATE_INCOMPATIBLE` blocked current-build installation
- direct `adb install` of the freshly built APK then still failed with `INSTALL_FAILED_USER_RESTRICTED`
- the practical workaround on this device was to push
  `app-thunderbird/build/outputs/apk/foss/debug/app-thunderbird-foss-debug.apk` to `/sdcard/Download/` and complete
  installation manually on-device
- this evidence therefore reflects a freshly installed current-build APK, but only through controlled debug-preview
  samples rather than live mailbox data or attachment-row parity

That fresh-build device pass confirmed all of the following:

- `app://taskmail/preview/detail/static-svg` opens on-device without renderer failure and keeps the Task Detail readable
- the static SVG case shows `Formula output`, `The backend produced a static SVG preview for the latest result.`, and a
  placeholder card labeled `Inline SVG` with `Static SVG formula preview`
- `app://taskmail/preview/detail/unmatched-image-fallback` opens on-device without renderer failure and keeps the Task
  Detail readable
- the unmatched/external-image fallback case shows `Remote preview fallback`, `Android ignored the unmatched external
  image reference and kept the detail readable.`, and `External chart preview unavailable.`
- the unmatched-image fallback case does **not** show `Inline image` or `Inline SVG`, confirming safe text fallback
  rather than placeholder-card chrome
- these controlled current-build previews still do not provide a true bitmap image/SVG preview

## Current Session Commands

The following commands were run in the captured executable validation session with:

- `JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot`

Local environment pitfall for future reruns:

- fresh PowerShell sessions on this workstation may still resolve Gradle to Java 11 through
  `C:\Users\Administrator\.gradle\gradle.properties`
- if that happens, explicitly re-export `JAVA_HOME` to the Adoptium 21 path above before any TaskMail Gradle command

Local device-input pitfall for future reruns:

- on the attached Xiaomi/MIUI device, repeated `adb shell input text ...` attempts during TaskMail form entry can switch
  the foreground app to `com.android.quicksearchbox/.SearchActivityTransparent`
- prefer manual user text entry for long-form `Repo:` / `Task:` guided new-thread smoke on this device

Local device-install pitfall for future reruns:

- on this Xiaomi/MIUI device, even after uninstalling an incompatible debug app, direct `adb install` can still fail
  with `INSTALL_FAILED_USER_RESTRICTED`
- when that happens, push the APK to `/sdcard/Download/` and complete installation manually from the device UI

### Passed

```text
.\gradlew.bat :feature:launcher:testDebugUnitTest --tests "app.k9mail.feature.launcher.navigation.FeatureLauncherNavHostTaskMailFlowTest"
.\gradlew.bat :feature:launcher:testDebugUnitTest :feature:navigation:drawer:dropdown:testDebugUnitTest :feature:taskmail:internal:testDebugUnitTest :feature:taskmail:api:testDebugUnitTest :legacy:ui:legacy:testDebugUnitTest
.\gradlew.bat :feature:taskmail:internal:detekt :feature:taskmail:internal:lintDebug
.\gradlew.bat :feature:taskmail:internal:detekt :feature:taskmail:internal:lintDebug :feature:taskmail:internal:testDebugUnitTest --tests "net.thunderbird.feature.taskmail.internal.ui.workspace.TaskWorkspaceViewModelTest" --tests "net.thunderbird.feature.taskmail.internal.ui.detail.TaskSessionDetailViewModelTest"
.\gradlew.bat :app-k9mail:assembleFossDebug :app-thunderbird:assembleFossDebug
.\gradlew.bat :app-thunderbird:assembleFossDebug
.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest --tests "net.thunderbird.feature.taskmail.internal.data.DefaultTaskMailRepositoryDualMailboxTest" --tests "net.thunderbird.feature.taskmail.internal.data.DefaultTaskMailRepositoryTest" --tests "net.thunderbird.feature.taskmail.internal.domain.parser.TaskMailMessageDetectorTest" :feature:taskmail:internal:detekt :feature:taskmail:internal:lintDebug
.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest --tests "net.thunderbird.feature.taskmail.internal.data.StorageBackedTaskMailDestinationAddressProviderTest" --tests "net.thunderbird.feature.taskmail.internal.data.LegacyTaskMailMimeMessageFactoryTest" :feature:taskmail:internal:detekt :feature:taskmail:internal:lintDebug
.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest --tests "net.thunderbird.feature.taskmail.internal.ui.newtask.TaskNewTaskViewModelTest" --tests "net.thunderbird.feature.taskmail.internal.ui.newtask.TaskNewTaskScreenKtTest" --tests "net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskBodySerializerTest" --tests "net.thunderbird.feature.taskmail.internal.data.LegacyTaskMailNewTaskMimeMessageFactoryTest" --tests "net.thunderbird.feature.taskmail.internal.domain.newtask.RealTaskMailNewTaskSenderTest"
.\gradlew.bat :feature:taskmail:internal:detekt :feature:taskmail:internal:lintDebug
.\gradlew.bat :feature:taskmail:api:testDebugUnitTest :feature:launcher:testDebugUnitTest :feature:taskmail:internal:testDebugUnitTest :legacy:ui:legacy:testDebugUnitTest
.\gradlew.bat :feature:taskmail:api:lintDebug :feature:taskmail:api:detekt :feature:launcher:lintDebug :feature:launcher:detekt :feature:taskmail:internal:lintDebug :feature:taskmail:internal:detekt :legacy:ui:legacy:lintDebug :legacy:ui:legacy:detekt
.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest --tests "*TaskWorkspaceViewModelTest" --tests "*TaskSessionDetailViewModelTest"
.\gradlew.bat :feature:taskmail:internal:detekt :feature:taskmail:internal:lintDebug
.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest
.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest :feature:taskmail:internal:detekt :feature:taskmail:internal:lintDebug --continue
.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest
.\gradlew.bat :feature:taskmail:internal:detekt
.\gradlew.bat :feature:taskmail:internal:lintDebug
.\gradlew.bat :feature:taskmail:internal:spotlessCheck
.\gradlew.bat :feature:taskmail:internal:spotlessApply
.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest :feature:taskmail:internal:detekt :feature:taskmail:internal:lintDebug :feature:taskmail:internal:spotlessCheck --continue
```

### Later Broader Rerun Now Clean

```text
.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest
```

As of the later 2026-03-18 rerun, the full TaskMail-internal module suite now passes cleanly.

For audit/history:

- the earlier 2026-03-17 rerun had exposed `TaskWorkspaceScreenKtTest > content should show refresh warning without hiding workspace list`
- that refresh-warning assertion at `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/ui/workspace/TaskWorkspaceScreenKtTest.kt:152`
  no longer reproduces in the current executable evidence set

### Device Smoke Evidence

The 2026-03-16 device pass additionally used adb to:

- confirm the attached device with `adb devices -l`
- install `app-thunderbird/build/outputs/apk/foss/debug/app-thunderbird-foss-debug.apk`
- observe foreground activity transitions with `adb shell dumpsys activity activities`
- verify UI states with `adb shell uiautomator dump ...` and `adb shell screencap -p ...`

Install caveat for future reruns:

- some devices may refuse to install a new debug APK over an older local install if the signing key differs; uninstall the old debug app first in that case

The meaningful observed transitions were:

- `MessageHomeActivity` drawer `Tasks` click -> `FeatureLauncherActivity`
- TaskMail workspace -> TaskMail session detail -> TaskMail workspace
- TaskMail workspace back -> `MessageHomeActivity`
- real inbox sync -> visible live thread `[DONE][S:thread_042] android-reply-892553`
- formal-host TaskMail detail send -> outgoing continuation on `thread_042` -> mailbox-side `ACCEPTED/RUNNING/DONE`
- debug-host TaskMail detail `/status` click -> outgoing `/status` timeline row -> mailbox-side `[STATUS][S:thread_042] ...`
- debug-host single-question detail `thread_043` -> labeled quick-answer tap `Ship it` -> outgoing canonical `approve` -> mailbox-side `ACCEPTED/RUNNING/DONE`
- debug-host multi-question detail `thread_044` -> no quick-answer section -> prefilled `Answers:` template
- debug-host paused detail `thread_042` -> paused helper copy and `Resume and send` -> Android sends `/resume` continuation -> mailbox-side `ACCEPTED/RUNNING/DONE`
- debug-host `thread_026` attachment rerun -> reply attachment selection disables `/status` -> outgoing `2026-03-16 19:34` attachment card appears -> `Open` launches Android viewer flow and `Save` launches Android document-picker flow

### Previously Attempted but Not Clean

The latest repo-wide formatting attempt remains:

```text
.\gradlew.bat spotlessCheck
```

`spotlessCheck` failed because the repository already contains broad unrelated formatting and line-ending violations outside this TaskMail slice, including existing files such as:

- `README.md`
- `build-plugin/build.gradle.kts`
- `app-common/build.gradle.kts`

This task was not re-run in the 2026-03-16 slice1 follow-up, so the captured evidence still does **not** newly certify a repo-wide formatting-clean state.

## Validation Matrix

| Capability area | Current executable evidence | This-session status | Remaining gap |
| --- | --- | --- | --- |
| Real mail read path, logical session aggregation, and protocol-aware parsing | `DefaultTaskMailRepositoryTest`, `DefaultTaskMailRepositoryLifecycleCompatibilityTest`, `DefaultTaskMailRepositoryDualMailboxTest`, `LegacyTaskMailBodyExtractorTest`, `TaskMailMessageDetectorTest`, `TaskStateCapsuleParserTest`, `TaskQuestionCapsuleParserTest`, `TaskMailSubjectParserTest`, `TaskMailReplySubjectBuilderTest`, `TaskMessageAttachmentDisplayPolicyTest` | Revalidated in `:feature:taskmail:internal:testDebugUnitTest`, including reply-like direction recovery, dual-mailbox user-mailbox preference, `[SYNC]` staying outside TaskMail session projection, `[PAUSED]` / `paused_from_status` parsing, lifecycle / `last_active_at` / `last_progress_at` parser and repository preservation, flattened capsule parsing, multipart container filtering, `Message-ID` duplicate collapse, and closely timed attachment-duplicate collapse | JSON replay helper exists but was not re-run against an external dataset in this session |
| Formal host route contract | `TaskMailNavigationTest`, `FeatureLauncherNavHostTaskMailCallbackTest`, `FeatureLauncherNavHostTaskMailFlowTest`, `FeatureLauncherTargetTest` | Revalidated in `:feature:launcher:testDebugUnitTest` and `:feature:taskmail:api:testDebugUnitTest`, including formal-host repo-selection callback coverage that stores the selected repo result before popping back to `New task`; a prior device rerun also manually re-smoked the drawer entry path, including real `FeatureLauncherActivity` workspace launch, workspace -> detail, detail -> workspace back behavior, and workspace exit back to `MessageHomeActivity` rather than onboarding | Direct shell deep-link launch of the formal host is still not available in debug builds because the debug manifest owns `app://taskmail/*`; use the in-app drawer for live host smoke |
| Drawer `Tasks` entry plumbing and launcher handoff | `DrawerViewModelTest`, `DrawerViewKtTest`, `MessageHomeTaskMailNavigationTest` | Revalidated in `:feature:navigation:drawer:dropdown:testDebugUnitTest` and `:legacy:ui:legacy:testDebugUnitTest`, then manually re-smoked on device by clicking the drawer `Tasks` entry and observing the handoff into `FeatureLauncherActivity` | Broader live TaskMail reply/attachment smoke remains outside this narrow navigation-path rerun |
| Workspace refresh and live update | `RefreshTaskMail`, `ObserveTaskMailStoreChanges`, `TaskMailForegroundRefreshLifecycleEffect`, `TaskWorkspaceViewModelTest`, `TaskSessionDetailViewModelTest`, `TaskWorkspaceScreenKtTest`, `TaskSessionDetailScreenKtTest` | Revalidated in focused ViewModel reruns for manual refresh, foreground loop start/stop, account-targeted sync resolution, stale-content retention, local-change reload, and detail draft/attachment preservation, plus clean `:feature:taskmail:internal:detekt` and `:feature:taskmail:internal:lintDebug`; a later 2026-03-18 rerun of the full `:feature:taskmail:internal:testDebugUnitTest` suite is now clean; later live closeout then covered post-sync workspace/detail summary freshness, and a later smoke pass confirmed draft / attachment retention while editing | Refresh-failure warning visibility and wider passive foreground-refresh smoke are currently deferred rather than blocking the frozen Phase 3 boundary |
| Dual-mailbox reply target | `StorageBackedTaskMailDestinationAddressProviderTest`, `LegacyTaskMailMimeMessageFactoryTest` | Revalidated in a focused `:feature:taskmail:internal:testDebugUnitTest` rerun plus clean `:feature:taskmail:internal:detekt` and `:feature:taskmail:internal:lintDebug`, including explicit bot-mailbox `To` targeting, empty `Cc`, preserved reply headers/subject tokens, and fail-fast behavior for missing or invalid bot-mailbox configuration | Real mailbox smoke that proves Android now delivers TaskMail replies to a configured bot mailbox is still pending |
| Runtime bot-mailbox settings | `TaskMailNavigationTest`, `FeatureLauncherTargetTest`, `FeatureLauncherActivityIntentTest`, `FeatureLauncherNavHostTaskMailFlowTest`, `StorageBackedTaskMailDestinationAddressProviderTest`, `DefaultTaskMailBotMailboxSettingsRepositoryTest`, `TaskMailSettingsViewModelTest`, `TaskMailSettingsScreenKtTest` | Revalidated in focused `:feature:taskmail:api:testDebugUnitTest`, `:feature:launcher:testDebugUnitTest`, `:feature:taskmail:internal:testDebugUnitTest`, and `:legacy:ui:legacy:testDebugUnitTest` reruns plus clean `:feature:taskmail:api:detekt`, `:feature:taskmail:api:lintDebug`, `:feature:launcher:detekt`, `:feature:launcher:lintDebug`, `:feature:taskmail:internal:detekt`, `:feature:taskmail:internal:lintDebug`, `:legacy:ui:legacy:detekt`, and `:legacy:ui:legacy:lintDebug`, including formal settings-route wiring, Android general-settings entry plumbing, trimmed save behavior, build-default fallback display, and immediate post-save visibility to TaskMail send-path destination resolution without restart; a 2026-03-18 device rerun then confirmed the save-then-send path on the installed Thunderbird debug APK | Happy-path device smoke is now closed; invalid-address and clear-address edge behavior still relies on focused automated coverage rather than broad device sweeps |
| Guided new-thread MVP | `TaskMailNavigationTest`, `TaskNewTaskViewModelTest`, `TaskNewTaskScreenKtTest`, `TaskMailNewTaskSubjectBuilderTest`, `TaskMailNewTaskBodySerializerTest`, `SendTaskMailNewTaskTest`, `GetTaskMailSenderAccountsTest`, `LegacyTaskMailSenderAccountSourceTest`, `LegacyTaskMailNewTaskMimeMessageFactoryTest`, `RealTaskMailNewTaskSenderTest` | Revalidated in a focused `:feature:taskmail:internal:testDebugUnitTest` rerun plus clean `:feature:taskmail:internal:detekt` and `:feature:taskmail:internal:lintDebug`, including zero / one / multiple sender-account states, title derivation from the first non-empty `Task:` line, canonical `[OC]` / `[CX]` subject plus body serialization, non-reply bot-mailbox `To` targeting without reply headers, and user-visible surfacing of missing or invalid bot-mailbox configuration; later device smoke also closed the single-account success path through canonical bot-mailbox delivery plus mailbox-side `[ACCEPTED] -> [RUNNING] -> [DONE]` on `thread_054`, and a later formal-host live pass confirmed `New task` latest-evidence review survives recent-tasks cold start | Current manual/device smoke is still missing for verifying the explicit multi-account sender-selection path on-device and for raw first-task body verification on a live mailbox |
| Phase 4 `new task` durable direct evidence | `RunTaskMailDirectOrFallbackTest`, `TaskNewTaskViewModelTest`, `TaskNewTaskScreenKtTest`, `RelayTaskMailDirectNewTaskSenderTest`, `RelayProtocolJsonCodecTest`, `FileBackedTaskMailNewTaskSendRecordRepositoryTest`, `TaskMailDirectSendEvidence`, `TaskMailNewTaskSendRecordRepository`, `TaskNewTaskDirectEvidenceCard` | Revalidated in focused `:feature:taskmail:internal:testDebugUnitTest` reruns plus clean `:feature:taskmail:internal:detekt` and `:feature:taskmail:internal:lintDebug`, including machine-readable `outcome` / `switchGate` / optional `requestId` / `receiptId` / optional `transportMessageId` evidence, sender-account-scoped persistence of the latest `new task` direct-or-fallback result, evidence rehydration after sender-account resolution or reselection, Android-side `packet_ack` hard-rejection / fallback classification coverage, a later documentation reconciliation that maps the current Android evidence into the shared Phase 4 parity / mismatch / rollback artifacts, a stable in-repo `New task` review surface for the latest persisted evidence, a later live fallback sample on `thread_093` that stayed reviewable after screen reload plus recent-tasks cold start, a later same-run summary readout that positively closes `thread_083`, `thread_093`, and `thread_094` while relegating `thread_084` to a historical retained-artifact gap, a later relay re-provision / fresh direct closeout on `thread_095` that revalidated `hello_ack -> DirectAccepted` plus PC `canonical_summary.json` consumption, a later formal-host fresh closeout rerun on `thread_097` that generated `taskmail_daily_closeout_bundle.json` and closed strong `transport_message_id` bind against PC canonical outcome, a subsequent formal-host rerun on `thread_098` that closed `request_id`-first bind on the current installed build, and a later Android code-path verdict rerun that confirmed no standalone `new_task` activation/config flag is currently needed to keep the guarded direct boundary scoped to formal-host `new_task` | The first Android-side shared-artifact readout now exists, the latest persisted evidence now has live-device review proof after screen reload plus recent-tasks cold start, same-run summary parity now has positive samples for `thread_083`, `thread_093`, `thread_094`, `thread_095`, and later `thread_098`, the frozen `daily_closeout_bundle` workflow has been re-used on fresh samples, the narrow `request_id`-first bind blocker is now closed, and the current Android verdict is to keep code unchanged rather than adding another `new_task` activation/config switch |
| Phase 5 `reply` / `/status` guarded direct durable evidence | `FileBackedTaskMailSessionActionSendRecordRepositoryTest`, `TaskSessionDetailViewModelTest`, `TaskSessionDetailScreenKtTest`, `TaskMailSessionActionSendRecord`, `TaskMailSessionActionSendRecordRepository`, `TaskSessionDetailDirectEvidenceCard` | Revalidated in focused `:feature:taskmail:internal:testDebugUnitTest` reruns plus clean `:feature:taskmail:internal:detekt` and `:feature:taskmail:internal:lintDebug`, including latest post-creation session-action record persistence keyed by canonical `workspace_id + session_id`, detail-side rehydration of the latest direct `reply` / `/status` evidence, stable review-surface rendering for `outcome` / `switchGate` / `bootstrapStatus` plus optional request or fallback fields, continued separation between direct accepted state and final canonical mail outcome, and a later 2026-03-23 formal-host live/manual closeout on `thread_019` plus `thread_020` that confirmed detail reload recovery and formal-host desktop-launch cold-start recovery of the latest evidence card | Live mailbox / device persistence closeout is now positively evidenced on the current installed build, but same-run shared-artifact strong bind against canonical mail outcome is still open because Android fallback session-action records do not yet carry `requestId` / `transportMessageId`, and the current PC post-creation fallback canonical artifacts still omit `action_type`, `target_session_identity`, and action-specific ingress anchors |
| Bootstrap discovery and repo-path handoff | `TaskMailNavigationTest`, `FeatureLauncherNavHostTaskMailCallbackTest`, `FeatureLauncherNavHostTaskMailFlowTest`, `TaskMailProjectSyncResultParserTest`, `TaskProjectSyncViewModelTest`, `TaskNewTaskViewModelTest`, `TaskNewTaskScreenKtTest` | Revalidated in focused `:feature:taskmail:api:testDebugUnitTest`, `:feature:launcher:testDebugUnitTest`, and `:feature:taskmail:internal:testDebugUnitTest` reruns plus clean `:feature:launcher:detekt`, `:feature:launcher:lintDebug`, `:feature:taskmail:internal:detekt`, and `:feature:taskmail:internal:lintDebug`, including dedicated `Project list` routing, `[SYNC] Project Folder List` parsing, zero / one / multiple sender-account handling on the discovery screen, explicit sync request plus refresh, and formal-host `Use this repo` handoff coverage that prefills `Repo:` in `New task` | Current manual/device smoke is still missing for issuing `[SYNC]` from the formal TaskMail host, rendering the returned project list on-device, and verifying `Use this repo` round-trips back into the composer while `[SYNC]` stays outside TaskMail session/detail projection |
| Session detail reply surface | `SendTaskMailReplyTest`, `TaskMailReplyBodySerializerTest`, `RealTaskMailReplySenderTest`, `TaskSessionDetailViewModelTest`, `TaskSessionDetailUiStateStructuredReplyValidationTest`, `TaskSessionDetailScreenKtTest` | Revalidated in `:feature:taskmail:internal:testDebugUnitTest`, including plain-text reply, attachment-only continuation reply, `/status`, single-question quick answers, multi-question structured template gating, local blocking of incomplete required answers, unknown `question_id`, and non-canonical choice values, plus paused-session `/resume` prefixing; later manually re-smoked on-device against live threads `thread_042`, `thread_043`, `thread_044`, and `thread_026`, where plain-text continuation stayed anchored to `thread_042`, `/status` produced `[STATUS][S:thread_042] ...`, single-question quick answer `Ship it` sent canonical `approve`, multi-question detail exposed the `Answers:` template without quick-answer shortcuts, paused-session send emitted an actual `/resume` continuation before completing successfully, and attachment-only continuation on `thread_026` succeeded with mailbox-side evidence on the existing session thread | Dedicated UI for protocol-superset fields/commands remains intentionally absent; live coverage is still concentrated on a few known threads rather than a broad corpus |
| Timeline attachment UX | `TaskSessionDetailViewModelTest`, `TaskSessionDetailScreenKtTest`, `TaskMessageAttachmentDisplayPolicyTest` | Revalidated in `:feature:taskmail:internal:testDebugUnitTest`, including timeline attachment metadata mapping, open/save effect plumbing, reply attachment selection/removal, and `/status` blocked while reply attachments are selected; later manually re-smoked on-device against live `thread_026 / 时间线测试`, where selecting a reply attachment disabled `/status` and allowed attachment-only send, the outgoing `2026-03-16 19:34` card exposed `Open` and `Save`, `Open` launched Android `ResolverActivity` through `ACTION_VIEW`, `Save` launched DocumentsUI `PickActivity` through `ACTION_CREATE_DOCUMENT`, historical `multipart/alternative` reply-like mail at `23:24` and `22:49` showed no pseudo-attachment rows, and the historical `18:56`, `18:34`, and `18:31` outgoing timestamps each appeared once | Broader device coverage beyond these focused live threads remains limited |
| Rich-text detail representative corpus | `TaskMailRichTextRepresentativeSampleTest`, `TaskSessionDetailRepresentativeSamplesTest`, `TaskMailPreviewData`, `TaskMailDebugPreviewDetail` | Revalidated in the 2026-03-21 full `:feature:taskmail:internal:testDebugUnitTest` rerun plus clean `:feature:taskmail:internal:detekt` and `:feature:taskmail:internal:lintDebug`, including controlled representative coverage for `External Deliveries`, `Attachment Notices`, and long-error `FAILED` mail seeded from current PC-side samples such as `thread_072/raw_020.json`, `thread_075/raw_138.json`, and `thread_051/raw_030.json` | This is still controlled preview/test-corpus evidence, not broad live-mailbox/device validation for the richer rich-text corpus |
| Multi-question compatibility boundary | `DefaultTaskMailRepositoryTest`, `TaskQuestionCapsuleParserTest`, `TaskSessionDetailViewModelTest`, `TaskSessionDetailUiStateStructuredReplyValidationTest`, `TaskSessionDetailScreenKtTest` | Revalidated in `:feature:taskmail:internal:testDebugUnitTest`, with flattened multi-question capsule parsing coverage, structured `Answers:` send gating, required-answer completeness checks, unknown-`question_id` rejection, canonical-choice-only validation, and legacy two-line structured-answer compatibility; later manually re-smoked on-device against live thread `thread_044`, where quick-answer shortcuts were absent and the composer prefilled `Answers:` with one line per question id | Sending a live multi-question structured reply was not re-run in this session |
| TaskMail-internal quality gate | `:feature:taskmail:internal:detekt`, `:feature:taskmail:internal:lintDebug`, `:feature:taskmail:internal:testDebugUnitTest` | Revalidated clean in the 2026-03-16 slice1 follow-up, later focused follow-ups, and again in the 2026-03-18 foreground-refresh follow-up, where `detekt`, `lintDebug`, focused workspace/detail reruns, and the full TaskMail-internal unit/screen suite all passed | Repo-wide quality gates and formatting are still not fully closed |
| Debug validation path | `TaskMailDebugActivity` wiring, `TaskMailNavHostBackCallbackTest`, `docs/TASKMAIL-DEBUG-VALIDATION.md` | Back-stack exit fallback revalidated in `:feature:taskmail:internal:testDebugUnitTest`; a 2026-03-16 device pass also confirmed that debug-build `app://taskmail/workspace` launches `TaskMailDebugActivity` rather than the formal launcher host | Full debug-host device flow still pending beyond routing observation |

## Targeted Manual Follow-up

Later in the same 2026-03-16 slice1 follow-up, a focused live rerun on `thread_026 / 时间线测试` captured stronger device-side evidence than the earlier informal "looks normal" check.

That rerun confirmed all of the following on the Thunderbird debug APK:

- attachment-only continuation can be sent from Android while `/status` stays disabled during reply-attachment selection
- the live outgoing `2026-03-16 19:34` attachment card exposes working `Open` and `Save` actions through Android viewer / document-picker flows
- reply-like `Re: [DONE][S:thread_026] ...` user mails at `23:24`, `22:49`, `18:56`, `18:34`, and `18:31` render as `Outgoing`, not `System`
- `multipart/alternative` reply-like mail without real files does not surface pseudo-attachment rows in the timeline
- the historical `18:56`, `18:34`, and `18:31` outgoing entries each appear once, matching the intended duplicate-collapse behavior

Treat this as focused but concrete device smoke evidence. It still does not replace a broader corpus-level device sweep.

## 2026-03-17 Manual Device Notes

The latest Android-side follow-up provided these additional manual observations:

- item 14 success path is now device-confirmed: pull-to-refresh completed while existing workspace content stayed visible
- an offline rerun did not show a refresh warning, so the failure-path warning behavior remains open
- item 15 has only partial evidence so far: after sending a reply, Android showed the latest outgoing entry locally, but
  no server feedback had arrived yet, so refreshed workspace/session summaries were not confirmed in this pass
- items 16-18 were intentionally deferred and remain pending

## Minimum Smoke Checklist

The following checklist remains the minimum manual smoke set for the current TaskMail Android slice.

Items 1-13 were completed on 2026-03-16.

1. Open TaskMail through the formal in-app entry path and verify the workspace screen is reachable without adb/deep-link tooling. Completed on 2026-03-16 via the drawer `Tasks` entry.
2. Open TaskMail through the drawer `Tasks` entry and verify it lands on the same workspace host path. Completed on 2026-03-16.
3. Open a session detail screen, press back once, and verify it returns to workspace rather than exiting unexpectedly. Completed on 2026-03-16.
4. On a reply-capable session, send a plain-text reply and verify the composer clears only after success. Completed on 2026-03-16 with live thread `thread_042 / android-reply-892553`; the composer cleared after send and mailbox-side evidence stayed on `[S:thread_042]`.
5. Trigger `/status` from detail and verify it uses the same existing session thread. Completed on 2026-03-16 with live thread `thread_042`; the device timeline showed an outgoing `/status` row and mailbox-side evidence returned `[STATUS][S:thread_042] android-reply-892553`.
6. On a single-question session, verify a quick answer is available and the UI sends the canonical choice value rather than the display label. Completed on 2026-03-16 with live thread `thread_043 / android-singleq-3889adca`; the device showed labeled quick answers `Ship it` and `Not yet`, the timeline added outgoing `approve`, and mailbox-side evidence completed `ACCEPTED -> RUNNING -> DONE`.
7. On a multi-question session, verify quick-answer shortcuts are not shown and the structured `Answers:` template is used instead. Completed on 2026-03-16 with live thread `thread_044 / android-multiq-60d0f4ab`; the device showed no quick-answer section and prefilled `Answers:` with `entry_position:` and `icon_strings:`.
8. On a `paused` session, verify the primary send path clearly communicates resume semantics and prepends `/resume`. Completed on 2026-03-16 with live thread `thread_042`; the device showed paused helper copy and `Resume and send`, and mailbox-side raw user mail captured `/resume` followed by `Please continue with PAUSED_RESUME_F1B7`.
9. Add one or more reply attachments and verify attachment-only free-text continuation can be sent, but `/status` is blocked while attachments are selected. Completed on 2026-03-16 with live thread `thread_026 / 时间线测试`; selecting `taskmail-attachment-smoke-20260316.txt` disabled `/status`, enabled `Send reply`, and produced mailbox-side raw user mail `Re: [DONE] [S:thread_026] 时间线测试` with empty body plus that attachment.
10. On a thread with real timeline attachments, verify `Open` and `Save` actions work through Android viewers / document picker flows. Completed on 2026-03-16 with live `thread_026`; the outgoing `2026-03-16 19:34` attachment card launched Android `ResolverActivity` via `ACTION_VIEW` for `Open` and DocumentsUI `PickActivity` via `ACTION_CREATE_DOCUMENT` for `Save`.
11. On a thread with reply-like `Re: [DONE]...` or `Re: [PAUSED]...` user mail, verify those entries render as outgoing cards rather than system cards. Completed on 2026-03-16 with live `thread_026`; reply-like `Re: [DONE][S:thread_026] 时间线测试` mail at `23:24`, `22:49`, `18:56`, `18:34`, and `18:31` rendered as `Outgoing`.
12. On a thread with status-mail MIME containers but no real files, verify timeline attachment rows do not show `multipart/alternative` pseudo-attachments. Completed on 2026-03-16 with live `thread_026`; mailbox-side raw files `raw_053.json` and `raw_049.json` were `multipart/alternative` with `attachments: []`, while the matching device timeline cards showed no attachment rows or `multipart/alternative` placeholder text.
13. On `thread_026 / 时间线测试`, verify older duplicate outgoing cards do not reappear around the historical `18:56`, `18:34`, and `18:31` timestamps. Completed on 2026-03-16; the device timeline showed each timestamp exactly once.
14. Pull down on the TaskMail workspace and verify the refresh indicator appears while already loaded workspace cards remain visible. Success path completed on 2026-03-17; device refresh succeeded and existing workspace content stayed visible. Failure-path warning closeout is still open because an offline rerun did not surface a visible warning.
15. Keep the TaskMail workspace visible long enough for a foreground refresh tick, or manually trigger refresh, then verify updated workspace/session summaries appear after newer mail lands and sync completes. Partially observed on 2026-03-17; after sending a reply, Android showed the newest local outgoing entry, but no backend feedback arrived during the test window, so refreshed workspace/session summaries remain pending.
16. Keep a partially written detail reply and one selected reply attachment, then let a local mail update or foreground refresh land and verify the detail timeline refreshes without clearing the draft or selected attachments. Pending current real-device smoke.
17. Configure both the user mailbox and the bot mailbox on-device, then verify TaskMail workspace/detail prefers the user-mailbox copy of a session and keeps reply enabled on the user account rather than surfacing duplicate or reply-disabled cards from the service mailbox. Pending current real-device smoke.
18. Trigger or replay a `[SYNC]` bootstrap mail and verify it stays outside TaskMail workspace/session/detail projection while remaining available through the normal mailbox view if needed. Pending current device or replay validation.
19. From the formal TaskMail workspace host, open `Project list`, trigger or refresh `[SYNC]`, and verify the returned project folders render on-device without creating TaskMail workspace/session/detail cards. Pending current manual/device smoke.
20. Tap `Use this repo` from the project list and verify the dedicated `New task` screen returns with `Repo:` prefilled while the field remains editable. Pending current manual/device smoke.
21. Open Android `General settings -> TaskMail bot mailbox`, save a valid mailbox address, return to TaskMail, and verify the next first-task or reply send uses the new destination without reinstalling or restarting the app. Completed on 2026-03-18.
22. On a live thread with HTML mail containing `cid:` inline images, verify Task Detail stays readable, the current
    inline-image placeholder path appears instead of blank content, and workspace/session cards remain plain-text
    summary driven. Completed on 2026-03-19 with live `thread_071`; the `2026-03-19 17:49` detail message showed
    `Artifacts:` plus `rotated.jpg (inline preview)` / `[Image Preview] rotated.jpg` and the matching `rotated270.jpg`
    placeholder lines, while workspace/session cards still used plain-text summaries.
23. On the latest debug build, open a controlled static SVG detail sample and verify the renderer shows readable rich
    content plus the expected placeholder card rather than blank content. Completed on 2026-03-19 via
    `app://taskmail/preview/detail/static-svg`; the device showed `Formula output`, the static SVG explanatory
    paragraph, `Inline SVG`, and `Static SVG formula preview`.
24. On the latest debug build, open a controlled unmatched/external-image fallback detail sample and verify the
    renderer degrades to safe text fallback without showing `Inline image` / `Inline SVG` chrome. Completed on
    2026-03-19 via `app://taskmail/preview/detail/unmatched-image-fallback`; the device showed `Remote preview
    fallback`, the explanatory paragraph, and `External chart preview unavailable.` with no inline-preview label.

## Still Not Revalidated in the Captured Session

The following areas remain outside the current executable validation boundary:

- full repo-wide build / assemble tasks
- broader live-mailbox device validation for controlled rich-text detail rendering beyond the targeted `thread_071`
  inline-image placeholder pass and the controlled debug-preview static SVG / unmatched-image fallback checks
- fresh device validation for the new raster inline-image preview path, plus any future true SVG attachment preview
  beyond the current placeholder-card path
- `connectedAndroidTest`
- full device or emulator smoke validation beyond the 2026-03-16 navigation-path rerun, the focused live-thread checks on `thread_042`, `thread_043`, `thread_044`, and the targeted `thread_026` attachment/timeline rerun
- refresh/live-update device closeout on items 14-16 in the checklist, especially refresh-warning visibility, confirmed
  post-sync summary freshness, and detail auto-refresh while editing
- guided new-thread and bootstrap discovery device/manual smoke, especially sender-account selection behavior,
  `Project list` rendering plus repo prefill, and raw first-task body verification on a live mailbox
- full repo-wide `lint`
- full repo-wide `detekt`
- full repo-wide `spotlessCheck`
- JSON replay validation using an external mail export file

## Recommended Interpretation

The safest current interpretation is:

- TaskMail Android has concrete unit/regression evidence for repository, host-route, drawer-event, and detail-interaction paths.
- That evidence now clearly includes paused-state handling, single-question canonical quick answers, structured multi-question replies, stricter local multi-question send gating for required-answer completeness plus unknown-`question_id` and canonical-choice enforcement, attachment-related UI rules, real-device attachment send plus open/save flows, reply-like outgoing rendering recovery, pseudo-attachment suppression, a formal-host TaskMail launcher flow regression, a real-device rerun of the drawer-entry navigation path, live-mail proof that plain-text continuation plus `/status` stay anchored to the existing TaskMail session thread, focused refresh/live-update ViewModel coverage for manual refresh plus foreground-only account-scoped workspace/detail refresh while visible, a current clean full `:feature:taskmail:internal:testDebugUnitTest` rerun, repository-level dual-mailbox preference that keeps `[SYNC]` outside TaskMail session projection, focused send-target coverage that locks TaskMail reply transport onto explicit bot-mailbox destination resolution, focused runtime settings coverage for saving the bot-mailbox destination from Android settings without restart-only assumptions, focused guided new-thread coverage for sender-account handling plus canonical first-task serialization, and focused bootstrap discovery coverage for `[SYNC]` project-list parsing plus `Repo:` prefill handoff.
- That evidence now also includes full live validation for the current Phase 2 `new task` slice: the formal Android
  host emitted a relay `packet`, received `packet_ack`, produced a direct-bridge first ingress mail with
  `X-TaskMail-Direct: 1`, correctly routed fallback-classified rejection back to mail on `thread_084`, and correctly
  stopped on hard rejection `invalid_payload` without creating any new adjacent-runtime thread after `thread_085`.
  Later status/result mail still continues over the retained mail path today.
- That evidence now also includes a formal-host Phase 4 fallback review sample on `thread_093`: after reinstall left
  relay bootstrap unconfigured, Android surfaced `[Mail fallback]`, persisted the exact fallback evidence
  (`not_configured` / `MailFallbackSucceeded` / `FallbackRequired`), kept that evidence visible after screen reload and
  recent-tasks cold start, and still reached mailbox-side `[DONE]` with the expected reply token.
- Same-run Android / PC summary parity is no longer blocked on a missing fallback-row positive sample:
  `thread_083` direct accepted, `thread_093` bootstrap-unavailable fallback, and later `thread_094` fallback parity
  replacement sample now all have retained Android `Done + token` workspace evidence that matches PC-side
  `last_summary`, while `thread_084` is now only a historical retained-Android artifact gap rather than a current
  blocker or confirmed mismatch.
- The Android-side manual `parity checklist -> mismatch ledger -> rollback trigger` consumption order is now explicitly
  documented, but repeated use of that workflow across future runs is still thin and not yet a substitute for automatic
  evidence binding.
- Manual device evidence now also confirms the workspace refresh success path keeps loaded content visible and can surface the newest local outgoing entry after reply, while the refresh-warning failure path, confirmed backend-fed summary updates, and detail auto-refresh while editing remain open.
- The rich-text detail body slice now has executable evidence for controlled HTML projection, minimal detail
  rendering, a focused live-device inline-image placeholder pass on `thread_071`, fresh-build controlled device preview
  checks for static SVG placeholder rendering plus unmatched-image safe text fallback, and a later focused 2026-03-20
  repository/UI follow-up that enables raster inline-image preview plus attachment deduplication. It now also includes
  controlled representative-sample coverage for `External Deliveries`, `Attachment Notices`, and long-error `FAILED`
  mail. Fresh device validation for the raster preview path and broader live-mailbox rich-text coverage still remain
  outside the current validation boundary.
- Release confidence is still incomplete because device coverage is still concentrated on a few targeted live threads,
  guided new-thread smoke still lacks multi-account sender-selection plus raw-body verification, bootstrap discovery
  still lacks `Project list` / repo-prefill closeout, and full build plus repo-wide quality validation have not been
  closed.

## 2026-03-21 Phase 3 Detail Live-Smoke Update

在 `2026-03-21` 的 Phase 3 `detail` live smoke 里，当前验证边界又变得更具体了一步。

这轮先确认了 retained debug relay bootstrap 仍然正常：

- 设备上的 `TaskMail relay debug` 成功连接 `ws://124.223.41.153:8787/relay`
- `hello_ack` 正常返回，说明 live relay 可达且当前保存的 transport token 仍然有效

随后围绕 live thread `thread_086 / phase3-detail-q-20260321_194446-211b05` 做了 detail 读侧验证：

- Android workspace 可以在手动刷新后看到新 session
- Android detail 在手动 `pull-to-refresh` 后，能够沿 durable mail 路径从 `Running` 进入
  `WaitingUser`，随后在再次手动刷新后进入 `Done`
- 保持 detail 页面打开但不手动刷新时，没有观察到预期的 direct live update

关键收敛点来自同 token 的 workstation websocket probe。该 probe 直接向 live relay 发送：

- `action = subscribe_session_detail`
- `workspace_id = workspace_d0a3ad8a2abc`
- `repo_path = E:\projects\android_task_manager`
- `workdir = .`
- `session_id = thread_086`
- `thread_id = thread_086`

返回结果不是静默超时，而是明确拒绝：

- `packet_ack.accepted = false`
- `error_code = session_not_found`
- `error_message = could not resolve a session for the requested workspace/session locator`

当前最佳解读应当是：

- 这轮未闭环的主 blocker 是 live relay/session registry 无法 resolve 当前 PC live smoke session
- 因此 Phase 3 `detail` direct live-update 仍未被 live 证实，但当前失败信号还不能直接归因为 Android
  `timeline merge` / `business_event_key reconciliation`
- durable mail refresh 路径在这轮仍然工作，说明 Phase 3 代码面并没有被这次 smoke 直接否掉

同一轮还补了一条次级证据：

- `thread_086` 的 `[QUESTION]` mail 原文 `raw_004.json` 自带两段 `TASK-QUESTION` capsule
- detail 中看到的 duplicate pending question 更像 mail body duplication，而不是 direct merge 重复

## 2026-03-21 Phase 3 Detail Live-Smoke Retest

同日稍后的 retest 把这条验证边界继续向前推进了一步。

先做的 workstation websocket probe 已经从前一轮的 `session_not_found` 变成了成功：

- `subscribe_session_detail` 对 canonical locator 返回 `packet_ack.accepted = true`
- relay 紧接着返回 `session_update(update_type = session_snapshot)`

随后围绕新的 live thread `thread_087 / phase3-detail-live-20260321_202113-1dbf6b` 做了真实设备 detail
retest，QUESTION mail 里的回复 token 是 `BAF751DE25`。这轮采用的验证约束是：

- detail 页保持打开
- 不做手动 `pull-to-refresh`
- 直接回复 QUESTION mail
- 只观察页面是否会自然推进

本轮实际观察到：

- reply 发出后，detail 先停在 `WaitingUser`
- 设备在 `20:27:32` 的后台 IMAP fetch 后收到 `thread_087` 的 `[ACCEPTED]` / `[RUNNING]`
- `+20s` UI dump 时，detail 已进入 `Running`
- 设备在 `20:28:37` 的后台 IMAP fetch 后收到 `thread_087` 的 `[DONE]`
- 再等一个自然处理窗口后，detail UI 进入 `Done`
- 最终 summary 显示 `QUESTION_FLOW_OK | BAF751DE25`

这轮新增的 validation 结论是：

- Android detail 已经有真实设备证据表明，不依赖手动刷新也能从 QUESTION 流程自然走到 DONE
- 前一轮的 relay session resolution blocker 不再成立
- 当前尚未单独 live 证明的是“direct websocket update 是否早于 durable mail sync 驱动页面变化”
- 因而这轮可以视为 Phase 3 `detail` auto-refresh closeout 的正向证据，但 direct ws 优先路径仍可继续补证

次级现象仍保持不变：

- `thread_087` detail 里的 duplicate pending question 依旧存在
- 结合前一轮 `thread_086` 的原始 mail 证据，这更像 `[QUESTION]` mail body / extractor 输入问题，
  而不是 `timeline merge + business_event_key reconciliation` 回归
## 2026-03-22 Phase 3 Fixture / Duplicate-Question Follow-up

On 2026-03-22, the remaining Phase 3 follow-up work narrowed both open questions into executable evidence.

Direct inbound follow-up:

- Android re-ran the shared Phase 3 fixture contract against the adjacent PC fixture package.
- On this workstation, the adjacent PC workspace is checked out under
  `E:\projects\mail_based_task_manager\taskMail_PC\docs\plans\fixtures\phase3_direct_inbound_v1`
  instead of the older flat `E:\projects\mail_based_task_manager\docs\...` layout.
- The Android-side fixture loader now accepts both adjacent-workspace layouts so the contract test is not falsely
  skipped as "fixture package missing" on this machine.
- `TaskMailDirectSessionFixtureContractTest` now re-runs cleanly against the current exported Phase 3 fixture package.
- `ObserveTaskMailDirectSessionDetailTest` also re-runs cleanly and continues to lock:
  - `subscribe_session_detail` packet emission
  - `session_snapshot/session_delta` consumption
  - canonical `workspace_id` reuse
  - gap-triggered `detail_refresh` resubscribe
  - Android-side direct-detail debug logging points

Duplicate pending-question follow-up:

- `TaskQuestionCapsuleParserTest` now explicitly proves that when a source QUESTION mail repeats the same
  `TASK-QUESTION` capsule, the current Android parser preserves both blocks instead of silently deduplicating them.
- `DefaultTaskMailRepositoryPendingQuestionsTest` now explicitly proves that repository/detail projection shows the same
  duplicate pending question even with **no direct overlay involved**.

The safest current interpretation after this follow-up is:

- Phase 3 `detail` direct inbound now has stronger executable evidence through the shared fixture contract plus the
  Android observer/use-case tests, showing that the Android implementation remains aligned with the current
  `session_snapshot/session_delta` contract.
- This still does **not** newly establish a live-device proof that direct websocket updates visibly beat durable mail
  sync in driving the UI; that narrower live-ordering boundary remains open for future log capture.
- The duplicate pending question seen in live smoke is currently best read as a source `[QUESTION]` mail / extractor
  input duplication issue, not as a confirmed `TaskTimelineMerge` / `business_event_key reconciliation` regression.

## 2026-03-22 Phase 3 Durable Mail Sync Live Closeout Update

### 2026-03-22 后续复验（placeholder session filter / workspace foreground reload 后）

- live thread：`thread_089 / phase3-workspace-refresh-A73D2C9F11`
- 在装入 placeholder-session 过滤修复的新包后，workspace 不再先出现可点击的 `Unknown` placeholder card；进入 detail 的是 canonical `thread_089`
- 在 detail 页面直接使用内置 structured reply composer 回答后，不手动刷新，设备本地 cache 先自然从 `WaitingUser` 推进到 `Running`，随后继续推进到 `Done`
- 同一轮 watch 中，`thread_089` 的本地 detail snapshot 从 `status = Running / pendingQuestions = 0 / timelineCount = 7 / lastSummary = Permission: default`，自然推进到 `status = Done / pendingQuestions = 0 / timelineCount = 8 / lastSummary = QUESTION_FLOW_OK | A73D2C9F11`
- UI 侧也拿到了对应正向证据：detail 画面在不做 `pull-to-refresh` 的前提下，先从 question composer 切到 running 态，再自然显示 `QUESTION_FLOW_OK | A73D2C9F11`
- 从已完成 detail 返回 workspace 后，不做任何手动 `pull-to-refresh`，session card 已立即显示 `Done` 和 `QUESTION_FLOW_OK | A73D2C9F11`
- 这说明上一轮的两个边界现在都已闭环：
  - `TaskWorkspaceViewModel` 的 foreground reload 修复了 workspace stale-card / stale-summary
  - placeholder session filter 消除了把用户带进 stale `Unknown` detail route 的根因
- 仍未关闭的尾项：`Pending questions` / structured reply template 中重复的 `reply_token` 仍然存在，而且当前必须把重复行都填上值，`Send answers` 才会点亮；这更像 source `[QUESTION]` mail / extractor 输入重复带来的 reply UX 问题，而不是本轮 workspace refresh 修复的回归

`2026-03-22` 这轮 live closeout 把 Phase 3 `durable mail sync` 的边界继续收紧到了 detail 与 workspace
两个可区分的结果。

## 2026-03-22 Phase 3 Freeze Decision

在同日后续的跨仓库收口之后，Phase 3 当前边界进一步更新为：

- duplicate pending question / duplicate answer line 现象现按 PC 侧 source `[QUESTION]` 输入问题处理，并已在上游修正；
  Android 当前不再把它视为本地 Phase 3 blocker
- `detail` 编辑态下 refresh 期间的 draft / attachment 保持已补过烟测，因此不再作为当前阶段冻结阻塞项
- refresh-failure warning 可见性、更宽的 passive foreground-refresh smoke，以及 duplicate-question 修复后的
  Android 侧补回归都明确后延
- 因此，当前 Android 侧决定先冻结 Phase 3，并把下一条主动工程主线切到 Phase 4

本轮 live thread 与输入约束：

- live thread：`thread_088 / phase3-direct-log-eb51a4`
- reply token：`1FAE661EFB`
- 验证约束：detail 保持打开，不手动 `pull-to-refresh`，直接回复 QUESTION mail，然后观察页面是否自然推进

detail 侧观察到的事实：

- reply 前，detail 里的 pending question 与 composer 模板都仍然显示了两行重复的
  `live_mailbox_answer`
- reply 发出后，`+55s` 的 detail UI dump 仍停留在 `WaitingUser`，但 timeline 已显示 outgoing 内容：
  `Answers:` + 两行 `live_mailbox_answer: 1FAE661EFB`
- `01:07:48` 的设备日志里，`RealImapConnection` / `ImapSync` 明确拉到了 `thread_088` 的
  `[ACCEPTED]`、`[RUNNING]`、`[DONE]` 三封状态 mail
- 同一批日志里，`TaskSessionDetailViewModel` 记录了 `Observed local TaskMail store change while detail is visible.`
- 约 `+130s` 后，不做任何手动刷新，detail UI 自然推进到 `Done`；顶部 timeline entry 显示
  `Status: DONE`、`Session ID: thread_088`、`Task ID: 20260322_010644_66c9`

workspace 侧观察到的事实：

- 从已完成的 detail 返回 TaskMail workspace 后，`phase3-direct-log-eb51a4` 对应 session card 仍停留在旧状态：
  `WaitingUser / Waiting`
- 同一张 card 的 summary 仍是旧的 `Reply with the exact token 1FAE661EFB`
- 在 workspace 列表页静置约 `25s` 后再次抓取 UI dump，上述旧状态仍未自然纠正
- 对 workspace 列表手动做一次 `pull-to-refresh` 后，session card 立即纠正为 `Done`，summary 变成
  `QUESTION_FLOW_OK | 1FAE661EFB`

本轮新增的验证结论：

- Phase 3 detail 现在已有新的真实设备证据表明：`durable mail sync` 本身可以在不手动刷新的前提下驱动
  detail 从 QUESTION 流程自然进入 `Done`
- 但同一轮 live 流程里，workspace/session card 没有证明会自然吸收这次状态推进；当前更像是
  workspace summary freshness 仍依赖显式 refresh
- duplicate pending question / duplicate answer line 现象在本轮继续出现，但结合既有 parser / repository
  证据，它仍更像源 `[QUESTION]` mail / extractor 输入重复，而不是新的 Phase 3 merge 回归
