# TaskMail Phase 4 收尾判断与 Phase 5 窄范围计划（v0.1）

更新时间：2026-03-22

## 状态

- 本文承接 `docs/taskmail/archive/android/handoff/taskmail-next-session-handoff-2026-03-22-phase4-new-task-matrix-reconciliation.md`。
- 本文的作用是把当前这条 Android / PC / VPS 主线读成一个明确的“Phase 4 到哪里收口、Phase 5 第一刀做什么”判断。
- 本文不重跑已经完成的 Phase 4 对账、`thread_095` direct fresh sample、或 relay re-provision 历史工作。
- 本文不替代以下 authority：
  - `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
  - `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
  - `docs/TASKMAIL-MAIL-RULES.md`
  - `E:\projects\mail_based_task_manager\docs/current/mail_protocol.md`

## 先读这些文档

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/TASKMAIL-DEBUG-VALIDATION.md`
- `docs/TASKMAIL-MAIL-RULES.md`
- `docs/taskmail/planning/android/phase4_dual_stack_parity_checklist.md`
- `docs/taskmail/planning/android/phase4_mismatch_ledger.md`
- `docs/taskmail/planning/android/phase4_rollback_trigger_note.md`
- `docs/taskmail/archive/android/handoff/taskmail-next-session-handoff-2026-03-22-phase4-new-task-matrix-reconciliation.md`
- `docs/taskmail/planning/android/taskmail-next-development-plan-v0.2.md`
- `E:\projects\mail_based_task_manager\docs/current/mail_protocol.md`
- `E:\projects\mail_based_task_manager\docs/plans/phase4_dual_stack_parity_plan.md`

## Phase 4 收尾判断

### 已经足够的部分

- 当前 covered flow 仍应只读作 `new_task`，不把 `reply`、`/status` 或更宽的 read-side direct transport 提前并入。
- shared artifacts 的消费顺序已经足够明确：
  - `parity checklist`
  - `mismatch ledger`
  - `rollback trigger`
- `new_task` 的三条 send-side 主路径都已有可复读证据面：
  - `direct accepted`
  - `fallback_to_mail`
  - `hard_rejection_stop`
- same-run parity 正向样本已经足以支撑首轮 matrix 收口：
  - `thread_083`
  - `thread_093`
  - `thread_094`
  - `thread_095`
- `thread_084` 当前应继续只读作 historical retained-artifact gap；它已被 `thread_094` 替代，不再是 active blocker。
- 当前 `phase4_mismatch_ledger.md` 保持空表是合理状态；现阶段没有足以升级成 confirmed mismatch 的 cross-repo drift。

### 仍然存在的 open tail

- shared artifacts 目前仍主要依赖 targeted closeout 人工消费，还没有收紧成稳定的日常流程。
- latest direct evidence 还没有稳定绑定到后续 canonical mail outcome；当前仍要联合 Android retained evidence 与 PC-side outcome 手动对账。
- 因此，当前还不能宣布 `new_task` 已可切成 `direct-default`。

### 不应继续算作本轮 Phase 4 范围的事项

- 不重复做 `thread_084` 的历史 artifact 追补。
- 不重复做已经闭环的 relay re-provision / fresh direct sample 对账。
- 不把 `reply`、`/status`、attachment binary sync、全量 workspace/history read-path 切换拉入当前主线。
- 不在有 `canonical_summary.json` 的前提下继续把 `raw_001` / `raw_004` 人工拼接当成默认 direct 行消费方式。

## Phase 4 收口结论

当前这条 Android / PC / VPS 主线应把 Phase 4 明确收口为：

- `new_task` parity baseline 已收紧到当前可接受边界。
- `new_task` direct-default switch gate 仍保持关闭。
- Phase 4 后续不再靠“继续补旧样本”推进，而是把剩余尾项转成下一阶段的 hardening 工作。

换句话说，Phase 4 在当前应读作：

- 已经足够停止扩 scope。
- 已经足够冻结 covered-flow 边界与 shared-artifact 读法。
- 还不足够授权 `new_task` 切到 `direct-default`。

## Phase 5 窄范围计划

### 目标

Phase 5 的第一刀不直接扩 flow，也不直接默认宣布 switch，而是先把 `new_task` 的 direct-default 决策输入收紧成可重复执行的 hardening 流程。

### 范围内

- `new_task` only
- shared artifacts 的日常消费流程
- latest direct evidence 与 canonical mail outcome 的稳定绑定
- 一份显式的 `new_task` switch-review note

### 范围外

- direct `reply`
- direct `/status`
- 更宽的 read-side default 切换
- 新一轮 relay 架构重配
- 重新追历史 retained-artifact 缺口

### Slice A：冻结日常对账流程

目标：把当前“只在 targeted closeout 时会做”的手工对账，收紧成下一轮可重复使用的日常流程。

本批应完成：

- direct 行优先消费 PC `canonical_summary.json`
- fallback 行在有 `canonical_summary.json` 时同样优先消费它；没有时再回退到 `thread_state.json` 加上按 terminal status mail 类型定位到的 `mail/raw_*.json`
- Android 侧最小留存字段冻结为：
  - latest send evidence
  - terminal summary dump 或同等级截图
  - sender-account scope
- shared artifacts 继续严格按：
  - `parity checklist -> mismatch ledger -> rollback trigger`
  顺序消费

closeout 条件：

- 同一 run 的 direct / fallback 样本都能按同一套步骤完成 closeout
- 对账不再默认依赖“人工翻 raw mail 再临时解释”

### Slice B：补 latest direct evidence 到 canonical outcome 的绑定

目标：让 `DirectAccepted` 不再只停在“send-side 证据成立”，而是能稳定挂到后续 canonical mail outcome。

本批应完成：

- 冻结 direct 行优先使用的绑定键顺序：
  - `request_id`
  - `transportMessageId` / `ingress_message_id`
  - `last_summary token` 作为较弱回退
- 明确 Android / PC closeout 时必须记录哪些 join 字段
- 把“绑定缺失”从笼统备注收紧成显式 switch-review blocker

closeout 条件：

- 至少一条 fresh direct sample 能不用临时猜测就完成 send-side -> canonical outcome 的稳定对接

### Slice C：输出 `new_task` switch-review note

目标：在不扩 scope 的前提下，给 `new_task` 一个明确的“仍未准备好 / 已可评审”的结论入口。

本批应完成：

- 基于 Slice A/B 的稳定流程，重新给出一次 `new_task` switch-review 结论
- 结论必须二选一：
  - `not_ready_keep_mail_default`
  - `ready_for_direct_default_review`
- 如果存在 open 的 `fallback_required` 或 `switch_blocker` 级别差异，结论只能保持 `not_ready_keep_mail_default`

closeout 条件：

- 下一轮不再需要先争论“该看哪些 artifact、缺哪个 thread、旧样本算不算 blocker”，而是直接进入 switch review

## Phase 5 退出门槛

当前建议把 Phase 5 第一刀的退出门槛收窄为：

- shared artifacts 已进入稳定日常消费
- latest direct evidence 到 canonical outcome 的绑定已足够稳定
- `new_task` 已有一份显式 switch-review note
- mail fallback 仍保持真实可执行

这一步完成后，才允许继续讨论：

- `new_task` 是否进入 `direct-default` 评审
- `reply` / `/status` 是否需要单独 contract freeze

## 当前结论

基于 2026-03-22 已有 authority、same-run positive rows 与 retained pitfalls，当前最稳妥的判断是：

- Phase 4 已经足够收口为 `new_task parity baseline`
- Phase 4 还不足以宣布 `new_task direct-default`
- Phase 5 的第一刀应先做“决策输入 hardening”，而不是继续扩样本范围或提前扩大 flow
