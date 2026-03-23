# TaskMail 主线入口

更新时间：2026-03-23

本文是 Android 仓库内 TaskMail 文档的默认入口。

默认阅读只回答三个问题：

- Android TaskMail 现在已经做到什么程度
- 当前为什么只能这样读，哪些边界不能误写
- 接下来真正活跃的主线是什么

## 默认阅读顺序

1. `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
2. `docs/TASKMAIL-MAIL-RULES.md`
3. `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
4. `docs/taskmail/planning/android/taskmail-next-development-plan-v0.2.md`

只有在下列场景再继续扩展阅读：

- 需要设备 / debug / adb 路径时：`docs/TASKMAIL-DEBUG-VALIDATION.md`
- 需要背景上下文时：`docs/taskmail_project_overview.md`
- 需要当前活跃 planning 细节时：`docs/taskmail/planning/README.md`
- 需要追历史证据、handoff 或旧 planning 时：`docs/taskmail/archive/README.md`

## 当前 authority 分层

- 当前实现真相：`docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- 协议与语义 authority：`docs/TASKMAIL-MAIL-RULES.md`
- 当前验证摘要：`docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- 调试 / 设备路径：`docs/TASKMAIL-DEBUG-VALIDATION.md`

这些文档共同回答当前事实；历史 planning 与 handoff 不再承担当前 authority。

## 当前活跃主线

- 总主线 roadmap：`docs/taskmail/planning/android/taskmail-next-development-plan-v0.2.md`
- `new_task` 观察与 guardrail：`docs/taskmail/planning/android/taskmail-phase5-new-task-guarded-rollout-observation-plan-v0.1.md`
- `reply` / `/status` guarded direct closeout：`docs/taskmail/planning/android/taskmail-phase5-reply-status-android-implementation-plan-v0.1.md`
- `[SYNC] Project list` direct-request 主线：`docs/taskmail/planning/android/taskmail-phase5-project-sync-android-implementation-plan-v0.1.md`

## 仍有现实约束价值的 reference docs

- `docs/taskmail/planning/android/taskmail-android-public-plaintext-direct-connect-authority-v0.1.md`
- `docs/taskmail/planning/android/taskmail-android-public-plaintext-direct-connect-plan-v0.1.md`
- `docs/taskmail/planning/android/taskmail-phase0-public-plaintext-baseline-v1.md`
- `docs/taskmail/planning/android/taskmail-phase2-direct-outbound-contract-v0.1.md`
- `docs/taskmail/planning/android/taskmail-phase4-dual-stack-boundary-freeze-v0.1.md`
- `docs/taskmail/planning/android/phase4_dual_stack_parity_checklist.md`
- `docs/taskmail/planning/android/phase4_mismatch_ledger.md`
- `docs/taskmail/planning/android/phase4_rollback_trigger_note.md`

这些文档可以帮助解释当前边界，但不再是默认阅读路径。

## archive 的用途

`docs/taskmail/archive/` 只承接三类材料：

- 历史 handoff
- 退役 planning / phase 文档
- 长版验证历史

如果你只是想知道“现在是什么”，不要先读 archive。

## 后续维护规则

- 不再把 `taskmail-next-session-handoff-*` 放回默认阅读路径。
- 同一条 active 主线只保留一份 owner planning 文档；状态变化直接回写 owner doc。
- 文档更新顺序固定为：`MAIL-RULES -> CURRENT-STATUS -> VALIDATION-LEDGER -> active planning`。
- 运行过程、重试过程、临时样本一律优先进入 archive，而不是继续堆进 authority 文档。
