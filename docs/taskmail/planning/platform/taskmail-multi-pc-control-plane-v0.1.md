# TaskMail 多 PC 控制面草案（v0.1）

更新日期：2026-03-25  
状态：平台层设计草案 / companion design；与主线 authority 配套使用，不直接替代 current-truth 文档

> 说明：
>
> 本文件用于冻结一条更激进但边界清晰的未来平台方向：
>
> - `VPS` 作为统一控制面
> - 多台 `PC` 作为执行节点
> - `workspace` 明确为 `pc-scoped` 本地目录
> - 可选流式输出作为正式能力进入协议
>
> 本文件不覆盖当前实现事实。当前实现与当前 authority 仍以以下文档为准：
>
> - `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
> - `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
> - `docs/TASKMAIL-MAIL-RULES.md`
> - `docs/taskmail/planning/android/taskmail-vps-first-multi-pc-authority-v0.1.md`
> - `docs/taskmail/planning/android/taskmail-android-public-plaintext-direct-connect-authority-v0.1.md`
> - `E:\projects\mail_based_task_manager\docs\plans\android_pc_vps_evolution_authority.md`
> - `E:\projects\mail_based_task_manager\state.md`

## 1. 文档目的

本文只回答一个问题：

**如果 TaskMail 后续转向 `VPS-first` 的统一工作台，并希望在一个平台上同时管理多台 PC，那么最小且清晰的控制面架构应当是什么样。**

本文聚焦：

- `PC <-> VPS` 之间的边界
- 多 PC 同平台管理的最小领域模型
- 控制面协议的最小消息流
- 流式输出在协议中的位置
- `execution_policy` 在平台层的归属边界
- legacy mail 语义如何投影到新控制面
- `result / artifact / error_code` 的平台层归属边界
- `command / event` 的 payload 归属边界

本文不展开 Android UI 设计，也不把当前 mail-first 行为误写成已经迁移完成。

## 2. 设计结论

本草案冻结以下核心结论：

1. 平台要解决的是“一个 VPS 同时管理多台 PC”，不是“让两台 PC 共同执行同一个 workspace”。
2. `workspace` 是某台 PC 下的本地唯一目录，因此它天然是 `pc-scoped` 对象，不是平台级共享资源。
3. `VPS` 持有控制面真相，`PC` 持有执行真相。
4. `session` 一旦创建，就固定绑定到某个 `workspace`，从而也固定绑定到某台 `PC`。
5. 第一版不支持跨 PC 热迁移运行中的 `session` 或 `run`。
6. 流式输出应作为正式能力加入协议，但它不替代结构化 `event` 或最终 `result`。

## 3. 适用边界

本草案适用于以下未来方向：

- 一个平台同时管理多台 PC
- 每台 PC 各自暴露本地 workspace 清单
- 新任务由平台路由到某台 PC 上的某个 workspace
- 后续控制动作按 `session` 固定回到原始 PC
- Mail 若保留，只做备份、通知或导出

本草案不适用于以下方向：

- 两台 PC 共同执行同一个本地目录
- 跨 PC 共享 native session
- 把当前 mail-first 现状误当成已经完成的 `VPS-first`
- 在第一版就做多用户协作、复杂 ACL 或跨组织调度

## 4. 当前问题与为什么这样切

如果目标是“同时管理两台 PC”，最容易犯的错是把 `workspace` 设计成平台级共享对象。

这会立刻带来几类不必要的问题：

- 两台 PC 是否能同时对同一路径执行
- 本地 native session 是否可迁移
- 同一 session 是否允许双写
- 运行中的 artifact 与日志归谁负责

但在本草案中，这些问题都可以主动回避。

因为这里的 `workspace` 不是抽象项目，而是：

**某台 PC 上一个可执行、可定位、不可跨机共享的本地目录。**

只要守住这个边界，多 PC 平台就会从“分布式共享执行问题”退化为“多执行节点管理问题”，复杂度会明显下降。

## 5. 固定假设

除非后续有新的 authority 文档重新打开，本文默认以下假设成立：

