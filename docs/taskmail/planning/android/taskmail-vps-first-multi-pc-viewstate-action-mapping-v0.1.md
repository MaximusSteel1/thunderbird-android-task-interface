# TaskMail VPS-First 多 PC 页面状态与交互映射（v0.1）

更新时间：2026-03-25

## 状态

本文是 `VPS-first 多 PC` 主线下，Android 侧关于“页面状态如何映射到 VPS 对象与交互动作”的 companion doc。

它依附于以下文档：

- `docs/taskmail/planning/android/taskmail-vps-first-multi-pc-user-requirements-authority-v0.1.md`
- `docs/taskmail/planning/android/taskmail-vps-first-multi-pc-information-architecture-v0.1.md`
- `docs/taskmail/planning/platform/taskmail-multi-pc-control-plane-v0.1.md`
- `docs/taskmail/planning/platform/taskmail-pc-vps-control-protocol-v0.1.md`

本文不定义传输协议细节，不假定具体 endpoint 形态，也不替代底层 JSON schema。

本文回答的问题是：

**Android 页面需要哪些 ViewState，这些 ViewState 应由哪些领域对象投影出来，又需要触发哪些对 VPS 的动作。**

## 核心分层

后续实现中必须固定以下三层：

1. UI 层
2. ViewState / UserAction 层
3. VPS 领域对象 / 交互动作层

不允许跳过中间层，直接把原始 JSON frame 绑定到 UI。

固定原则是：

- UI 对 ViewState
- ViewModel / Reducer 对领域对象
- 数据层对 JSON / transport

## Android 默认消费的领域对象

在这条主线下，Android 应默认消费以下领域对象，而不是 mail 文本：

- `pc`
- `workspace`
- `session`
- `run`
- `execution_policy`
- `command`
- `command_ack`
- `event`
- `output_chunk`
- `result`
- `artifact`

## Android 默认发出的交互动作

Android 面向 VPS 的交互动作应收敛成以下几类：

- 列表查询
- 详情查询
- 命令提交
- 时间线订阅 / 实时更新
- replay / 补发
- 文件下载

Android 不应直接实现 `PC -> VPS` 节点消息，例如：

- `pc_hello`
- `heartbeat`
- `workspace_snapshot`

这些属于 PC 节点职责，不属于手机客户端职责。

## 1. 首页映射

### 首页 ViewState

首页建议至少包含以下状态块：

- `pcCards`
- `workspaceSections`
- `attentionSessions`
- `activeSessions`
- `recentSessions`
- `refreshState`
- `connectionBanner`

### 首页依赖的领域对象

- `pc[]`
- `workspace[]`
- `session[]`

其中：

- `pc` 提供在线状态与能力摘要
- `workspace` 提供可路由工作目录
- `session` 提供用户当前要继续处理的任务入口

### 首页需要的 VPS 交互

- 查询 `pc` 列表
- 查询 `workspace` 摘要
- 查询 `session` 列表
- 刷新首页 snapshot

### 首页不应直接依赖的对象

- 原始 `output_chunk`
- 原始 `command_dispatch`
- `connection_epoch`
- 原始 envelope

## 2. 新任务页映射

### 新任务 ViewState

新任务页建议至少包含：

- `selectedPc`
- `availableWorkspaces`
- `selectedWorkspace`
- `taskInput`
- `executionPolicyEditor`
- `submitState`
- `validationErrors`

### 新任务页依赖的领域对象

- `pc`
- `workspace`
- `execution_policy`

### `execution_policy` 的用户侧映射

新任务页里：

- `backend` 是必选或显式默认项
- `profile` 来自目标 `PC/workspace` 能力上报
- `permission` 来自目标 `PC/workspace` 支持范围
- `backend_transport` 默认隐藏或放进高级项

### 新任务页需要的 VPS 交互

- 查询目标 `PC/workspace` 的能力
- 提交 `new_task command`
- 接收 `command_ack`
- 接收初始 `session` 绑定结果

### 新任务页提交动作的逻辑形态

虽然底层最终会变成 JSON，但页面不应直接拼接协议 frame。

页面层应只发出一个语义动作：

- `SubmitNewTask(pcId, workspaceId, prompt, executionPolicy)`

数据层再把它投影到实际请求。

## 3. Session 页映射

### Session 页 ViewState

Session 页建议至少包含：

- `sessionHeader`
- `statusCard`
- `recentContextSummary`
- `historyEntryPoint`
- `timelineItems`
- `streamSections`
- `resultCard`
- `artifactList`
- `followUpComposer`
- `transientActionState`

### Session 页依赖的领域对象

- `session`
- `run`
- `event[]`
- `output_chunk[]`
- `result`
- `artifact[]`

### Session 页的固定投影规则

- `sessionHeader` 主要来自 `session`
- `statusCard` 主要来自当前最新 `event`
- `recentContextSummary` 由最近一轮 `command / command_ack / event / result` 组合投影
- `historyEntryPoint` 负责把用户带到完整历史上下文层
- `timelineItems` 由 `command_ack + event + result` 组合投影
- `streamSections` 主要来自 `output_chunk`
- `resultCard` 主要来自 `result`
- `artifactList` 主要来自 `artifact_manifest`

