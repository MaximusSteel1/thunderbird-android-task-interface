# TaskMail Phase 5 `new_task` Switch Review Note（v0.1）

更新时间：2026-03-22

## 状态

- 本文是 Phase 5 Slice C 的首版 `new_task` switch-review note。
- 当前结论已推进为：`ready_for_direct_default_review`
- 本文只评审 `new_task`，不扩到 `reply`、`/status` 或更宽的 direct read-side transport。
- 本文负责沉淀 evidence readout；后续 guarded decision 见
  `docs/taskmail/planning/android/taskmail-phase5-new-task-direct-default-review-decision-v0.1.md`；
  activation 边界见
  `docs/taskmail/planning/android/taskmail-phase5-new-task-direct-default-rollout-activation-note-v0.1.md`。

## 先读这些文档

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/taskmail/planning/android/phase4_dual_stack_parity_checklist.md`
- `docs/taskmail/planning/android/phase4_mismatch_ledger.md`
- `docs/taskmail/planning/android/phase4_rollback_trigger_note.md`
- `docs/taskmail/planning/android/taskmail-phase4-closeout-and-phase5-narrow-plan-v0.1.md`
- `E:\projects\mail_based_task_manager\docs\plans\phase4_dual_stack_parity_checklist.md`
- `E:\projects\mail_based_task_manager\docs\plans\phase4_mismatch_ledger.md`
- `E:\projects\mail_based_task_manager\docs\plans\phase4_rollback_trigger_note.md`

## 当前 review 输入

### 已经成立的正向输入

- covered flow 已冻结在 `new_task`
- same-run positive rows 已存在：
  - `thread_083`
  - `thread_093`
  - `thread_094`
  - `thread_095`
  - `thread_098`
- `thread_084` 已降级为 historical retained-artifact gap，不再作为当前 blocker
- shared artifacts 的消费顺序已冻结为：
  - `parity checklist`
  - `mismatch ledger`
  - `rollback trigger`
- Android / PC shared artifacts 现已补入：
  - `daily_closeout_bundle`
  - `direct accepted` 行 same-run bind 顺序
  - evidence gap 与 mismatch / rollback 的分界
- `thread_097` 已把 freeze 之后的第一条 fresh `daily_closeout_bundle` closeout 跑通：
  - `thread_097/runs/20260322_184156_afc8/taskmail_daily_closeout_bundle.json` 同时冻结了 Android latest send evidence、Android terminal summary、PC canonical outcome、PC terminal mail
  - `same_run_bind.effective_bind_level = transport_message_id`
  - `same_run_bind.matched_fields = transport_message_id + last_summary`
  - helper note = `android_request_id_missing`
- `thread_098` 已在安装当前 formal-host 构建后关闭 `request_id` 首键 bind：
  - Android latest send record 现已带出 `requestId = req_2a1e0790bdd54194bdce17e96c378e5e`
  - `thread_098/runs/20260322_190954_7e1f/taskmail_daily_closeout_bundle.json` 记录 `same_run_bind.effective_bind_level = request_id`
  - `same_run_bind.matched_fields = request_id + transport_message_id + last_summary`
  - helper notes 为空
- 当前 `phase4_mismatch_ledger.md` 仍为空表，且空表读法已明确：这不等于全对齐，只表示当前没有 confirmed mismatch
- mail fallback 仍保持活路径，不是死代码

### 当前仍未闭环的输入

- `thread_097` 已关闭 post-freeze `daily_closeout_bundle` 复用边界
- `thread_098` 已关闭 `request_id -> transportMessageId / ingress_message_id -> last_summary` 的 fresh formal-host bind 边界
- 因此，这份 switch-review note 当前已不再卡在 narrow evidence blocker；下一步是进入 guarded direct-default review，而不是继续补同类 fresh sample

## Gate Readout

| gate_item | status | notes |
| --- | --- | --- |
| covered flow freeze | `pass` | 当前仍只评审 `new_task` |
| same-run positive matrix rows | `pass` | `thread_083` / `thread_093` / `thread_094` / `thread_095` / `thread_098` 已提供正向样本 |
| historical gap downgrade | `pass` | `thread_084` 不再构成 active blocker |
| fallback remains executable | `pass` | 当前 evidence 未显示 fallback path 腐化或失效 |
| daily closeout workflow post-freeze rerun | `pass` | `thread_097` 已生成 `taskmail_daily_closeout_bundle.json`，并闭环 Android latest send evidence、Android terminal summary、PC canonical outcome、PC terminal mail |
| latest direct evidence -> canonical outcome bind under frozen workflow | `pass` | `thread_098` 已在当前 formal-host 构建上闭环 `request_id -> transportMessageId / ingress_message_id -> last_summary`，且 helper 不再给出 `android_request_id_missing` |

## 当前结论

当前 `new_task` 的 switch-review 结论是：

- `ready_for_direct_default_review`

原因是：

- 先前 open 的两个 narrow evidence gate 现在都已关闭：
  - `thread_097` 已证明 freeze 后的 `daily_closeout_bundle` 可在 fresh sample 上复用
  - `thread_098` 已证明当前 formal-host 构建上的 Android latest send evidence 可以带出 `requestId`，并按既定顺序完成 same-run bind
- 因此，当前 `new_task parity baseline` 已具备进入 guarded direct-default review 的证据条件
- 这仍不等于“立即切换 direct-default 已被授权”；它只表示后续可以围绕既有 rollback / mismatch guardrails 做显式 review，而不需要继续先补同类 evidence blocker

因此，当前不应把下述事项误读成 blocker：

- `thread_084` 的历史 retained-artifact 缺口
- 再做一轮 relay re-provision
- 把 `reply` / `/status` 提前拉进当前评审范围

## 进入 Direct-Default Review 后仍应保持

1. 当前 direct-default review 仍只覆盖 `new_task`，不提前扩到 `reply` / `/status`。
2. 后续 fresh sample 若继续进入评审，仍必须按固定顺序完成 same-run bind：
   - `request_id`
   - `transportMessageId` / `ingress_message_id`
   - `last_summary token` 仅作弱回退
3. 如果后续 fresh sample 再次退回到 `transportMessageId` / `ingress_message_id` 才能闭环，或 helper 重新出现 `android_request_id_missing`，则本结论应回退为 `not_ready_keep_mail_default`。

## 本轮未做的事

- 没有新增生产代码
- 没有新增测试代码
- 已做新的 `:app-thunderbird:installFullDebug` 安装与 formal-host / shared-artifact fresh closeout，但没有新增 Gradle 测试命令

这是一份基于现有 authority 与 shared artifacts 的文档化 switch-review 结论。