1. 每个 `workspace` 都唯一隶属于一台 `PC`。
2. 同一 `workspace` 不会被其他 `PC` 执行。
3. 同一 `session` 任一时刻只属于一个 `workspace`。
4. 同一 `run` 任一时刻只在一台 `PC` 上执行。
5. `VPS` 是控制面真相层，不直接接管 repo、worktree、本地 backend 进程和 native session。
6. `PC` 是执行节点，不持有平台级统一控制面真相。
7. 若保留邮件，其角色应退化为备份、通知或导出，而不再承担主控制面。
8. 第一版允许加入流式输出，但不要求第一版就做多用户协作。

## 6. 最小领域模型

### 6.1 `pc`

表示一个接入平台的执行节点。

建议最小字段：

- `pc_id`
- `display_name`
- `credential_id`
- `status`
- `last_seen_at`
- `connection_epoch`
- `capabilities`
- `host_fingerprint`

设计说明：

- `pc_id` 是稳定机器标识，负责绑定和路由。
- `display_name` 只用于展示，不应用作唯一主键。
- `status` 应由 `heartbeat + last_seen_at` 投影得出，而不是手工维护一个长期布尔值。
- `connection_epoch` 用于 fencing，避免旧连接残留时继续写入。
- `capabilities` 至少应能表达：
  - `supported_backends`
  - `profile_catalogs`
  - `permission_modes`
  - 可用 `backend_transport` 模式

### 6.2 `workspace`

表示某台 PC 上的一个本地执行目录。

建议最小字段：

- `workspace_id`
- `pc_id`
- `repo_path`
- `workdir`
- `display_name`
- `capabilities`
- `last_snapshot_at`

设计说明：

- `workspace` 天然从属于某个 `pc_id`。
- `workspace_id` 应稳定，可由 `pc_id + repo_path + workdir` 派生，也可以单独生成并持久化。
- 这个对象表达的是“可被该 PC 执行的本地目录”，不是全局共享工程实体。
- `workspace.capabilities` 应至少支持表达：
  - 该 workspace 允许的 `backend`
  - 可用 `profile`
  - 可用 `permission` 档位
  - 可用 `backend_transport`

### 6.3 `session`

表示一条持续任务会话。

建议最小字段：

- `session_id`
- `workspace_id`
- `pc_id`
- `state`
- `created_at`
- `updated_at`
- `last_summary`
- `current_run_id`

设计说明：

- `session` 创建后固定绑定到某个 `workspace_id`。
- 因为 `workspace` 已绑定到 `pc_id`，所以 `session` 天然也绑定到某台 PC。

### 6.4 `run`

表示某次具体执行。

建议最小字段：

- `run_id`
- `session_id`
- `status`
- `started_at`
- `finished_at`
- `backend`
- `backend_session_id`

### 6.5 `execution_policy`

表示一次执行应采用什么后端、模型档位与权限档位。

详细语义以 `taskmail-execution-policy-appendix-v0.1.md` 为准。

建议最小字段：

- `backend`
- `profile`
- `permission`
- `backend_transport`
- `resolved_model`

设计说明：

- `backend`
  - 第一版建议固定为 `codex | opencode`
  - 对新建 session 应显式给出
  - 对 follow-up 默认继承当前 session，不建议在 V1 支持中途切换 backend
- `profile`
  - 作为稳定的用户侧模型档位标签进入协议
  - 例如 `fast`、`strong`、`vision`、`android` 这类 label
  - 不建议把 raw model id 直接作为主控制面输入
- `permission`
  - 第一版建议固定为 `default | highest`
  - 新建 session 省略时表示“使用 backend 默认权限”
  - follow-up 省略时表示“继承当前 session 权限”
- `backend_transport`
  - 表示执行侧路由，例如 `cli | sdk`
  - 它属于执行实现维度，不是用户主语义
- `resolved_model`
  - 表示当前 PC 最终解析到的真实模型
  - 它应作为执行侧回报和审计字段，而不是用户主输入

### 6.6 `command`

表示用户发起的一次动作。

