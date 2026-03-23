# TaskMail Next Session Handoff - 2026-03-22 - Phase 4 New Task Matrix Reconciliation

## 当前决策

- Phase 4 当前仍只围绕唯一 covered flow `new task` 推进，不把 `reply`、`/status` 或更宽的 read-side direct transport 提前拉进实现范围。
- Android 侧已经把 `new task` 的三类场景：
  - `direct accepted`
  - `fallback_to_mail`
  - `hard_rejection_stop`
  回填到 shared `parity checklist` / `mismatch ledger` / `rollback trigger` 文档。
- shared artifacts 的手动消费顺序现在也已冻结为：先写 `parity checklist`，再决定是否进入 `mismatch ledger`，最后再用
  `rollback trigger note` 判定是否阻塞 switch。
- Android 侧现在不只是“有 durable evidence 落盘”，而是已经有 formal `New task` 页面里的 latest-evidence review
  surface，并且 screen reload 与 recent-tasks cold-start 回看都已在真机上补齐。
- 同一 run 的 Android / PC summary parity 现在也不再是完全空白：
  - `thread_083` direct accepted -> 已对齐
  - `thread_095` direct accepted after relay re-provision -> 已对齐
  - `thread_093` bootstrap-unavailable fallback -> 已对齐
  - `thread_094` fallback-to-mail replacement sample -> 已对齐
  - `thread_084` 现应读作历史 retained-Android artifact gap，已被 `thread_094` 替代，不再是当前 blocker
- 当前仍不能把这批进展解读成 `new task` 已可切成 `direct-default`；mail fallback 必须继续保留。

## Read First

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/TASKMAIL-MAIL-RULES.md`
- `docs/taskmail/planning/android/taskmail-next-development-plan-v0.2.md`
- `docs/taskmail/planning/android/taskmail-phase4-dual-stack-boundary-freeze-v0.1.md`
- `docs/taskmail/planning/android/phase4_dual_stack_parity_checklist.md`
- `docs/taskmail/planning/android/phase4_mismatch_ledger.md`
- `docs/taskmail/planning/android/phase4_rollback_trigger_note.md`
- `E:\projects\mail_based_task_manager\docs/plans/phase4_dual_stack_parity_checklist.md`
- `E:\projects\mail_based_task_manager\docs/plans/phase4_mismatch_ledger.md`
- `E:\projects\mail_based_task_manager\docs/plans/phase4_rollback_trigger_note.md`

## 本次代码与验证

- 已改生产代码：是
- 已改测试代码：是
- 已更新 authority / status / planning 文档：是
- 已做新的本地验证：是

本次新增的代码点：

- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/newtask/TaskNewTaskDirectEvidenceCard.kt`
  - 把 `lastDirectSendEvidence` 落成 formal `New task` 页面里的 latest-evidence review card
  - 当前可 review：
    - `outcome`
    - `switchGate`
    - `bootstrapStatus`
    - 可选 `receiptId`
    - 可选 `transportMessageId`
    - 可选 fallback reason
    - 可选 error message
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/newtask/TaskNewTaskContent.kt`
  - 把 latest-evidence card 接入 form list
- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/ui/newtask/TaskNewTaskScreenKtTest.kt`
  - 新增 accepted review 场景
  - 新增 fallback / error review 场景

本次通过的命令：

- `.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest --tests "net.thunderbird.feature.taskmail.internal.ui.newtask.TaskNewTaskScreenKtTest" --tests "net.thunderbird.feature.taskmail.internal.ui.newtask.TaskNewTaskViewModelTest"`
- `.\gradlew.bat :feature:taskmail:internal:detekt :feature:taskmail:internal:lintDebug`

另外，本轮之前的三场景矩阵对账 focused tests 仍保持通过：

