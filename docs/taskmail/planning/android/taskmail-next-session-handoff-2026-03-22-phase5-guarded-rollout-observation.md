# TaskMail Next Session Handoff - 2026-03-22 - Phase 5 Guarded Rollout Observation

## 当前决策

- 当前 `new_task` 仍保持：
  - `ready_for_direct_default_review`
  - `proceed_with_guarded_direct_default_review`
  - `no_additional_new_task_activation_config_needed`
- 当前 Android 侧继续推进 Phase 5 的主线，不是新增 `new_task` 生产代码，而是 guarded rollout / observation。
- `reply`、`/status` 与更宽的 read-side direct-default 继续留在 scope 外。
- 本轮新增了一份显式 observation plan：
  - `docs/taskmail/planning/android/taskmail-phase5-new-task-guarded-rollout-observation-plan-v0.1.md`
- 这份 plan 把三件事写清楚了：
  - Android 侧后续 fresh sample 的最小留存与 closeout discipline
  - 什么时候要把 verdict 回退为 `not_ready_keep_mail_default`
  - 如果后续要继续推进，需要 PC 侧先配合提供哪些 artifact / contract freeze

## Read First

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/TASKMAIL-MAIL-RULES.md`
- `docs/taskmail/planning/android/taskmail-phase5-new-task-switch-review-v0.1.md`
- `docs/taskmail/planning/android/taskmail-phase5-new-task-direct-default-review-decision-v0.1.md`
- `docs/taskmail/planning/android/taskmail-phase5-new-task-direct-default-rollout-activation-note-v0.1.md`
- `docs/taskmail/planning/android/taskmail-phase5-new-task-guarded-rollout-observation-plan-v0.1.md`
- `docs/taskmail/planning/android/taskmail-next-development-plan-v0.2.md`
- `E:\projects\mail_based_task_manager\docs/current/mail_protocol.md`

## 本次代码与验证

- Android 生产代码改动：无
- Android 测试代码改动：无
- planning / handoff 文档改动：有
- 新的 Gradle / 设备验证：无

## 当前最合理的下一步

1. 继续保持 Android authority / handoff 与 `no_additional_new_task_activation_config_needed` 一致。
2. 如果后续自然出现 fresh formal-host `new_task` sample，继续按 observation plan 消费 shared artifacts，而不是回到 old-style 人工拼接。
3. 如果需要相邻 PC 侧配合，优先提出：
   - `canonical_summary.json`
   - `taskmail_daily_closeout_bundle.json`
   - `request_id` / `ingress_message_id` / `terminal_mail_subject` 保留
4. 只有在 `new_task` guarded observation 不再需要继续盯 current gate 时，才重新评估 `reply` / `/status` 的 direct contract freeze。

## 本轮范围说明

- 这是一轮 Android 仓内的 planning 推进，不包含 PC 仓代码修改。
- 如果后续需要 PC 侧配合，应以“提出 artifact / contract 要求”为主，不在本仓里替对方实现。
