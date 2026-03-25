# TaskMail VPS-First 多 PC Compose 页面结构与实现骨架（v0.1）

更新时间：2026-03-25

## 状态

本文是 `VPS-first 多 PC` 主线下，Android 侧从页面规划进入实际 Compose 实现前的骨架文档。

它依附于以下文档：

- `docs/taskmail/planning/android/taskmail-vps-first-multi-pc-user-requirements-authority-v0.1.md`
- `docs/taskmail/planning/android/taskmail-vps-first-multi-pc-information-architecture-v0.1.md`
- `docs/taskmail/planning/android/taskmail-vps-first-multi-pc-viewstate-action-mapping-v0.1.md`
- `docs/taskmail/planning/android/taskmail-vps-first-multi-pc-core-screen-low-fi-v0.1.md`
- `docs/taskmail/planning/android/taskmail-vps-first-multi-pc-page-api-requirements-v0.1.md`

本文不冻结最终接口字段，也不替代具体 Kotlin 代码实现。

本文回答的问题是：

**在当前仓库既有的 TaskMail Compose / MVI 结构下，`VPS-first 多 PC` 主线应如何落成页面级 Contract、Screen、Content 与组件边界。**

## 目的

本文只冻结以下内容：

1. 现有 TaskMail 页面结构中哪些部分应直接复用
2. 三张主线页面在 Compose 层面的推荐骨架
3. Screen / Contract / ViewModel / Content / Component 的职责边界
4. Session 页“最近上下文摘要 / 历史上下文层”在 Compose 里的落位方式

它不冻结：

- 最终命名是否全量从 `Workspace` 改成 `Workbench`
- 最终 repository / transport / cache 代码
- 最终 design token 与主题实现

## 当前仓库中的可复用实现骨架

当前仓库 TaskMail 已经存在一套稳定的 Compose + MVI 形态，后续实现应优先沿用，而不是另起一套页面框架。

当前可直接对齐的入口包括：

- `feature/taskmail/internal/ui/workspace/TaskWorkspaceScreen.kt`
- `feature/taskmail/internal/ui/workspace/TaskWorkspaceContract.kt`
- `feature/taskmail/internal/ui/workspace/TaskWorkspaceViewModel.kt`
- `feature/taskmail/internal/ui/newtask/TaskNewTaskScreen.kt`
- `feature/taskmail/internal/ui/newtask/TaskNewTaskContract.kt`
- `feature/taskmail/internal/ui/newtask/TaskNewTaskViewModel.kt`
- `feature/taskmail/internal/ui/detail/TaskSessionDetailScreen.kt`
- `feature/taskmail/internal/ui/detail/TaskSessionDetailContract.kt`
- `feature/taskmail/internal/ui/detail/TaskSessionDetailViewModel.kt`
- `feature/taskmail/api/TaskMailRoute.kt`

因此，VPS-first 第一阶段的默认策略是：

- 复用现有 `TaskMailRoute.Workspace / NewTask / SessionDetail`
- 复用现有 `Contract + ViewModel + Screen + Content` 分层
- 在现有 screen 内演进状态与组件，而不是先做大范围 rename

## 第一阶段的实现策略

### 固定策略

第一阶段不要求立刻把所有命名改成未来最理想形态。

推荐采用以下保守但可持续的路径：

1. 保留现有 route
2. 保留现有 screen 文件族
3. 扩展 Contract.State / Event / Effect
4. 在 Content 与 component 层长出 VPS-first 所需结构
5. 等页面稳定后，再决定是否做命名收口

### 原因

这样做有三个好处：

- 与现有仓库结构一致，进入实现最快
- 不把“视觉和交互演进”绑死在“先全量 rename”
- 能让旧 mail/direct 兼容实现与新控制面 UI 演进暂时共存

## 路由与页面的阶段性映射

当前推荐的页面映射如下：

- `TaskMailRoute.Workspace`
  - 作为未来首页 / 工作台的承载 route
  - 第一阶段可继续沿用 `Workspace` 命名
- `TaskMailRoute.NewTask`
  - 继续作为新任务页 route
- `TaskMailRoute.SessionDetail`
  - 继续作为 Session 工作页 route
- `历史上下文层`
  - 第一阶段不新增一级 route
  - 作为 `SessionDetail` 内的 sheet / dialog / secondary screen state

## 1. 工作台首页的 Compose 骨架

### 推荐文件落位

- `ui/workspace/TaskWorkspaceScreen.kt`
- `ui/workspace/TaskWorkspaceContract.kt`
- `ui/workspace/TaskWorkspaceViewModel.kt`
- `ui/workspace/TaskWorkspaceContent.kt`
- `ui/workspace/component/...`

### Screen 层职责

`TaskWorkspaceScreen` 继续承担：

- 绑定 `TaskWorkspaceViewModel`
- 处理 effect 导航
- 生命周期 refresh 钩子

它不应承担：

- section 数据拼装
- 复杂过滤逻辑
- 卡片具体渲染细节

