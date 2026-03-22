# TaskMail Planning 与历史归档

本目录不是 TaskMail 当前实现事实或邮件协议 authority。

如需先理解 TaskMail 文档整体结构，请先看：

- `docs/taskmail/README.md`

判断 Android TaskMail 当前状态时，优先阅读：

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/TASKMAIL-MAIL-RULES.md`
- `docs/TASKMAIL-DEBUG-VALIDATION.md`

## 当前仍活跃的 planning 文档

本目录下当前仍应视为 active planning 的例外文档有：

- `docs/taskmail/planning/android/taskmail-android-public-plaintext-direct-connect-authority-v0.1.md`
- `docs/taskmail/planning/android/taskmail-android-public-plaintext-direct-connect-plan-v0.1.md`
- `docs/taskmail/planning/android/taskmail-next-development-plan-v0.2.md`
- `docs/taskmail/planning/android/taskmail-phase4-closeout-and-phase5-narrow-plan-v0.1.md`
- `docs/taskmail/planning/android/taskmail-phase5-new-task-switch-review-v0.1.md`
- `docs/taskmail/planning/android/taskmail-phase5-new-task-direct-default-review-decision-v0.1.md`
- `docs/taskmail/planning/android/taskmail-phase5-new-task-direct-default-rollout-activation-note-v0.1.md`
- `docs/taskmail/planning/android/taskmail-phase5-new-task-guarded-rollout-observation-plan-v0.1.md`
- `docs/taskmail/planning/android/taskmail-phase5-reply-status-direct-contract-prerequisites-v0.1.md`
- `docs/taskmail/planning/android/taskmail-phase5-reply-status-pc-coordination-checklist-v0.1.md`
- `docs/taskmail/planning/android/taskmail-phase5-reply-status-pc-message-template-v0.1.md`
- `docs/taskmail/planning/android/taskmail-phase5-reply-status-pc-alignment-readout-v0.1.md`
- `docs/taskmail/planning/android/taskmail-phase5-reply-status-android-implementation-plan-v0.1.md`
- `docs/taskmail/planning/android/taskmail-phase4-dual-stack-boundary-freeze-v0.1.md`
- `docs/taskmail/planning/android/phase4_dual_stack_parity_checklist.md`
- `docs/taskmail/planning/android/phase4_mismatch_ledger.md`
- `docs/taskmail/planning/android/phase4_rollback_trigger_note.md`
- `docs/taskmail/planning/android/taskmail-phase0-public-plaintext-baseline-v1.md`
- `docs/taskmail/planning/android/taskmail-phase2-direct-outbound-contract-v0.1.md`
- `docs/taskmail/planning/android/taskmail-next-session-handoff-2026-03-22-phase5-android-implementation-plan.md`

这些文档当前的使用方式是：

- `taskmail-android-public-plaintext-direct-connect-authority-v0.1.md` 用于冻结当前 public plaintext 宏观方向
- `taskmail-android-public-plaintext-direct-connect-plan-v0.1.md` 用于 Android 侧分阶段执行计划
- `taskmail-next-development-plan-v0.2.md`、`taskmail-phase4-dual-stack-boundary-freeze-v0.1.md` 与三份 `phase4_*`
  文档共同构成当前 Android 侧 Phase 4 planning 集
- `taskmail-phase4-closeout-and-phase5-narrow-plan-v0.1.md` 与当前一组 `taskmail-phase5-new-task-*.md`
  文档共同构成当前 Android 侧 `new_task` guarded direct-default review / observation planning 集
- `taskmail-phase5-reply-status-direct-contract-prerequisites-v0.1.md` 用于把 `reply` / `/status` 进入 direct contract
  freeze 之前必须先关闭的协议前提、范围切分与 PC 配合项显式写清
- `taskmail-phase5-reply-status-pc-coordination-checklist-v0.1.md` 用于把上面那份 prerequisites 压缩成可直接发给
  相邻 PC 侧的短版 review checklist
- `taskmail-phase5-reply-status-pc-message-template-v0.1.md` 用于把 checklist 再压缩成可直接发给相邻 PC 侧的
  短消息模板
- `taskmail-phase5-reply-status-pc-alignment-readout-v0.1.md` 用于回写相邻 PC 侧已经给出的 repo-side reading 与
  shared planning-layer contract，并明确哪些点已对齐、哪些点仍未成为 Layer 1 authority、Android 为什么可以进入
  实现规划但还不能误写成 current protocol
- `taskmail-phase5-reply-status-android-implementation-plan-v0.1.md` 用于把 shared contract 落到 Android 侧
  实现规划，并记录同日后续 `Batch A` / `Batch B` / `Batch C` 的 guarded code 落地状态与 2026-03-23 的
  `Batch D` durable evidence 首段；当前剩余工作已收敛到 live / closeout 边界
- `taskmail-phase0-public-plaintext-baseline-v1.md` 用于记录 public IP、端口、endpoint 与 fallback freeze
- `taskmail-phase2-direct-outbound-contract-v0.1.md` 用于记录第一版 direct `new task` payload 与 fallback freeze
- 最新 handoff note 只用于短周期续接上下文，不应提升为长期 authority

## 其余文档如何使用

除上面点名的 active 文档外，本目录下大多数文件都应视为：

- 历史材料
- 实施参考
- 已完成 slice 的 handoff 记录
- 平台导入背景资料

这些文件可以帮助理解来龙去脉，但不能覆盖当前 Android protocol / implementation authority。

截至 2026-03-21，较早期的 Android 中间 planning 文档、已完成的 handoff 记录，以及旧的
mail-first / TLS-gated planning 线已经做过一轮清理。

因此，当前实现事实应该回到以下三份主文档重建：

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/TASKMAIL-MAIL-RULES.md`

## 仍有参考价值的材料

### Platform 历史 / 更高层背景

- [Task Manager Platform Design (v0.2)](platform/task-manager-platform-design-v0.2.md)
- [Task Manager Platform Delivery (v0.2)](platform/task-manager-platform-delivery-v0.2.md)
- [Task Manager Tool Interface Spec (v0.1)](platform/task-manager-tool-interface-spec-v0.1.md)
- [Task Manager Implementation Roadmap (v0.1)](platform/task-manager-implementation-roadmap-v0.1.md)
- [Task Manager Schemas (v0.1)](platform/task-manager-schemas-v0.1.md)

## 维护说明

- `android/` 目录包含 Android 侧历史 planning 与实施参考文档
- `platform/` 目录包含导入的 Task Manager platform 背景资料
- `platform/` 文档只有在新的 authority 文档明确提升时，才可重新视为 Android / PC 活跃路线图
- `platform/schemas/task-manager-schemas-v0.1/` 保留了从提供的 zip 包中抽出的 JSON Schema