legacy mail 语义如何映射到 `command_type / payload`，见 `taskmail-legacy-mail-to-control-plane-mapping-v0.1.md`。
不同 `command_type` 的 payload 形状，见 `taskmail-command-event-payload-appendix-v0.1.md`。

建议最小字段：

- `command_id`
- `command_type`
- `pc_id`
- `workspace_id`
- `session_id`
- `execution_policy`
- `payload`
- `issued_at`
- `issuer_id`

设计说明：

- `command` 应显式携带 `execution_policy` 或明确说明“继承 session 当前策略”。
- 对 `new_task`，建议要求 `backend` 为显式字段。
- 对 follow-up，建议允许只覆盖 `profile` 或 `permission`，并继承当前 session 的 `backend`。

### 6.7 `event`

表示运行中的结构化状态变化。

不同 `event_type` 的 payload 形状，见 `taskmail-command-event-payload-appendix-v0.1.md`。

建议最小字段：

- `event_id`
- `command_id`
- `session_id`
- `run_id`
- `event_type`
- `payload`
- `emitted_at`

建议首批 `event_type`：

- `queued`
- `accepted`
- `running`
- `awaiting_user_input`
- `paused`
- `done`
- `failed`
- `killed`

### 6.8 `output_chunk`

表示流式文本输出片段。

建议最小字段：

- `stream_id`
- `run_id`
- `seq`
- `channel`
- `text`
- `emitted_at`

建议首批 `channel`：

- `assistant_text`
- `system`
- `stderr`

### 6.9 `result`

表示某次动作或某次 run 的最终收口结果。

详细字段与 `structured_payload.kind` 读法，见 `taskmail-result-artifact-errorcode-appendix-v0.1.md`。

建议最小字段：

- `result_id`
- `command_id`
- `session_id`
- `run_id`
- `final_status`
- `summary`
- `effective_execution_policy`
- `structured_payload`
- `generated_at`

设计说明：

- `result` 建议带回实际生效的执行策略：
  - `backend`
  - `profile`
  - `permission`
  - `backend_transport`
  - `resolved_model`

### 6.10 `artifact`

表示文件与产物元数据。

详细 artifact descriptor 与 `download_ref` 读法，见 `taskmail-result-artifact-errorcode-appendix-v0.1.md`。

建议最小字段：

- `artifact_id`
- `run_id`
- `session_id`
- `kind`
- `name`
- `size`
- `content_type`
- `download_ref`

## 7. 真相层分工

### 7.1 VPS 负责什么

`VPS` 负责：

- `pc` 注册、在线状态与连接代次
- `workspace` 清单与可见性
- `session / run / command / event / result / artifact metadata`
- 命令下发
- 事件与结果持久化
- replay 与断线补发
- 多 PC 统一工作台的投影

### 7.2 PC 负责什么

`PC` 负责：

- 本地 repo / worktree / workdir
- backend 进程与 native session
- 本地 `profile -> model` 解析
- backend-specific `permission` 投影
- 真正的任务执行
- 本地运行日志与原始 artifact 文件
- 将 `event / output_chunk / result / artifact_manifest` 回推 VPS

### 7.3 不应混淆的边界

以下边界应明确保持：

- `VPS` 不是本地执行目录的拥有者
- `PC` 不是平台级状态真相层
- `output_chunk` 不是状态真相
- `mail` 不是主控制面真相

## 8. 关键运行规则

### 8.1 绑定规则

- 新建 `session` 时，必须同时选定 `pc_id + workspace_id`。
- 一旦 `session` 创建完成，后续 `reply / status / pause / resume / kill` 等动作只按 `session_id` 回到原始 `pc_id + workspace_id`。
- 第一版不支持自动跨 PC 漂移。

### 8.2 在线规则

- `online / stale / offline` 由 `heartbeat` 与 `last_seen_at` 投影得出。
- 旧连接只要 `connection_epoch` 落后，就不得继续提交新 `event`、`output_chunk` 或 `result`。

### 8.3 并发规则

- 同一 `session` 任一时刻最多只能有一个 active `run`。
- 多个 `session` 是否并发，第一版继续由 PC 本地 scheduler 决定。
- `VPS` 在第一版只负责命令归属，不负责重写 PC 的细粒度排队策略。

