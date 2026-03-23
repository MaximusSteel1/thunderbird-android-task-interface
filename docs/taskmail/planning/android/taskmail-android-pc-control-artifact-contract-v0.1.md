# TaskMail Android-PC 控制面与文件面合同（v0.1）

更新时间：2026-03-24

## 状态

- 本文是 TaskMail unified control plane 的 shared contract draft。
- 本文冻结 Android 与 PC 之间的通用 transport shell，不冻结 `new_task`、`session_action`、`project_sync` 等业务 payload 细节。
- 当前已实现行为仍以 `E:\projects\mail_based_task_manager\docs\current\android_runner_communication_contract.md` 为准。
- 本文代表 vNext 目标合同；只有在 Android / PC / VPS 三端 cutover 后，它才会取代当前 mail-first boundary。
- 截至 2026-03-24，live relay `/v1/files` current behavior 仍只接受 `kind=image|file`；Android 真机单样本已证明文本文件可以先按 `kind=file` + 正确 `mime_type` 通过，这属于 current compatibility 约束，不改变本文对 vNext `kind` 枚举的目标定义。

## Read First

- `docs/taskmail/planning/android/taskmail-unified-control-plane-architecture-v0.1.md`
- `docs/taskmail/planning/android/taskmail-unified-control-plane-cutover-map-v0.1.md`
- `docs/taskmail/planning/android/taskmail-android-pc-communication-development-conditions-v0.1.md`
- `docs/taskmail/planning/android/taskmail-transport-probe-payload-contract-v0.1.md`
- `docs/taskmail/planning/android/taskmail-transport-observability-harness-v0.1.md`
- `E:\projects\mail_based_task_manager\docs\plans\taskmail_android_pc_control_artifact_companion_note_v0.1.md`
- `E:\projects\mail_based_task_manager\docs\current\android_runner_communication_contract.md`
- `E:\projects\mail_based_task_manager\docs\platform\relay_transport_protocol_draft.md`
- `E:\projects\mail_based_task_manager\docs\plans\run_artifact_delivery_plan.md`

## 1. 一句话合同

TaskMail vNext 采用双平面通信：

- 文本与控制面：`WebSocket + JSON envelope`
- 文件与二进制面：`HTTP upload/download + JSON metadata reference`

文件内容不走 WebSocket 正式载荷；WebSocket 只传命令、结果、事件和 artifact 引用。

## 2. 这份合同解决什么问题

当前结构的问题不是能不能发数据，而是 transport 与业务耦合太紧：

- direct sender 每条业务线各写一套 envelope
- artifact / image / attachment 没有统一引用层
- result、event、replay、probe 不能复用同一 transport shell

本合同冻结的是这些共性：

- 连接与握手
- 通用 envelope 结构
- 相关性键与实体 identity
- `command -> ack -> result/event` 生命周期
- 文件上传下载与元数据引用
- 小图片在 JSON 中的受限支持
- replay / idempotency / observability 最低规则

## 3. 核心决策

### 3.1 控制面固定为 WebSocket

控制面统一走：

- `ws://.../control` 或 `wss://.../control`

适用内容：

- `hello`
- `command`
- `command_ack`
- `event`
- `result`
- `error`
- `ping`
- `pong`

### 3.2 文件面固定为 HTTP

文件面统一走：

- `POST /v1/files`
- `GET /v1/files/{file_id}`
- `GET /v1/files/{file_id}/content`

适用内容：

- 图片
- 文档
- 附件
- 大文本
- JSON sidecar
- run artifact

### 3.3 JSON 允许引用小图片，但不承载正式图片真相

这里冻结两条规则：

1. 图片的 canonical truth 仍是 HTTP 文件对象。
2. JSON metadata 可以可选携带 `inline_preview`，用于极小图片预览或 probe/debug，但它不是 canonical image body。

## 4. 非目标

本文当前不冻结：

- `new_task` 的业务字段
- `session_reply` / `session_command` 的业务字段
- `project_sync` 的业务字段
- 正式产品 UI 行为
- 邮件格式与 mail fallback 细节

这些内容将作为独立 payload contract 挂到本文定义的 envelope 壳上。

## 5. 参与方与逻辑拓扑

逻辑参与方：

- `android_client`
- `pc_runtime`
- `vps_relay`

逻辑合同是 Android 与 PC 之间的共享合同；VPS 负责 transport termination、routing、durable ack/result、artifact hosting。

推荐拓扑：

```text
Android
  -> WebSocket /control
  -> HTTP /v1/files
  -> VPS relay
  -> PC runtime
```

PC -> Android 的结果、事件、artifact 也通过同一对 `/control` 和 `/v1/files` 面返回。

