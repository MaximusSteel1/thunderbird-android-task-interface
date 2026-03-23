# TaskMail Next Session Handoff - 2026-03-21 - Phase 3 Direct Detail Overlay

## 当前结论

- Phase 3 已经接通 `detail` 页面的 direct inbound 最小 runtime slice。
- 当前实现范围只覆盖 `active detail` 的 direct subscribe 和 header/question overlay。
- `timeline` 的 durable merge、`business_event_key` 跨源 suppress/replace、canonical `workspace_id` 持久化回本地仓库，仍未实现。

## 本次已完成

- 新增 `RelayTaskMailDirectSessionDetailSubscriber`，按 `phase3-direct-inbound-wire-v1` 发送 `subscribe_session_detail` packet。
- 新增 `ObserveTaskMailDirectSessionDetail`，对当前 detail 建立 relay 连接、发起订阅、消费 `sessionUpdates`、在 gap 后以 `detail_refresh` 重新订阅，并复用 direct 返回的 canonical `workspace_id`。
- `TaskSessionDetailViewModel` 现在会在 mail detail 成功加载后启动 direct observation，并把 direct `status`、`lastSummary`、`pendingQuestions` overlay 到现有 detail UI。
- direct overlay 仍然保留 mail path 的 `replyContext`、timeline、attachments，不做 transport 或 protocol 语义改写。
- 新增 observer tests，并扩展 detail ViewModel tests，覆盖 initial snapshot、gap resubscribe、canonical workspace reuse、quick answer overlay。

## 代码范围

- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/data/relay/RelayTaskMailDirectSessionDetailSubscriber.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/usecase/ObserveTaskMailDirectSessionDetail.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/TaskSessionDetailViewModel.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/TaskMailModule.kt`
- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/domain/usecase/ObserveTaskMailDirectSessionDetailTest.kt`
- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/TaskSessionDetailViewModelTest.kt`

## 下一步建议

1. 在 Android 侧实现 direct/mail timeline merge，按 PC 侧 `business_event_key` 规则 suppress/replace provisional direct items。
2. 评估 canonical `workspace_id` 是否需要回写到 repository/cache，避免后续 detail reopen 仍只能依赖 `repo_path + workdir` fallback。
3. 把 PC 侧 `phase3_direct_inbound_v1` JSON fixtures 接进 Android merge/apply tests，而不是只停留在 projector contract tests。
4. 在条件允许时补一次真实 relay + debug host 的 detail live-update smoke，重点看 gap recovery 和 question overlay。

## Read First

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/TASKMAIL-MAIL-RULES.md`
- `docs/taskmail/planning/android/taskmail-next-development-plan-v0.2.md`
- `E:/projects/mail_based_task_manager/docs/plans/phase3_direct_inbound_wire_v1.md`
- `E:/projects/mail_based_task_manager/docs/plans/phase3_direct_inbound_mapping_v1.md`
- `E:/projects/mail_based_task_manager/docs/plans/phase3_direct_inbound_fixture_package_v1.md`
- `E:/projects/mail_based_task_manager/docs/plans/fixtures/phase3_direct_inbound_v1/manifest.json`

## 本次验证

- `.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest --console=plain`
- `.\gradlew.bat :feature:taskmail:internal:detekt --console=plain`
- `.\gradlew.bat :feature:taskmail:internal:lintDebug --console=plain`

## 未做事项

- 未做设备侧或 debug-host 手工 smoke。
- 未更新 `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`，因为当前工作树已有其他文档改动，本次先用 handoff 记录更安全。
