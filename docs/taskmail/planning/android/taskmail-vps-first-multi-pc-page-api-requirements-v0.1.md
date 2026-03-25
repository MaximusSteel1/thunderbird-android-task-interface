# TaskMail VPS-First 多 PC 页面级 API 需求（v0.1）

更新时间：2026-03-25

## 状态

本文是 `VPS-first 多 PC` 主线下，Android 侧页面级 API 需求的 companion doc。

它依附于以下文档：

- `docs/taskmail/planning/android/taskmail-vps-first-multi-pc-viewstate-action-mapping-v0.1.md`
- `docs/taskmail/planning/android/taskmail-vps-first-multi-pc-core-screen-low-fi-v0.1.md`
- `docs/taskmail/planning/platform/taskmail-multi-pc-control-plane-v0.1.md`
- `docs/taskmail/planning/platform/taskmail-pc-vps-control-protocol-v0.1.md`

它不冻结最终 endpoint 名称，也不直接定义 transport 选型或 JSON schema。

本文回答的问题是：

**为了支撑首页、新任务页、Session 页，Android 侧最少需要 VPS 提供哪些页面级能力。**

## 目标

本文只冻结以下内容：

1. 各页面需要哪些读接口能力
2. 各页面需要哪些写接口能力
3. 哪些能力必须支持 snapshot
4. 哪些能力必须支持 live update / replay
5. 页面层不该直接承担哪些协议复杂度

## API 需求的分层原则

页面级 API 需求必须按下面三层理解：

1. 页面要什么
2. 页面动作是什么
3. 底层 transport 如何实现

当前只冻结前两层，不冻结第三层。

因此本文中的 “查询 / 提交 / 订阅 / 下载” 都是能力语义，不强制等于：

- REST
- GraphQL
- WebSocket
- SSE

## 全局要求

VPS 若要支撑 Android 主线，至少应满足以下全局能力：

1. 能返回 `pc / workspace / session / run / result / artifact` 的 snapshot
2. 能接受结构化 `command`
3. 能返回 `command_ack`
4. 能把 `event / output_chunk / result` 作为可持续读取的时间线输出
5. 能支持最小 replay / 补发
6. 能支持 artifact 下载

## 1. 首页 API 需求

### 首页需要的读能力

首页至少需要以下查询能力：

- 查询 `pc` 列表
- 查询 `workspace` 摘要
- 查询 `session` 列表

### 首页查询的最小数据面

#### `pc` 列表最少需要

- `pc_id`
- `display_name`
- `status`
- `last_seen_at`
- `workspace_count`
- 能力摘要

#### `workspace` 摘要最少需要

- `workspace_id`
- `pc_id`
- `display_name`
- `repo_path`
- `workdir`
- 可用 execution-policy 摘要

#### `session` 列表最少需要

- `session_id`
- `pc_id`
- `workspace_id`
- `state`
- `last_summary`
- `updated_at`
- `current_run_id`
- 是否 `awaiting_user_input`

### 首页需要的交互能力

- 手动刷新首页 snapshot
- 按 `pc`、`workspace`、`state` 过滤

### 首页对实时性的要求

首页不一定要求秒级连续流，但至少应支持：

- 用户回到首页时快速刷新
- `awaiting_user_input / done / failed` 这类关键变化能在合理时间内反映

## 2. 新任务页 API 需求

### 新任务页需要的读能力

新任务页至少需要：

- 查询当前可选 `pc`
- 查询选中 `pc` 下的 `workspace`
- 查询目标 `pc/workspace` 支持的 `execution_policy` 组合

### 新任务页需要的写能力

新任务页至少需要一个语义动作：

- `CreateSessionCommand`

它的最小业务输入应包括：

- `pc_id`
- `workspace_id`
- `prompt`
- `execution_policy`

### 新任务页提交后需要的回包能力

至少需要：

- `command_id`
- `command_ack`
- 新绑定的 `session_id`
- 当前是否 accepted / queued / rejected

### 新任务页对错误语义的要求

页面必须能拿到稳定拒绝原因，而不是泛化成失败字符串。

最少应支持：

- `unsupported_backend`
- `unsupported_profile`
- `unsupported_permission`
- `profile_model_unresolved`
- `workspace_unavailable`
- `pc_offline`

## 3. Session 页 API 需求

### Session 页需要的读能力

Session 页至少需要三类读能力：

1. Session snapshot
2. 实时时间线
3. 结果与文件

### Session snapshot 最少需要

