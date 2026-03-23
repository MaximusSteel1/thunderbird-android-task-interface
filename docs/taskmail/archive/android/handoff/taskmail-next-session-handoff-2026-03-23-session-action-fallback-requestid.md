# TaskMail Next Session Handoff - 2026-03-23 - Session Action Fallback RequestId

## 当前决策

- Android 侧已把 `TaskMailDirectSessionActionResult.FallbackToMail` / `Rejected` 的 `requestId`，以及可用时的 `receiptId` / `transportMessageId`，贯通到 `RunTaskMailDirectOrFallback`、`TaskMailSessionActionSendRecord` 和 detail latest-evidence review surface。
- 这次改动只补 durable-evidence / strong-bind 所需的本地锚点，不改变 `reply`、`/status`、mail fallback、hard rejection 的既有协议语义。
- `thread_021` / `thread_022` 的 VPS retest blocker 仍按 PC / VPS 侧 current-session locator 问题处理，不在本次 Android 修复范围内。

## Read First

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/TASKMAIL-MAIL-RULES.md`
- `docs/taskmail/archive/android/handoff/taskmail-next-session-handoff-2026-03-23-phase5-batch-d-durable-evidence.md`
- `docs/taskmail/archive/android/handoff/taskmail-next-session-handoff-2026-03-23-vps-retest.md`

## 本次代码与验证

- Android 生产代码改动：有
- Android 测试代码改动：有
- authority / handoff 文档改动：有
- 新的 Gradle 验证：有
- 新的设备验证：无

已执行的验证：

- `.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest --console=plain --tests "net.thunderbird.feature.taskmail.internal.data.relay.RelayTaskMailDirectSessionActionSenderTest" --tests "net.thunderbird.feature.taskmail.internal.domain.usecase.RunTaskMailDirectOrFallbackTest" --tests "net.thunderbird.feature.taskmail.internal.ui.detail.TaskSessionDetailViewModelTest"`
- `.\gradlew.bat :feature:taskmail:internal:detekt`
- `.\gradlew.bat :feature:taskmail:internal:lintDebug`

当前已知校验阻塞：

- `.\gradlew.bat :feature:taskmail:internal:spotlessCheck` 仍被本次未触碰的既有格式漂移阻塞，当前输出指向：
  - `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/TaskMailModule.kt`
  - `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/data/cache/FileBackedTaskSessionDetailRepositoryTest.kt`
  - `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/TaskSessionDetailScreenKtTest.kt`

## 已改动的关键位置

- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/sessionaction/TaskMailDirectSessionActionResult.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/data/relay/RelayTaskMailDirectSessionActionSender.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/usecase/RunTaskMailDirectOrFallback.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/TaskSessionDetailViewModel.kt`

## 下一步

1. 在当前安装包上重跑一组很窄的 live/manual 样本，优先仍用 `thread_019` / `thread_020` 或等价的 current-session `/status` + plain reply 对，确认 fallback evidence 卡和 closeout bundle 现在都能读到 `requestId` 优先锚点。
2. 与 PC 侧继续对齐 post-creation fallback canonical artifacts，补齐 `action_type`、`target_session_identity`、action-specific `ingress_message_id`、`terminal_mail_subject`。
3. 只有在 strong-bind rerun 之后仍复现时，才重新打开 `thread_020` 的 `Unable to load session` UI drift 修复。