## 6. 认证与连接

### 6.1 统一 transport token

WebSocket 与 HTTP 共用同一份 transport token。

推荐：

- HTTP: `Authorization: Bearer <transport_token>`
- WebSocket upgrade: 同样携带 `Authorization: Bearer <transport_token>`

### 6.2 `hello` 仍然保留

即使 transport token 已在握手层认证，控制面仍保留 `hello` / `hello_ack`，因为它负责：

- client identity
- feature negotiation
- supported payload schema declaration
- heartbeat 协商

## 7. Endpoint 冻结

### 7.1 健康检查

- `GET /healthz`

只用于 debug / ops，不承载业务。

### 7.2 控制面

- `WS /control`

### 7.3 文件上传

- `POST /v1/files`

`v0.1` 冻结为单次上传，不引入 chunk/resume 协议。

第一版共享上限同步冻结为：

- `single_file_upload_limit_bytes = 33554432`
- 即 `32 MiB`

请求格式：

- `multipart/form-data`
- part `metadata`: JSON
- part `file`: binary content

### 7.4 文件元数据

- `GET /v1/files/{file_id}`

返回 JSON metadata。

### 7.5 文件下载

- `GET /v1/files/{file_id}/content`

返回原始 bytes。

## 8. 控制面 base envelope

所有 WebSocket JSON frame 共享同一 base envelope：

```json
{
  "schema_version": "taskmail-control-artifact-contract-v1",
  "message_type": "command",
  "envelope_id": "env_01JQ...",
  "sent_at": "2026-03-23T18:00:00Z",
  "sender": {
    "peer_kind": "android_client",
    "peer_id": "android-debug-device-01"
  },
  "trace": {
    "trace_id": "trace_01JQ...",
    "probe_id": "probe_01JQ..."
  }
}
```

固定字段：

- `schema_version`
- `message_type`
- `envelope_id`
- `sent_at`
- `sender`
- `trace`

其中：

- `trace_id` 必填
- `probe_id` 选填；transport harness 与调试场景强烈建议填写

## 8.1 相关性键冻结

仅有 `operationId / requestId / packetId` 不够支撑 projector 与跨端 bind。

因此 v0.1 额外冻结一组通用相关性键。它们不要求所有 message type 全量出现，但一旦语义成立，就必须显式给出，不能回退到启发式拼接：

- `trace_id`
- `probe_id`
- `request_id`
- `packet_id`
- `receipt_id`
- `result_id`
- `subscription_id`
- `workspace_id`
- `session_id`
- `message_id`
- `source_id`
- `artifact_id`
- `file_id`

推荐做法是在 `command` / `event` / `result` 里统一带一个 `related` block：

```json
{
  "related": {
    "workspace_id": "workspace_01JQ...",
    "session_id": "thread_101",
    "message_id": "<mail_01JQ@example>",
    "source_id": "relay-session-update:thread_101:snapshot:0001",
    "artifact_ids": ["artifact_01JQFILE"],
    "file_ids": ["file_01JQFILE"]
  }
}
```

冻结规则：

- `workspace_id` / `session_id` 是 projector bind 的一等键，不允许仅靠标题或正文启发式恢复
- `message_id` 只表示 mail artifact identity，不替代 `source_id`
- `source_id` 表示“这条外部 source 事实”的稳定 identity
- `artifact_id` / `file_id` 必须稳定区分“逻辑 artifact”与“底层文件对象”
- Android / PC / VPS 任一侧只要已经知道这些键，就必须透传，不得主动丢弃

## 9. 控制面 message type

### 9.1 `hello`

连接建立后由 client 发送。

附加字段：

- `client_version`
- `supported_payload_schemas`
- `supported_extensions`

### 9.2 `hello_ack`

由 relay 返回。

附加字段：

- `connection_id`
- `server_time`
- `heartbeat_seconds`
- `transport_token_id`
- `accepted_payload_schemas`

### 9.3 `command`

所有主动请求统一用 `command`。

附加字段：

- `request_id`
- `packet_id`
- `command_type`
- `payload_schema`
- `payload`
- `artifacts`
- `related`

说明：

- `request_id` 表示逻辑请求 identity
- `packet_id` 表示 transport idempotency identity
- `payload_schema` 指向具体业务合同标题
- `artifacts` 是本次请求携带的文件引用列表
- `related` 是与该 command 绑定的 workspace/session/message/source/artifact identity

示例：

