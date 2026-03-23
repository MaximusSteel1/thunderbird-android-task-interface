# TaskMail Planning 索引

更新时间：2026-03-23

本目录只保留当前仍然活跃的 planning owner docs 与少量 reference docs。

它不承担当前实现事实，也不承担验证证据 authority。

## 当前 active planning

- 总体架构：`android/taskmail-unified-control-plane-architecture-v0.1.md`
- 收敛蓝图：`android/taskmail-unified-control-plane-cutover-map-v0.1.md`
- 共享协议：`android/taskmail-android-pc-control-artifact-contract-v0.1.md`
- 开发条件：`android/taskmail-android-pc-communication-development-conditions-v0.1.md`
- probe payload：`android/taskmail-transport-probe-payload-contract-v0.1.md`
- 通讯 harness：`android/taskmail-transport-observability-harness-v0.1.md`
- 总主线：`android/taskmail-next-development-plan-v0.2.md`
- `new_task`：`android/taskmail-phase5-new-task-guarded-rollout-observation-plan-v0.1.md`
- `reply` / `/status`：`android/taskmail-phase5-reply-status-android-implementation-plan-v0.1.md`
- `[SYNC] Project list`：`android/taskmail-phase5-project-sync-android-implementation-plan-v0.1.md`

## 当前 reference docs

- `android/taskmail-android-public-plaintext-direct-connect-authority-v0.1.md`
- `android/taskmail-android-public-plaintext-direct-connect-plan-v0.1.md`
- `android/taskmail-phase0-public-plaintext-baseline-v1.md`
- `android/taskmail-phase2-direct-outbound-contract-v0.1.md`
- `android/taskmail-phase4-dual-stack-boundary-freeze-v0.1.md`
- `android/phase4_dual_stack_parity_checklist.md`
- `android/phase4_mismatch_ledger.md`
- `android/phase4_rollback_trigger_note.md`

## archive 在哪里

- handoff：`../archive/android/handoff/`
- 历史 planning / phase：`../archive/android/planning/`
- 长版验证历史：`../archive/validation/`

## 当前 owner 规则

- 同一条 active 主线只保留一份 owner planning 文档。
- 当 owner doc 需要拆出“收敛蓝图”或“调试 / harness”这类配套设计时，允许少量 companion docs 与 owner doc 并存，但它们必须显式依附于该 owner doc。
- 只有出现新的主线阶段边界时，才允许新建 owner doc。
- 细碎的会话续接、live smoke 过程、运行态 pitfall，默认进入 archive handoff。
- active planning 的 `Read First` 不再要求 handoff 作为必读输入。
