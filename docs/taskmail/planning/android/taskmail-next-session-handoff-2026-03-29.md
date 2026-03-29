# TaskMail Android 下一轮交接（2026-03-29）

## 本轮已完成

- detail 当前主写路径已切到 Android-facing `POST /v1/android/session-action`
- 当前 detail public surface 已统一走同一家 `session-action` family：
  - plain reply
  - quick answer
  - structured `Answers`
  - `attachment_continuation`
  - `/status`
  - `/kill`
  - `/end`
- `session-action` 写路径当前按 `session_id` 为主锚点；缺 canonical `workspace_id` 时，只要已有 canonical `session_id`，仍可提交
- submit 成功后会把 `pendingSubmissions` 写入本地 detail cache，并按 `session_snapshot.latest_session_action.command_id` 清理
- detail public UI 不再暗示 paused session 会自动 prepend `/resume`
- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md` 与 `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md` 已更新到当前读法

## 当前验证

本轮已通过的 focused 单测：

```powershell
.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest --tests "net.thunderbird.feature.taskmail.internal.ui.detail.TaskSessionDetailViewModelTest" --tests "net.thunderbird.feature.taskmail.internal.ui.detail.TaskSessionDetailScreenKtTest" --tests "net.thunderbird.feature.taskmail.internal.data.facade.OkHttpTaskMailSessionActionFacadeSenderTest" --tests "net.thunderbird.feature.taskmail.internal.data.facade.OkHttpTaskSessionHistorySnapshotFacadeRepositoryTest" --tests "net.thunderbird.feature.taskmail.internal.ui.workspace.TaskWorkspaceViewModelTest" --tests "net.thunderbird.feature.taskmail.internal.domain.usecase.GetTaskSessionDetailTest" --tests "net.thunderbird.feature.taskmail.internal.data.cache.FileBackedTaskSessionDetailRepositoryTest" --tests "net.thunderbird.feature.taskmail.internal.domain.usecase.SyncTaskMailCacheTest" --tests "net.thunderbird.feature.taskmail.internal.domain.usecase.ObserveTaskMailDirectSessionDetailTest"
```

## 当前未闭环

- 仍缺 fresh device / VPS live smoke，尚未对本轮 `session-action` 主写路径做真机闭环
- `paused` / `resume` 虽已进入 action family / sender contract，但 detail public UI 还没有 dedicated control
- Android 读侧仍保持 `workspace-aware`；这轮没有推进本地 `session_id only` identity 收口

## 下一步建议

1. 先做 fresh device / VPS live smoke：
   - plain reply
   - `attachment_continuation`
   - `/status`
   - `/kill`
   - `/end`
2. 核对真机链路是否稳定闭环：
   - submit 成功
   - 本地 `pendingSubmissions` 写入
   - `latest_session_action.command_id` 回流
   - pending closeout
   - detail 可见状态连续
3. 若以上闭环稳定，再决定是否把 `pause` / `resume` 作为下一轮 dedicated detail control 推进
