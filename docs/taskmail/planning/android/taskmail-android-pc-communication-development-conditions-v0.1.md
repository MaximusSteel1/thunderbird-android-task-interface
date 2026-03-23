# TaskMail Android-PC 通讯开发条件冻结（v0.1）

更新时间：2026-03-23

## 状态

- 本文冻结 Android 与 PC 通讯重构在开始实现前必须满足的开发条件。
- 本文不是 wire contract；wire contract 由 `taskmail-android-pc-control-artifact-contract-v0.1.md` 定义。
- 本文的作用是回答：哪些条件必须先成立，后续 `gateway / store / probe / business payload` 的实现才不会变成盲改。

## Read First

- `docs/taskmail/planning/android/taskmail-unified-control-plane-architecture-v0.1.md`
- `docs/taskmail/planning/android/taskmail-unified-control-plane-cutover-map-v0.1.md`
- `docs/taskmail/planning/android/taskmail-android-pc-control-artifact-contract-v0.1.md`
- `docs/taskmail/planning/android/taskmail-transport-probe-payload-contract-v0.1.md`
- `docs/taskmail/planning/android/taskmail-transport-observability-harness-v0.1.md`
- `E:\projects\mail_based_task_manager\docs\plans\taskmail_android_pc_control_artifact_companion_note_v0.1.md`
- `E:\projects\mail_based_task_manager\docs\current\android_runner_communication_contract.md`
- `E:\projects\mail_based_task_manager\docs\platform\relay_transport_protocol_draft.md`

## 1. 一句话规则

在 Android 开始重写 `new_task`、`reply/status`、`[SYNC]` 之前，必须先冻结并满足 Android-PC 通讯的共享前提：

- 同一份 transport shell
- 同一份 payload schema registry
- 同一套 endpoint / auth / artifact hosting 边界
- 同一套 probe 与 observability 证据

缺少这些前提时，不允许把问题归到 UI、state machine 或 projector。

## 2. 本文覆盖什么

本文覆盖以下“开发前置条件”：

- shared protocol publication
- correlation key freeze
- relay / HTTP endpoint 基线
- auth 与 token 使用边界
- PC runtime 必备能力
- 文件面与小图片支持的最低落地条件
- file-backed consistency / recovery baseline
- probe / validation / merge gate

本文不覆盖：

- `new_task` / `session_action` / `project_sync` 业务字段
- 正式产品 UI 设计
- 邮件 fallback 的最终产品策略

## 3. Shared Contract 条件

### 3.1 Android 不能单边冻结

在开始 Kotlin 实现前，PC 仓必须存在以下之一：

- 同名 shared contract 文档
- 或者明确引用 Android 侧合同标题的 companion note

最低要求是 PC 侧文档必须明确承认：

- `taskmail-control-artifact-contract-v1`
- `WS /control`
- `HTTP /v1/files`
- `command / command_ack / event / result`
- `artifact descriptor`

### 3.2 Payload schema registry 必须先有

在写任何业务 payload 前，必须先有一份最小 registry，至少列出：

- schema title
- owner repo
- version
- producer
- consumer
- replay / fallback 语义

第一批至少要留位：

- `taskmail-transport-probe-payload-v1`
- `taskmail-operation-new-task-v1`
- `taskmail-operation-session-action-v1`
- `taskmail-operation-project-sync-v1`

其中：

- `taskmail-transport-probe-payload-v1` 当前由 `taskmail-transport-probe-payload-contract-v0.1.md` 冻结

### 3.2.1 相关性键必须一起冻结

在开始 projector、gateway 与 result bind 实现前，以下 identity 键必须作为 shared set 冻结：

- `workspace_id`
- `session_id`
- `message_id`
- `source_id`
- `artifact_id`
- `file_id`

规则：

- 这些键只要语义成立，就必须显式透传
- 不允许把 workspace/session/artifact bind 留给标题、时间窗或正文启发式恢复

### 3.3 错误分类必须共享

在 Android 写 direct/fallback 逻辑前，PC/VPS/Android 必须共享同一套最低错误分类：

- `replayable`
- `fallbackable`
- `terminal`

