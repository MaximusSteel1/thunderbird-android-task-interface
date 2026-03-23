# TaskMail `transport_probe` Payload 合同（v0.1）

更新时间：2026-03-23

## 状态

- 本文冻结 `taskmail-transport-probe-payload-v1` 的 payload 语义与 carrier 映射。
- 本文属于 unified control plane 的 companion contract，服务于 transport harness、gateway 与 probe 脚本实现。
- 本文不改写当前 mail-first 现状；当前已实现行为仍以 `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md` 与 PC 仓 `docs/current/*` 为准。

## Read First

- `docs/taskmail/planning/android/taskmail-unified-control-plane-architecture-v0.1.md`
- `docs/taskmail/planning/android/taskmail-android-pc-control-artifact-contract-v0.1.md`
- `docs/taskmail/planning/android/taskmail-android-pc-communication-development-conditions-v0.1.md`
- `docs/taskmail/planning/android/taskmail-transport-observability-harness-v0.1.md`
- `E:\projects\mail_based_task_manager\docs\plans\taskmail_android_pc_control_artifact_companion_note_v0.1.md`
- `E:\projects\mail_based_task_manager\docs\plans\taskmail_transport_probe_payload_companion_note_v0.1.md`

## 1. 一句话合同

`transport_probe` 是一套脱离业务语义的共享 probe payload：

- 控制面 carrier 走 `command / event / result`
- mail carrier 走确定性的 subject + `text/plain` header block
- 全链路围绕同一个 `probe_id`

它的目标不是表达业务，而是把 Android / PC / VPS 的 transport 可观测性冻结成一套稳定 contract。

## 2. Schema identity 与 owner

冻结如下：

- `payload_schema = taskmail-transport-probe-payload-v1`
- owner repo:
  - Android planning authority：`E:\projects\android_task_manager`
  - PC companion authority：`E:\projects\mail_based_task_manager`
- producer:
  - `android_client`
  - `pc_runtime`
  - `vps_relay` 只产生 progress/result event，不发起新的 probe command
- consumer:
  - `android_client`
  - `pc_runtime`
  - `vps_relay`

## 3. 非目标

本文当前不定义：

- `new_task`、`session_action`、`project_sync` 的业务字段
- 正式产品 UI
- 邮件 fallback 业务策略
- 分块上传、断点续传或 ranged download

## 4. 核心不变量

### 4.1 一个 probe，只能有一个 `probe_id`

同一条 probe 在 Android / PC / VPS / mailbox / artifact 上必须围绕同一个 `probe_id`。

### 4.2 probe 不携带业务语义

不得把以下业务字段塞进 `transport_probe`：

- `Repo:`
- `Task:`
- `/resume`
- `Answers:`
- `[SYNC]`

### 4.3 相关性键必须显式透传

除 `probe_id` 外，以下键在语义成立时必须显式透传：

- `trace_id`
- `request_id`
- `packet_id`
- `receipt_id`
- `result_id`
- `workspace_id`
- `session_id`
- `message_id`
- `source_id`
- `artifact_id`
- `file_id`

### 4.4 同一 payload，允许挂到不同 carrier

`transport_probe` 的 canonical payload 不因为 carrier 改写语义：

- relay 直连时挂到 `command.payload`
- relay progress/result 时挂到 `event.payload` / `result.payload`
- mail probe 时映射到 subject + body

## 5. Canonical command payload

`message_type = command` 且 `command_type = transport_probe` 时，`payload` 冻结为：

```json
{
  "probe_id": "probe_01JQ...",
  "scenario": "android_direct_ping_to_vps_to_pc",
  "direction": "android_to_pc",
  "transport_kind": "relay_direct",
  "payload_text": "PING relay path",
  "timeout_seconds": 60
}
```

字段说明：

- `probe_id`
  - 必填
  - 与 `trace.probe_id` 必须相同
- `scenario`
  - 必填
  - 枚举：
    - `android_mail_ping_to_pc`
    - `android_direct_ping_to_vps_to_pc`
    - `pc_mail_ping_to_android`
    - `pc_mail_ping_reply_loop`
- `direction`
  - 必填
  - 枚举：
    - `android_to_pc`
    - `pc_to_android`
- `transport_kind`
  - 必填
  - 枚举：
    - `mail`
    - `relay_direct`
    - `mail_reply_loop`