- `.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest --tests "net.thunderbird.feature.taskmail.internal.domain.usecase.RunTaskMailDirectOrFallbackTest" --tests "net.thunderbird.feature.taskmail.internal.ui.newtask.TaskNewTaskViewModelTest" --tests "net.thunderbird.feature.taskmail.internal.data.relay.RelayTaskMailDirectNewTaskSenderTest" --tests "net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayProtocolJsonCodecTest" --tests "net.thunderbird.feature.taskmail.internal.data.cache.FileBackedTaskMailNewTaskSendRecordRepositoryTest"`

本次 live/manual closeout：

- formal host live sample `Phase 4 live review 20260322 A` 的用户可见发送结果是 `[Mail fallback]`
- app-private send record 落成：
  - `bootstrapStatus = not_configured`
  - `outcome = MailFallbackSucceeded`
  - `switchGate = FallbackRequired`
  - `fallbackReason = Relay host, port, and transport token are required.`
- 同一条 latest-evidence card 已确认可在：
  - screen reload 后继续 review
  - recent-tasks cold start 后重新进 `Tasks -> New task` 继续 review
- mailbox-side 同 run 结果收口到 `[DONE][S:thread_093] Phase 4 live review 20260322 A`

本次补充的 fallback parity replacement sample：
- formal workspace refresh 后保留 `_tmp_device/phase4_fallback_after_refresh.xml`
  - `Phase 4 fallback parity 20260322 B / Done / Codex / PHASE4_FALLBACK_PARITY_20260322_B`
- 最新 app-private send record 仍落成：
  - `bootstrapStatus = not_configured`
  - `outcome = MailFallbackSucceeded`
  - `switchGate = FallbackRequired`
  - `fallbackReason = Relay host, port, and transport token are required.`
- mailbox-side 同 run 结果收口到 `[DONE][S:thread_094] Phase 4 fallback parity 20260322 B`

本次补充的 fresh direct-accepted sample：
- live relay config 已重新配回当前 plaintext runtime：
  - `host = 124.223.41.153`
  - `port = 8787`
  - `path = /relay`
  - `useTls = false`
  - token fingerprint = `6f05b17d957d`
- retained debug relay screen 已再次闭环：
  - `Healthz -> status=ok | ... | token_id=6f05b17d957d`
  - `Connect -> connection=connected`
- formal host `Phase 4 direct parity 20260322 C` 用户可见发送提示手工确认为 `[Relay]`
- app-private latest send record 落成：
  - `bootstrapStatus = hello_ack`
  - `outcome = DirectAccepted`
  - `switchGate = KeepDirectDefault`
  - `receiptId = relay-receipt:android-taskmail:new-task:req_f8f52bd45be6445185c0553b4b248fb0:03bed494`
  - `transportMessageId = <177416864698.472093.13590235740865598638@mail-runner.local>`
- mailbox-side与 PC canonical outcome 收口到 `thread_095`
  - `thread_state.json.last_summary = PHASE4_DIRECT_ACCEPT_20260322_C`
  - `runs/20260322_163746_d160/canonical_summary.json` 记录：
    - `ingress_type = direct_bridge`
    - `request_id = req_f8f52bd45be6445185c0553b4b248fb0`
    - `ingress_message_id = <177416864698.472093.13590235740865598638@mail-runner.local>`
    - `terminal_mail_subject = [DONE][S:thread_095] Phase 4 direct parity 20260322 C`

本次 same-run parity readout：

- `thread_083`
  - Android retained dump：`_tmp_device/taskmail_phase2_negative_after_tap.xml`
  - Android 可见结果：`Phase2 direct smoke B / Done / PHASE2_DIRECT_SMOKE_20260321_B`
  - PC canonical outcome：`thread_state.json.last_summary = PHASE2_DIRECT_SMOKE_20260321_B`
- `thread_095`
  - Android retained evidence：latest send record `hello_ack / DirectAccepted / KeepDirectDefault`
  - Android 用户可见结果：formal-host 发送提示手工确认为 `[Relay]`
  - PC canonical outcome：`canonical_summary.json.ingress_type = direct_bridge` 且 `thread_state.json.last_summary = PHASE4_DIRECT_ACCEPT_20260322_C`