以及稳定字段：

- `error_code`
- `error_message`
- `retry_class`

否则 Android 无法正确决定：

- 重连 replay
- mail fallback
- 直接报错并停住

## 4. Endpoint 与网络条件

### 4.1 endpoint 名称先冻结

开发阶段先固定：

- `GET /healthz`
- `WS /control`
- `POST /v1/files`
- `GET /v1/files/{file_id}`
- `GET /v1/files/{file_id}/content`

不允许在 Android 先写死临时路径，后面再全仓替换。

### 4.2 scheme 必须显式一致

开发环境允许：

- `ws + http`
- 或 `wss + https`

但必须满足：

- WebSocket 与 HTTP 使用同一 host
- WebSocket 与 HTTP 的 TLS 选择显式一致
- `healthz` 返回当前真实 scheme / TLS 状态

不允许：

- `healthz` 是一套 host/scheme，`/control` 是另一套
- Android 靠猜测切换 `useTls`
- WebSocket 走明文、文件面走 TLS，但文档里没有写清楚

### 4.3 token 认证边界先固定

必须共享：

- 同一个 transport token 同时用于 `/control` 与 `/v1/files`
- Android 不使用 bot mailbox 凭据做 relay/file 面认证
- token 只走 transport 层，不进业务 payload

## 5. PC Runtime 必备能力

在 Android 开工前，PC 侧至少要能提供以下能力，不然 Android 侧 gateway 只能写成猜谜游戏。

### 5.1 command ingress

PC runtime 必须能消费来自 relay 的：

- `request_id`
- `packet_id`
- `command_type`
- `payload_schema`
- `payload`
- `artifacts`

### 5.2 result/event egress

PC runtime 必须能通过 relay 返回：

- `command_ack`
- `event`
- `result`

并保留：

- `receipt_id`
- `result_id`

### 5.3 artifact host bind

PC runtime 必须明确：

- 谁负责上传文件
- 谁负责生成 `file_id`
- 谁负责保存 metadata
- 谁负责给 Android 提供 `download_url`

不允许 Android 先假定“PC 会自己想办法把文件暴露出来”。

### 5.4 durable replay

PC/VPS 至少有一侧必须保证：

- 同一个 `packet_id + request_id` replay 语义稳定
- accepted 后可以重新拿到同一 `receipt_id`
- 已完成结果可以重新拿到同一 `result_id`

如果这件事没准备好，Android 侧不能先写 accepted-after-disconnect recovery。

## 6. 文件面条件

### 6.1 文件真相必须先从 mail 语义里分离

在 Android 开始接图片/附件之前，PC 侧必须接受一条边界：

- 文件的 canonical truth 是 `file_id + metadata + HTTP content`
- mail attachment 只是 delivery adapter
- WebSocket artifact 引用不是 mail attachment 的别名字符串

### 6.2 v0.1 先冻结单次上传

开发第一阶段允许不做：

- chunk upload
- resume upload
- ranged upload

但必须满足：

- 单次上传成功路径稳定
- 超限时明确返回 `413` 或等价错误
- metadata 与下载内容一致

### 6.3 大小上限要先写清楚

开始实现前，三端必须共享至少这三个数值：

- `inline_preview_max_bytes = 65536`
- `json_text_field_soft_limit = 65536`
- `single_file_upload_limit_bytes = 33554432`

其中第三个现按第一版基线冻结为 `32 MiB`，用于 `/v1/files` 的单文件上传上限。

### 6.4 小图片支持的最低条件

要允许 JSON 覆盖小图片，最低必须保证：

- `inline_preview` 与正式文件分离
- preview bytes 有明确 `mime_type`
- preview 超限时仍能只返回 metadata + `download_url`
- Android 可以在没有 preview 的情况下正常回落到下载原图

## 7. File-Backed 一致性条件

在 Android 继续使用 file-backed ledger / event store / projection store 的前提下，实现前必须先冻结：

### 7.1 原子写规则

- 使用 `temp file -> fsync -> rename` 或等价原子替换
- 不允许直接覆盖正式文件

### 7.2 版本升级规则

