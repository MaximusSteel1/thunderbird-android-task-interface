# TaskMail Phase 5 `new_task` Direct-Default Review Decision（v0.1）

更新时间：2026-03-22

## 状态

- 本文承接 `docs/taskmail/planning/android/taskmail-phase5-new-task-switch-review-v0.1.md`。
- 当前 decision 结论为：`proceed_with_guarded_direct_default_review`
- 这不等于“现在立刻把 `new_task` 切成 `direct-default`”。
- 本文只处理 `new_task`；`reply`、`/status`、更宽的 direct read-side transport 继续留在 scope 外。
- 后续 activation 边界见
  `docs/taskmail/planning/android/taskmail-phase5-new-task-direct-default-rollout-activation-note-v0.1.md`。

## 先读这些文档

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/taskmail/planning/android/taskmail-phase5-new-task-switch-review-v0.1.md`
- `docs/taskmail/planning/android/phase4_dual_stack_parity_checklist.md`
- `docs/taskmail/planning/android/phase4_mismatch_ledger.md`
- `docs/taskmail/planning/android/phase4_rollback_trigger_note.md`
- `E:\projects\mail_based_task_manager\docs\plans\phase4_dual_stack_parity_checklist.md`

## 决策输入

当前作出 guarded review 决策所依赖的输入是：

- covered flow 仍冻结在 `new_task`
- fallback row 仍保持可执行，且 same-run positive samples 已存在：
  - `thread_093`
  - `thread_094`
- direct accepted 正向样本已覆盖：
  - `thread_083`
  - `thread_095`
  - `thread_098`
- `thread_084` 已稳定降级为 historical retained-artifact gap，而不是当前 blocker
- `thread_097` 已证明 freeze 后的 `daily_closeout_bundle` workflow 可在 fresh sample 上复用
- `thread_098` 已证明当前 formal-host build 上的 Android latest send evidence 能带出 `requestId`，并完成：
  - `request_id`
  - `transportMessageId / ingress_message_id`
  - `last_summary`
  的 same-run bind 顺序
- 当前 `phase4_mismatch_ledger.md` 仍为空，且空表读法已固定为“暂无 confirmed mismatch”，而不是“已经全面对齐”
- `phase4_rollback_trigger_note.md` 当前没有被 `thread_097` / `thread_098` 激活新的 rollback 触发项

## 决策

基于上面的证据，当前决策是：

- `new_task` 已具备进入 guarded `direct-default` review 的证据条件
- 当前不需要继续先补同类 fresh bind blocker
- 下一步可以讨论“是否起草受控切换 / rollout 方案”，而不是继续争论“证据是否还不够”

但本文同时明确：

- 本文不授权立即改动生产默认行为
- 本文不授权把 `reply` / `/status` 一并拉入 direct-default
- 本文不授权删除或弱化 mail fallback

## 必须保持的 Guardrails

进入 guarded review 后，至少仍要保持以下边界：

1. scope 只覆盖 `new_task`
2. mail fallback 继续保持真实可执行，不能退化成死路径
3. shared artifact 消费顺序继续固定为：
   - `parity checklist`
   - `mismatch ledger`
   - `rollback trigger`
4. PC 若已有 `canonical_summary.json`，仍优先消费它；`mail/raw_*.json` 只作 supporting evidence，不重新写成稳定编号合同
5. direct accepted 行继续按既定 same-run bind 顺序评审：
   - `request_id`
   - `transportMessageId / ingress_message_id`
   - `last_summary token` 仅作弱回退

## 何时撤回本决策

如果后续出现以下任一情况，当前 decision 应回退为 `not_ready_keep_mail_default`：

- 新的 fresh sample 再次只能靠 `transportMessageId / ingress_message_id` 才能闭环
- helper 再次出现 `android_request_id_missing`
- `phase4_mismatch_ledger.md` 出现 confirmed mismatch，且该 mismatch 直接落在 `new_task` current baseline
- `phase4_rollback_trigger_note.md` 中已有 trigger 被新的 same-run evidence 激活
- fallback path 在 formal-host 或 current production-like boundary 上失真、失效或不可复用

## 当前不做的事

- 不继续追更多“只是重复证明同一件事”的 fresh direct samples
- 不提前实现 `reply` / `/status` direct path
- 不把 debug-host deep link 当成 formal-host 等价验证
- 不在本文里直接给出生产切换命令、开关改动或 rollout 代码方案

## 下一步

本文对应的 guarded rollout / activation note 现已落地：

1. `docs/taskmail/planning/android/taskmail-phase5-new-task-direct-default-rollout-activation-note-v0.1.md`
2. 因此下一步不再是“去写这份 note”，而是判断 Android 当前是否还需要单独的 `new_task` flow-scoped activation / config change。
3. 在这个问题明确前，仍不提前扩到 `reply` / `/status`。

本文不替代 switch-review note；它是在 switch-review 已变成 `ready_for_direct_default_review` 之后，把“可进入评审”与“已经授权切换”这两件事明确拆开。
