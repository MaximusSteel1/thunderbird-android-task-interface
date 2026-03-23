# TaskMail Next Session Handoff - 2026-03-22 - Phase 3 Workspace Refresh Closeout

## 当前结论

- `workspace/session list` 不自然刷新的主问题已经在真机上闭环。
- placeholder session filter 生效后，`phase3-workspace-refresh-A73D2C9F11` 不再先出现可点击的 `Unknown` placeholder card。
- 在 canonical `thread_089` detail 内直接回复后，不手动刷新，detail 会自然从 `WaitingUser` 推进到 `Running`，随后推进到 `Done`。
- 从已完成的 detail 返回 workspace 后，不做 `pull-to-refresh`，session card 已立即显示 `Done` 和 `QUESTION_FLOW_OK | A73D2C9F11`。

## 本轮 live 证据

- live thread：`thread_089 / phase3-workspace-refresh-A73D2C9F11`
- reply token：`A73D2C9F11`
- reply 通过 TaskMail debug detail 内置 composer 发出，而不是外部 mail UI
- 不手动刷新 detail 的 watch 结果：
  - `02:30:18`：`cacheStatus = Running`，`pendingQuestions = 0`，`timelineCount = 7`，`lastSummary = Permission: default`
  - `02:31:13`：`cacheStatus = Done`，`pendingQuestions = 0`，`timelineCount = 8`，`lastSummary = QUESTION_FLOW_OK | A73D2C9F11`
  - 同轮 UI dump 从 question/reply composer 自然切到了 done summary
- 返回 workspace 后的 UI dump 直接显示：
  - status：`Done`
  - summary：`QUESTION_FLOW_OK | A73D2C9F11`

## 仍然打开的尾项

- duplicate pending question 仍存在。
- 当前 QUESTION detail / structured reply template 会重复出现两条相同的 `reply_token`。
- 这不只是展示问题：当前必须把重复行都填上值，`Send answers` 才会点亮。
- 结合现有 parser / repository 证据，这更像源 `[QUESTION]` mail / extractor 输入重复，而不是 placeholder session 或 workspace foreground refresh 回归。

## 下次优先事项

1. 沿 `TaskQuestionCapsuleParser` / extractor 输入继续收口 duplicate question 根因。
2. 在修 duplicate question 时补一轮 detail reply UX 验证，确认单问题场景不再被错误提升为 structured reply。
3. 如果要继续做 Phase 3 收尾，可补 `draft / attachment` 在 refresh 下的保持验证。

## Read First

- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/TASKMAIL-MAIL-RULES.md`
- `docs/taskmail/archive/android/handoff/taskmail-next-session-handoff-2026-03-22-phase3-placeholder-session-filter.md`

## 本次是否改代码 / 验证

- 已改生产代码：否（本文件对应的是 post-fix 真机 closeout；代码修复已在上一份 handoff 记录）
- 已改测试代码：否
- 已改文档：是
- 已做新的真机 closeout：是
