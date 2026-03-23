# TaskMail Phase 5 `reply` / `/status` Android 实现规划（v0.1）

更新时间：2026-03-23

## 状态

- 本文是当前 `reply` / `/status` 主线的 owner planning doc。
- 本文只维护 guarded direct lane 的当前 scope、当前事实、当前 blocker 和 closeout 顺序。
- 旧的 prerequisites / checklist / alignment notes 已降级到 archive planning。

## Read First

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/TASKMAIL-MAIL-RULES.md`
- `docs/taskmail/planning/android/taskmail-next-development-plan-v0.2.md`

## 当前 scope

当前 v1 scope 固定为：

- `current-session plain reply`
- `current-session /status`

当前明确不在 scope 内：

- quick answer
- 多问题 `Answers:`
- paused `/resume`
- attachment continuation
- targeted-session variant
- cross-workspace switching

## 当前事实

截至 2026-03-23，这条主线应这样读：

- Android guarded direct lane 已落地
- canonical target identity 仍按 `workspace_id + session_id`
- latest session-action evidence 已能 durable 保存并在 detail 页面复读
- Android fallback evidence 现在也保留 `requestId`，并在可用时保留 `receiptId` / `transportMessageId`
- `thread_105` 的 formal-host rerun 已正向证明：
  - `/status` 可 direct accepted，并回收到 canonical `[STATUS]`
  - plain reply 可 direct accepted，并回收到 `[ACCEPTED] -> [RUNNING] -> [DONE]`

## 当前 blocker 与 closeout 重点

这条主线当前最大的工作不再是“把 lane 做出来”，而是“把 closeout 做强”。

当前仍未完全闭环的点有：

- PC-side fallback canonical artifacts 仍缺 `action_type`
- PC-side fallback canonical artifacts 仍缺 `target_session_identity`
- PC-side fallback canonical artifacts 仍缺 action-specific ingress anchors
- 当前 installed build 上，带最新 Android fallback evidence 的 same-run stronger bind 仍需要继续做窄 rerun

## 当前已确认的运行态 pitfall

- relay-visible `task_root` 如果是旧快照，server-side current-session locator 会直接拒绝 `workspace/session` 解析
- 因此 formal-host direct smoke 前，必须先保证 relay 可见的 task root 与本地 live task store 同步

这个 pitfall 影响的是 closeout 与验证前置条件，不应被误写成 Android send lane 本身的设计错误。

## 当前执行顺序

1. 保持当前 v1 scope，不再继续拉新 direct 语义
2. 先补强 same-run bind 与 fallback artifact
3. 再在需要时做更窄的 fresh rerun
4. 只有 closeout 完整后，才讨论是否扩大 direct scope

## 当前结论

`reply` / `/status` 当前是 guarded closeout 主线，而不是全量 direct 化主线。
