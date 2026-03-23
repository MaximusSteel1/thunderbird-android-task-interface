# TaskMail Transport Observability Harness 设计（v0.1）

更新时间：2026-03-23

## 状态

- 本文定义一套脱离业务语义的 TaskMail 通讯观察 harness。
- 本文目标不是产品功能，而是为 Android / PC / VPS 联调提供一条极简、可回放、可脚本化的观察链。
- 本文默认它会和 unified control plane 一起落地，而不是继续叠在旧 phase smoke 脚本旁边。

## Read First

- `docs/taskmail/planning/android/taskmail-unified-control-plane-architecture-v0.1.md`
- `docs/taskmail/planning/android/taskmail-unified-control-plane-cutover-map-v0.1.md`
- `docs/taskmail/planning/android/taskmail-android-pc-control-artifact-contract-v0.1.md`
- `docs/taskmail/planning/android/taskmail-android-pc-communication-development-conditions-v0.1.md`
- `docs/taskmail/planning/android/taskmail-transport-probe-payload-contract-v0.1.md`
- `docs/TASKMAIL-DEBUG-VALIDATION.md`
- `scripts/taskmail_bot_mailbox_smoke.py`
- `E:\projects\mail_based_task_manager\scripts\live_smoke_mail_roundtrip.py`
- `E:\projects\mail_based_task_manager\scripts\live_smoke_mail_sync.py`
- `E:\projects\mail_based_task_manager\docs\current\android_runner_communication_contract.md`

## 1. 一句话目标

把“TaskMail 能不能通”从具体业务里剥出来，变成一条独立 probe 通路：

- Android 可以发一个极简 probe
- PC 可以查询 probe 是否到达、何时开始、何时结束
- VPS 可以记录 relay/bridge 的开始与结束
- PC 可以发一个极简 probe mail
- Android 可以确认 probe 已被收到并写出本地证据

这样调试 transport 问题时，不必同时背着 `new_task`、`reply/status`、`[SYNC]` 的业务状态机。

## 2. 为什么需要独立 harness

当前 live smoke 脚本虽然有价值，但它们主要验证的是“业务流程能否走完”，不是“每个 transport hop 是否清晰可见”。

现状问题：

- `new_task` 冒烟把 transport 与业务 prompt、backend 运行结果绑在一起
- `[SYNC]` 冒烟把 transport 与 project-root 枚举绑在一起
- Android 回邮验证脚本需要人工配合，且主要覆盖 mail reply，不覆盖 relay hop 时间线
- VPS 当前对 Android 来说更像 transport 黑盒，不是 probe 时间线的一等事实源

独立 harness 的作用是把问题缩到最小：

- 先证明通路
- 再证明延迟与丢失点
- 最后才回到具体业务

## 3. 设计原则

### 3.1 脱离业务语义

probe payload 可以是任意短文本，不依赖：

- `Repo:`
- `Task:`
- `/resume`
- `[SYNC]`
- `state capsule`
- `question capsule`

### 3.2 每一跳都落证据

每个 probe 都必须能生成统一时间线，而不是只靠 logcat 或人工截图。

### 3.3 一条 probe，一个 `probe_id`

所有 Android / PC / VPS / mailbox artifact 都围绕同一个 `probe_id`。

### 3.4 可脚本驱动

Android 侧不应要求脚本去模拟复杂 UI 点击。应提供 debug-only probe entrypoint，让 adb / script 可以直接传入：

- `probe_id`
- `transport`
- `direction`
- `payload_text`

### 3.5 不引入新的产品协议承诺

probe 只用于 debug 与验证，不自动成为产品面向用户的公开 API。

## 4. 最小 probe 模型

本节描述 harness 消费的最小 artifact 与时间线模型；carrier 上的 canonical payload 以 `taskmail-transport-probe-payload-contract-v0.1.md` 为准。

### 4.1 `TransportProbeManifest`

建议字段：

- `probe_version`
- `probe_id`
- `scenario`
- `transport_kind`
- `direction`
- `payload_text`
- `created_at`
- `android_package`
- `expected_bot_mailbox`
- `expected_user_mailbox`
- `request_id`
- `packet_id`

建议固定：

- `probe_version = taskmail-transport-observability-harness-v1`

### 4.2 `TransportProbeEvent`

建议字段：

- `probe_id`
- `event_type`
- `actor`
- `recorded_at`
- `clock_source`
- `monotonic_ms`
- `clock_offset_ms`
- `summary`
- `message_id`
- `receipt_id`
- `result_id`
- `request_id`
- `packet_id`
- `workspace_id`
- `session_id`
- `source_id`
- `artifact_ids`
- `metadata`

### 4.3 `TransportProbeArtifact`

建议每个 `probe_id` 最终至少生成：

- `manifest.json`
- `events.jsonl`
- `timeline.json`
- `timeline.md`

