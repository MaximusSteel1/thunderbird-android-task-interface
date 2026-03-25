# TaskMail VPS-First 多 PC Android 骨架实施计划（v0.1）

更新时间：2026-03-26

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
4. 哪些旧结构应被替换、删除，以及迁移期适配器可以放在哪些边界

## 一句话结论

Android 当前不缺新的平台文档，真正缺的是：

- 让 `Workspace / NewTask / SessionDetail` 这三张真实页面开始承载新的控制面对象
- 让 route / state / DTO 不再继续以 `threadId + mail timeline + senderAccount` 为主语
- 停止为旧 mail/direct 入口继续保兼容

所以接下来的第一批工作，不是继续补高层设计，而是开始做：

- 页面状态骨架
- 协议 DTO 骨架
- 主键与路由骨架

## Hard Cutover 原则

从本文生效后，后续 slice 默认按“替换旧入口并尽快删除 legacy seam”推进：

- 不再把 mail/direct 旧线当长期并存前提
- 不再把 `threadId fallback`、`mail-backed summary`、`senderAccount / repoPath / workdir bridge` 当默认保留项
- 若短期出现过渡 adapter，只允许位于 ingress / adapter boundary，并且必须带明确 retirement 条件

## 当前代码与新主线的主要差距

### 1. Route 与主键仍偏向旧 mail 读法

当前 `TaskMailRoute.SessionDetail` 仍要求：

- `sessionId`
- `threadId`
- 可选 `workspaceId`

而新主线的 follow-up 读法应是：

- 主键以 `session_id` 为主
- `workspace_id` 用于固定路由归属
- `threadId` 退出页面路由、页面 state 与主仓库接口的默认主键读法

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

- control-plane DTO 是唯一继续增长的产品状态对象
- 现存 relay-era DTO 或 mapper 只按短期导入桥读取，不再继续扩大使用面
- 新代码不再直接以 `RelayCommand / RelayCommandAck / RelayEvent / RelayResult` 作为 UI 或 domain state 的默认输入

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

### Cutover 原则

- `senderAccount / repoPath / workdir` 不再作为需要持续保留的 UI 兼容层
- 一旦正式 `pc/workspace/submit` path 可用，直接删除对应 bridge，不再保留双读法
- 不再新增 compatibility getter、bridge 文案或新的 mail-era 表单字段

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
- 不要求在这一 slice 内完成全部 PC/VPS 联调
- 但当前 mail-backed summary 只按待替换遗留输入读取，不再作为后续默认数据基线

### 验收读法

完成后，首页的 UI 组织应已经变成：

- session 是主对象
- pc/workspace 是辅助路由上下文

## Slice 5：路由与主键收口

### 目标

让 Android 侧主键、路由与仓库入口完成 control-plane hard cutover。

### 主要改动范围

- `feature/taskmail/api/TaskMailRoute.kt`
- `feature/taskmail/internal/domain/model/TaskSessionKey.kt`
- `feature/taskmail/internal/navigation/*`
- 相关 mapper / repository key

### 推荐策略

- `sessionId + workspaceId` 成为路由、页面状态与仓库接口的默认主锚点
- `threadId` 从 `TaskMailRoute`、`TaskSessionKey`、主导航事件与页面 contract 中退出
- 若历史数据迁移仍需 `threadId` lookup，只允许存在于 repository / ingress adapter 边界，不再进入 public route/state contract

### 这一步不要求

- 不要求在同一提交里删净所有历史测试样例、预览数据或旧文档
- 不要求为了 cutover 先做 repo-wide rename

### 验收读法

完成后，新的页面状态、导航逻辑与仓库 key 不再接受 `threadId` 作为必填或 fallback 主锚点。

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

## 2026-03-25 当前一批推进口径

当前建议不要把 Android 侧下一批工作切成零散的小修。

更合适的做法是把以下三件事收成同一批 Android local-readiness 推进：

1. `route / key` 收口
2. `NewTask` 主表单 cutover
3. 本地 control-plane 纵切自洽

这批工作的目标不是宣称 Android 已完成正式跨端握手，也不是宣称 mail/direct 兼容代码已经可以一次删净。

这批工作的目标是：

- 把 `threadId` 从页面 state、列表 identity 与公开交互读法中退出
- 把 `TaskNewTask` 页面明确改读成 `pc + workspace + task + execution_policy`
- 把 `repoPath / workdir` 明确降为当前 relay submit 仍在使用的 bridge 输入，而不是产品主表单主语
- 让 Android 侧已经落地的 canonical `command_ack / event / result / artifact_manifest` DTO 不再只停在 codec / mapper 层，而是能在本地 representative JSON / fake-data 纵切里稳定投影到 `SessionDetail`

### A. `route / key` 收口

