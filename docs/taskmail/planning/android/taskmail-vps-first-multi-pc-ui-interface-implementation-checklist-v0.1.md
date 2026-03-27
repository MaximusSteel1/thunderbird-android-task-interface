# TaskMail VPS-First 多 PC UI 到代码接口实现清单（v0.1）

更新时间：2026-03-27

## 文档目的

本文只回答一件事：

**按照当前已经确认的高保真设计，Android 仓库里哪些 UI 部件或行为已经有代码接口，哪些还只是半接通状态，哪些仍是明确缺口。**

后续实现建议直接围绕本清单推进。

本文不替代以下 truth / authority：

- 当前实现事实：`docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- 用户需求 authority：`docs/taskmail/planning/android/taskmail-vps-first-multi-pc-user-requirements-authority-v0.1.md`
- 页面状态映射：`docs/taskmail/planning/android/taskmail-vps-first-multi-pc-viewstate-action-mapping-v0.1.md`

## 本文覆盖的设计稿

- 首页树形工作台补充高保真：`docs/taskmail/planning/android/mockups/taskmail-vps-first-multi-pc-home-tree-high-fi-preview-v0.1.html`
- 新任务页补充高保真：`docs/taskmail/planning/android/mockups/taskmail-vps-first-multi-pc-new-task-high-fi-preview-v0.1.html`
- Session 状态轮替高保真：`docs/taskmail/planning/android/mockups/taskmail-vps-first-multi-pc-session-mode-high-fi-preview-v0.1.html`
- 历史复盘高保真补充：`docs/taskmail/planning/android/mockups/taskmail-vps-first-multi-pc-history-review-high-fi-preview-v0.1.html`

## 结论先行

当前代码里最成熟的接口面是：

- `new_task -> create-session facade -> session binding -> SessionDetail`
- `SessionDetail` 的读路径、回复、`/status`、timeline attachment
- 本地 `VPS-native session detail cache/projection`

当前最关键的缺口是：

- 首页还没有 `PC -> workspace -> session` 的公开数据接口面
- 新任务页还没有真实的 `PC / workspace` 选项加载
- 新任务页还没有“输入附件”接口，且 `create-session` payload 也还没有附件字段
- 历史复盘还没有“回合模型”，当前只有 Session 内部的简化 history preview
- Session 页还没有 `停止运行 / 结束活跃 / 引导输入 / 回复时权限覆盖` 这组控制动作接口

## 读法约定

本文把每一项能力分成三档：

- `已有码接口`：Contract / ViewModel / UseCase / Repository / Transport 至少已经连成一条真实可调用链
- `半接通`：状态壳或协议模型已经存在，但还没有进入当前 public UI 主链
- `缺口`：当前仓库里还没有对应接口，或只在设计稿里存在

---

## 1. 首页 / Workbench

### 1.1 已有码接口

- 正式 route 已存在：`Workspace` 是默认首页入口
  - `feature/taskmail/api/src/main/kotlin/net/thunderbird/feature/taskmail/api/TaskMailRoute.kt`
- 首页 Contract 已支持这些主行为：
  - `LoadData`
  - `RefreshRequested`
  - `ForegroundRefreshStarted / Stopped`
  - `ProjectListClicked`
  - `NewTaskClicked`
  - `SessionClicked`
  - 文件：`feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/workspace/TaskWorkspaceContract.kt`
- 首页 ViewModel 已有真实加载链：
  - `GetTaskSessionDetails`
  - `RefreshTaskMail`
  - `ObserveTaskMailStoreChanges`
  - `ObserveTaskSessionDetailStoreChanges`
  - `SyncTaskMailCache`
  - 文件：`feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/workspace/TaskWorkspaceViewModel.kt`
- 首页当前 public UI 状态已经能表达：
  - `attentionSessions`
  - `activeSessions`
  - `recentSessions`
  - `workspaceSummaries`
  - 文件：`feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/workspace/TaskWorkspaceContract.kt`
- Session 列项当前已有这些字段：
  - `workspaceId`
  - `sessionId`
  - `sessionName`
  - `status`
  - `backend`
  - `lastSummary`
  - `pendingQuestion`
  - `routeLabel`
  - `lastUpdatedAt`
  - 文件：`feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/workspace/TaskWorkspaceUiState.kt`

### 1.2 半接通

- `TaskMailRepository.getTaskWorkspaceSummaries()` 和 `GetTaskWorkspaceSummaries` 已存在，但当前首页主链没有使用它
  - 文件：`feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/repository/TaskMailRepository.kt`
  - 文件：`feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/usecase/GetTaskWorkspaceSummaries.kt`
- 数据层已有 `TaskWorkspaceSummary` / `TaskSessionSummary`，但没有 `PC` 一层的 public UI model
  - 文件：`feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/model/TaskWorkspaceSummary.kt`
  - 文件：`feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/model/TaskSessionSummary.kt`
- 协议层已经有 `ControlPlanePc`、`ControlPlaneWorkspaceSnapshot`、能力目录等模型：
  - `supported_backends`
  - `profile_catalogs`
  - `permission_modes`
  - 但当前还没有被首页 public UI 消费
  - 文件：`feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/data/controlplane/protocol/ControlPlaneProtocolModels.kt`
- `活跃 / 非活跃` 当前只能从 `status` 派生：
  - `Queued / Running` 会进入 active
  - `WaitingUser / Paused / Failed` 会进入 attention
  - 没有单独的 `isActive` 字段
  - 文件：`feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/workspace/TaskWorkspaceUiState.kt`

### 1.3 缺口

- 没有 `PC -> workspace -> session` 树形首页的 UI 状态模型
- 没有 `PC` 层的展开/折叠事件与持久状态
- 没有 `PC 在线 / 离线` 的首页公开接口
- 没有 `workspace 已缺失但 session 继续显示` 的公开标记接口
- 当前首页主链不是树形工作台，而是 `attention / active / recent + workspace summaries`

### 1.4 实现建议

建议新增一组首页树形状态模型，至少包括：

- `TaskHomePcNodeUi`
- `TaskHomeWorkspaceNodeUi`
- `TaskHomeSessionLeafUi`

并显式补上：

- `pcStatus = online | offline | stale`
- `workspacePresence = normal | missing`
- `sessionActivity = active | inactive`
- `isExpanded`

---

## 2. 新任务页

### 2.1 已有码接口

- 正式 route 已存在：`TaskMailRoute.NewTask`
  - 文件：`feature/taskmail/api/src/main/kotlin/net/thunderbird/feature/taskmail/api/TaskMailRoute.kt`
- 新任务页 Contract 已定义这些主要输入：
  - `senderAccount`
  - `pcSelection`
  - `workspaceSelection`
  - `taskInput`
  - `executionPolicyEditor`
  - `submitState`
  - 文件：`feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/newtask/TaskNewTaskContract.kt`
- 新任务页 ViewModel 已有真实提交链：
  - 校验表单
  - 构建 `TaskMailNewTaskDraft`
  - 调 `CreateTaskMailSession`
  - accepted 后 seed provisional detail
  - 跳转 `SessionDetail`
  - 文件：`feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/newtask/TaskNewTaskViewModel.kt`
- `create-session` 真实 facade client 已存在，当前主写路径是：
  - `POST /v1/android/create-session`
  - 请求主键：`pc_id + workspace_id`
  - 文件：`feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/data/facade/OkHttpTaskMailCreateSessionFacadeClient.kt`
- 当前 draft / result 已覆盖：
  - `pcId`
  - `workspaceId`
  - `repoPath`
  - `workdir`
  - `taskText`
  - `subjectTitle`
  - `backend`
  - `profile`
  - `permission`
  - `acceptanceCriteria`
  - `sessionBinding`
  - 文件：`feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/newtask/TaskMailNewTaskDraft.kt`
  - 文件：`feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/newtask/TaskMailCreateSessionResult.kt`
- 当前唯一已经接通的新任务预填是：
  - `ProjectSync -> repoPath`
  - 文件：`feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/newtask/TaskNewTaskScreen.kt`
  - 文件：`feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/navigation/TaskMailNavHost.kt`

### 2.2 半接通

- `pcOptions` / `workspaceOptions` 状态字段已经有，但当前 `LoadData` 只加载 sender accounts，没有真实选项加载逻辑
  - 文件：`feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/newtask/TaskNewTaskContract.kt`
  - 文件：`feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/newtask/TaskNewTaskViewModel.kt`
- `backend / profile / permission` 已进入 draft 和 HTTP payload，但当前不是按目标 `PC/workspace` 能力动态驱动
- `repoPath / workdir` 仍带有明显 bridge 兼容语义，尚未完全收成纯 VPS-first 路由输入

### 2.3 缺口

- 没有“输入附件”接口：
  - Contract 没有 attachment state / event
  - ViewModel 没有 attachment 处理
  - Content 没有 attachment UI
  - `create-session` payload 也没有 attachment 字段
- 没有 `PC / workspace` 的真实查询 use case
- 没有 `PC / workspace` 的导航预选参数
- 没有“被拒绝状态”的结构化 UI state，只是 `sendError`

### 2.4 关键提醒

**如果要实现你刚设计的新任务“输入附件”，不能只改 UI。**

至少要同时补：

1. `TaskNewTaskContract` 的 attachment state / event
2. `TaskNewTaskViewModel` 的 attachment 处理
3. `TaskMailNewTaskDraft` 的 attachment 字段
4. `create-session` facade request payload 的 attachment 表达
5. 上游 Android-facing facade 对 attachment 的协议支持

---

## 3. Session 页

### 3.1 已有码接口

- 正式 route 已存在：`TaskMailRoute.SessionDetail(workspaceId?, sessionId)`
  - 文件：`feature/taskmail/api/src/main/kotlin/net/thunderbird/feature/taskmail/api/TaskMailRoute.kt`
- Session Contract 已支持这些主行为：
  - `LoadDetail`
  - `DraftChanged`
  - `AttachmentsSelected / RemoveAttachmentClicked`
  - `SendReplyClicked`
  - `SendChoiceClicked`
  - `StatusQueryClicked`
  - `RefreshClicked`
  - `HistoryClicked / HistoryDismissed`
  - timeline attachment 的 open/save
  - 文件：`feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/TaskSessionDetailContract.kt`
- Session Detail UI 状态已经覆盖：
  - `recentContext`
  - `resultSummary`
  - `artifacts`
  - `historyPreview`
  - `pendingQuestions`
  - `quickAnswerChoices`
  - `structuredReplyTemplate`
  - `replyContext`
  - `timeline`
  - 文件：`feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/TaskSessionDetailUiState.kt`
- Session ViewModel 已有真实读链：
  - `GetTaskSessionDetail`
  - `RefreshTaskMail`
  - `SyncTaskMailCache`
  - `ObserveTaskMailDirectSessionDetail`
  - control-plane overlay merge
  - 文件：`feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/TaskSessionDetailViewModel.kt`
- 当前 direct 写路径已接入：
  - plain reply
  - quick answer
  - `/status`
  - 文件：`feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/sessionaction/TaskMailDirectSessionActionRequest.kt`
  - 文件：`feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/usecase/SendTaskMailDirectSessionAction.kt`

### 3.2 半接通

- `replyAttachments` 接口已经有：
  - 可选
  - 可删除
  - 但当前 direct plain reply 明确不支持 attachments
  - 文件：`feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/TaskSessionDetailViewModel.kt`
- control-plane snapshot 已经能补 recent context / result / artifacts，但当前 UI 主结构仍然不是你定义的“结果主导 / 当前轮输入主导”两种模式
- `historyPreview` 已存在，但它只是 merged timeline 的轻投影，不是“回合复盘模型”

### 3.3 缺口

- 没有 `停止运行 / kill`
- 没有 `结束活跃 / deactivate`
- 没有 `引导输入` 动作和对应 event
- 没有 reply 时的 `permission` 覆盖接口
- 没有“当前轮输入卡 / 上一轮结果卡”的模式化 UI state
- 没有“输入附件 / 结果附件”分组模型

### 3.4 关键事实

当前 direct session action request 只有两类：

- `reply`
- `status`

也就是说，`stop / deactivate / guide` 这组动作现在不只是 UI 缺口，**连 request type 都还没有**。

文件：

- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/sessionaction/TaskMailDirectSessionActionRequest.kt`