### 4.4 跨机时间语义

只用 `recorded_at` 排序跨 Android / PC / VPS 时间线是不够的，时钟漂移会污染“谁先谁后”。

因此 probe event 必须额外冻结：

- `clock_source`
  - `android_wall_clock`
  - `pc_wall_clock`
  - `vps_wall_clock`
- `monotonic_ms`
  - 单机 monotonic 时间戳；只要求在该 actor 内可比较
- `clock_offset_ms`
  - 可选；表示该 actor 相对参考时钟的已知偏移

合并报表规则：

- 同一 actor 内优先使用 `monotonic_ms`
- 跨 actor 默认展示 `recorded_at`
- 如果存在可信 `clock_offset_ms`，允许生成 offset-corrected order
- 当 offset 不可信时，timeline 必须明确标注“跨机先后仅为近似顺序”

## 5. 建议事件类型

### 5.1 Android 发送侧

- `android_probe_dispatch_started`
- `android_mail_probe_submitted`
- `android_relay_probe_submitted`
- `android_probe_dispatch_failed`

### 5.2 VPS / Relay 侧

- `vps_probe_packet_received`
- `vps_probe_packet_accepted`
- `vps_probe_bridge_started`
- `vps_probe_bridge_finished`
- `vps_probe_result_started`
- `vps_probe_result_finished`
- `vps_probe_rejected`

### 5.3 PC 侧

- `pc_probe_mail_observed`
- `pc_probe_handler_started`
- `pc_probe_handler_finished`
- `pc_probe_mail_submitted`

### 5.4 Android 接收侧

- `android_probe_mail_observed`
- `android_probe_projection_observed`
- `android_probe_receive_timeout`

`projection_observed` 很重要，因为它能区分：

- 邮件已到本机 mailbox/local store
- UI/projection 还没真正看到

## 6. 四条最小场景

### 6.1 `android_mail_ping_to_pc`

目标：

- 验证 Android -> user mailbox outbox -> bot mailbox -> PC 这条最基础 mail 控制链

最小负载：

- subject: `[TPROBE][A2P][MAIL] <probe_id>`
- body: 使用 `taskmail-transport-probe-payload-v1` 定义的固定 header block

成功标准：

- Android 写出本地 `dispatch_started` / `mail_probe_submitted`
- PC mailbox/query 脚本看到对应 mail
- PC 写出 `mail_observed` / `handler_started` / `handler_finished`

### 6.2 `android_direct_ping_to_vps_to_pc`

目标：

- 验证 Android -> relay -> VPS -> PC 的 direct ingress 通路

最小负载：

- relay `command_type = transport_probe`
- payload 使用 `taskmail-transport-probe-payload-v1`

成功标准：

- Android 写出 `relay_probe_submitted`
- VPS 写出 `packet_received` / `packet_accepted` / `bridge_started` / `bridge_finished`
- PC 写出 `mail_observed` 或 `direct_observed`

### 6.3 `pc_mail_ping_to_android`

目标：

- 验证 PC -> user mailbox -> Android read path

最小负载：

- subject: `[TPROBE][P2A][MAIL] <probe_id>`
- body: 使用 `taskmail-transport-probe-payload-v1` 定义的固定 header block

成功标准：

- PC 写出 `mail_submitted`
- Android mail ingress 看到该 mail
- Android 写出 `mail_observed` / `projection_observed`

### 6.4 `pc_mail_ping_reply_loop`

目标：

- 在不触发真实 TaskMail 业务的前提下，验证 Android 收到 probe 后能按 debug route 回一个极简 ack

这个场景不是第一优先级，但它能快速判断：

- Android 读链正常
- Android debug probe 发送链也正常
- mailbox headers continuity 没坏

## 7. Android 侧建议实现

### 7.1 新增 debug-only probe entrypoint

建议新增一个 `TaskMail Transport Probe` debug surface，支持：

- `send_mail_probe`
- `send_direct_probe`
- `watch_inbox_for_probe`
- `export_probe_artifacts`

更推荐的入口不是普通 UI 按钮，而是可脚本驱动的 deep link / intent extra，例如：

- `app://taskmail/debug/probe?mode=send_mail_probe&probe_id=...`
- `app://taskmail/debug/probe?mode=watch_inbox_for_probe&probe_id=...`

### 7.2 Android 本地 artifact 目录

建议路径：

- `/sdcard/Android/data/<debug package>/files/taskmail-debug/transport-probe/<probe_id>/`

每次 probe 只写本 probe 的独立目录，不混入 `project-sync-debug.log`。

### 7.3 Android 脚本

建议在本仓新增统一入口：

- `scripts/taskmail_transport_probe.py`

建议子命令：

- `send-mail-probe`
- `send-direct-probe`
- `wait-receive`
- `pull-artifacts`
- `merge-report`

这个脚本应优先复用：