```json
{
  "schema_version": "taskmail-control-artifact-contract-v1",
  "message_type": "command",
  "envelope_id": "env_01JQCMD",
  "sent_at": "2026-03-23T18:01:00Z",
  "sender": {
    "peer_kind": "android_client",
    "peer_id": "android-debug-device-01"
  },
  "trace": {
    "trace_id": "trace_01JQCMD",
    "probe_id": "probe_01JQCMD"
  },
  "request_id": "req_01JQCMD",
  "packet_id": "pkt_01JQCMD",
  "command_type": "transport_probe",
  "payload_schema": "taskmail-transport-probe-payload-v1",
  "payload": {
    "direction": "android_to_pc",
    "payload_text": "PING"
  },
  "artifacts": []
}
```

### 9.4 `command_ack`

relay 在 durable accept 或 reject 后返回。

附加字段：

- `request_id`
- `packet_id`
- `accepted`
- `receipt_id`
- `error_code`
- `error_message`
- `retry_class`
- `received_at`
- `related`

`retry_class` 冻结为：

- `replayable`
- `fallbackable`
- `terminal`

关键规则：

- `accepted = true` 表示该请求已被 durable 接受，后续必须能产出 result 或 replay 同一 result
- `accepted = false` 才允许按业务合同决定是否 fallback

### 9.5 `event`

用于 push 型中间事件或订阅事件。

附加字段：

- `request_id`
- `subscription_id`
- `event_type`
- `payload_schema`
- `payload`
- `artifacts`
- `related`

### 9.6 `result`

用于最终结果或阶段性结果。

附加字段：

- `request_id`
- `packet_id`
- `receipt_id`
- `result_id`
- `result_type`
- `status`
- `payload_schema`
- `payload`
- `artifacts`
- `related`

`status` 冻结为：

- `partial`
- `completed`
- `failed`

### 9.7 `error`

用于协议级错误，而不是业务结果。

附加字段：

- `code`
- `message`
- `request_id`
- `packet_id`

### 9.8 `ping` / `pong`

用于心跳与 idle 保活。

## 10. 业务 payload 挂接规则

本文不定义业务 payload 字段，但冻结其挂接方式：

- 每个 `command` / `event` / `result` 必须带 `payload_schema`
- `payload_schema` 必须指向一个明确标题的 companion contract
- 同一 `payload_schema` 下，`payload` 必须保持机器可验证语义稳定

典型例子：

- `taskmail-operation-new-task-v1`
- `taskmail-operation-session-action-v1`
- `taskmail-operation-project-sync-v1`
- `taskmail-transport-probe-payload-v1`

## 11. Artifact metadata 冻结

所有文件引用都使用统一 `artifact descriptor`：

```json
{
  "artifact_id": "artifact_01JQFILE",
  "file_id": "file_01JQFILE",
  "name": "chart.png",
  "kind": "image",
  "role": "attachment",
  "mime_type": "image/png",
  "byte_size": 182733,
  "sha256": "2f9f...",
  "metadata_url": "/v1/files/file_01JQFILE",
  "download_url": "/v1/files/file_01JQFILE/content",
  "image": {
    "width": 1280,
    "height": 720
  },
  "inline_preview": {
    "encoding": "base64",
    "mime_type": "image/webp",
    "byte_size": 14321,
    "data": "UklGR..."
  }
}
```

固定字段：

- `artifact_id`
- `file_id`
- `name`
- `kind`
- `role`
- `mime_type`
- `byte_size`
- `sha256`
- `metadata_url`
- `download_url`

可选字段：

- `image`
- `inline_preview`

`kind` 当前冻结为：

- `file`
- `image`
- `text`
- `json`

但要明确区分：

- 以上枚举是 vNext shared contract 目标
- 截至 2026-03-24 的 live runtime current behavior 仍只接受 `image | file`
- 因此当前联调阶段，`text/plain` 或 `application/json` sidecar 若走 live `/v1/files`，仍应暂时编码为 `kind=file`，并保留准确 `mime_type`

`role` 当前冻结为：

- `attachment`
- `input`
- `output`
- `thumbnail`
- `debug_artifact`

## 12. 小图片规则

你的判断是对的：JSON 应该覆盖对小图片的支持，但应是受限支持，而不是让 JSON 重新变成文件通道。

因此冻结为：

### 12.1 合理

- 在 artifact metadata 里描述图片尺寸、类型、哈希：合理
- 在 artifact metadata 里可选携带极小图片预览：合理

### 12.2 不作为主路径

- 大图不进 JSON
- 正式图片不进 JSON
- 所有图片的 canonical truth 仍是 `file_id + HTTP download`

### 12.3 `inline_preview` 限制

`inline_preview` 只允许：

- `mime_type` 为 `image/png`、`image/jpeg`、`image/webp`
- `byte_size <= 65536`
- 仅用于预览