- `thread_093`
  - Android retained dump：`_tmp_device/current_taskmail_workspace2.xml`
  - Android 可见结果：`Phase 4 live review 20260322 A / Done / PHASE4_LIVE_REVIEW_20260322_A`
  - PC canonical outcome：`thread_state.json.last_summary = PHASE4_LIVE_REVIEW_20260322_A`
- `thread_094`
  - Android retained dump：`_tmp_device/phase4_fallback_after_refresh.xml`
  - Android 可见结果：`Phase 4 fallback parity 20260322 B / Done / PHASE4_FALLBACK_PARITY_20260322_B`
  - PC canonical outcome：`thread_state.json.last_summary = PHASE4_FALLBACK_PARITY_20260322_B`
- `thread_084`
  - PC canonical outcome 仍闭环到 `PHASE2_FALLBACK_SMOKE_20260321`
  - Android 当前 retained live artifact 只有 `_tmp_device/taskmail_phase2_hardreject_prep.xml`，仍停在更早的
    `Unknown + prompt` 卡片
  - 当前应读作历史 retained-artifact gap，已被 `thread_094` 替代，不读作 confirmed mismatch

## 当前已知边界 / 小坑

- 当前“latest-evidence review surface 的 live/manual closeout”已经补齐，但样本还是 fallback case，不等于 accepted
  path 的同 run parity 也已自动收口。
- 这次 `thread_093` 命中 `[Mail fallback]` 的直接原因是重装后 relay bootstrap 处于 `not_configured`，不是新的
  accepted-direct regression。
- `accepted_without_expected_outcome` 和 `summary_outcome_drift` 这两类 trigger 现在仍要依赖 shared parity checklist 联合 Android / PC 证据判断；Android 侧还没有把同一 run 的后续 canonical mail outcome 自动绑定回 latest direct evidence。
- `thread_084` 的 retained-summary 缺口现在已经被 `thread_094` 替代样本覆盖；当前更具体的 open tail 是 shared
  artifacts 的稳定 operational consumption，以及 latest direct evidence 到后续 canonical mail outcome 的自动绑定缺失。
- `thread_095` 说明 PC-side `canonical_summary.json` 已足够作为更稳定的 same-run summary artifact；下一轮优先继续把 shared checklist 的 direct 行改成先消费它，而不是继续默认人工翻 `raw_001` / `raw_004`。
- 虽然手动消费顺序已经写进文档，但它现在仍主要依赖 targeted closeout，而不是自动化或日常化绑定。
- 当前 `mismatch ledger` 仍为空，这不代表“已经全部对齐”；只代表这轮没有拿到足以升级成 confirmed mismatch 的 cross-repo drift。

## 下一步最合理顺序

1. 把 shared parity checklist / mismatch ledger / rollback trigger 的消费步骤收紧成更稳定的日常对账流程；direct 行优先消费 PC `canonical_summary.json`，fallback 行再回退到 `thread_state.json` 加上按 terminal status mail 类型定位到的 `mail/raw_*.json`。
2. 如果出现真实 cross-repo drift，再显式回填 `phase4_mismatch_ledger.md`；如果没有，就继续把 rollback trigger 的 evidence source 收紧成可重复执行的 closeout 步骤。
3. 后续如果还要补 formal-host cold-start 烟测，沿用“桌面启动 Thunderbird -> 手动进 `Tasks`”这条路径，不再把 adb 直接拉起 activity 当成等价替代。
4. 只有在 latest-evidence 到 canonical mail outcome 的绑定口径更稳、shared artifacts 的 evidence consumption 更稳后，才评估 `new task` 的 `direct-default` switch gate，以及 `reply` / `/status` 是否进入单独 contract freeze。