- ledger / event / projection 文件都必须带 `schemaVersion`
- 版本不兼容时要么迁移，要么 fail-fast，不允许静默半读半跳过

### 7.3 crash recovery 规则

- 启动时必须检测 temp 文件残留
- 必须检测 projection checkpoint 落后于 event store
- projection 损坏时必须允许从 ledger + event store 重建

### 7.4 提交顺序规则

- 先 durable 写 event / ledger
- 再更新 projection
- 不允许先更新 projection、再补写事实源

## 8. Android 侧开发条件

### 8.1 先做 gateway，不先做业务 ViewModel

Android 第一批实现必须先落在：

- `RelayTaskMailGateway`
- `FileArtifactGateway`
- `TaskMailEventStoreRepository`
- `transport_probe` debug entrypoint

不应先改：

- `TaskNewTaskViewModel`
- `TaskSessionDetailViewModel`
- `TaskProjectSyncViewModel`

### 8.2 先做 debug-only probe entrypoint

在业务重构前，Android 必须先具备：

- 发 `transport_probe` command
- 观察 probe mail/result/event
- 拉取本地 probe artifact

否则后续业务联调会缺最基础的 transport 定位能力。

### 8.3 本地 artifact 目录先固定

Android debug artifact 路径必须先冻结，例如：

- `/sdcard/Android/data/<debug package>/files/taskmail-debug/transport-probe/<probe_id>/`

在 Kotlin 实现前先冻结目录，有利于脚本与文档同步。

## 9. Probe 与验证条件

### 9.1 `transport_probe` 必须早于业务 payload

正式业务 payload 开工前，必须先实现并闭环：

- `android_mail_ping_to_pc`
- `android_direct_ping_to_vps_to_pc`
- `pc_mail_ping_to_android`

如果这三条还没通，不允许先推进 `new_task` / `reply/status` / `[SYNC]` 的统一迁移。

### 9.2 三端都必须产出时间线证据

最小证据要求：

- Android: `probe_id`、`request_id`、`packet_id`
- Android: `clock_source`、`monotonic_ms`
- VPS: `received_at`、`accepted_at`、`bridge_started_at`、`bridge_finished_at`
- VPS: `clock_source`、`monotonic_ms`
- PC: `observed_at`、`handler_started_at`、`handler_finished_at`
- PC: `clock_source`、`monotonic_ms`

### 9.3 手工与自动化边界先写清楚

开发阶段允许部分步骤人工触发，但必须保证：

- 人工步骤只发生在 UI 打开、按钮点击这类入口
- transport 证据采集、artifact 拉取、timeline merge 必须脚本化

### 9.4 harness 允许先交付

harness 在逻辑归属上属于 unified control plane，但在交付顺序上允许先行：

- 可以先于大 cutover 落地
- 不要求等业务 ViewModel 重写完成后才出现

这是本轮明确授权的例外，因为重构期最需要的是 observability。

## 10. Merge Gate

下面这些条件满足前，不应把 Android-PC 通讯重构往主业务流合并：

1. PC 侧已经明确承认 shared contract 标题与 endpoint。
2. `transport_probe` payload schema 已冻结。
3. 相关性键 shared set 已冻结。
4. `/control` 与 `/v1/files` 的 token 认证路径已跑通。
5. 至少一条文件上传下载样本已经闭环并校验 `sha256`。
6. 三条最小 probe 场景已有 artifact 时间线，且包含跨机时间语义字段。
7. accepted -> replay 的同 id 语义已经有样本。
8. file-backed 原子写 / crash recovery 规则已有实现说明或测试样本。

## 11. 当前结论

“先冻结开发条件”这一步是必要的，因为它把后续实现的责任边界写死了：

- contract 不再只在 Android 仓里想象
- endpoint、auth、artifact hosting 不再边写边猜
- probe 先于业务 payload
- `transport_probe` payload schema 已单独冻结
- `/v1/files` 的首版单文件上限已固定为 `33554432`
- gateway 先于 ViewModel

只有这些条件先冻结，后面的 unified control plane 重构才是工程，而不是探索式试错。