### Contract 层建议

`TaskWorkspaceContract.State` 后续应逐步从“纯 workspace 列表页”演进到“工作台首页状态”。

建议状态块至少收成：

- `attentionSessions`
- `activeSessions`
- `recentSessions`
- `pcSummaries`
- `workspaceSummaries`
- `isLoading`
- `isRefreshing`
- `error`
- `refreshError`
- `filters`

### Event 层建议

首页最小事件建议包括：

- `LoadData`
- `RefreshRequested`
- `SessionClicked`
- `NewTaskClicked`
- `PcFilterClicked`
- `WorkspaceQuickStartClicked`
- `ForegroundRefreshStarted`
- `ForegroundRefreshStopped`

### Effect 层建议

首页 effect 保持轻量：

- `OpenSessionDetail`
- `OpenNewTask`
- 可选 `OpenProjectSync`

### Content 与 component 边界

`TaskWorkspaceContent` 应只负责 section 排布，不负责把原始领域对象投影成 section。

推荐子组件拆分为：

- `AttentionSessionSection`
- `ActiveSessionSection`
- `RecentSessionSection`
- `PcSummarySection`
- `WorkspaceSummarySection`
- `TaskWorkbenchTopBar`
- `TaskWorkbenchPrimaryAction`

## 2. 新任务页的 Compose 骨架

### 推荐文件落位

- `ui/newtask/TaskNewTaskScreen.kt`
- `ui/newtask/TaskNewTaskContract.kt`
- `ui/newtask/TaskNewTaskViewModel.kt`
- `ui/newtask/TaskNewTaskContent.kt`
- `ui/newtask/component/...`

### Screen 层职责

`TaskNewTaskScreen` 继续承担：

- 绑定 `TaskNewTaskViewModel`
- 处理返回与项目目录选择 effect
- 处理外部传回的预选 repo / workspace

### Contract 层建议

`TaskNewTaskContract.State` 后续应从 mail/direct 输入页演进为 VPS-first 新任务页状态。

建议至少稳定包含：

- `selectedPc`
- `pcOptions`
- `selectedWorkspace`
- `workspaceOptions`
- `taskInput`
- `executionPolicyEditor`
- `submitState`
- `validationErrors`
- 可选兼容层字段

这里的兼容原则是：

- 允许短期保留现有 `repoPath / workdir / backend / permission / profile`
- 但页面组合应优先朝 `PC + workspace + task + execution_policy` 收口

### Event 层建议

建议事件至少包括：

- `LoadData`
- `PcSelected`
- `WorkspaceSelected`
- `TaskChanged`
- `AdvancedToggleClicked`
- `BackendChanged`
- `ProfileChanged`
- `PermissionChanged`
- `SubmitClicked`
- `DismissSubmitError`

### Effect 层建议

新任务页 effect 保持为：

- `NavigateBack`
- `OpenProjectSync`
- `ShowMessage`
- 后续可加 `OpenCreatedSession`

### Content 与 component 边界

推荐子组件拆分为：

- `TaskInputCard`
- `PcSelectorCard`
- `WorkspaceSelectorCard`
- `ExecutionPolicyCard`
- `SubmitFooter`
- `SubmitErrorCard`

不建议把：

- `execution_policy`
- 路由选择
- 任务输入

混成一个超大表单组件。

## 3. Session 页的 Compose 骨架

### 推荐文件落位

- `ui/detail/TaskSessionDetailScreen.kt`
- `ui/detail/TaskSessionDetailContract.kt`
- `ui/detail/TaskSessionDetailViewModel.kt`
- `ui/detail/TaskSessionDetailContent.kt`
- `ui/detail/component/...`

### Screen 层职责

`TaskSessionDetailScreen` 继续承担：

- 装配 `workspaceId / sessionId / threadId`
- 处理 attachment picker / save dialog
- 处理返回与 message effect
- 绑定 foreground refresh 生命周期

第一阶段不建议把“历史上下文层”做成新 route。
更推荐把它挂在 `TaskSessionDetailScreen` 内部，使用：

- `ModalBottomSheet`
- 或 `Dialog / full-screen secondary state`

### Contract 层建议

`TaskSessionDetailContract.State` 应从“详情 + 回复”扩展到“工作态 + 上下文 + 结果 + 文件 + follow-up”。

建议至少包含：

- `sessionHeader`
- `statusCard`
- `recentContextSummary`
- `historyPreview`
- `isHistoryVisible`
- `historyLoadState`
- `streamSections`
- `resultCard`
- `artifactList`
- `followUpComposer`
- `transientActionState`
- `screenError`
- `refreshError`

### Event 层建议

Session 页最小事件建议包括：

- `LoadDetail`
- `RefreshClicked`
- `StatusQueryClicked`
- `DraftChanged`
- `SendReplyClicked`
- `HistoryEntryClicked`
- `HistoryDismissed`
- `HistoryRoundExpanded`
- `HistoryRoundCollapsed`
- `OpenTimelineAttachmentClicked`
- `SaveTimelineAttachmentClicked`
- `ForegroundRefreshStarted`
- `ForegroundRefreshStopped`