### Session 页需要的 VPS 交互

- 查询 session snapshot
- 查询 run / result 摘要
- 查询最近上下文摘要
- 查询按回合聚合的历史摘要
- 订阅该 session 的状态与输出更新
- 断线后 replay 缺失事件和输出

### 历史上下文层 ViewState

历史上下文层建议至少包含：

- `historyRounds`
- `expandedRoundId`
- `historyLoadState`

### 历史上下文层的固定投影规则

- `historyRounds` 不直接等于原始 mail thread 或原始 frame 列表
- 每个 round 优先投影：用户输入、关键状态变化、结果摘要、文件摘要
- 完整直播明细只在 round 展开时再读取或再显示

### Session 页不应做的映射

- 不把 mail thread 投影成默认 timeline
- 不把完整历史默认铺成 Session 主页面
- 不把流式文本直接当成最终结果卡片
- 不把状态完全由文本流推断

## 4. Follow-up 动作映射

### Follow-up ViewState

Follow-up 输入区建议至少包含：

- `draft`
- `availableActions`
- `policyOverrideState`
- `sendState`

### Follow-up 的语义动作

最小动作集：

- `SendReply(sessionId, text, policyOverride?)`
- `RequestStatus(sessionId, policyOverride?)`

后续扩展动作：

- `PauseSession(sessionId)`
- `ResumeSession(sessionId)`
- `KillSession(sessionId)`

### Follow-up 的策略覆盖规则

对于 follow-up：

- 默认继承 session 当前 `execution_policy`
- 可选只覆盖 `profile`
- 可选只覆盖 `permission`
- V1 不建议暴露中途切换 `backend`

### Follow-up 对 VPS 的交互

- 提交 follow-up command
- 接收 `command_ack`
- 在当前 Session 页继续消费后续 `event / output_chunk / result`

## 5. 结果与文件映射

### 结果区 ViewState

结果区建议至少包含：

- `finalStatus`
- `summary`
- `structuredHighlights`
- `effectiveExecutionPolicy`
- `finishedAt`

### 文件区 ViewState

文件区建议至少包含：

- `artifactItems`
- `downloadState`
- `previewAvailability`

### 依赖对象

- `result`
- `artifact[]`

### 需要的 VPS 交互

- 查询 `result`
- 查询 `artifact_manifest`
- 发起文件下载

### 固定规则

- `resolved_model` 只在结果区展示为“实际生效设置”
- 它不是新任务页的主输入

## 6. 设置页映射

### 设置页 ViewState

设置页建议至少包含：

- `serverConfig`
- `defaultPc`
- `defaultWorkspace`
- `notificationSettings`
- `backupMailSettings`
- `debugSettings`

### 设置页需要的交互

- 保存本地客户端配置
- 验证 VPS 可达性
- 打开诊断信息

### 设置页不应依赖的领域对象

- `command`
- `event`
- `result`
- 节点 token 原文

## 7. 通知映射

通知应围绕用户动作价值，而不是协议事件数量。

建议重点通知以下状态：

- `awaiting_user_input`
- `done`
- `failed`
- 关键 artifact 已生成

通知默认应锚定到：

- 某个 `session`
- 某个结果状态

而不是一条原始 event 文本。

## 8. 状态同步与 replay 映射

### Android 本地建议缓存

- `session summary`
- 最近 `event_id`
- 最近 `stream_id + seq`
- 最近 `result`
- `artifact` 摘要

### 重连逻辑

Android 重连后应做的事：

1. 先恢复 session snapshot
2. 再按游标补 event
3. 再按 `stream_id + seq` 补输出
4. 再确认是否已有新的 canonical result

### 不能做的事

- 不能只靠最后一段文本判断任务是否结束
- 不能因为直播缺了一段就推翻已有 `result`
- 不能把 replay 逻辑直接暴露成用户主操作

## 9. `permission / profile` 的固定读法

这两个字段在 Android 侧的固定读法如下：

- `profile`：运行档位
- `permission`：执行权限档位

它们不是：

- 账号角色
- 用户 ACL
- 系统设置里的长期权限中心

因此，它们应主要出现于：

- 新任务页高级执行设置
- Session 页 follow-up 的临时高级覆盖
- 结果页的实际生效策略回显

## 10. 后续 UI 设计的直接约束

后续做 UI 设计稿或实现时，任何页面都应能回答以下问题：

1. 这个页面的主 ViewState 是什么
2. 这些 ViewState 由哪些领域对象投影
3. 页面会触发哪些语义动作
4. 这些语义动作最终如何对接 VPS
5. 哪些底层 JSON 字段不应直接泄漏到 UI

如果答不出来，就说明设计还停留在“协议草图”阶段，没有真正进入产品实现阶段。

## 与后续实现的关系

本文不要求立即冻结 endpoint，也不要求立即冻结 transport 形态。

本文要求先固定的是：

- 页面状态
- 语义动作
- 领域对象映射

等这三层稳定后，再去收口：

- JSON schema
- HTTP / WS / SSE 选择
- 本地缓存结构
