# TaskMail Batch D VPS-Native Projection 验证清单（临时 v0.1）

更新时间：2026-03-27

## 文档状态

- 临时文档
- 只服务当前 `Batch D / VPS-native projection cache` 主线验证
- 不替代：
  - `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
  - `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
  - `docs/taskmail/planning/android/taskmail-vps-first-multi-pc-android-vps-native-cache-and-projection-contract-v0.1.md`
  - `docs/taskmail/planning/android/taskmail-next-session-handoff-2026-03-26.md`

## 本文要回答的问题

当前这条主线到底需要验证什么，才可以把它读成：

- `create-session -> session_binding -> provisional session -> workspace/detail -> reply/status first-hop update`
- 已进入 `VPS-native projection cache` 的 session，不再默认等 mail 才成立

## 验证范围

本轮只验证下面 4 条主链：

1. `new_task` 走 Android-facing `create-session facade`
2. `session_binding` 返回后，workspace/detail 能立即成立
3. detail 内 `/status` 与 plain reply 能先由本地 projection 驱动第一跳 UI 更新
4. app 返回、重进、冷启动后，projection cache 仍可支撑 workspace/detail

## 本轮不验证

- raw live `pc-control` websocket 全量 cutover
- `[SYNC] Project list`
- quick answer / 多问题 `Answers:` / attachment continuation
- `/v1/files` 与 `transport_probe`
- UI 美观、信息密度、视觉 polish

## 预置条件

- [ ] A1. 手机安装的是包含 Batch D 第一轮代码的新 APK。
- [ ] A2. `TaskMail relay debug` 中已经填好本轮可用的 `android_app_token`。
- [ ] A3. `host / port / TLS / path` 配置与当前联调环境一致。
- [ ] A4. 已知一个可用的 `PC ID` 和 `Workspace ID`。
- [ ] A5. `Repository bridge` 指向真实 repo，而不是占位路径。
- [ ] A6. PC/VPS 当前在线，能承接至少一个简单任务。

## 编号清单

### B. Create-Session 主链

- [ ] B1. 从正式 Thunderbird 宿主进入 `Tasks -> New task`，页面可正常打开，无闪退。
- [ ] B2. 填写有效的 `PC ID / Workspace ID / Backend / Repository bridge / Task / Title` 后，`Send task` 可点击。
- [ ] B3. 点击发送后，不出现本地校验错误，也不出现同步失败类 toast。
- [ ] B4. 发送成功后，页面不是简单停在原地或只返回列表；本轮主预期是拿到 `session_binding` 并进入 detail。

### C. Session Binding 与 Workspace 立即可见

- [ ] C1. 发送后能直接进入新 session 的 detail，而不是 `not found`、空白页或旧 session。
- [ ] C2. 即使此时 canonical mail 还没到，返回 workspace 后也已经能看到这条新 session。
- [ ] C3. workspace 中这条 session 只出现一次，不会先无、后重复、再被 mail 生成第二条副本。
- [ ] C4. workspace 卡片上的 `Workspace ID`、session 标题和状态至少是语义正确的，不出现明显串号。

### D. Detail 首屏与第一跳更新

- [ ] D1. detail 首屏能显示 provisional/queued 状态，不需要退出重进才出现。
- [ ] D2. detail 首屏不会因为缺少 `thread_id` 而报错或显示 `Task session detail was not found.`。
- [ ] D3. 当第一条 VPS session update 到达后，detail 内的 `status / summary / timeline` 会直接更新。
- [ ] D4. 第一条 VPS update 到达后，用户不需要退出 detail 再进，页面就能看到变化。
- [ ] D5. workspace 随后也能跟到这次变化，不需要等 canonical mail 才刷新。

### E. `/status` 与 Plain Reply

- [ ] E1. 在当前 session detail 内点 `/status`，会先出现第一跳 accepted / updating 反馈，而不是完全无变化。
- [ ] E2. `/status` 发送后，detail 会继续跟到后续 status 变化，不需要退出重进。
- [ ] E3. 在当前 session detail 内发送一条 plain reply，会先出现第一跳 accepted / updating 反馈。
- [ ] E4. plain reply 发送后，detail 的 timeline / summary / status 会继续前进，而不是卡住到必须重进。
- [ ] E5. `/status` 与 plain reply 都不会把当前 session 打成第二条新 session。

### F. 返回、重进、冷启动

- [ ] F1. 从 detail 返回 workspace，再重新点进同一 session，最新 projection 状态仍在。
- [ ] F2. 完全杀掉 app 再冷启动，workspace 仍能看到这条 session。
- [ ] F3. 冷启动后重新进入 detail，仍能读到上一次已落地的 projection，而不是回退成空白或旧 mail-only 状态。

### G. Mail Compatibility Repair 边界

- [ ] G1. canonical mail 晚到时，不会把已经显示正确的 VPS-native session 覆盖回更旧的 summary/status。
- [ ] G2. canonical mail 晚到时，如果 timeline 增加了 mail item，应表现为合流，而不是 session 重建或 duplicated row。
- [ ] G3. 晚到 mail 不应让 workspace/detail 把这条 session 重新解释成另一条 thread。

## 最小通过门槛

本轮若要判定“主线基本成立”，至少需要满足：

- [ ] P1. `B1-B4` 全过
- [ ] P2. `C1-C3` 全过
- [ ] P3. `D1-D4` 全过
- [ ] P4. `E1-E4` 至少各过一轮
- [ ] P5. `F1-F3` 至少过一轮

如果 `G1-G3` 失败，不代表主链完全不通，但代表 `mail compatibility repair` 仍可能把 `VPS-native` 主读链拉回旧读法，不能直接关单。

## 失败时最少要回传的证据

如果某一项失败，最少回传下面 4 类之一：

- 失败项编号，例如 `C2`
- 对应页面截图
- 发生前后各一张截图，尤其是 workspace/detail 切换前后
- 如果能拿到，再补 `TaskMail relay debug` 当前配置页或失败提示

推荐回报格式：

- `B4 失败：发送后只返回上一页，没有进 detail`
- `D3 失败：detail 停在 queued，等了 30 秒没更新`
- `G1 失败：mail 到达后 summary 从新值回退成旧值`

## 通过后该如何读结果

如果本清单大部分通过，当前主线就可以被读成：

- Android 已具备 `create-session -> session_binding -> provisional session -> VPS-native projection cache -> workspace/detail` 的真机成立条件
- workspace/detail 对这类 session 已不再默认依赖 mail 才出现
- 下一步应优先继续做 live smoke closeout 和 continuity 收口，而不是回头改旧 mail-era UI 语义
