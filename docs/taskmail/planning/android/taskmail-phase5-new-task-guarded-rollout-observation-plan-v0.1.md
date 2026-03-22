# TaskMail Phase 5 `new_task` Guarded Rollout Observation Plan（v0.1）

更新时间：2026-03-22

## 状态

- 本文承接：
  - `docs/taskmail/planning/android/taskmail-phase5-new-task-switch-review-v0.1.md`
  - `docs/taskmail/planning/android/taskmail-phase5-new-task-direct-default-review-decision-v0.1.md`
  - `docs/taskmail/planning/android/taskmail-phase5-new-task-direct-default-rollout-activation-note-v0.1.md`
- 当前 Android 侧结论仍为：
  - `ready_for_direct_default_review`
  - `proceed_with_guarded_direct_default_review`
  - `no_additional_new_task_activation_config_needed`
- 本文不授权立即改动生产默认行为，也不把 `reply` / `/status` 拉进当前评审范围。
- 本文只定义：在不新增 Android `new_task` activation/config 代码的前提下，Phase 5 接下来如何继续推进 guarded rollout / observation。

## 先读这些文档

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/TASKMAIL-MAIL-RULES.md`
- `docs/TASKMAIL-DEBUG-VALIDATION.md`
- `docs/taskmail/planning/android/phase4_dual_stack_parity_checklist.md`
- `docs/taskmail/planning/android/phase4_mismatch_ledger.md`
- `docs/taskmail/planning/android/phase4_rollback_trigger_note.md`
- `docs/taskmail/planning/android/taskmail-phase5-new-task-switch-review-v0.1.md`
- `docs/taskmail/planning/android/taskmail-phase5-new-task-direct-default-review-decision-v0.1.md`
- `docs/taskmail/planning/android/taskmail-phase5-new-task-direct-default-rollout-activation-note-v0.1.md`
- `E:\projects\mail_based_task_manager\docs/current/mail_protocol.md`
- `E:\projects\mail_based_task_manager\scripts\build_taskmail_closeout_bundle.py`

## 当前读法

截至 2026-03-22，Android 侧对 Phase 5 `new_task` 的最佳读法已经不是：

- 继续补同类 fresh bind 样本
- 继续争论是否还需要单独的 `new_task` activation/config 开关
- 提前把 `reply` / `/status` 拉进 direct-default 评审

而是：

- 保持当前 guarded direct-default review verdict
- 继续按冻结后的 shared-artifact 流程消费后续自然出现的 fresh sample
- 在没有新的 rollback / mismatch signal 之前，不新增 Android 生产代码

## 范围内

- formal-host `new_task` only
- Android retained evidence 的最小留存与复读
- shared artifacts 的持续消费顺序
- fresh sample 出现时的 closeout discipline
- 需要相邻 PC 侧配合提供的 evidence / contract freeze 要求

## 范围外

- direct `reply`
- direct `/status`
- 更宽的 read-side direct-default
- Android 侧新的 `new_task` activation/config 开关或额外 transport seam
- PC 仓生产代码改动
- 把 repo-wide 质量门或更宽设备烟测误写成当前 Phase 5 narrow blocker

## Android 侧继续推进方式

### 1. 保持当前 guarded verdict

在没有新 evidence 回退前，Android 侧当前 verdict 保持为：

- `ready_for_direct_default_review`
- `proceed_with_guarded_direct_default_review`
- `no_additional_new_task_activation_config_needed`

因此下一步不是改代码，而是保持 authority / handoff 与这个 verdict 一致。

### 2. fresh sample 出现时的最小留存

如果后续自然出现新的 formal-host `new_task` sample，Android 侧至少应保留：

- latest send evidence
  - `bootstrapStatus`
  - `outcome`
  - `switchGate`
  - `requestId`
  - `receiptId`
  - `transportMessageId`
- sender-account scope
- formal-host terminal summary dump、同等级截图，或可复读的 workspace retained artifact
- 当前 run 与构建环境的最小说明
  - 例如 fresh install / current formal-host build / reused installed build

### 3. shared-artifact 消费顺序继续冻结

后续 manual closeout 仍必须按固定顺序消费：

1. `phase4_dual_stack_parity_checklist.md`
2. `phase4_mismatch_ledger.md`
3. `phase4_rollback_trigger_note.md`

不回退到 old-style 手工拼接 `raw_001` / `raw_004` 再倒推结论。

### 4. 当前观察重点

对 direct accepted 行，优先观察：

- `same_run_bind.effective_bind_level`
- `same_run_bind.matched_fields`
- `same_run_bind.notes`

当前理想 readout 仍是：

- `effective_bind_level = request_id`
- `matched_fields` 至少覆盖 `request_id + transport_message_id + last_summary`
- `notes = []`

fallback 与 hard rejection 若在观察窗口内自然出现，仍应继续写回 shared artifacts，而不是因为当前 focus 在 direct review 就忽略它们。

## 回退条件

出现以下任一情况时，Android 侧应把当前结论降回 `not_ready_keep_mail_default`：

- fresh direct accepted sample 再次只能靠 `transportMessageId` / `ingress_message_id` 才能闭环
- helper 再次出现 `android_request_id_missing`
- `phase4_mismatch_ledger.md` 出现直接落在 `new_task` current baseline 上的 confirmed mismatch
- `phase4_rollback_trigger_note.md` 中已有 trigger 被新的 same-run evidence 激活
- formal-host fallback path 失真、失效或不可复用
- hard rejection 不再保持本地 stop + draft retention，而是退化成静默 mail fallback

## 需要 PC 侧配合的事项

以下是继续推进当前 Phase 5 guarded observation 时，Android 侧对相邻 PC 侧的明确要求。

这些是配合项，不是本仓在本轮要直接修改的内容。

### A. run closeout artifacts

对于进入观察窗口的 fresh `new_task` sample，PC 侧应尽量提供或保留：

- `canonical_summary.json`
- `taskmail_daily_closeout_bundle.json`
- `terminal_mail_subject`
- `terminal_mail_message_id`
- `ingress_message_id`
- `request_id`

当前 Android 侧默认希望继续消费 `build_taskmail_closeout_bundle.py` 产出的 bundle，而不是重新回到人工拼接。

### B. same-run bind readout

PC 侧 bundle helper 应继续保留 direct accepted 行的 same-run bind readout：

- `effective_bind_level`
- `matched_fields`
- `notes`

这样 Android 侧才能继续把“是否仍保持 `request_id` 首键 bind”读成稳定 gate，而不是临时口头解释。

### C. 若要推进到 `reply` / `/status`

如果下一阶段要从 `new_task` 扩到 post-creation actions，PC 侧需要先给出 current-protocol 层面的 freeze，而不是只留在历史 planning：

- direct `reply` 是扩展 `phase2-direct-outbound-contract-v1`，还是另起 session-action contract
- single-question、multi-question `Answers:`、paused `/resume`、attachment-only continuation 是否共用同一 envelope
- direct `/status` 的 current canonical result 应如何与现有 mail contract 对齐

在这些 freeze 之前，Android 侧不会把 `reply` / `/status` 接到 direct path。

## 退出读法

本文对应的 Phase 5 继续推进，可以读作以下更窄结论：

- Android 侧当前不再等待新的 `new_task` activation/config 实现
- Android 侧当前继续推进的主要工作是 guarded observation、authority 对齐、以及必要时的 cross-repo evidence consumption
- 真正需要新的跨仓工程实现时，下一刀更可能是 post-creation direct contract freeze，而不是回头再改当前 `new_task` send path
