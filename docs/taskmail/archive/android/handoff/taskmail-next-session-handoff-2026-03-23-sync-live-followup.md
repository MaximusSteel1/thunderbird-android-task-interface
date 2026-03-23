# TaskMail Next Session Handoff - 2026-03-23 - [SYNC] Live Follow-up

## 当前结论

- Android 侧这轮新增的两个改动已经按 live trace 生效：
  - `RelayTaskMailDirectProjectSyncSender` 现在把 `[SYNC]` direct `packet_ack` 等待窗口放宽到 `30_000 ms`
  - `TaskProjectSyncViewModel` 在 direct/mail request success 后，会额外安排一次 `30_000 ms` 的 follow-up `refreshTaskMail()`
- 当前剩余问题已不再像最早那样是 Android `15s` 早超时。
- 当前更像是 upstream `[SYNC] Project Folder List` 结果生成 / bridge / 投递回用户邮箱存在明显延迟；`30s` follow-up 只能覆盖中短延迟，覆盖不了分钟级慢链路。

## 本轮 live 证据

### 1. patched run A：direct ack 已经恢复正常

- formal-host `Project list` 页面触发 `[SYNC]` 后，log 记录：
  - `03-23 15:32:12.799` `Sending relay packet packetId=android-taskmail:project-sync:req_4cce084878b841ff896f5d5e325687cf`
  - `03-23 15:32:13.417` `Received relay packet ack for packetId=android-taskmail:project-sync:req_4cce084878b841ff896f5d5e325687cf`
- 同一 trace 随后立刻出现：
  - `03-23 15:32:13.434` `Running command 'checkMail'`
- 因此这轮已经不再复现 pre-patch 的 `Relay packet acknowledgement timed out after waiting for 15000 ms`。

### 2. run A 在 20 秒时页面仍旧，但结果后来确实回到本地

- `taskmail_sync_after20.xml` 里，`Project list` 仍显示：
  - `Scanned at 2026-03-23T15:26:30`
- 稍后再次查看当前页面时，已经自动变成：
  - `Scanned at 2026-03-23T15:43:18`
- 后续设备 log 还能看到 inbox 拉取到了对应的新 reply：
  - `Date: Mon, 23 Mar 2026 15:43:20 +0800`
  - `Subject: [SYNC] Project Folder List`
  - `To: jiangchun@tongji.edu.cn`
- 这说明：
  - Android 页面对“新 `[SYNC]` reply 到达本地库后自动重读”这件事本身没有坏
  - run A 的慢点主要在“结果晚到”，不是“结果到了但 Android 不读”

### 3. patched run B：30 秒 follow-up refresh 确实触发

- 再次触发 `[SYNC]` 后，log 记录：
  - `03-23 16:57:34.995` `Sending relay packet packetId=android-taskmail:project-sync:req_b90c78584fc44575afba8f99526e7307`
  - `03-23 16:57:35.486` `Received relay packet ack for packetId=android-taskmail:project-sync:req_b90c78584fc44575afba8f99526e7307`
  - `03-23 16:57:35.499` 首次 `checkMail`
- 同一轮 `35s` 采样里还能看到第二次 `checkMail`：
  - `03-23 16:58:06.577` `Running command 'checkMail'`
- 这个时间点与 `postSyncFollowUpDelayMillis = 30_000L` 基本吻合，因此 follow-up refresh 已被 live 证明确实执行。

### 4. run B 在 35 秒窗口内仍未出现新结果

- `taskmail_sync_after35.xml` 仍显示：
  - `Scanned at 2026-03-23T15:43:18`
- relay `/healthz` 已增量证明请求到达：
  - 之前 baseline：`packet_count = 368`
  - run B 之后：`packet_count = 369`
- 但到 `35s` 采样为止，设备端还没有看到新的 `[SYNC] Project Folder List` reply 被拉进本地库。

## 当前最佳理解

- Android 侧：
  - direct `[SYNC]` request 可以正常发到 relay
  - `packet_ack` 可以在 `1s` 内返回
  - immediate `checkMail()` 与 `30s` follow-up `checkMail()` 都会执行
  - 当新的 `[SYNC] Project Folder List` 真正到达本地邮箱时，`Project list` 页面能自动更新
- 当前没有新的 Android 侧 blocker 证据表明：
  - direct ack 还在早超时
  - request success 后完全不触发 refresh
  - `[SYNC]` reply 已到本地但页面不更新
- 当前剩余风险更像：
  - upstream `sync_project_folders` -> canonical `[SYNC]` reply 的整体 turnaround 偶发达到分钟级
  - 单次 `30s` follow-up refresh 无法覆盖这类慢链路

## 下一步建议

1. PC / relay 侧优先补一条同 run 时间线：
   - Android `packet_id` / `request_id`
   - relay ingress accepted 时间
   - PC 真正开始跑 folder sync 的时间
   - canonical `[SYNC] Project Folder List` 发回用户邮箱的时间
2. 如果上游确认这种分钟级延迟是常态，再回 Android 评估是否要把当前“一次 `30s` follow-up”扩成：
   - 多次 follow-up refresh
   - 或更长窗口的有限重试
3. 如果上游修完代理 / bridge 后 turnaround 回到几十秒内，当前 Android 补丁大概率已经足够。

## 本轮涉及的 Android 代码

- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/data/relay/RelayConnectionClient.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/data/relay/OkHttpRelayConnectionClient.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/data/relay/RelayTaskMailDirectProjectSyncSender.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/projectsync/TaskProjectSyncViewModel.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/TaskMailModule.kt`

## 本轮验证

- `.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest --tests "*RelayTaskMailDirectProjectSyncSenderTest" --tests "*TaskProjectSyncViewModelTest" --tests "*OkHttpRelayConnectionClientTest" --console=plain`
- `.\gradlew.bat :feature:taskmail:internal:detekt --console=plain`
- `.\gradlew.bat :feature:taskmail:internal:lintDebug --console=plain`
- `.\gradlew.bat :app-thunderbird:assembleFossDebug --console=plain`
- 真机 formal-host live smoke：有
