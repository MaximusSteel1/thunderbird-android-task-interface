# TaskMail Next Session Handoff - 2026-03-22 - Phase 3 Durable Mail Closeout

## 当前结论

- `thread_088 / phase3-direct-log-eb51a4` 这轮 live 验证已经补上了新的正向证据：
  detail 在不手动 `pull-to-refresh` 的情况下，最终可以依靠 `durable mail sync` 自然推进到 `Done`
- 设备日志已经抓到 `thread_088` 的 `[ACCEPTED]`、`[RUNNING]`、`[DONE]` 三封状态 mail 被 IMAP sync 拉取，
  且 `TaskSessionDetailViewModel` 在 detail 可见期间记录了 local store change
- 但从同一个已完成 detail 返回 workspace 后，session card 仍停留在旧的 `WaitingUser / Waiting`
  与旧 summary；静置约 `25s` 也没有自然纠正
- 只有对 workspace 列表手动做一次 `pull-to-refresh` 后，session card 才立即更新为 `Done`，summary 变成
  `QUESTION_FLOW_OK | 1FAE661EFB`

## 本轮验证结果

- live thread：`thread_088 / phase3-direct-log-eb51a4`
- reply token：`1FAE661EFB`
- 约束：detail 保持打开，不手动刷新，直接发送 QUESTION reply

detail 侧：

- reply 前，pending question 与 composer 模板都出现两行重复的 `live_mailbox_answer`
- `+55s` 的 UI dump 里，timeline 已出现 outgoing `Answers:`，且同一答案行重复两次
- `+130s` 的 UI dump 里，detail 自然推进到 `Done`

workspace 侧：

- 返回 workspace 后，目标 session card 初始仍显示 `WaitingUser / Waiting`
- 静置约 `25s` 后再次抓取，仍未自然更新
- 手动 `pull-to-refresh` 后立即变成 `Done`，summary 为 `QUESTION_FLOW_OK | 1FAE661EFB`

## 次级发现

- duplicate pending question / duplicate answer line 在本轮依旧存在
- 结合 `TaskQuestionCapsuleParserTest` 与 `DefaultTaskMailRepositoryPendingQuestionsTest` 的既有结论，
  这仍更像源 `[QUESTION]` mail / extractor 输入重复，而不是 `timeline merge + business_event_key reconciliation`
  新引入的重复

## Read First

- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/TASKMAIL-DEBUG-VALIDATION.md`
- `docs/TASKMAIL-MAIL-RULES.md`
- `docs/taskmail/archive/android/handoff/taskmail-next-session-handoff-2026-03-21-phase3-detail-live-smoke-retest.md`
- `docs/taskmail/archive/android/handoff/taskmail-next-session-handoff-2026-03-21-phase3-timeline-merge.md`

## 下一步

1. 如果要继续收尾 Phase 3 refresh/live-update，优先把 workspace/session list 的自然刷新边界单独固化：
   确认是 view-model 订阅范围、返回导航后的重载时机，还是列表页只在显式 refresh 时才重投影
2. 继续补 item 16 一类尚未关闭的场景，尤其是 detail 编辑态下的 draft / attachment 保持
3. 如果需要更强的 direct-vs-mail ordering 证据，再做一轮更窄的日志抓取，专门对齐
   `session_update`、`ImapSync` 和 UI 状态变化时间点

## 本次是否改代码 / 验证

- 已改生产代码：否
- 已改测试代码：否
- 已改文档：是
- 已做验证：是
