# TaskMail Next Session Handoff - 2026-03-21 - Phase 3 Detail Live Smoke Retest

## 当前结论

- 前一轮 live relay `session_not_found` blocker 已解除。
- 工作站侧 `subscribe_session_detail` probe 现在能收到 `packet_ack.accepted = true` 和
  `session_update(update_type = session_snapshot)`。
- 真实设备上，`thread_087 / phase3-detail-live-20260321_202113-1dbf6b` 的 detail 页在不手动刷新的情况下，
  已经自然完成 `WaitingUser -> Running -> Done`。
- 因此 Phase 3 `timeline merge + business_event_key reconciliation` 没有在这轮 live retest 里暴露新的
  回归。

## 本轮验证结果

- live thread：
  `thread_087 / phase3-detail-live-20260321_202113-1dbf6b`
- reply token：
  `BAF751DE25`
- reply mail：
  `E:\projects\mail_based_task_manager\_tmp_live_mail_question_smoke\phase3-detail-live-20260321_202113-1dbf6b\answer_sent.json`

设备侧观察：

- detail 初始停在 `WaitingUser`
- 不做手动 `pull-to-refresh`
- 约 `20:27:32` 的后台 IMAP fetch 后，detail 进入 `Running`
- 约 `20:28:37` 的 `[DONE]` fetch 后，再过一个自然处理窗口，detail 进入 `Done`
- 最终 summary 为 `QUESTION_FLOW_OK | BAF751DE25`

工作站侧补充确认：

- 前一轮同 locator 的 relay probe 不再返回 `session_not_found`
- 当前更窄的未闭环点不是 session resolution，而是设备侧没有抓到足够明确的 direct websocket 日志，
  还不能证明 `session_update` 比 IMAP mail sync 更早驱动 detail

## 次级发现

- `thread_087` detail 里的 pending question 仍显示两次
- 这与前一轮 `thread_086` 的现象一致，更像原始 `[QUESTION]` mail / extractor 输入重复，而不是
  `TaskTimelineMerge` 新引入的重复

## Read First

- `docs/TASKMAIL-DEBUG-VALIDATION.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/TASKMAIL-MAIL-RULES.md`
- `docs/taskmail/planning/android/taskmail-next-session-handoff-2026-03-21-phase3-detail-live-smoke.md`
- `docs/taskmail/planning/android/taskmail-next-session-handoff-2026-03-21-phase3-timeline-merge.md`

## 下一步

1. 如果要把 Phase 3 的 direct live-update 证据再做实，给 Android detail 订阅路径补更明确的 debug log，
   然后重做一次 `session_snapshot/session_update` 观测。
2. 单独开一个小切片处理 duplicate pending question，优先检查 `[QUESTION]` mail 原文和 extractor 输入，
   不要先动 merge。
3. 继续做剩余的 validation-closeout，尤其是 draft/attachment 保持场景和更系统的 workspace/detail refresh
   证据。

## 本次是否改代码/验证

- 已改生产代码：否
- 已改测试代码：否
- 已改文档：是
- 已做验证：是
