# TaskMail Android 下一阶段开发计划（v0.2）

更新时间：2026-03-23

## 状态

- 本文是当前 Android TaskMail 的总主线 roadmap。
- 本文不承担实现 truth，也不承担验证证据 authority。
- 本文只回答：当前有哪些 active 主线，它们各自处于什么阶段，下一步先后顺序是什么。

## Read First

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-MAIL-RULES.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/taskmail/planning/android/taskmail-phase5-new-task-guarded-rollout-observation-plan-v0.1.md`
- `docs/taskmail/planning/android/taskmail-phase5-reply-status-android-implementation-plan-v0.1.md`
- `docs/taskmail/planning/android/taskmail-phase5-project-sync-android-implementation-plan-v0.1.md`

## 当前主线图

截至 2026-03-23，Android TaskMail 的活跃工程主线应固定为三条：

1. `new_task`
2. `reply` / `/status`
3. `[SYNC] Project list`

### 1. `new_task`

当前读法：

- formal-host `new_task` direct-first / mail-fallback 已经落地
- durable evidence、request-first bind、review surface 都已经存在
- 当前阶段不是继续开新 transport seam，也不是继续加 activation/config
- 当前阶段是 observation / rollback guardrail

因此：

- 没有 fresh mismatch / rollback signal 时，不应再为 `new_task` 继续扩 planning 面积

### 2. `reply` / `/status`

当前读法：

- guarded direct lane 已经存在
- v1 scope 仍只限 `current-session plain reply` 与 `current-session /status`
- `thread_105` 已正向证明 direct accepted path 可行
- 当前剩余工作不是继续拉新 scope，而是把 closeout 做强

因此：

- 继续围绕 stronger bind、PC fallback artifact gaps、relay-visible task root 前置条件推进

### 3. `[SYNC] Project list`

当前读法：

- `Project list` 读取、渲染、`Use this repo` 回填已经存在
- `[SYNC]` request 当前已经推进到 direct-first
- canonical `[SYNC] Project Folder List` mail 仍是唯一结果面
- 当前问题不再是 Android 15 秒早超时，而是上游回流时间线仍可能偏慢

因此：

- 这条主线当前属于 active implementation / live closeout 阶段

## 当前建议顺序

1. 先保持 `new_task` 只读为 observation 主线，不再制造新的 decision note
2. 再优先收口 `reply` / `/status` closeout，因为 direct lane 已有正向 live 样本，但强绑定还未最终关单
3. 同步推进 `[SYNC]`，重点拿到 direct request 到 canonical reply 的同轮时间线，判断 Android follow-up refresh 是否需要扩大

## 当前 reference docs

以下文档仍有现实约束价值，但不再是默认主线入口：

- `taskmail-android-public-plaintext-direct-connect-authority-v0.1.md`
- `taskmail-android-public-plaintext-direct-connect-plan-v0.1.md`
- `taskmail-phase0-public-plaintext-baseline-v1.md`
- `taskmail-phase2-direct-outbound-contract-v0.1.md`
- `taskmail-phase4-dual-stack-boundary-freeze-v0.1.md`
- `phase4_dual_stack_parity_checklist.md`
- `phase4_mismatch_ledger.md`
- `phase4_rollback_trigger_note.md`

## 当前结论

当前最重要的规划动作不是继续扩文档，而是稳定以下读法：

- `new_task` 已从实施主线降到观察主线
- `reply` / `/status` 仍是 guarded closeout 主线
- `[SYNC] Project list` 是新近抬升出来的 active 主线，必须从 handoff 提升为 owner planning，而不是继续躺在会话记录里
