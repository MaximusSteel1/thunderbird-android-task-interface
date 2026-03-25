# TaskMail VPS-First 多 PC Android 骨架实施计划（v0.1）

更新时间：2026-03-25

## 状态

本文是 `VPS-first 多 PC 控制面` 主线下，Android 侧进入第一批实际编码前的 owner implementation plan。

它依附于以下文档：

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/taskmail/planning/android/taskmail-vps-first-multi-pc-authority-v0.1.md`
- `docs/taskmail/planning/android/taskmail-vps-first-multi-pc-user-requirements-authority-v0.1.md`
- `docs/taskmail/planning/android/taskmail-vps-first-multi-pc-compose-screen-structure-v0.1.md`
- `docs/taskmail/planning/platform/taskmail-vps-first-control-plane-freeze-v0.1.md`
- `docs/taskmail/planning/platform/taskmail-pc-vps-control-protocol-v0.1.md`
- `E:\projects\mail_based_task_manager\docs\plans\vps_first_multi_pc_control_plane_mainline_v0.1.md`
- `E:\projects\mail_based_task_manager\docs\plans\vps_first_multi_pc_phase1_execution_plan_v0.1.md`

本文不替代：

- Android 当前实现 truth
- 平台字段 freeze
- PC/VPS repo-side Phase 1 owner note

本文回答的问题是：

**在当前 Android 仓库里，真正进入代码阶段后，第一批 VPS-first 骨架改造应按什么顺序推进、落在哪些文件、以什么边界推进。**

## 目标

这份实施计划只冻结以下内容：

1. Android 当前代码与新协议/新页面骨架之间的主要差距
2. 第一批建议进入编码的切片顺序
3. 每个切片的目标、写入范围和验收读法
4. 哪些旧结构现在只是兼容层，哪些仍不应急着删

## 一句话结论

Android 当前不缺新的平台文档，真正缺的是：

- 让 `Workspace / NewTask / SessionDetail` 这三张真实页面开始承载新的控制面对象
- 让 route / state / DTO 不再继续以 `threadId + mail timeline + senderAccount` 为主语

所以接下来的第一批工作，不是继续补高层设计，而是开始做：

- 页面状态骨架
- 协议 DTO 骨架
- 主键与路由骨架

## 当前代码与新主线的主要差距

### 1. Route 与主键仍偏向旧 mail 读法

当前 `TaskMailRoute.SessionDetail` 仍要求：

- `sessionId`
- `threadId`
- 可选 `workspaceId`

而新主线的 follow-up 读法应是：

- 主键以 `session_id` 为主
- `workspace_id` 用于固定路由归属
- `threadId` 只保留为 legacy fallback / compatibility hint

### 2. 首页仍是 workspace-first

当前首页 contract/state 仍主要围绕：

- `workspaces`
- workspace 下挂 session

而新主线首页应改成：

- `attention sessions`
- `active sessions`
- `recent sessions`
- `pc summaries`
- `workspace summaries`

也就是 `session-first + pc-aware`，而不是 `workspace-first list`。

### 3. 新任务页仍是 sender-account/repo-path 驱动

当前新任务页仍以：

- `senderAccount`
- `repoPath`
- `workdir`
- `backend/profile/permission`

组织表单。

但新主线要求：

- 新任务先选 `pc_id`
- 再选 `workspace_id`
- 再输入任务正文
- `execution_policy` 作为高级执行设置出现

### 4. Session 页仍是 timeline/reply 中心

当前 Session detail state 仍主要围绕：

- `status`
- `pendingQuestions`
- `timeline`
- `reply`

而新主线要求它至少开始长出：

- `statusCard`
- `recentContextSummary`
- `history sheet`
- `streamSections`
- `resultCard`
- `artifactList`
- `effectiveExecution`

### 5. relay/protocol DTO 仍是旧 request/packet 语义

当前 `relay/protocol` 下的对象仍以：

- `request_id`
- `packet_id`
- `accepted: Boolean`
- `result_type`
- `status`

组织。

而新主线 Phase 1 需要逐步承载：

- `command_id`
- `ack_status`
- `event_type`
- `final_status`
- `effective_execution`
- `structured_payload.kind`
- `artifact_manifest`

## Android 第一批编码切片

推荐把 Android 侧第一批编码切成 5 个 slice。

## Slice 1：Session 页骨架先落地

### 目标

让 `TaskSessionDetail` 先从“mail-detail + reply composer”演进成“VPS-first Session 工作面板”。

### 主要改动范围

- `feature/taskmail/internal/ui/detail/TaskSessionDetailContract.kt`
- `feature/taskmail/internal/ui/detail/TaskSessionDetailUiState.kt`
- `feature/taskmail/internal/ui/detail/TaskSessionDetailContent.kt`
- `feature/taskmail/internal/ui/detail/component/*`

### 这一步至少应长出的状态块

- `sessionHeader`
- `statusCard`
- `recentContextSummary`
- `historyPreview`
- `isHistoryVisible`
- `streamSections`
- `resultCard`
- `artifactList`
- `followUpComposer`

### 这一步不要求

- 不要求已经接上正式 VPS Phase 1 API
- 不要求删除旧 timeline
- 不要求立刻把 attachment / reply 能力重写

### 验收读法

完成后，Session 页代码结构应允许：

- 主页先显示当前态
- 主页显示最近上下文摘要
- 历史上下文可通过 sheet/state 进入
- 结果与文件有明确独立落点

## Slice 2：控制面 DTO 骨架

### 目标

为后续 Phase 1 对接预留新的协议对象，而不是继续把旧 `relay/protocol` 当最终形状。

### 主要改动范围

- `feature/taskmail/internal/data/relay/protocol/*`
- 或新增 `feature/taskmail/internal/data/controlplane/protocol/*`
- 对应 JSON codec / mapper

### 推荐策略

- 第一阶段允许新旧 DTO 并存
- 旧 `RelayCommand / RelayCommandAck / RelayEvent / RelayResult` 不要强行一次性改死
- 新增一层更接近 freeze doc 的 control-plane DTO 更稳

### 新 DTO 至少应覆盖

- `pc`
- `workspace`
- `execution_policy`
- `command`
- `command_ack`
- `event`
- `output_chunk`
- `result`
- `artifact_manifest`

### 这一步不要求

- 不要求已经打通网络
- 不要求直接替换当前 direct lane
- 不要求先做完整 replay

### 验收读法

完成后，Android 侧应已经有一套与 freeze doc 同名同语义的 DTO / mapper 基线，后续不再继续围绕 `request_id / packet_id / accepted:Boolean` 生长产品状态。

## Slice 3：新任务页表单骨架

### 目标

让 `TaskNewTask` 从 mail-first 表单演进为 VPS-first 新任务入口。

### 主要改动范围

- `feature/taskmail/internal/ui/newtask/TaskNewTaskContract.kt`
- `feature/taskmail/internal/ui/newtask/TaskNewTaskContent.kt`
- `feature/taskmail/internal/ui/newtask/TaskNewTaskViewModel.kt`
- `feature/taskmail/internal/domain/newtask/TaskMailNewTaskDraft.kt`

### 这一步至少应长出的状态块

- `selectedPc`
- `pcOptions`
- `selectedWorkspace`
- `workspaceOptions`
- `taskInput`
- `executionPolicyEditor`
- `submitState`

### 兼容原则

- 短期仍可保留 `senderAccount`
- 短期仍可保留 `repoPath/workdir`
- 但这些应逐步退到 compatibility / bridge 位置

### 这一步不要求

- 不要求已接通正式 `pc list / workspace list`
- 不要求立刻删除 project sync prefill

### 验收读法

完成后，页面结构应已按 `pc + workspace + task + execution_policy` 组织，而不是继续把 `sender account + repo path` 当作产品主读法。

## Slice 4：首页 / 工作台骨架

### 目标

让 `TaskWorkspace` 从“workspace 列表”演进到“工作台首页”。

### 主要改动范围

- `feature/taskmail/internal/ui/workspace/TaskWorkspaceContract.kt`
- `feature/taskmail/internal/ui/workspace/TaskWorkspaceUiState.kt`
- `feature/taskmail/internal/ui/workspace/TaskWorkspaceContent.kt`
- `feature/taskmail/internal/ui/workspace/TaskWorkspaceViewModel.kt`
- `feature/taskmail/internal/ui/workspace/component/*`

### 这一步至少应长出的 section

- `attentionSessions`
- `activeSessions`
- `recentSessions`
- `pcSummaries`
- `workspaceSummaries`

### 这一步不要求

- 不要求 route 名字立刻从 `Workspace` 改为 `Workbench`
- 不要求当前 mail-backed summary 立刻退场

### 验收读法

完成后，首页的 UI 组织应已经变成：

- session 是主对象
- pc/workspace 是辅助路由上下文

## Slice 5：路由与主键收口

### 目标

让 Android 侧主键读法与新控制面收敛，但又不立刻砍掉 legacy 兼容。

### 主要改动范围

- `feature/taskmail/api/TaskMailRoute.kt`
- `feature/taskmail/internal/domain/model/TaskSessionKey.kt`
- `feature/taskmail/internal/navigation/*`
- 相关 mapper / repository key

### 推荐策略

- `sessionId` 升为 follow-up 主锚点
- `workspaceId` 保持路由归属
- `threadId` 退为 fallback / compatibility hint

### 这一步不要求

- 不要求一次性删除所有 `threadId`
- 不要求 current-truth mail path 立刻停用

### 验收读法

完成后，新的页面状态与导航逻辑不应继续把 `threadId` 当第一产品主键。

## 推荐顺序

Android 侧推荐按以下顺序编码：

1. Slice 1 `Session 页骨架`
2. Slice 2 `控制面 DTO 骨架`
3. Slice 3 `新任务页表单骨架`
4. Slice 4 `首页 / 工作台骨架`
5. Slice 5 `路由与主键收口`

原因：

- Session 页是最集中承载 `event / output_chunk / result / artifact` 的地方
- DTO 骨架越早有，越不会在 UI 里继续泄漏旧 packet 模型
- 新任务页和首页随后收口，才能让 UI 真正变成 VPS-first
- route/key 最后做更稳，可以减少过早大范围连锁修改

## 当前明确不做的事

这份实施计划当前明确不要求：

- 先全量删除旧 mail/direct 代码
- 先重写整个 repository
- 先切完正式 VPS transport
- 先做多用户 ACL
- 先做跨 PC 热迁移
- 先全面 rename `Workspace/NewTask/SessionDetail`

## 对实现时的直接约束

后续真正动代码时，默认继续遵守：

1. Android 当前 truth 仍以 `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md` 为准
2. 文档里冻结的页面骨架，优先落在现有 `Contract / ViewModel / Content / component` 上
3. 新协议对象优先用 freeze doc 的 canonical 名称
4. `senderAccount / threadId / repoPath` 当前仍可保留，但读法必须逐步降为 compatibility seam

## 与 PC 端握手时机

如果 Android 侧希望避免过早进入跨端联调，默认应在下面这些前提满足后，再开始第一轮正式握手：

- `Slice 1 Session 页骨架`
- `Slice 2 控制面 DTO 骨架`
- `Slice 3 新任务页表单骨架`
- `Slice 5` 中与 `sessionId` 主锚点有关的最小收口

具体 gate checklist 见：

- `docs/taskmail/planning/android/taskmail-vps-first-multi-pc-pc-handshake-readiness-checklist-v0.1.md`

## 一句话结论

**Android 下一步不是再写抽象文档，而是按 `Session -> DTO -> NewTask -> Home -> RouteKey` 这条顺序，把现有 TaskMail 代码骨架逐步掰成 VPS-first 控制面形状。**