- `session`
- 当前 `run`
- 当前 `execution_policy` 摘要
- 最近上下文摘要
- 最近 `result`
- 最近 `artifact` 摘要

### 实时时间线最少需要

- `command_ack`
- `event`
- `output_chunk`
- `result`

### 历史上下文最少需要

- 按回合聚合的历史摘要
- 每个回合的用户输入摘要
- 每个回合的结果摘要
- 可选的文件摘要

### Session 页需要的写能力

至少需要：

- 发送 `reply`
- 发送 `status`

后续可扩展：

- `pause`
- `resume`
- `kill`

### Session 页时间线的最小一致性要求

- `event` 不应依赖文本流推断
- `result` 必须可明确识别 canonical terminal outcome
- `output_chunk` 缺失不应推翻已有 `result`
- 时间线必须能按 session 聚合，而不是只按裸 run 聚合
- 前续历史必须能按回合聚合，而不是只回放原始 frame

## 4. 实时订阅与 replay 需求

### Android 侧最低要求

Session 页若要可用，VPS 至少要支持：

- 针对单个 `session_id` 的实时订阅或轮询增强读法
- 从某个时间点或游标后补 `event`
- 从某个 `stream_id + seq` 后补 `output_chunk`

### replay 需求

replay 最少要覆盖：

- `event`
- `output_chunk`
- 必要时的 `result`

### replay 的页面语义

对页面来说，replay 的目标不是“看原始协议历史”，而是：

- 恢复直播区
- 恢复状态区
- 恢复结果区

## 5. 结果与文件 API 需求

### 结果区需要的读能力

结果区至少需要：

- `final_status`
- `summary`
- `structured_payload` 的可投影子集
- `effective_execution`
- `finished_at`

### 文件区需要的读能力

文件区至少需要：

- `artifact_id`
- `name`
- `kind`
- `size`
- `content_type`
- `download_ref`

### 文件区需要的写 / 动作能力

最少需要：

- 基于 `download_ref` 的文件下载

如果后续支持预览，还需要：

- 文件 metadata 查询
- 可预览类型标识

## 6. 执行策略 API 需求

### Android 需要的能力上报读法

Android 至少需要能读到：

- `supported_backends`
- `profile_catalogs`
- `permission_modes`
- `backend_transport_modes`

这些能力可挂在：

- `pc`
- `workspace`
- 或专门的 capability 查询

但页面层不应自行推断。

### Android 需要的结果回显读法

结果区至少要能读到：

- `backend`
- `profile`
- `permission`
- `backend_transport`
- `resolved_model`

否则页面无法明确展示“最终实际是怎么跑的”。

## 7. 首页、新任务、Session 页的页面级能力矩阵

### 首页

- 读：
  - `pc list`
  - `workspace summary`
  - `session list`
- 写：
  - 无主写接口
- 可选实时：
  - 关键状态刷新

### 新任务页

- 读：
  - `pc/workspace capability`
- 写：
  - `create new_task command`
- 立即回包：
  - `command_ack`
  - `session binding`

### Session 页

- 读：
  - `session snapshot`
  - `timeline stream`
  - `result`
  - `artifacts`
- 写：
  - `reply`
  - `status`
  - 可选 `pause/resume/kill`
- 必须支持：
  - replay
  - result consistency

## 8. 页面层不应承担的事情

页面层不应直接承担以下复杂度：

- 自己拼装原始协议 envelope
- 自己维护 `connection_epoch`
- 自己做 `profile -> resolved_model` 解析
- 自己判断 terminal result 是否 canonical
- 自己从文本流拼结果
- 自己管理节点 token

这些都应下沉到：

- 数据层
- session orchestration 层
- VPS 控制面

## 9. 页面级 API 的错误处理要求

Android 页面需要的错误，不是任意字符串，而是可投影的错误家族。

最少应能区分：

- 路由不可用
- execution_policy 不支持
- session 不可继续
- 文件下载失败
- 实时流中断但 snapshot 仍可读

这样页面才能给出正确的用户动作建议。

## 10. 与后续接口冻结的关系

本文不要求立刻冻结 endpoint URL，也不要求立刻选定：

- HTTP + SSE
- WebSocket
- 混合 transport

本文先冻结的是：

- 页面一定需要哪些能力
- 这些能力的最小数据面
- 这些能力的用户语义

等这层稳定后，再继续冻结：

- endpoint 形状
- auth 绑定
- transport 细节
- 本地缓存模型