- `payload_text`
  - 必填
  - UTF-8 纯文本
  - 归一化后必须为单行
  - 建议不超过 `1024` bytes
- `timeout_seconds`
  - 选填
  - 范围 `5..600`
  - 未给定时按 scenario 默认值处理

规则：

- `direction` 与 `scenario` 不得矛盾
- `transport_kind` 与 `scenario` 不得矛盾
- `payload_text` 只承担 transport 观察载荷，不承担命令语义

## 6. Scenario 默认语义

### 6.1 `android_mail_ping_to_pc`

- `direction = android_to_pc`
- `transport_kind = mail`
- 默认 `timeout_seconds = 180`
- 成功标准：
  - PC 看到 `pc_probe_mail_observed`
  - PC 看到 `pc_probe_handler_finished`

### 6.2 `android_direct_ping_to_vps_to_pc`

- `direction = android_to_pc`
- `transport_kind = relay_direct`
- 默认 `timeout_seconds = 120`
- 成功标准：
  - relay 返回 accepted `command_ack`
  - relay/PC 最终产出 `result`
  - 或至少能看到 `pc_probe_handler_finished`

### 6.3 `pc_mail_ping_to_android`

- `direction = pc_to_android`
- `transport_kind = mail`
- 默认 `timeout_seconds = 180`
- 成功标准：
  - Android 看到 `android_probe_mail_observed`
  - Android 看到 `android_probe_projection_observed`

### 6.4 `pc_mail_ping_reply_loop`

- `direction = pc_to_android`
- `transport_kind = mail_reply_loop`
- 默认 `timeout_seconds = 240`
- 成功标准：
  - Android 看到 mail probe
  - Android 发出 debug ack mail
  - PC 看到回环 ack

## 7. Event payload

`message_type = event` 且 `payload_schema = taskmail-transport-probe-payload-v1` 时，冻结：

- outer `event_type` 使用 probe 事件枚举
- inner `payload.probe_event_type` 必须与 outer `event_type` 相同

`payload` 结构：

```json
{
  "probe_id": "probe_01JQ...",
  "scenario": "android_direct_ping_to_vps_to_pc",
  "direction": "android_to_pc",
  "transport_kind": "relay_direct",
  "probe_event_type": "vps_probe_bridge_finished",
  "actor": "vps_relay",
  "summary": "Relay bridge finished",
  "timeline": {
    "clock_source": "vps_wall_clock",
    "monotonic_ms": 1845221,
    "clock_offset_ms": 18
  },
  "metadata": {
    "bridge_mode": "mail_adapter"
  }
}
```

冻结字段：

- `probe_id`
- `scenario`
- `direction`
- `transport_kind`
- `probe_event_type`
- `actor`
- `summary`
- `timeline`

`timeline` 固定字段：

- `clock_source`
- `monotonic_ms`
- `clock_offset_ms`

`actor` 枚举：

- `android_client`
- `pc_runtime`
- `vps_relay`

`probe_event_type` 当前冻结为：

- `android_probe_dispatch_started`
- `android_mail_probe_submitted`
- `android_relay_probe_submitted`
- `android_probe_dispatch_failed`
- `vps_probe_packet_received`
- `vps_probe_packet_accepted`
- `vps_probe_bridge_started`
- `vps_probe_bridge_finished`
- `vps_probe_result_started`
- `vps_probe_result_finished`
- `vps_probe_rejected`
- `pc_probe_mail_observed`
- `pc_probe_handler_started`
- `pc_probe_handler_finished`
- `pc_probe_mail_submitted`
- `android_probe_mail_observed`
- `android_probe_projection_observed`
- `android_probe_receive_timeout`

规则：

- `timeline.clock_source` 必须与 actor 对应
- 同一 actor 的事件排序优先依赖 `monotonic_ms`
- `metadata` 只放 carrier-specific 细节，不放业务语义

## 8. Result payload

`message_type = result` 且 `payload_schema = taskmail-transport-probe-payload-v1` 时：

- outer `result_type = transport_probe_result`
- outer `status` 使用 shared contract 的：
  - `partial`
  - `completed`
  - `failed`

`payload` 结构：

