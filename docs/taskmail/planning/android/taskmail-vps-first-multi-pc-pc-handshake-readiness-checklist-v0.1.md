# TaskMail VPS-First 多 PC 与 PC 端握手联调时机清单（v0.1）

更新时间：2026-03-25

## 状态

本文是 `VPS-first 多 PC 控制面` 主线下，Android 侧用于判断“何时适合开始与 PC 端做第一轮正式握手联调”的 gate checklist。

它依附于以下文档：

- `docs/taskmail/planning/android/taskmail-vps-first-multi-pc-android-skeleton-implementation-plan-v0.1.md`
- `docs/taskmail/planning/android/taskmail-vps-first-multi-pc-session-slice1-file-plan-v0.1.md`
- `docs/taskmail/planning/platform/taskmail-vps-first-control-plane-freeze-v0.1.md`
- `docs/taskmail/planning/platform/taskmail-pc-vps-control-protocol-v0.1.md`
- `E:\projects\mail_based_task_manager\docs\plans\vps_first_multi_pc_phase1_execution_plan_v0.1.md`

本文不替代 current-truth，也不替代 Android/PC 的 owner implementation plan。

本文回答的问题是：

**Android 侧应该在开发推进到什么程度时，再去和 PC 端做正式握手联调，既不太早，也不拖太晚。**

## 一句话结论

不建议在“只有协议文档和页面草图”时就开始正式握手联调。

更合适的握手点是：

- Android 已完成 `Slice 1 Session 页骨架`
- Android 已完成 `Slice 2 控制面 DTO 骨架`
- Android 已完成 `Slice 3 新任务页表单骨架`
- Android 至少完成 `Slice 5` 中与 `sessionId` 主锚点相关的最小收口

也就是：

**当 Android 已经能在本地 fixture / fake data 上自洽地跑通 `new_task -> command_ack -> event -> result -> SessionDetail` 这条最小产品链时，再开始与 PC 端正式握手。**

## 为什么不建议太早握手

如果在下面这些事情都还没落稳时就握手：

- 新任务页仍以 `senderAccount + repoPath` 为主
- Session 页仍只有 timeline / reply 结构
- DTO 仍是旧 `request_id / packet_id / accepted:Boolean` 读法
- `threadId` 仍是产品主锚点

那么联调时会同时发生三种变化：

- 协议字段在变
- 页面骨架在变
- 路由主键在变

这会让联调本身失去判断力，很难区分：

- 是 Android 页面问题
- 是 DTO 映射问题
- 还是 PC/VPS 协议问题

## 为什么也不建议太晚握手

也不建议等到下面这些都完成后再握手：

- 首页 / 工作台全部改完
- 历史上下文层完整 polish 完成
- artifact 打开 / 下载体验全收口
- `output_chunk` 完整直播体验完成
- route / screen rename 全部做完

因为这些都不是第一轮 PC Phase 1 握手的主阻塞项。

第一轮真正高风险、需要尽早验证的，是：

- `command_ack`
- `event`
- `result`
- `effective_execution`
- `sessionId`
- `workspaceId`

## 推荐握手位置

推荐把第一轮正式握手放在这条窄纵切上：

1. `NewTask`
2. `SessionDetail`

也就是：

- Android 选择 `pc_id + workspace_id`
- Android 提交 `new_task command`
- Android 接收 `command_ack`
- Android 观察 `event`
- Android 渲染 `result`
- Android 可显示最小 `artifact` 摘要

不推荐把第一轮正式握手放在：

- 首页 / Workbench 聚合读路径
- 完整历史上下文层
- 旧 mail/direct 兼容链

## 开始正式握手前，Android 侧应满足的最低条件

### A. 页面骨架

- [ ] `TaskNewTask` 页面结构已按 `pc + workspace + task + execution_policy` 组织
- [ ] `TaskSessionDetail` 已有 `status / recentContext / result / artifact / follow-up` 基本落位
- [ ] 历史上下文层至少已有 state 和入口，不要求完整 polish

### B. DTO / 字段承载

- [ ] Android 已有与 freeze doc 对齐的 `command / command_ack / event / result / artifact_manifest` DTO 或 mapper 基线
- [ ] 页面状态不再继续直接绑定旧 `request_id / packet_id / accepted:Boolean` 语义
- [ ] `effective_execution` 在 Android 侧已有明确承载位置

### C. 主键与路由

- [ ] `sessionId` 已经是 follow-up 主锚点
- [ ] `workspaceId` 已明确作为路由归属或数据归属字段
- [ ] `threadId` 已降为 compatibility fallback，而不是新的控制面主键

### D. 本地自洽验证

- [ ] Android 侧已能用 fake data / representative JSON 走通 `new_task -> ack -> event -> result` 纵切
- [ ] Session 页现有 UI 测试已覆盖最近上下文卡片和结果区的最小可见性
- [ ] ViewModel 测试已覆盖历史层开合与最小结果投影

## 不是握手阻塞项的事项

在第一轮正式握手前，以下事项可以尚未完成：

- 首页 / Workbench 完整改造
- 历史上下文层完整交互 polish
- `output_chunk` 完整直播体验
- artifact 真正下载/打开闭环
- route / screen 全量 rename
- 旧 mail/direct 兼容代码删除

## 推荐的开发顺序与握手时点

推荐按以下顺序推进：

1. 先做 `Slice 1 Session 页骨架`
2. 再做 `Slice 2 控制面 DTO 骨架`
3. 再做 `Slice 3 新任务页表单骨架`
4. 再补 `Slice 5` 中与 `sessionId` 主锚点有关的最小收口
5. 到此时开始与 PC 端正式握手联调
6. 握手通过后，再回头推进 `Slice 4 首页 / 工作台`

## 这轮握手应验证什么

第一轮正式握手建议只验证：

- Android 能正确构造 `new_task command`
- PC/VPS 能回 `command_ack`
- Android 能区分 `accepted / accepted_but_queued / rejected`
- Android 能消费 `event`
- Android 能消费 `result`
- Android 能显示 `effective_execution`

如果这一轮通过，再扩大到：

- `output_chunk`
- `artifact_manifest`
- `reply / status`

## 一句话结论

**真正合适的握手点，不是刚有协议文档时，也不是整套 UI 都做完时，而是 Android 已经把 `NewTask -> SessionDetail` 这条最小产品链在本地站稳之后。**
