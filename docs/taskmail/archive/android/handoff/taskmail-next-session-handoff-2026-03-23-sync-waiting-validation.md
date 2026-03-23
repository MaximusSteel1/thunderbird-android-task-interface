# TaskMail Next Session Handoff - 2026-03-23 - [SYNC] Waiting Validation

## 当前结论

- Android 当前 `[SYNC]` 路径已经稳定进入 `direct-first` 主路径。
- `packet_ack` 已恢复到亚秒级，不再出现旧的 `15s` 早超时。
- `Project list` 页面对“请求已接受但结果尚未回流”已经能显式显示等待态。
- 当前设备验证没有发现新的本机 mail fallback；最新本机 `Sent Items` 的 `[SYNC]` 仍停留在 `2026-03-23T15:05:24.775+08:00`。
- 当前剩余问题仍然是上游 reply 回流慢。本轮验证里，`30s` 和 `90s` 两次 follow-up `checkMail()` 都执行了，但都没有拉到新的 `[SYNC] Project Folder List`。

## 本轮设备验证

### 1. direct ack 正常

- 请求时间：
  - `2026-03-23 17:33:53.366 +08:00`
  - `Sending relay packet packetId=android-taskmail:project-sync:req_4ad94296865c4abebfe55af025efeffa`
- ack 时间：
  - `2026-03-23 17:33:53.768 +08:00`
  - `Received relay packet ack for packetId=android-taskmail:project-sync:req_4ad94296865c4abebfe55af025efeffa`
- 本轮 `packet_ack RTT` 约 `402 ms`。

### 2. waiting UI 生效

- 设备页面 dump 显示：
  - `Waiting for updated project list`
  - `The sync request was accepted, but a newer [SYNC] reply has not arrived yet. This page keeps checking mail for a short time.`
- 同一时刻页面仍显示旧结果：
  - `Scanned at 2026-03-23T16:57:54`

### 3. follow-up refresh 生效，但未拉到新 reply

- 第一次 follow-up：
  - `2026-03-23 17:34:24.664 +08:00` `checkMail`
  - `2026-03-23 17:34:24.885 +08:00` `ImapSync: ... 0 new messages`
- 第二次 follow-up：
  - `2026-03-23 17:35:25.625 +08:00` `checkMail`
  - `2026-03-23 17:35:25.820 +08:00` `ImapSync: ... 0 new messages`
- 这说明当前 Android 侧的有限补刷已经按预期执行，问题不在“没补刷”。

### 4. 本机 fallback 仍未出现

- 导出设备 mailstore 后查询最新 `[SYNC]` 记录：
  - 最新 `Inbox` reply 仍是 `2026-03-23T16:57:57+08:00`
  - 最新 `Sent Items` `[SYNC]` 仍是 `2026-03-23T15:05:24.775+08:00`
- 因此，这一轮 `2026-03-23 17:33` 的请求没有产生新的本机发件 `[SYNC]`。

## 当前最佳理解

- Android 侧现在已经满足：
  - direct request 正常发出
  - relay ack 快速返回
  - 页面可区分“请求失败”与“等待新结果”
  - 页面会在有限窗口内自动补刷邮箱
- 当前未闭环的部分仍是：
  - relay / PC / canonical mail reply 回流耗时不稳定
  - 当 reply 超过 `90s` 才到达时，Android 页面会继续停留在等待态，直到用户手动再次进入或后续有别的邮箱刷新触发本地变更

## 下一步建议

1. 先让 PC / relay 对齐同一 `request_id` 的 ingress、开始处理、发回 canonical `[SYNC] Project Folder List` 时间。
2. 如果分钟级回流延迟是常态，再决定 Android 是否要把当前 `30s + 90s` 扩成更长但仍有限的补刷窗口。
3. 在上游回流仍慢的前提下，当前 Android 侧已经足够继续联调，不必再回退到 mail fallback。

## 本轮代码与验证

- 涉及代码：
  - `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/projectsync/TaskProjectSyncContract.kt`
  - `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/projectsync/TaskProjectSyncViewModel.kt`
  - `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/projectsync/TaskProjectSyncContent.kt`
  - `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/ui/projectsync/TaskProjectSyncViewModelTest.kt`
- 本轮命令：
  - `.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest --tests "*TaskProjectSyncViewModelTest" --console=plain`
  - `.\gradlew.bat :feature:taskmail:internal:detekt --console=plain`
  - `.\gradlew.bat :feature:taskmail:internal:lintDebug --console=plain`
  - `.\gradlew.bat :app-thunderbird:assembleFossDebug --console=plain`
- 真机验证：
  - 已安装新的 `fossDebug` 包
  - 已在 formal-host 路径手动点击 `Project list -> Sync project list`