```json
{
  "probe_id": "probe_01JQ...",
  "scenario": "android_direct_ping_to_vps_to_pc",
  "direction": "android_to_pc",
  "transport_kind": "relay_direct",
  "outcome": "observed",
  "final_event_type": "pc_probe_handler_finished",
  "summary": "Probe reached PC runtime",
  "timeline": {
    "clock_source": "pc_wall_clock",
    "monotonic_ms": 98213,
    "clock_offset_ms": -12
  },
  "metadata": {
    "observed_event_count": 6
  }
}
```

`outcome` 枚举：

- `observed`
- `replied`
- `timed_out`
- `rejected`
- `failed`

规则：

- `status = completed` 时，`outcome` 只能是：
  - `observed`
  - `replied`
- `status = failed` 时，`outcome` 只能是：
  - `timed_out`
  - `rejected`
  - `failed`

## 9. Mail carrier 映射

mail-carried probe 不另造 payload；它把 canonical command payload 映射到确定性的 mail subject 与 body。

### 9.1 Subject

冻结如下：

- `android_mail_ping_to_pc`
  - `[TPROBE][A2P][MAIL] <probe_id>`
- `pc_mail_ping_to_android`
  - `[TPROBE][P2A][MAIL] <probe_id>`
- `pc_mail_ping_reply_loop`
  - probe mail：`[TPROBE][P2A][MAIL] <probe_id>`
  - ack mail：`[TPROBE][A2P][MAIL][ACK] <probe_id>`

### 9.2 Body

body 使用 `text/plain; charset=UTF-8`，固定 header block：

```text
Probe-Version: taskmail-transport-probe-payload-v1
Probe-Id: probe_01JQ...
Scenario: android_mail_ping_to_pc
Direction: android_to_pc
Transport-Kind: mail
Timeout-Seconds: 180
Payload-Text: PING mailbox path
```

规则：

- 字段顺序固定，不重排
- `Payload-Text` 必须单行
- 不在 body 中混入业务字段或 quoted reply
- 如果 carrier 需要更多 metadata，放到本地 artifact，不放进 mail body

## 10. 文件面与上传上限

`transport_probe` 的 v1 canonical payload 默认是文本；但为了验证文件面，允许 probe 通过 shared envelope `artifacts` 携带最多一个 `role = debug_artifact` 的文件引用。

冻结如下：

- `single_file_upload_limit_bytes = 33554432`
- 即 `32 MiB`
- 与 PC 仓当前 relay runtime / tests 已使用的 `32 * 1024 * 1024` ceiling 对齐，并在 shared contract 中以 bytes 形式固定

规则：

- 超限文件必须返回 `413` 或等价错误
- probe 文件仍遵守统一 artifact descriptor
- `inline_preview` 上限仍是 `65536` bytes

## 11. Probe artifact 目录与必备文件

probe 最终至少应生成：

- `manifest.json`
- `events.jsonl`
- `timeline.json`
- `timeline.md`

建议额外保留：

- `command.json`
- `command_ack.json`
- `result.json`
- `mail_subject.txt`
- `mail_body.txt`

`manifest.json` 至少包含：

- `probe_version`
- `probe_id`
- `scenario`
- `direction`
- `transport_kind`
- `request_id`
- `packet_id`
- `receipt_id`
- `result_id`

## 12. 相关性与 envelope 绑定规则

### 12.1 `trace.probe_id`

所有 relay `command` / `event` / `result`：

- `trace.probe_id` 必填
- 并且必须等于 payload 里的 `probe_id`

### 12.2 `related`

`related` block 在 probe 场景下遵循：

- `message_id`
  - mail probe 被观察到后必须补齐
- `source_id`
  - 每个外部 source fact 必须稳定
- `artifact_id` / `file_id`
  - 有文件面交互时必须透传

### 12.3 `request_id` / `packet_id` / `receipt_id` / `result_id`

- relay direct probe 必须全链路保留
- mail-only probe 可以没有 `packet_id`
- 一旦 relay path accepted，就必须能 replay 出同一 `receipt_id` 与 `result_id`

## 13. 当前结论

`transport_probe` 现在不再只是 harness 里的“建议字段”，而是正式冻结成一份 companion payload contract：

- payload schema 已定
- event/result shape 已定
- mail carrier 编码已定
- probe 文件上限已定

后续 Android / PC / VPS 的 probe 脚本、gateway、event store 与合并报表，都应直接引用本文，而不是各自再长一套私有 probe 语义。
