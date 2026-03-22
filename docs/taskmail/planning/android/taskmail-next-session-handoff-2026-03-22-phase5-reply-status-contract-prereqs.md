# TaskMail Next Session Handoff - 2026-03-22 - Phase 5 Reply / Status Contract Prereqs

## 当前决策

- 当前 `new_task` 主线仍保持：
  - `ready_for_direct_default_review`
  - `proceed_with_guarded_direct_default_review`
  - `no_additional_new_task_activation_config_needed`
- 在这个前提下，Android 仓已继续推进到下一份 planning note：
  - `docs/taskmail/planning/android/taskmail-phase5-reply-status-direct-contract-prerequisites-v0.1.md`
- 这份 note 的作用不是授权实现 `reply` / `/status` direct path，而是把进入 direct contract freeze 之前必须先关闭的前提显式写清。

## Read First

- `docs/TASKMAIL-MAIL-RULES.md`
- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/taskmail/planning/android/taskmail-phase5-new-task-guarded-rollout-observation-plan-v0.1.md`
- `docs/taskmail/planning/android/taskmail-phase5-reply-status-direct-contract-prerequisites-v0.1.md`
- `E:\projects\mail_based_task_manager\docs/current/mail_protocol.md`
- `E:\projects\mail_based_task_manager\docs/current/android_reply_method_rules.md`

## 本次代码与验证

- Android 生产代码改动：无
- Android 测试代码改动：无
- planning / handoff 文档改动：有
- 新的 Gradle / 设备验证：无

## 本轮结论

- 当前不把 `TaskSessionDetailViewModel` 接到 direct seam
- 当前不猜 direct `reply` 或 direct `/status` 的最终 packet schema
- 当前先要求 PC 侧在 current-protocol 层关闭这些问题：
  - 第一批 action scope
  - contract ownership
  - session identity / reply-anchor 等价物
  - mode-driven reply semantics
  - `/status` canonical outcome
  - machine-readable error classification 与 closeout artifact 方案

## 下一步最合理顺序

1. Android 侧继续保持 `new_task` guarded observation 与 authority 同步。
2. 如果要推动 `reply` / `/status`，先把上面这些 PC 配合项提成明确 review 输入。
3. 只有在这些前提被 current-protocol 层冻结后，才起 Android 实现批次。