---

## 4. 历史复盘页

### 4.1 已有码接口

- 当前只有 Session 内的 history 入口，不是独立页面 route
- Session state 里已有：
  - `isHistoryVisible`
  - `historyPreview`
  - 文件：`feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/TaskSessionDetailContract.kt`
  - 文件：`feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/TaskSessionDetailUiState.kt`
- 当前 `HistoryContextSheet` 已能展示 `TaskHistoryRoundUi` 列表
  - 文件：`feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/component/HistoryContextSheet.kt`

### 4.2 半接通

- `TaskHistoryRoundUi` 已有壳，但当前字段只有：
  - `title`
  - `summary`
  - `statusLabel`
  - `messagePreview`
- 它当前来自 `timeline` 投影，不是独立的 `round projector`

### 4.3 缺口

- 没有独立 `HistoryReview` route
- 没有“回合展开态”的数据模型
- 没有 `输入 / 过程记录 / 结果 / 输入附件 / 结果附件` 这套 round detail model
- 没有“多回合同页展开”的状态管理
- 没有“过程记录折叠/展开”的 per-round state

### 4.4 关键判断

你刚确认的历史设计不是“时间线 sheet”，而是“回合复盘工作台”。  
这意味着当前 `historyPreview` 这条链最多只能复用一部分数据，不能直接拿来当最终实现模型。

