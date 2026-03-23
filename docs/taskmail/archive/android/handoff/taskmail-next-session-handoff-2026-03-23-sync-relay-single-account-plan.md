# TaskMail Next Session Handoff - 2026-03-23 - [SYNC] Relay Single-Account MVP

## 当前决策

- `[SYNC]` 的改造目标不是把整个 project-sync 协议改成 relay-native，而是把“请求入口”改成 `relay direct`，同时继续复用 canonical mail control plane 结果。
- MVP 只做 `single-account available` 版本：
  - Android 侧点 `Sync project list` 时优先走 relay direct
  - relay 接受后桥接成 canonical `[SYNC]` mail
  - PC 侧继续生成 canonical `[SYNC] Project Folder List` reply
  - Android 侧继续从本地邮箱读取最新 `[SYNC] Project Folder List`
- MVP 不处理多账号严格匹配；如果 Android 当前选中的 sender account 与 VPS 上配置的 `taskmail_direct_from_addr` 不是同一个邮箱，仍应保留 mail fallback。
- `[SYNC]` 仍然保持 bootstrap action 边界：
  - 不创建 task
  - 不创建 thread/session
  - 不进入 TaskMail workspace/session/detail projection

## Read First

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-MAIL-RULES.md`
- `docs/taskmail/archive/android/handoff/taskmail-next-session-handoff-2026-03-23-sync-smoke-result.md`
- `E:\projects\mail_based_task_manager\docs\current\mail_protocol.md`
- `E:\projects\mail_based_task_manager\mail_runner\app.py`
- `E:\projects\mail_based_task_manager\mail_runner\project_folder_sync.py`
- `E:\projects\mail_based_task_manager\mail_runner\relay_server\app.py`
- `E:\projects\mail_based_task_manager\mail_runner\relay_server\direct_actions.py`

## 目标边界

### 本次要做

- 让 Android formal-host 的 `[SYNC]` 请求支持 `direct-first / mail-fallback`
- 让 VPS relay 接受一个新的 direct `[SYNC]` packet
- 让 relay 把该 packet 桥接回 bot mailbox 的 canonical `[SYNC]` ingress
- 保持 Android 现有 `Project list` 读取、渲染、`Use this repo` 回填链路不变

### 本次不做

- 不新增 `[SYNC]` 的 relay-native 结果协议
- 不把 `[SYNC]` 投影成 TaskMail session/detail 卡片
- 不做多账号 sender-address 能力协商
- 不按 Android 本地 `sender_account_uuid` 在服务端做账号映射
- 不移除 mail fallback

## Android 侧改动

### 建议实现

- 保留现有 `TaskProjectSyncViewModel` 的交互语义：
  - 点 `Sync project list`
  - 请求成功后 `refreshTaskMail()`
  - 继续等待 `[SYNC] Project Folder List` 到达本地邮箱
- 将当前 mail-only 的 `TransportBackedTaskMailProjectSyncRequester` 改成 direct-first：
  - 新增 `TaskMailDirectProjectSyncSender`
  - 新增 `RelayTaskMailDirectProjectSyncSender`
  - 在 `TaskMailProjectSyncRequester` / repository 层内部复用 `RunTaskMailDirectOrFallback`
- direct packet 建议使用一个新的 bootstrap schema，例如：
  - `schema_version = "taskmail-bootstrap-control-contract-v1"`
  - `action = "sync_project_folders"`
  - `dispatch_metadata.channel = "taskmail_android_direct"`
  - `dispatch_metadata.fallback_policy = "mail"`
- packet 中建议带上：
  - `request_id`
  - `origin.client = "android_taskmail"`
  - `origin.sender_account_uuid`

### Android MVP 先不扩的面

- `TaskMailProjectSyncRequester.requestSync()` 仍可保持 `Result<Unit>`，先不引入新的 public result shape
- 可以先不新增 `TaskMailProjectSyncSendRecord`
- `TaskProjectSyncContent` / `TaskProjectSyncScreen` 不需要新 UI 控件

### Android 验收

- relay 可用时，点击 `Sync project list` 不影响现有页面交互
- relay fallback 时，仍能退回当前 mail `[SYNC]` 路径
- 现有 `[SYNC]` parser / result-reader / `Use this repo` 行为不变

## VPS / Relay 侧改动

### 建议实现

- 在 `mail_runner/relay_server/` 新增一个 direct handler，例如：
  - `RelayTaskMailDirectProjectSyncMailBridge`
- 该 handler 的职责不是直接列目录，而是：
  - 校验 direct `[SYNC]` packet
  - 桥接发送一封 canonical mail 到 `taskmail_bot_mailbox_addr`
  - `Subject: [SYNC]`
  - body 可为空
  - 附带 `X-TaskMail-Direct`、`X-TaskMail-Relay-Packet-Id`、`X-TaskMail-Relay-Request-Id`
- 在 `relay_server/app.py` 的 runtime handler 注册链中加入这个 `[SYNC]` bridge
- 错误分类继续遵守现有 direct 规则：
  - payload/schema 问题 -> hard rejection
  - bridge 暂时不可用 -> fallback-classified rejection

### VPS 配置边界

- MVP 继续复用当前单一 `taskmail_direct_from_addr`
- 这意味着 relay bridge 发进 bot mailbox 的 `[SYNC]` 首封会来自该 direct mailbox
- 因此 MVP 只适合“Android 当前 sender account 就是该 direct mailbox”的场景

### VPS 验收

- direct `[SYNC]` packet 被接受后，bot mailbox 中能看到 canonical `[SYNC]` 请求
- relay `packet_ack` 返回 `accepted=true`
- 不引入新的 session/thread/task 状态

## PC 侧改动

### 建议实现

- PC 侧不要重写 `[SYNC]` 语义，继续以现有 mail control plane 为 authority：
  - `app.py::_handle_project_folder_sync()`
  - `project_folder_sync.py::build_project_folder_sync_body()`
- 重点是确认“relay bridge 注入的 canonical `[SYNC]` mail”和“用户直接发来的 `[SYNC]` mail”行为一致：
  - 回复 `[SYNC] Project Folder List`
  - 全局只保留最新一封 `[SYNC]` system reply
  - 不创建 task/thread/session
- PC 侧如果需要改动，优先只做：
  - 测试补齐
  - 文档补齐
  - 必要时抽一个轻量 helper，避免 relay bridge 与 mail poll path 的 `[SYNC]` 规则分叉

### PC 验收

- relay bridge 触发的 `[SYNC]` 与现有 mail `[SYNC]` 输出正文完全兼容
- `Project list` 页面继续能解析最新 reply
- `[SYNC]` 仍然不进入 Android TaskMail projection

## 建议测试

### Android

- `TransportBackedTaskMailProjectSyncRequester` 替换后的 unit test：
  - direct accepted
  - direct fallback -> mail send
  - direct hard rejection -> surfaced failure
- `TaskProjectSyncViewModelTest` 保持通过，必要时补一条 direct fallback 不破坏现有提示文案

### VPS / PC

- `test_relay_server_app.py`
  - runtime 在 `taskmail_direct_ingress_enabled=true` 时注册新的 `[SYNC]` handler
- `test_relay_server_direct_actions.py`
  - direct `[SYNC]` bridge accepted 后发送 canonical `[SYNC]` mail
  - bridge unavailable 时返回 fallback-classified rejection
  - invalid payload / wrong schema 时 hard reject
- `test_relay_server_runtime.py`
  - websocket `hello -> packet -> packet_ack` 路径可跑通 `[SYNC]`
- `test_app_phase2.py`
  - 保持现有 `[SYNC]` mail semantics regression 通过

## 风险与注意事项

- 这次只做 `single-account available`，不能把它表述成“多账号都已正确支持 `[SYNC] direct`”。
- 当前 relay direct bridge 使用的是全局 `taskmail_direct_from_addr`，不是 Android 侧当前选中的 sender account。
- 因此 Android 只有在“所选 sender account 与 relay direct mailbox 相同”时，最终 `[SYNC] Project Folder List` 才会稳定回到当前 account 的本地邮箱。
- 不要把 Android 本地 `sender_account_uuid` 当作服务端账号映射键；该字段只适合做客户端本地 evidence 或未来协商输入。
- 不要把 `[SYNC]` 结果改成 detail push / session projection；这会破坏当前 bootstrap 边界和已有 parser/reader 设计。

## 分阶段顺序

1. VPS/relay 先加 direct `[SYNC]` bridge 和测试
2. PC 侧补回归测试，确认 bridge 注入的 canonical `[SYNC]` 与现有 mail path 一致
3. Android 再把 `[SYNC]` request 切到 `direct-first / mail-fallback`
4. formal-host 单账号 smoke：
   - `Project list -> Sync project list`
   - mailbox-side 确认新的 `[SYNC]` 请求与 `[SYNC] Project Folder List` reply
   - Android `Project list` 页面确认 `scannedAt` 更新
   - `Use this repo` 回填仍正常
   - `Tasks` 工作区仍无 `[SYNC]` 卡片

## 可直接转发给 PC / VPS 的需求摘要

标题：
`TaskMail [SYNC] single-account MVP: accept relay direct request, bridge into canonical [SYNC] mail ingress, keep [SYNC] Project Folder List as the only result surface`

需求要点：

- Android 希望把 `[SYNC]` 的“请求入口”改成 relay direct，但不改现有 `[SYNC]` mail 结果协议
- relay 需要新增 direct `[SYNC]` packet handler，并桥接成 canonical `[SYNC]` mail 到 bot mailbox
- PC 侧继续复用现有 `_handle_project_folder_sync()` 生成 `[SYNC] Project Folder List`
- `[SYNC]` 仍然不创建 task/thread/session，也不进入 session projection
- 本轮只要求单账号可用；多账号 sender-address 协商留到后续

验收标准：

- direct `[SYNC]` packet `accepted`
- bot mailbox 收到 canonical `[SYNC]`
- PC 返回 canonical `[SYNC] Project Folder List`
- Android `Project list` 页面可读到新的结果
- `Tasks` 工作区没有新增 `[SYNC]` 卡片

## 本次代码与验证

- Android 生产代码改动：无
- Android 测试代码改动：无
- planning / handoff 文档改动：有
- 新的设备 / Gradle / VPS 验证：无