### 8.4 故障规则

- 若 PC 暂时离线，VPS 保留控制面历史，但不伪造执行进度。
- 若连接中断，重连后应按 `connection_epoch` 与 replay 规则恢复事件流。
- 若保留邮件备份，邮件失败只影响备份状态，不应推翻 `result` 的 canonical 地位。

## 9. 最小协议

### 9.1 统一包头

所有消息建议共享统一 envelope。

示例：

```json
{
  "schema_version": "v1",
  "type": "event",
  "message_id": "msg_01",
  "pc_id": "pc_home",
  "connection_epoch": 12,
  "sent_at": "2026-03-25T10:00:00Z",
  "trace_id": "trace_01",
  "payload": {}
}
```

### 9.2 `PC -> VPS`

建议第一版支持以下消息类型：

- `pc_hello`
- `heartbeat`
- `workspace_snapshot`
- `command_ack`
- `event`
- `output_chunk`
- `result`
- `artifact_manifest`

### 9.2.1 `pc_hello`

用途：

- 建立连接
- 认证执行节点
- 上报能力与主机信息

建议字段：

- `pc_id`
- `display_name`
- `token`
- `capabilities`
- `host_fingerprint`

### 9.2.2 `heartbeat`

用途：

- 维持在线状态
- 上报轻量健康信息

建议字段：

- `active_run_count`
- `workspace_count`
- `load_hint`
- `timestamp`

### 9.2.3 `workspace_snapshot`

用途：

- 上报当前 PC 上的 workspace 列表
- 刷新路由目标

建议字段：

- `workspaces[]`
  - `workspace_id`
  - `repo_path`
  - `workdir`
  - `display_name`
  - `capabilities`

### 9.2.4 `command_ack`

用途：

- 表示该 PC 已接收命令
- 说明是立即执行、排队还是拒绝

建议字段：

- `command_id`
- `ack_status`
- `reason`

建议首批 `ack_status`：

- `accepted`
- `accepted_but_queued`
- `rejected`

### 9.2.5 `event`

用途：

- 投影结构化过程状态

建议字段：

- `event_id`
- `command_id`
- `session_id`
- `run_id`
- `event_type`
- `payload`

### 9.2.6 `output_chunk`

用途：

- 实时传输文本流

建议字段：

- `stream_id`
- `run_id`
- `seq`
- `channel`
- `text`

### 9.2.7 `result`

用途：

- 投影一次命令或一次 run 的最终结果

建议字段：

- `result_id`
- `command_id`
- `run_id`
- `final_status`
- `summary`
- `structured_payload`

### 9.2.8 `artifact_manifest`

用途：

- 上报本次运行产生的文件与下载入口

建议字段：

- `run_id`
- `artifacts[]`

### 9.3 `VPS -> PC`

建议第一版支持以下消息类型：

- `hello_ack`
- `command_dispatch`
- `cancel_command`
- `replay_request`
- `workspace_refresh_request`

### 9.3.1 `hello_ack`

用途：

- 确认连接已建立
- 返回当前有效 `connection_epoch`

### 9.3.2 `command_dispatch`

用途：

- 向某台 PC 下发一个结构化动作

建议字段：

- `command_id`
- `command_type`
- `pc_id`
- `workspace_id`
- `session_id`
- `payload`

### 9.3.3 `cancel_command`

用途：

- 取消尚未开始的动作

### 9.3.4 `replay_request`

用途：

- 请求 PC 从某个游标之后补发 `event` 或 `output_chunk`

### 9.3.5 `workspace_refresh_request`

用途：

- 要求 PC 重新上报 workspace 列表

## 10. 最小消息流

### 10.1 连接建链

1. `PC -> VPS: pc_hello`
2. `VPS -> PC: hello_ack(connection_epoch)`
3. `PC -> VPS: workspace_snapshot`
4. `PC -> VPS: heartbeat*`

### 10.2 新任务