---

## 5. 跨页面 / 协议层现状

### 5.1 已存在但尚未进入 public UI 的协议对象

- `ControlPlanePc`
- `ControlPlaneWorkspaceSnapshot`
- `ControlPlaneWorkspace`
- `supported_backends`
- `profile_catalogs`
- `permission_modes`
- `backend_transport_modes`

文件：

- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/data/controlplane/protocol/ControlPlaneProtocolModels.kt`

### 5.2 当前实现上的重要约束

- 首页 public workbench 现在以 `session-first` 为主，不是 `PC-first`
- `PC` 目前只在 new-task submit payload 和协议模型里是明确对象
- 新任务输入附件当前连 submit payload 都没有
- Session direct action 当前只支持 `reply / status`

---

## 6. 推荐实施顺序

### P0. 新任务页：先把真实路由选择接通

目标：

- 让 `PC / workspace` 不再只是空状态字段
- 把设计稿里的“在哪做什么”真正立起来

建议切片：

1. 新增 `PC / workspace` 查询 use case
2. 给 `TaskNewTaskViewModel.LoadData` 接入真实选项加载
3. 支持 `ProjectSync -> repoPath` 之外的 `workspace` 预选
4. 把 `backend / profile / permission` 改成能力驱动选项，而不是纯自由输入

### P1. 新任务页：补输入附件端到端

目标：

- 让高保真里的“输入附件”不是假 UI

建议切片：

1. Contract / ViewModel / Content 补 attachment
2. `TaskMailNewTaskDraft` 补 attachment 字段
3. `create-session` facade request 补 attachment payload
4. 联动上游 Android-facing facade

### P2. Session 页：先把模式驱动结构落出来

目标：

- 把 Session 从“平铺的 detail + timeline”收成你定义的两种主模式

建议切片：

1. 新增 `结果主导 / 当前轮输入主导` 顶部 state
2. 先重排已有 `recentContext / resultSummary / artifacts / reply`
3. 暂不碰 kill/deactivate，先把主读法跑通

### P3. Session 页：补控制动作

目标：

- 支持 `停止运行 / 结束活跃 / 引导`

建议切片：

1. 扩展 `TaskMailDirectSessionActionRequest`
2. 扩展 sender / use case
3. 再把按钮接回 Session 顶部控制区

### P4. 历史复盘页：补回合模型

目标：

- 从 timeline preview 升级成 round review

建议切片：

1. 定义 `TaskHistoryRoundDetailUi`
2. 从 timeline + control-plane 投影 round
3. 支持同页多展开与过程折叠

### P5. 首页：最后切到树形工作台

目标：

- 落地 `PC -> workspace -> session`

原因：

- 这一步依赖 `PC / workspace` 数据面更完整
- 否则很容易先做出“树形壳”，但数据还是假的

建议切片：

1. 明确首页树形数据源
2. 补 `PC` 层 UI model
3. 补在线/离线与 workspace 缺失标记
4. 再接展开/折叠交互

---

## 7. 当前实现时不要误判的点

- 不要把新任务“输入附件”误判成只要加个按钮就行；它现在是协议级缺口
- 不要把首页树形稿误判成只改 Compose 层就能完成；当前缺少公开 `PC` 数据层
- 不要把历史复盘误判成改一下 `HistoryContextSheet` 样式就够；当前缺的是 round model
- 不要把 Session 页的 `停止运行 / 结束活跃 / 引导` 误判成只改按钮；当前 direct request type 还不存在

## 8. 推荐作为后续实现入口的文件

- 首页：
  - `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/workspace/TaskWorkspaceContract.kt`
  - `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/workspace/TaskWorkspaceViewModel.kt`
  - `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/workspace/TaskWorkspaceUiState.kt`
- 新任务：
  - `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/newtask/TaskNewTaskContract.kt`
  - `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/newtask/TaskNewTaskViewModel.kt`
  - `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/newtask/TaskMailNewTaskDraft.kt`
  - `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/data/facade/OkHttpTaskMailCreateSessionFacadeClient.kt`
- Session：
  - `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/TaskSessionDetailContract.kt`
  - `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/TaskSessionDetailViewModel.kt`
  - `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/sessionaction/TaskMailDirectSessionActionRequest.kt`
- 历史复盘：
  - `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/TaskSessionDetailUiState.kt`
  - `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/component/HistoryContextSheet.kt`

以上清单当前作为后续实现的默认起点。