这一批的最低要求是：

- `TaskMailRoute.SessionDetail` 继续只以 `workspaceId + sessionId` 作为公开路由读法
- `TaskWorkspace` 的 session item identity 不再直接暴露 `threadId`
- `threadId` 只允许继续存在于 repository、cache、ingress、direct subscription 或 action adapter 等 compatibility boundary

本批不要求：

- 立刻删除仓库内所有 `threadId` 字段
- 先改 parser、mail cache 或历史样本的 canonical truth

### B. `NewTask` 主表单 cutover

这一批的最低要求是：

- 页面顺序继续固定为 `route target -> task input -> execution policy -> submit bridge`
- `senderAccount + repoPath` 不再被写成产品主读法
- `repoPath / workdir` 必须以“当前 relay submit bridge”身份显式呈现
- 若未来 `workspace option` 已携带 repo/workdir bridge 信息，ViewModel 应允许从该 option 自动解析 bridge，而不是强迫用户再次手填

本批不要求：

- 立刻接通正式 `pc list / workspace list`
- 立刻删除 `ProjectSync -> repoPath` 回填
- 在 Android 侧同一提交里切完真正的 VPS network path

### C. 本地 control-plane 纵切自洽

这一批的最低要求是：

- Android 侧已有的 canonical `command_ack / event / result / artifact_manifest` DTO，必须能通过本地 representative JSON 或 fake data 走通最小 UI 投影
- `SessionDetail` 至少应能从这条本地纵切稳定承载：
  - `recent context`
  - `result summary`
  - `effective execution`
  - `artifact list`
- 这条纵切的结论应明确读成 “Android local readiness evidence”，而不是 live handshake closeout

### 这批工作的验收读法

如果这批推进完成，Android 侧应满足以下读法：

1. 页面和路由已经不再继续把 `threadId` 当作未来主线主键
2. `NewTask` 的主表单已经切正，但兼容 bridge 仍被诚实保留在边界层
3. canonical control-plane DTO 已经在本地自洽地进入 `SessionDetail` UI，而不是只存在于协议模型和 mapper
4. Android 可以据此更稳地进入第一次 `NewTask -> SessionDetail` 正式握手，而不必在握手时同时改路由、改表单、改 DTO 承载

## 2026-03-26 SessionDetail ingress/cache 收口口径

在 `2026-03-25` 那一批 Android local-readiness 完成后，下一步不应再去横向扩更多页面。

当前最短主线推进，应聚焦把 `SessionDetail` 从“本地 fake overlay 能看见 control-plane”推进到“真实 direct observation / cache reload 也能继续承载 control-plane”。

### 这一批的目标

这一批只冻结以下事情：

1. `TaskSessionDetail` domain model 与本地缓存明确承载 `controlPlaneSnapshot`
2. direct observation 不再只消费旧 `session update`，而是同时并入 relay `event / result`
3. `SessionDetail` ViewModel 能把 direct observation 里出现的 control-plane 快照写回 detail repository，并在 reload 后继续显示
4. mail rebuild 不得抹掉已经落到本地 detail cache 的 control-plane 快照

### 这一批完成后的正确读法

完成后，Android 侧应按下面的口径解读：

- `event / result / effective_execution` 已经不再只是测试内 overlay 数据，而是能通过真实 direct observation 进入 `SessionDetail`
- `TaskSessionDetailJsonCodec` 与 file-backed detail cache 已经可以保留这批 control-plane 数据
- `SyncTaskMailCache` 在重建 mail-backed detail 时，会尽量保留已存在的 `controlPlaneSnapshot`
- 这仍然是 Android 本地 ingress/cache readiness，并不等于正式 live handshake 已经关单

### 这一批明确不做的事

这一批当前明确不做：

- 不把 `artifact_manifest` live stream 一起接进 direct detail 订阅链
- 不把 `command_ack` 从发送链直接并入 detail subscription 观察链
- 不在同一批内扩到 `Workbench`、artifact 下载或 `output_chunk` 直播

### 这批工作的验收读法

如果这批推进完成，应该至少能稳定证明：

1. relay `event` 到达后，`SessionDetail` 最近上下文会更新
2. relay `result` 到达后，`SessionDetail` 结果摘要与 `effective execution` 会更新
3. 这些更新会被写回本地 detail repository，并在缓存读回时继续存在
4. mail-backed rebuild 不会把这批 control-plane 快照直接覆盖掉

## 当前明确不做的事

这份实施计划当前明确不要求：

- 先在同一提交里全量删除仓库内每一处旧 mail/direct 文件
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
4. `senderAccount / threadId / repoPath` 这类旧字段不再作为新设计 baseline；若迁移期短期存在，只能隔离在边界 adapter 并附明确删除条件

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
