# TaskMail Phase 5 `new_task` Guarded Rollout Observation Plan（v0.1）

更新时间：2026-03-23

## 状态

- 本文是当前 `new_task` 主线的 owner planning doc。
- 本文只负责维护 formal-host `new_task` 的当前 verdict、guardrail 和 observation 读法。
- 本文不再依赖旧的 switch-review / decision / activation note 作为必读输入。

## Read First

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/TASKMAIL-MAIL-RULES.md`
- `docs/taskmail/planning/android/taskmail-next-development-plan-v0.2.md`
- `docs/taskmail/planning/android/phase4_dual_stack_parity_checklist.md`
- `docs/taskmail/planning/android/phase4_mismatch_ledger.md`
- `docs/taskmail/planning/android/phase4_rollback_trigger_note.md`

## 当前 verdict

截至 2026-03-23，Android 对 `new_task` 的当前读法保持为：

- `ready_for_direct_default_review`
- `proceed_with_guarded_direct_default_review`
- `no_additional_new_task_activation_config_needed`

## 为什么当前要按 observation 读

- formal-host `new_task` direct-first / mail-fallback 已经落地
- Android latest evidence 已经 durable，并可在正式 `New task` 页面复读
- `requestId`、可用时的 `receiptId`、可用时的 `transportMessageId` 都已进入 current evidence path
- 当前没有新的 confirmed mismatch 需要把主线拉回实施态

因此，`new_task` 当前的正确动作不是继续扩代码，而是：

- 保持当前 verdict
- 继续消费自然出现的 fresh sample
- 只有在 mismatch / rollback signal 出现时才重新扩大工程面

## 当前必须保持的 guardrails

- mail fallback 必须继续保留
- hard rejection 不能偷偷降级成 mail fallback success
- `new_task` 的当前结论不应外推到 `reply` / `/status`
- 不能再为 `new_task` 新增独立 activation/config 文档链

## 当前非目标

- 不把 `reply` / `/status` 并入本主线
- 不新增新的 `new_task` activation/config 开关
- 不把 observation 文档重新拆成多份 decision note

## 何时重新打开这条主线

仅当出现以下任一情况时，才应把 `new_task` 从 observation 拉回 active implementation：

- `phase4_mismatch_ledger.md` 出现直接落在 `new_task` current baseline 上的 confirmed mismatch
- `phase4_rollback_trigger_note.md` 中已有 trigger 被新的 same-run evidence 激活
- formal-host 最新 evidence 在 current installed build 上出现回归

## 当前结论

`new_task` 现在已经不是“继续写规划”的主线，而是“保持读法稳定、等待新信号”的主线。