接收方规则：

- 可以直接渲染 `inline_preview`
- 不得把 `inline_preview` 当作正式原图保存真相
- 如果需要正式内容，必须按 `download_url` 拉取

## 13. 文本大小规则

控制面文本默认 inline 在 JSON 中。

但为了防止控制面退化成“大文本传输层”，冻结以下建议边界：

- 单个普通文本字段建议不超过 `64 KiB`
- 超过该大小的正文、Markdown、JSON sidecar 应上传为 `kind = text` 或 `kind = json` artifact，再在 `payload` 中引用

current implementation compatibility 注记：

- 在 vNext 合同里，上述读法仍成立
- 但截至 2026-03-24 的 live runtime 尚未接受 `kind=text/json`
- 因此当前 Android / PC / VPS 真机联调里，大文本或 JSON 文件若要走 live `/v1/files`，仍需先按 `kind=file` 上传，同时依赖 `mime_type` 区分真实内容类型

## 14. HTTP 上传合同

### 14.1 `POST /v1/files`

请求 `metadata` JSON 建议字段：

- `name`
- `kind`
- `role`
- `mime_type`
- `byte_size`
- `sha256`
- `image`
- `trace`

current implementation compatibility 注记：

- 截至 2026-03-24，若 `metadata.kind` 直接发送 `text` 或 `json`，live runtime 当前可能返回 `invalid_metadata`
- 因此当前兼容实现仍应优先发送 `kind=file`，并用 `mime_type=text/plain` 或 `mime_type=application/json` 表达真实内容类型

服务端职责：

- 校验 token
- 接收文件
- 计算并确认 `sha256`
- 生成稳定 `file_id`
- 回填 canonical artifact descriptor

响应：

```json
{
  "schema_version": "taskmail-control-artifact-contract-v1",
  "file_id": "file_01JQFILE",
  "stored_at": "2026-03-23T18:03:00Z",
  "artifact": {
    "artifact_id": "artifact_01JQFILE",
    "file_id": "file_01JQFILE",
    "name": "chart.png",
    "kind": "image",
    "role": "attachment",
    "mime_type": "image/png",
    "byte_size": 182733,
    "sha256": "2f9f...",
    "metadata_url": "/v1/files/file_01JQFILE",
    "download_url": "/v1/files/file_01JQFILE/content"
  }
}
```

## 15. HTTP 下载合同

### 15.1 `GET /v1/files/{file_id}`

返回 JSON metadata。

### 15.2 `GET /v1/files/{file_id}/content`

返回原始 bytes 与正确 `Content-Type`。

建议：

- 支持 `ETag`
- 支持 `Cache-Control`
- 权限仍绑定 transport token

## 16. 幂等与 replay 规则

### 16.1 控制面

同一个 `packet_id + request_id` 重发时：

- `command_ack` 必须语义稳定
- 如果之前已经 `accepted = true`，则必须返回同一 `receipt_id`
- 若已有 final result，则 replay 时必须返回同一 `result_id`

### 16.2 文件面

`file_id` 一旦生成后不可复写为不同内容。

如果服务端选择按 `sha256` 去重，必须保证：

- 返回的 `file_id` 始终指向相同 bytes
- metadata 中的 `sha256`、`byte_size` 与下载内容一致

## 17. Observability 最低要求

本合同要求 transport 本身可观察。

最低要求：

- 每个 WebSocket frame 都有 `envelope_id` 与 `trace_id`
- 每个 command 都有 `request_id`、`packet_id`
- 每个 accepted command 都有 `receipt_id`
- 每个 final result 都有 `result_id`
- 每个 artifact 都有 `file_id`、`sha256`
- `probe_id` 在 transport harness 场景中应全链路透传

## 18. 与 transport harness 的关系

本文与 `taskmail-transport-observability-harness-v0.1.md` 的关系是：

- 本文定义 transport shell
- `taskmail-transport-probe-payload-contract-v0.1.md` 定义 `transport_probe` payload
- harness 文档定义如何用 `transport_probe` 场景消费这个 shell

也就是说：

- `transport_probe` 是本合同上的一个 payload schema
- 不是一套另起炉灶的脚本私有协议

## 19. 当前结论

这轮冻结明确采纳以下方向：

- 文件面：`HTTP upload/download + JSON metadata reference`
- 文本与控制面：`WebSocket + JSON envelope`
- 小图片：允许在 JSON metadata 里受限支持 `inline_preview`
- 正式文件真相：始终回到 `file_id + HTTP`

这套边界比“所有东西都塞进 WebSocket”更稳，也比“继续按业务线各写一套 transport”更适合 unified control plane。
