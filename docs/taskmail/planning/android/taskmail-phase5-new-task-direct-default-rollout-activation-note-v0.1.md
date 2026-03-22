# TaskMail Phase 5 `new_task` Guarded Direct-Default Rollout / Activation Note（v0.1）

更新时间：2026-03-22

## 状态

- 本文承接：
  - `docs/taskmail/planning/android/taskmail-phase5-new-task-switch-review-v0.1.md`
  - `docs/taskmail/planning/android/taskmail-phase5-new-task-direct-default-review-decision-v0.1.md`
- 本文不新增新的 gate token；它只把 `new_task` 进入 guarded `direct-default` activation 评审时必须遵守的边界写清楚。
- 本文仍不等于“现在立刻改动生产默认行为”。
- 如果后续确实需要 Android 侧实现或配置改动，必须另起窄范围批次，并继续只覆盖 `new_task`。

## 先读这些文档

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/TASKMAIL-DEBUG-VALIDATION.md`
- `docs/taskmail/planning/android/taskmail-phase5-new-task-switch-review-v0.1.md`
- `docs/taskmail/planning/android/taskmail-phase5-new-task-direct-default-review-decision-v0.1.md`
- `docs/taskmail/planning/android/phase4_dual_stack_parity_checklist.md`
- `docs/taskmail/planning/android/phase4_mismatch_ledger.md`
- `docs/taskmail/planning/android/phase4_rollback_trigger_note.md`
- `E:\projects\mail_based_task_manager\docs\plans\phase4_dual_stack_parity_checklist.md`

## 激活前提

进入 `new_task` guarded `direct-default` activation 评审前，至少要同时满足：

1. `taskmail-phase5-new-task-switch-review-v0.1.md` 仍保持 `ready_for_direct_default_review`。
2. `taskmail-phase5-new-task-direct-default-review-decision-v0.1.md` 仍保持 `proceed_with_guarded_direct_default_review`。
3. `thread_097` 继续代表 freeze 后 `daily_closeout_bundle` workflow 可在 fresh sample 上复用。
4. `thread_098` 继续代表当前 formal-host build 上的 Android latest send evidence 能闭环 `request_id -> transportMessageId / ingress_message_id -> last_summary`。
5. `phase4_mismatch_ledger.md` 仍无落在 `new_task` current baseline 上的 confirmed mismatch。
6. `phase4_rollback_trigger_note.md` 未被新的 same-run evidence 激活 rollback trigger。
7. fallback row 与 hard-rejection row 的 shared 读法未退化：
   - fallback 仍是可执行活路径
   - hard rejection 仍是本地 stop + draft retention，而不是静默降级成 mail fallback
8. formal-host 验证路径仍沿用“桌面启动 Thunderbird -> 手动进入 `Tasks`”；不把 adb deep link / debug-host 当等价替代。

如果后续发现 Android 现有实现无法把激活边界限制在 `new_task`，应先停下并重审边界，而不是顺手扩大到 `reply` / `/status`。

## 激活边界

本 note 允许讨论的 activation 边界，仅限于：

- flow scope 只覆盖 formal-host `new_task`
- direct accepted / fallback / hard rejection 的运行时分类继续沿用当前 contract：
  - `TaskMailDirectSwitchGate.KeepDirectDefault`
  - `TaskMailDirectSwitchGate.FallbackRequired`
  - `TaskMailDirectSwitchGate.SwitchBlocker`
- mail fallback 继续保持真实可执行，不弱化成死路径
- hard rejection 继续保持本地失败可见性与 draft retention
- PC 侧若已有 `canonical_summary.json`，仍优先消费它；`mail/raw_*.json` 只作 supporting evidence
- shared artifact 手动消费顺序继续固定为：
  - `parity checklist`
  - `mismatch ledger`
  - `rollback trigger`

本 note 不允许讨论的内容包括：

- 把 `reply` / `/status` 一并纳入 direct-default
- 取消或绕过 mail fallback
- 用 debug-host shortcut 替代 formal-host activation closeout
- 以“当前样本看起来没问题”为理由跳过 mismatch / rollback readout

## 观察窗口

当前观察窗口按 artifact closeout 约束，而不是先写死为某个样本数：

1. activation 边界一旦进入评审，后续自然发生的 fresh `new_task` sample 仍应继续走冻结后的 closeout 流程。
2. direct accepted sample 若具备对应 PC run，应优先生成或消费同 run 的 `taskmail_daily_closeout_bundle.json`。
3. direct accepted sample 进入 guarded activation readout 时，优先读：
   - `same_run_bind.effective_bind_level`
   - `same_run_bind.matched_fields`
   - `notes`
4. 对当前 baseline，理想 readout 仍是：
   - `effective_bind_level = request_id`
   - `matched_fields` 至少覆盖 `request_id + transport_message_id + last_summary`
   - `notes` 为空
5. fallback / hard rejection 样本若在观察窗口内自然出现，仍要写回 shared artifacts，而不是因为 activation 讨论已开始就忽略它们。

换句话说，观察窗口的重点不是“再追更多同类 direct 样本”，而是“不让 activation 评审脱离现有 closeout discipline”。

## 回退动作

出现以下任一情况时，应停止 guarded activation 讨论，并把 `new_task` 结论降回 `not_ready_keep_mail_default`：

- fresh direct accepted sample 再次只能靠 `transportMessageId / ingress_message_id` 才能闭环
- helper 再次给出 `android_request_id_missing`
- `phase4_mismatch_ledger.md` 出现 confirmed mismatch，且该 mismatch 直接落在 `new_task` current baseline
- `phase4_rollback_trigger_note.md` 中已有 trigger 被新的 evidence 激活
- fallback path 在 formal-host 或 current production-like boundary 上失真、失效或不可复用

若未来某个独立实现批次已经真的落下了 `new_task` 的 activation / config change，则回退动作也必须保持窄范围：

- 只撤回 `new_task` 的 direct-default activation 讨论或对应实现
- 不顺势把 `reply` / `/status` 拉进同一批修补
- 不把 Android 侧 evidence gap 误写成 PC 侧实现故障

## 本文不做的事

- 不直接给出 Android 代码改动、feature flag 改动或 rollout 命令
- 不新增 PC 仓代码或要求 PC 仓同步实现
- 不把 `thread_084` 重新抬回当前 blocker
- 不把“已有 evidence 足够进入评审”误写成“已经正式授权切换”

## 下一步

本文落地后，下一步应先回答一个更窄的问题：

1. Android 当前实现是否还需要单独的 `new_task` flow-scoped activation / config change，才能把 guarded activation 边界表达清楚？
2. 如果答案是“不需要”，那下一步只是把 activation verdict 同步回 authority / handoff，而不是再造新代码。
3. 如果答案是“需要”，则另起一轮只覆盖 `new_task` 的实现批次，并配 focused tests、`installFullDebug` 与 formal-host closeout 验证。

在这个问题明确前，不继续扩到 `reply` / `/status`。
