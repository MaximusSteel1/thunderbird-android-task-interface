# TaskMail Next Session Handoff - 2026-03-21 - Phase 3 Detail Live Smoke

## 当前结论

- 已完成一轮真实设备 + live relay 的 Phase 3 `detail` smoke。
- `timeline merge + business_event_key reconciliation` 的代码面已经落地，durable mail 手动刷新路径本轮没有被否定。
- 当前 live blocker 不是 websocket 连不上，而是 live relay 对 `subscribe_session_detail` 返回
  `packet_ack.accepted = false`，错误为 `session_not_found`。
- 因此这轮还不能把 `detail` 不自动刷新直接归因为 Android `TaskMailDirectSessionProjector` /
  `TaskSessionDetailViewModel` 回归。

## 本轮验证结果

- 设备 `TaskMail relay debug` 成功连接 `ws://124.223.41.153:8787/relay`。
- 针对 live thread `thread_086 / phase3-detail-q-20260321_194446-211b05`：
  - workspace 手动刷新后能看到 session
  - detail 手动刷新后能从 `Running -> WaitingUser -> Done`
  - detail 保持打开但不手动刷新时，没有观察到 direct live update
- 工作站侧用同一 token 做 websocket probe 后，live relay 对以下 locator 返回 `session_not_found`：
  - `workspace_id = workspace_d0a3ad8a2abc`
  - `repo_path = E:\projects\android_task_manager`
  - `workdir = .`
  - `session_id = thread_086`
  - `thread_id = thread_086`

## 次级发现

- `E:\projects\mail_based_task_manager\_tmp_live_mail_runner\tasks\thread_086\mail\raw_004.json`
  自身包含两段 `TASK-QUESTION` capsule。
- `detail` 中的 duplicate pending question 更像 mail body duplication / extractor 输入问题，不像这轮
  direct merge 新引入的问题。

## Read First

- `docs/TASKMAIL-DEBUG-VALIDATION.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/TASKMAIL-MAIL-RULES.md`
- `docs/taskmail/planning/android/taskmail-next-session-handoff-2026-03-21-phase3-timeline-merge.md`
- `E:/projects/mail_based_task_manager/docs/plans/phase3_direct_inbound_wire_v1.md`
- `E:/projects/mail_based_task_manager/docs/plans/phase3_direct_inbound_mapping_v1.md`

## 下一步

1. 先确认 live relay 实际绑定的 task-root / session registry，并让它能够 resolve 当前 smoke thread；
   或者改用 relay 已知 task-root 下已存在的 session 做 `subscribe_session_detail` smoke。
2. 在 relay-side session resolution 打通后，重做 Android `detail` live smoke，重点观察：
   - subscribe 后是否立刻收到 `session_snapshot`
   - `WaitingUser -> Done` 期间是否收到后续 `session_update`
   - `gap resubscribe` 后 provisional item 是否被 durable mail suppress
3. 单独开一个小切片处理 duplicate pending question，优先看 mail body / extractor 输入，而不是先改 merge。

## 本次是否改代码/验证

- 已改生产代码：否
- 已改文档：是
- 已做验证：是
