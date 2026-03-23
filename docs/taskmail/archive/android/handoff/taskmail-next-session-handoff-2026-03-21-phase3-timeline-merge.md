# TaskMail Next Session Handoff - 2026-03-21 - Phase 3 Timeline Merge

## 当前结论

- 已完成 Android 侧 `detail` timeline 的 mail/direct merge 与 `business_event_key` reconciliation 首轮实现。
- mail timeline 现在会为 durable 事件派生 `business_event_key`；direct projector 会保留 provisional timeline，并在 mail 同 key durable 项到达后 suppress。
- 当前范围只覆盖仓库层、projector、detail ViewModel merge 和对应单测；还没有做 fixture-driven merge/apply 全链路校验，也没有做设备或 relay smoke。

## 本次代码变更

- `TaskTimelineItem` 与 detail cache 增加 `businessEventKeys` 持久化字段。
- `DefaultTaskMailRepository` 为 mail timeline 派生 durable keys：
  - `status/<status>/<event_ts>`
  - `question/<question_set_id>/<event_ts>`
  - `paused/<paused_from_status>/<event_ts>`
  - `terminal/<status>/<event_ts>`
  - `reply/<event_ts>`
- `LegacyTaskMailBodyExtractor` 增加 durable `Reply:` block 提取，用于 reply key 派生。
- `TaskMailDirectSessionProjector` 输出 `provisionalTimeline`，并在 snapshot、gap refresh、mail key 已持久化时 suppress/replace direct items。
- `TaskSessionDetailViewModel` 通过 `TaskTimelineMerge.kt` 合并 mail timeline 与 direct provisional timeline，并按时间排序展示。
- 新增/扩展测试覆盖 terminal/question/paused/reply key 派生、gap refresh 替换、mail suppress direct、detail timeline merge。

## 已确认坑点

- `feature:taskmail:internal` 当前 `lintDebug` 会把 `java.time` 的 `Instant`、`OffsetDateTime`、`LocalDateTime` 识别为 `NewApi`，因为模块 `minSdk` 是 23。
- 这类 timeline/business key 时间处理应优先使用 UTC `SimpleDateFormat`，除非后续明确引入 desugaring 或统一时间工具。

## Read First

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/TASKMAIL-MAIL-RULES.md`
- `docs/taskmail/archive/android/handoff/taskmail-next-session-handoff-2026-03-21-phase3-direct-detail-overlay.md`
- `E:/projects/mail_based_task_manager/docs/plans/phase3_direct_inbound_wire_v1.md`
- `E:/projects/mail_based_task_manager/docs/plans/phase3_direct_inbound_mapping_v1.md`
- `E:/projects/mail_based_task_manager/docs/plans/phase3_direct_inbound_fixture_package_v1.md`

## 下一步

1. 把 PC 侧 `phase3_direct_inbound_v1` fixtures 接入 Android merge/apply tests，减少手写样例与协议漂移。
2. 补强 multi-question、paused、terminal 场景下的 UI timeline 顺序与去重校验，确认 durable mail 到达后 direct provisional 项只保留一份。
3. 在 debug host 或 relay 可用时做一次 detail live-update smoke，重点验证 gap resubscribe 后 provisional item 被 durable mail 替换。

## 本次验证

- `.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest --console=plain`
- `.\gradlew.bat :feature:taskmail:internal:detekt --console=plain`
- `.\gradlew.bat :feature:taskmail:internal:lintDebug --console=plain`

## 本次是否改代码/验证

- 已改代码：是
- 已做验证：是
