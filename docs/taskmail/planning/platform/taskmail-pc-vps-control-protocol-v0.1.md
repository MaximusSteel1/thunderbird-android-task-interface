# TaskMail PC-VPS 控制协议草案（v0.1）

更新日期：2026-03-25  
状态：平台层协议草案 / companion protocol；与主线 authority 配套使用，不直接替代 current-truth 文档

> 说明：
>
> 本文件描述的是 `VPS-first 多 PC 控制面` 方向下，`PC <-> VPS` 之间应逐步冻结的最小控制协议。
>
> 它不声称当前仓库已经实现这些消息。
>
> 当前实现事实仍以：
>
> - `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
> - `E:\projects\mail_based_task_manager\docs\current\android_runner_communication_contract.md`
> - `E:\projects\mail_based_task_manager\docs\current\taskmail_direct_control_file_contract.md`
>
> 为准。

## 1. 文档目的

本文只回答一个问题：

**在 `VPS-first 多 PC 控制面` 方向下，`PC` 与 `VPS` 之间最小且可演进的结构化协议应当长什么样。**

本文聚焦：

- 统一 envelope
- 主键与幂等规则
- `PC -> VPS` / `VPS -> PC` 消息类型
- replay / fencing / streaming 的最小约束
- `execution_policy` 的最小协议读法
- legacy mail 语义如何映射到 `command / event / result`
- `result / artifact / error_code` 的最小协议读法
- `command / event` 的最小 payload 读法

本文不负责：

- Android UI 读写细节
- 当前 mail-first 协议行为说明
- 第一版之外的多用户 ACL 细化

## 2. 协议目标

该协议需要同时满足四个目标：

1. 支持一个 `VPS` 同时管理多台 `PC`
2. 允许 `workspace` 作为 `pc-scoped` 资源被稳定路由
3. 同时支持结构化状态与流式输出
4. 在断线、重连、重发时具备基础 replay 与 fencing 能力

## 3. 协议边界

本协议假定：

- `VPS` 持有控制面真相
- `PC` 持有执行真相
- `workspace` 从属于某个 `PC`
- `session` 绑定到某个 `workspace`
- `run` 在绑定的 `PC` 上执行

本协议不解决：

- 两台 `PC` 同时执行同一个 `workspace`
- 运行中 session 的跨 PC 热迁移
- mail 作为主控制面的兼容设计

## 4. 统一 Envelope

所有消息都建议使用统一 envelope。

示例：

```json
{
  "schema_version": "v1",
  "type": "event",
  "message_id": "msg_01HZX3YF4QJ7A5K7S5E5X1",
  "trace_id": "trace_01HZX3YF4QJ7A5K7S5E5X1",
  "pc_id": "pc_home",
  "connection_epoch": 12,
  "sent_at": "2026-03-25T10:00:00Z",
  "payload": {}
}
```

建议固定字段：

- `schema_version`
- `type`
- `message_id`
- `trace_id`
- `pc_id`
- `connection_epoch`
- `sent_at`
- `payload`

字段说明：

- `schema_version`
  - 当前协议版本，首版固定为 `v1`
- `type`
  - 消息类型，例如 `pc_hello`、`heartbeat`、`command_dispatch`
- `message_id`
  - 该消息自身的唯一标识
- `trace_id`
  - 同一业务链路的追踪标识
- `pc_id`
  - 当前连接对应的执行节点
- `connection_epoch`
  - 当前有效连接代次，用于 fencing
- `sent_at`
  - 发送时间
- `payload`
  - 具体业务载荷

## 5. 核心主键

建议协议内固定以下主键。

### 5.1 节点与资源

- `pc_id`
- `workspace_id`
- `session_id`
- `run_id`

### 5.2 控制面对象

- `command_id`
- `event_id`
- `result_id`
- `artifact_id`

### 5.3 流式输出

- `stream_id`
- `seq`

### 5.4 连接与 replay

- `connection_epoch`
- `message_id`
- `trace_id`

### 5.5 `execution_policy`

建议把执行策略作为一等协议对象固定下来。

更细的字段语义与 backend-specific 投影，见 `taskmail-execution-policy-appendix-v0.1.md`。

推荐结构：

```json
{
  "backend": "codex",
  "profile": "strong",
  "permission": "highest",
  "backend_transport": "sdk",
  "resolved_model": null
}
```

字段说明：

- `backend`
  - 第一版建议固定为 `codex | opencode`
  - `new_task` 应显式给出
  - follow-up 默认继承当前 session backend
- `profile`
  - 用户与控制面应优先传稳定 profile label，而不是 raw model id
  - 例如 `fast | strong | vision | android`
  - 具体支持集合应由 PC 通过能力上报
- `permission`
  - 第一版建议固定为 `default | highest`
  - 新建 session 省略时表示 backend 默认权限
  - follow-up 省略时表示继承当前 session 权限
- `backend_transport`
  - 执行侧路由维度，例如 `cli | sdk`
  - 第一版可选
- `resolved_model`
  - 真实模型 id
  - 建议只作为执行侧回报字段，不作为主控制面输入

## 6. 通用约束

### 6.1 连接 fencing

- 同一 `pc_id` 只允许最高 `connection_epoch` 的连接继续提交消息
- 旧连接即使仍存活，也不得继续写入新的 `event`、`output_chunk` 或 `result`

### 6.2 命令幂等

- `command_id` 是一次用户动作的稳定主键
- 同一 `command_id` 不应被重复执行成两次独立 run
- 重连、补发、重放都应围绕同一个 `command_id`

### 6.3 流式输出顺序

- `output_chunk` 以 `stream_id + seq` 作为顺序键
- 相同 `stream_id` 下 `seq` 必须严格递增
- `seq` 不应用于跨 stream 排序

### 6.4 终态唯一性

- 同一 `command_id` 最多只应有一个 canonical `result`
- 同一 `run_id` 最多只应有一个 terminal `result`

### 6.5 执行策略约束

- `backend`、`profile`、`permission` 是控制面一等字段，不应继续只作为 mail 语义 sidecar 存在
- `profile` 解决模型档位选择，不直接暴露 raw model id
- `permission` 解决执行权限，不等于能力开关
- `permission=highest` 不应隐式打开被全局关闭的能力，例如搜索
- `Codex` 与 `OpenCode` 的权限投影应允许不同，但外层字段保持同一语义

## 7. `PC -> VPS`

第一版建议支持以下消息：

- `pc_hello`
- `heartbeat`
- `workspace_snapshot`
- `command_ack`
- `event`
- `output_chunk`
- `result`
- `artifact_manifest`

### 7.1 `pc_hello`

用途：

- 建立执行节点会话
- 绑定 `pc_id`
- 上报最小能力信息

示例：

```json
{
  "schema_version": "v1",
  "type": "pc_hello",
  "message_id": "msg_hello_01",
  "trace_id": "trace_boot_01",
  "pc_id": "pc_home",
  "connection_epoch": 0,
  "sent_at": "2026-03-25T10:00:00Z",
  "payload": {
    "display_name": "Home PC",
    "client_version": "0.1.0",
    "host_fingerprint": "host_abc123",
    "runtime_fingerprint": "runtime_def456",
    "capabilities": {
      "streaming": true,
      "artifact_manifest": true,
      "workspace_snapshot": true,
      "supported_backends": ["codex", "opencode"],
      "profile_catalogs": {
        "codex": ["fast", "strong", "vision"],
        "opencode": ["fast", "strong", "vision"]
      },
      "permission_modes": ["default", "highest"],
      "backend_transport_modes": {
        "codex": ["cli", "sdk"],
        "opencode": ["cli"]
      }
    }
  }
}
```

最小字段：

- `display_name`
- `client_version`
- `host_fingerprint`
- `runtime_fingerprint`
- `capabilities`

说明：

- 认证建议走 transport header 或 token 绑定，不建议把长期 token 本身写入 payload
- 首次 `pc_hello` 可带 `connection_epoch = 0`，由服务端在 `hello_ack` 中下发当前有效 epoch

### 7.2 `heartbeat`

用途：

- 维持在线状态
- 上报节点轻量健康信息

示例：

```json
{
  "schema_version": "v1",
  "type": "heartbeat",
  "message_id": "msg_hb_01",
  "trace_id": "trace_hb_pc_home",
  "pc_id": "pc_home",
  "connection_epoch": 12,
  "sent_at": "2026-03-25T10:00:10Z",
  "payload": {
    "active_run_count": 2,
    "workspace_count": 5,
    "load_hint": "normal"
  }
}
```

最小字段：

- `active_run_count`
- `workspace_count`
- `load_hint`

### 7.3 `workspace_snapshot`

用途：

- 上报当前 PC 的 workspace 清单
- 让 VPS 拿到可路由资源列表

示例：

```json
{
  "schema_version": "v1",
  "type": "workspace_snapshot",
  "message_id": "msg_ws_01",
  "trace_id": "trace_ws_pc_home",
  "pc_id": "pc_home",
  "connection_epoch": 12,
  "sent_at": "2026-03-25T10:00:15Z",
  "payload": {
    "snapshot_id": "ws_snap_01",
    "workspaces": [
      {
        "workspace_id": "ws_pc_home_repo_a_src",
        "repo_path": "E:\\projects\\repo_a",
        "workdir": "src",
        "display_name": "repo_a/src",
        "capabilities": {
          "codex": true,
          "opencode": true
        }
      }
    ]
  }
}
```

最小字段：

- `snapshot_id`
- `workspaces[]`

每个 `workspace` 至少包含：

- `workspace_id`
- `repo_path`
- `workdir`
- `display_name`
- `capabilities`

其中 `capabilities` 至少建议包含：

- `supported_backends`
- `profile_catalogs`
- `permission_modes`
- `backend_transport_modes`

### 7.4 `command_ack`

用途：

- 表示命令已被该 PC 接收
- 说明该命令是立即执行、进入队列还是被拒绝

示例：

```json
{
  "schema_version": "v1",
  "type": "command_ack",
  "message_id": "msg_ack_01",
  "trace_id": "trace_cmd_01",
  "pc_id": "pc_home",
  "connection_epoch": 12,
  "sent_at": "2026-03-25T10:01:00Z",
  "payload": {
    "command_id": "cmd_01",
    "ack_status": "accepted_but_queued",
    "queue_position": 1,
    "reason": null,
    "error_code": null
  }
}
```

建议 `ack_status`：

- `accepted`
- `accepted_but_queued`
- `rejected`

建议首批 `error_code`：

- `unsupported_backend`
- `unsupported_profile`
- `unsupported_permission`
- `profile_model_unresolved`

更完整的错误码分层，见 `taskmail-result-artifact-errorcode-appendix-v0.1.md`。

### 7.5 `event`

用途：

- 传输结构化状态变化

不同 `event_type` 的 payload 形状，见 `taskmail-command-event-payload-appendix-v0.1.md`。

示例：

```json
{
  "schema_version": "v1",
  "type": "event",
  "message_id": "msg_evt_01",
  "trace_id": "trace_cmd_01",
  "pc_id": "pc_home",
  "connection_epoch": 12,
  "sent_at": "2026-03-25T10:01:20Z",
  "payload": {
    "event_id": "evt_01",
    "command_id": "cmd_01",
    "workspace_id": "ws_pc_home_repo_a_src",
    "session_id": "sess_01",
    "run_id": "run_01",
    "event_type": "running",
    "payload": {
      "effective_execution": {
        "backend": "codex",
        "profile": "strong",
        "permission": "highest",
        "backend_transport": "sdk",
        "resolved_model": "gpt-5-codex"
      }
    }
  }
}
```

建议首批 `event_type`：

- `queued`
- `accepted`
- `running`
- `awaiting_user_input`
- `paused`
- `done`
- `failed`
- `killed`

### 7.6 `output_chunk`

用途：

- 传输实时文本流

示例：

```json
{
  "schema_version": "v1",
  "type": "output_chunk",
  "message_id": "msg_out_01",
  "trace_id": "trace_cmd_01",
  "pc_id": "pc_home",
  "connection_epoch": 12,
  "sent_at": "2026-03-25T10:01:25Z",
  "payload": {
    "stream_id": "stream_run_01_assistant",
    "command_id": "cmd_01",
    "run_id": "run_01",
    "seq": 1,
    "channel": "assistant_text",
    "text": "正在读取项目结构..."
  }
}
```

建议首批 `channel`：

- `assistant_text`
- `system`
- `stderr`

### 7.7 `result`

用途：

- 传输某次命令或某次 run 的最终收口结果

详细字段、`structured_payload.kind` 与错误码分层，见 `taskmail-result-artifact-errorcode-appendix-v0.1.md`。

示例：

```json
{
  "schema_version": "v1",
  "type": "result",
  "message_id": "msg_res_01",
  "trace_id": "trace_cmd_01",
  "pc_id": "pc_home",
  "connection_epoch": 12,
  "sent_at": "2026-03-25T10:02:00Z",
  "payload": {
    "result_id": "res_01",
    "command_id": "cmd_01",
    "workspace_id": "ws_pc_home_repo_a_src",
    "session_id": "sess_01",
    "run_id": "run_01",
    "final_status": "done",
    "summary": "完成重构并保留原有输出。",
    "effective_execution": {
      "backend": "codex",
      "profile": "strong",
      "permission": "highest",
      "backend_transport": "sdk",
      "resolved_model": "gpt-5-codex"
    },
    "structured_payload": {
      "kind": "task_outcome",
      "changed_files": ["floor_shear.py"]
    }
  }
}
```

建议 `final_status`：

- `accepted`
- `running`
- `done`
- `failed`
- `killed`
- `paused`
- `awaiting_user_input`

### 7.8 `artifact_manifest`

用途：

- 传输本次 run 产生的文件元数据

详细 artifact descriptor 与 `download_ref` 读法，见 `taskmail-result-artifact-errorcode-appendix-v0.1.md`。

示例：

```json
{
  "schema_version": "v1",
  "type": "artifact_manifest",
  "message_id": "msg_art_01",
  "trace_id": "trace_cmd_01",
  "pc_id": "pc_home",
  "connection_epoch": 12,
  "sent_at": "2026-03-25T10:02:05Z",
  "payload": {
    "run_id": "run_01",
    "artifacts": [
      {
        "artifact_id": "art_01",
        "kind": "file",
        "name": "summary.md",
        "size": 1024,
        "content_type": "text/markdown",
        "download_ref": {
          "kind": "vps_file",
          "file_id": "file_01",
          "metadata_url": "/v1/files/file_01",
          "content_url": "/v1/files/file_01/content"
        }
      }
    ]
  }
}
```

## 8. `VPS -> PC`

第一版建议支持以下消息：

- `hello_ack`
- `command_dispatch`
- `cancel_command`
- `replay_request`
- `workspace_refresh_request`

### 8.1 `hello_ack`

用途：

- 确认连接建立
- 下发当前有效 `connection_epoch`

示例：

```json
{
  "schema_version": "v1",
  "type": "hello_ack",
  "message_id": "msg_hello_ack_01",
  "trace_id": "trace_boot_01",
  "pc_id": "pc_home",
  "connection_epoch": 12,
  "sent_at": "2026-03-25T10:00:01Z",
  "payload": {
    "accepted": true,
    "keepalive_seconds": 15
  }
}
```

### 8.2 `command_dispatch`

用途：

- 向某台 PC 下发一个结构化动作

legacy mail 动作到 `command_type` 的映射，见 `taskmail-legacy-mail-to-control-plane-mapping-v0.1.md`。
不同 `command_type` 的 payload 形状，见 `taskmail-command-event-payload-appendix-v0.1.md`。

示例：

```json
{
  "schema_version": "v1",
  "type": "command_dispatch",
  "message_id": "msg_cmd_01",
  "trace_id": "trace_cmd_01",
  "pc_id": "pc_home",
  "connection_epoch": 12,
  "sent_at": "2026-03-25T10:00:50Z",
  "payload": {
    "command_id": "cmd_01",
    "command_type": "new_task",
    "workspace_id": "ws_pc_home_repo_a_src",
    "session_id": null,
    "execution_policy": {
      "backend": "codex",
      "profile": "strong",
      "permission": "highest",
      "backend_transport": "sdk"
    },
    "payload": {
      "task_text": "重构 floor_shear.py"
    }
  }
}
```

建议 `command_type` 首批支持：

- `new_task`
- `reply`
- `status`
- `pause`
- `resume`
- `kill`

执行策略规则建议固定为：

- `new_task` 必须显式带 `backend`
- `new_task` 可选带 `profile`
- `new_task` 可选带 `permission`
- follow-up 若未显式带 `backend`，则继承 session 当前 backend
- V1 不建议支持 running session 的 backend 切换

### 8.3 `cancel_command`

用途：

- 取消尚未开始的命令

最小字段：

- `command_id`
- `reason`

### 8.4 `replay_request`

用途：

- 要求 PC 从指定游标之后补发 `event` 或 `output_chunk`

最小字段：

- `command_id`
- `run_id`
- `stream_id`
- `after_seq`
- `after_event_id`

### 8.5 `workspace_refresh_request`

用途：

- 要求 PC 立即重发 `workspace_snapshot`

最小字段：

- `reason`

## 9. 最小时序

### 9.1 建链

1. `PC -> VPS: pc_hello`
2. `VPS -> PC: hello_ack`
3. `PC -> VPS: workspace_snapshot`
4. `PC -> VPS: heartbeat*`

### 9.2 新任务

1. 用户选择 `pc + workspace`
2. `VPS -> PC: command_dispatch(new_task)`
3. `PC -> VPS: command_ack`
4. `PC -> VPS: event(accepted/running/...)`
5. `PC -> VPS: output_chunk*`
6. `PC -> VPS: result`
7. `PC -> VPS: artifact_manifest`

### 9.3 Follow-up

1. 用户对某个 `session_id` 发起动作
2. `VPS` 通过 `session_id` 找到固定 `pc_id + workspace_id`
3. `VPS -> PC: command_dispatch(reply/status/pause/resume/kill)`
4. 后续流程同上

## 10. Replay 规则

建议第一版固定以下规则：

### 10.1 `command_dispatch`

- 同一 `command_id` 可因重连被重复投递
- `PC` 应按 `command_id` 做幂等去重

### 10.2 `event`

- `event_id` 是事件去重键
- `VPS` 应允许重复收到同一 `event_id` 而不产生双写

### 10.3 `output_chunk`

- `stream_id + seq` 是流式输出去重键
- `VPS` 若发现缺洞，可通过 `replay_request` 要求补发

### 10.4 `result`

- `result_id` 是结果去重键
- 如果 terminal `result` 已经存在，重复提交应收敛为同一结果，而不是创建第二个 terminal outcome

## 11. 第一版推荐实现边界

第一版协议实现建议保持保守：

- 只做单节点命令归属
- 只做稳定的 `backend/profile/permission` 控制面字段，不直接把 raw model id 做成主输入
- 不做跨 PC session 迁移
- 不做复杂 priority / lease / 抢占调度
- 不做多租户复杂授权
- 不要求一开始就把 mail 完全剔出系统

## 12. 一句话结论

**`PC <-> VPS` 的第一版协议应先冻结成一条结构化、可 replay、可 fencing、可承载流式输出的最小控制协议，而不是继续围绕 mail/direct 特例逐条补洞。**