### Effect 层建议

Session 页 effect 继续保持“用户动作结果”语义，不把历史展开做成 effect。

建议 effect 仍主要包括：

- `NavigateBack`
- `ShowMessage`
- `OpenAttachment`
- `CreateAttachmentDocument`
- `ShowAttachmentActionError`

历史上下文层的打开与关闭优先留在 `State` 中表达，而不是跳成新的导航副作用。

## 4. Session 页组件层的固定拆分

### 主内容结构

`TaskSessionDetailContent` 建议固定为：

1. `SessionHeaderCard`
2. `TaskStatusCard`
3. `RecentContextCard`
4. `LiveStreamSection`
5. `ResultSummaryCard`
6. `ArtifactSection`
7. `FollowUpComposerCard`
8. `HistoryContextLayer`

### 推荐新增组件

在 `ui/detail/component/` 下，推荐新增或演进：

- `RecentContextCard`
- `HistoryContextEntryRow`
- `HistoryRoundSheet`
- `HistoryRoundCard`
- `ResultSummaryCard`
- `ArtifactSection`
- `SessionExecutionPolicyChipRow`

### 可复用的既有组件

当前可继续复用或演进：

- `SessionHeader`
- `TaskStateCard`
- `TaskReplyComposer`
- `PendingQuestionCard`
- `TimelineList`

但要注意：

- `TimelineList` 不再等于整个 Session 主视图
- 完整前续历史不应继续默认铺在 timeline 上

## 5. “最近上下文摘要 / 历史上下文层”的 Compose 规则

### 最近上下文摘要

`RecentContextCard` 是 Session 主页面的一部分。

它至少应承载：

- 上一轮用户输入摘要
- 最近一次结果摘要
- 当前是否等待用户回复
- `查看前续` 入口

它的目标是：

- 让用户尽快继续当前任务
- 降低“先翻很长历史才能回复”的成本

### 历史上下文层

`HistoryContextLayer` 不应直接复用旧 mail timeline 展示方式。

它的固定规则：

- 按回合渲染
- 最新回合默认展开
- 更早回合默认折叠
- 完整直播作为 round 的次级展开内容

第一阶段推荐优先采用：

- `ModalBottomSheet`

原因：

- 与“从当前态进入前续历史”的交互距离最短
- 对现有 `SessionDetail` route 最小侵入
- 不要求先补新的导航协议

## 6. ViewModel 与 projector 边界

### ViewModel 负责的事

ViewModel 应负责：

- 加载 screen snapshot
- 订阅或轮询 live update
- 把领域对象投影成 screen state
- 协调 send / refresh / replay / 历史加载

### Content 不负责的事

Compose Content 与 component 不应负责：

- 原始 `event / output_chunk / result` 聚合
- `history round` 切分
- `resolved_model` 推断
- terminal result 识别

这些应在：

- ViewModel
- projector / mapper
- repository / orchestrator

层完成后再进入 UI。

## 7. 推荐的 UI 模型演进方式

为避免 `Contract.State` 继续变成单一大对象，推荐把 screen state 分成嵌套 UI model：

- `TaskWorkbenchUiState`
- `TaskNewTaskFormUiState`
- `TaskSessionHeaderUiState`
- `TaskSessionStatusUiState`
- `TaskRecentContextUiState`
- `TaskHistoryRoundUiState`
- `TaskResultSummaryUiState`
- `TaskArtifactItemUiState`
- `TaskFollowUpComposerUiState`

第一阶段不要求马上把全部文件拆完，但新字段尽量挂到这些子模型里，而不是持续往顶层平铺。

## 8. Phase 1 的非目标

当前这份骨架文档明确不要求：

- 先做全量 route rename
- 先把 `Workspace` route 改名成 `Workbench`
- 先把 mail 兼容线全部删除
- 先把 repository 全量重写成 VPS-first
- 先为历史上下文层引入独立导航图

## 9. 推荐的实现顺序

从当前仓库继续推进时，推荐顺序是：

1. 扩展 `TaskSessionDetailContract.State`，加入 `recentContextSummary / historyPreview / isHistoryVisible`
2. 在 `TaskSessionDetailContent` 中加入 `RecentContextCard` 和 `HistoryContextLayer`
3. 扩展 `TaskWorkspaceContract.State`，把首页 section 从“workspace 列表”提升到“工作台”
4. 扩展 `TaskNewTaskContract.State`，把 `PC + workspace + execution_policy` 收成新的表单模型
5. 再决定是否需要做 route / screen 命名收口

## 与后续实现的关系

本文是从 planning 进入实现前的最后一层结构冻结。

继续往下推进时，下一步最自然的是二选一：

- 直接开始 Kotlin `Contract / Content / component` skeleton 改造
- 或先补一份 `VPS-first TaskMail UI model / projector plan`

如果目标是尽快动代码，优先走第一条。