- 当前 adb / debug deep link 路径
- 当前 `scripts/taskmail_bot_mailbox_smoke.py` 对 PC workspace 的导入方式

而不是再造一套孤立的环境加载逻辑。

## 8. PC 侧建议实现

### 8.1 PC probe 脚本族

建议在 `mail_based_task_manager` 新增：

- `scripts/transport_probe_send.py`
- `scripts/transport_probe_query.py`
- `scripts/transport_probe_watch.py`
- `scripts/transport_probe_report.py`

### 8.2 PC 侧 artifact

建议路径：

- `E:\projects\mail_based_task_manager\_tmp_transport_probe\<probe_id>\`

建议落盘：

- `manifest.json`
- `events.jsonl`
- `mail_observed.json`
- `pc_handler_result.json`
- `result.json`

### 8.3 与现有脚本的关系

现有脚本不应继续独立扩展：

- `scripts/taskmail_bot_mailbox_smoke.py`
- `scripts/live_smoke_mail_roundtrip.py`
- `scripts/live_smoke_mail_sync.py`

建议处理方式：

- 能吸收的逻辑吸收到新的 probe 脚本族
- 旧脚本保留为 wrapper 或进入 archive，不再继续加功能

## 9. VPS / Relay 侧建议实现

用户提到的“VPS 带上执行的开始与结束”是这套 harness 的关键价值点。

建议 relay 对 probe 额外持久化：

- `probe_id`
- `request_id`
- `packet_id`
- `received_at`
- `accepted_at`
- `bridge_started_at`
- `bridge_finished_at`
- `result_started_at`
- `result_finished_at`
- `reject_code`
- `reject_message`

建议不要把这件事实现成新的产品业务 endpoint；更合理的是：

- 作为 relay packet history 的 probe 扩展字段
- 或者作为 debug-only probe artifact 文件

## 10. 合并报表

需要一个统一合并器把 Android / PC / VPS 证据拉到一条时间线上。

建议输出：

- `timeline.json`
- `timeline.md`

建议排序键：

1. actor 内：`monotonic_ms`
2. 跨 actor：`recorded_at + clock_offset_ms`（如果 offset 已知）
3. fallback：`recorded_at`
4. `actor_priority`
5. `event_type`

这样可以直接看出：

- probe 有没有发出去
- 是丢在 Android 发送前、VPS bridge 中，还是 Android 接收后 projection 没刷新

如果跨 actor 只能退回 `recorded_at`，报表必须显式提示：

- 当前时间线包含 wall-clock 近似排序
- 不应把相邻跨机事件的毫秒级先后解读为严格因果

## 11. 与 unified control plane 的关系

这套 harness 不是附属品，它本身应该成为 unified control plane 的一部分。

原因：

- 统一 gateway 天然适合暴露 probe send/receive entrypoint
- 统一 event store 天然适合存 probe event
- 统一 projection 天然适合输出 `probe receive observed`

如果 harness 继续做在旧旁支外面，它会再次复制 transport 逻辑，最终又变成另一个平行系统。

同时它也必须复用共享 transport shell，而不是定义自己的 wire：

- `transport_probe` 应作为 `taskmail-android-pc-control-artifact-contract-v0.1.md` 上的 payload schema
- 不能再做成脚本私有 envelope

交付策略上则应明确：

- 逻辑归属上，harness 属于 unified control plane
- 交付顺序上，允许 harness 先于大 cutover 落地

原因是重构期间最缺的不是更多业务功能，而是可观测性。

## 12. 非目标

本文当前不要求：

- probe 一开始就支持全部正式业务动作
- probe 进入正式 product UI
- probe 取代现有所有业务 smoke
- Android 立即支持 PC -> Android direct relay-native inbound

当前最小闭环只要求：

- Android 发简单 probe，PC 能查到
- VPS 有 relay start/end 证据
- PC 发简单 probe mail，Android 能确认收到

## 13. 推荐落地顺序

1. 先冻结 `TransportProbeManifest` / `TransportProbeEvent` 的 v1 字段。
   这些 artifact 字段已与 `taskmail-transport-probe-payload-contract-v0.1.md` 对齐；后续脚本应直接引用该 payload contract。
2. 在 PC / VPS 侧补最小 probe event 持久化。
3. 在 Android debug host 加 `TaskMail Transport Probe` entrypoint 与本地 artifact 写入。
4. 新增 `scripts/taskmail_transport_probe.py` 作为 Android 仓统一入口。
5. 把旧 smoke 脚本改成 wrapper 或停止扩展。

## 14. 当前结论

用户提出的方向是对的：

- 把通讯观察从业务里剥离出来
- 把 Android / PC / VPS 三端证据放到同一条 probe 时间线
- 先证明 transport，再看业务

这会显著降低 debug 成本，也会让后续 unified control plane 的验证从“看运气的 live smoke”变成“有统一证据的 transport probe”。