1. 用户在平台选择 `pc + workspace`
2. `VPS` 生成 `command`
3. `VPS -> PC: command_dispatch`
4. `PC -> VPS: command_ack`
5. `PC -> VPS: event*`
6. `PC -> VPS: output_chunk*`
7. `PC -> VPS: result`
8. `PC -> VPS: artifact_manifest`

### 10.3 已有 session 的 follow-up

1. 用户对某个 `session_id` 发起 follow-up
2. `VPS` 根据 `session_id` 找到固定 `pc_id + workspace_id`
3. `VPS -> PC: command_dispatch`
4. 其余流程同新任务

## 11. 流式输出的正式位置

流式输出建议作为第一类协议对象保留，但必须和 `event`、`result` 分层。

推荐分工：

- `event` 负责状态
- `output_chunk` 负责即时文本
- `result` 负责最终收口

这样做的好处是：

- UI 可以实时显示工作过程
- 断线后可以按 `stream_id + seq` replay
- 即使流式输出有丢失，也不会污染最终状态机
- 最终结果不需要通过拼接文本流反推

因此，流式输出可以纳入第一版协议，但不应承担唯一真相职责。

## 12. 路由与调度建议

### 12.1 新任务路由

第一版建议由平台显式选择目标：

- 选择哪台 `PC`
- 选择该 PC 下哪个 `workspace`
- 选择 `backend`
- 选择 `profile`
- 选择 `permission`

这样最简单，也最符合当前单用户工作台的实际使用方式。

### 12.2 Follow-up 路由

对已有 `session` 的 follow-up，不再重新选择目标，而是固定回原始：

- `pc_id`
- `workspace_id`

执行策略建议默认也按 session 继承：

- `backend` 默认继承
- `profile` 可按动作覆盖
- `permission` 可按动作覆盖

### 12.3 本地调度

第一版不建议把 PC 上已有的 scheduler 逻辑整体搬到 VPS。

更稳的分工是：

- `VPS` 决定命令归属
- `PC` 决定本地何时真正开始执行
- `PC` 通过 `queued / running / done` 等 `event` 把状态投影回 `VPS`

## 13. 认证与未来扩展

### 13.1 第一版认证

第一版最小可行方式：

- 每台 `PC` 一个独立 token
- `pc_id` 与 token 在服务端绑定

这已经足够支持多 PC 接入和基本节点级鉴权。

### 13.3 执行策略边界

第一版建议同时固定以下执行策略边界：

- `profile` 解决“用哪个模型档位”
- `permission` 解决“以什么权限运行”
- 这两个字段必须保持解耦
- `permission` 不应隐式打开全局关闭的能力，例如搜索或联网开关
- `resolved_model` 由 PC 侧根据本地配置解析并回报，不要求 `VPS` 直接持有每台机器的 raw model map

### 13.2 后续多用户

若后续引入多个 Android 用户或多个操作主体，建议新增单独的 `principal` 或 `user` 层，而不是把用户身份塞进 `pc`。

推荐未来关系：

- `principal`
- `pc`
- `workspace`
- `session`

也就是说：

- `PC` 是执行节点
- `user` 是操作主体

这两个维度从语义上应保持解耦。

## 14. 非目标

第一版明确不做以下事项：

- 不做共享 workspace 的多 PC 执行
- 不做运行中 session 的跨 PC 热迁移
- 不做 VPS 接管本地 repo / worktree / native session
- 不做多租户复杂权限系统
- 不做把 mail-first 当前协议直接套壳成长期主协议

## 15. 对当前代码线的含义

这条草案与当前实现的关系应理解为：

- 当前实现仍是 mail-first，PC 仍是 execution truth
- 本文讨论的是后续 `VPS-first` 多 PC 平台的控制面方向
- 现有邮件业务语义可以复用
- 但长期协议应收敛成 `command / event / output_chunk / result / artifact`

因此，这份文档不是“当前实现说明”，而是“后续平台化重构的边界草案”。

## 16. 一句话结论

**TaskMail 若转向多 PC 平台，最合理的第一步不是共享 workspace，而是建立一个 `VPS 控制面 + 多 PC 执行节点 + pc-scoped workspace + 可选流式输出` 的统一模型。**
