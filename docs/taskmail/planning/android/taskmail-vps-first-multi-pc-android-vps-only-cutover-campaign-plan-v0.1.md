# TaskMail VPS-First 多 PC Android VPS-Only Cutover Campaign 计划（v0.1）

更新时间：2026-03-26

## 状态

本文是 Android 侧在 `VPS-first 多 PC 控制面` authority 下，**当目标被明确收紧为“尽快切到 Android-facing VPS-only”** 时的 owner campaign 文档。

它回答的问题不是“最终理想骨架长什么样”，而是：

**如果现在要以最快速度把 Android 公共主流程从 mail / relay compatibility 读法切到 `VPS` 主控制面，后续 1~5 应按什么顺序推进，哪些事该先做，哪些事暂时不要再投入。**

本文不替代：

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-MAIL-RULES.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/taskmail/planning/android/taskmail-vps-first-multi-pc-authority-v0.1.md`
- `docs/taskmail/planning/android/taskmail-vps-first-multi-pc-android-skeleton-implementation-plan-v0.1.md`
- `docs/taskmail/planning/platform/taskmail-pc-vps-control-protocol-v0.1.md`

它冻结的是 **campaign 排序、阶段边界与删旧口径**。

## Read First

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/taskmail/planning/android/taskmail-vps-first-multi-pc-authority-v0.1.md`
- `docs/taskmail/planning/android/taskmail-next-development-plan-v0.2.md`
- `docs/taskmail/planning/android/taskmail-vps-first-multi-pc-android-skeleton-implementation-plan-v0.1.md`
- `docs/taskmail/planning/android/taskmail-next-session-handoff-2026-03-26.md`
- `docs/taskmail/planning/android/taskmail-vps-first-multi-pc-pc-handshake-readiness-checklist-v0.1.md`
- `docs/taskmail/planning/platform/taskmail-vps-first-control-plane-freeze-v0.1.md`
- `docs/taskmail/planning/platform/taskmail-pc-vps-control-protocol-v0.1.md`
- `E:\projects\mail_based_task_manager\docs\plans\vps_first_multi_pc_control_plane_mainline_v0.1.md`
- `E:\projects\mail_based_task_manager\docs\plans\vps_first_multi_pc_phase1_execution_plan_v0.1.md`

## 术语边界

本文里的 `Android-facing VPS-only` 只表示：

- Android 公共页面与公共写路径的**主协议入口**切到 `VPS` 控制面
- `mail`、旧 `/relay` packet、`threadId` fallback、mail-backed summary 不再作为后续切片的默认产品基线
- 迁移期如果仍存在 legacy adapter，它们只能留在 ingress / repository / import boundary

它**不等于**：

- 同一天删净仓库内所有 mail 代码
- 当前 truth 文档立刻改写成“mail 已经不存在”
- 第一批提交里同时完成所有页面美化、全部 artifact 能力和所有 live closeout

## 一句话结论

如果目标是“尽快切到 Android-facing VPS-only”，Android 不应从首页重排或旧 timeline 打磨起手，而应先做两件事：

1. 把 `NewTask` 的真实提交路径切到 `pc-control`
2. 让 `SessionDetail` 能直接承载 `command / event / result`

只有这两件事先成立，后面的 route/key、reply/status、workbench 与删旧，才真正有 cutover 价值。

## Campaign 原则

### 1. 优先看 cutover 价值，不优先看局部骨架完整度

如果某一批改动不能让 Android 侧离 “mail 不再是主流程真相” 更近，它就不应排在前面。

### 2. 每一批都要尽量形成用户可感知的主链闭环

优先做能直接改变：

- 任务如何创建
- detail 如何更新
- reply/status 如何回流

而不是先做纯结构占位。

### 3. Legacy seam 只能继续留在边界层

`RunTaskMailDirectOrFallback`、`threadId fallback`、`mail-backed summary`、`senderAccount / repoPath / workdir bridge`
这类对象若短期仍存在，只允许留在：

- ingress adapter
- repository compatibility lookup
- 当前 relay bridge 输入边界

它们不再继续进入页面主状态、主路由或产品主交互。

### 4. 验收按“是否还能继续回到旧主线”来判断

每一批完成后，应该更难再把产品主流程解释为 mail-first。  
如果完成某一批后，Android 仍主要靠 canonical mail 才能让公共主流程成立，那这批还不算真正过线。

## 推荐 Campaign 顺序

本文把后续推进冻结成 5 个 owner 批次。

### 1. `NewTask` 写路径 hard cutover

#### 目标

把 `TaskNewTask` 从“旧 `/relay` compatibility packet + mail-era bridge submit”切到真正的 `pc-control` 主线入口。

#### 本批必须完成

- 冻结 Android 第一轮正式主线入口：
  - 如果只是短期 operator 验证，可临时用 debug dispatch 入口
  - 但若目标是 Android-facing cutover，不把 debug endpoint 写成产品主入口
- `pc_id`、`workspace_id` 成为提交硬校验，而不是仅在 mapper 层非空
- `TaskMailNewTaskDraft` 的 canonical control-plane command 真正进入 transport sender
- 发送成功语义改成 control-plane 读法，不再继续强化 `[Relay]` compatibility copy
- 发送后能够拿到可用 `command_ack`

#### 本批暂不要求

- 不要求同批完成首页重排
- 不要求同批切 reply/status
- 不要求立刻删净 `repoPath / workdir` bridge

#### 批次验收

完成后，Android 至少应能稳定读成：

- `NewTask` 主提交已经不再依赖旧 `/relay` packet
- session 创建不再以 canonical mail arrival 作为唯一首次可见时机
- 用户看到的是 control-plane submit 成功，而不是 “mail fallback / relay compatibility” 成功

### 2. `SessionDetail` control-plane 承载与缓存收口

#### 目标

让 `SessionDetail` 不再主要依赖 mail timeline 才能成立，而是能直接承载：

- `event`
- `result`
- `effective_execution`
- 可用时的 `artifact_manifest`

#### 本批必须完成

- `TaskSessionDetail` domain / UI state 明确承载 `controlPlaneSnapshot`
- direct observation / local fake overlay 不再只是演示层，而能进入真实 detail repository
- reload 后保留 control-plane 快照，不被 mail rebuild 直接抹掉
- detail 页面至少稳定展示：
  - 最近上下文
  - 结果摘要
  - 实际执行摘要
  - artifact 区落点

#### 本批暂不要求

- 不要求同批做 output chunk 全直播
- 不要求同批做 artifact 下载全闭环
- 不要求旧 timeline 当场删除

#### 批次验收

完成后，保持 detail 页打开时，`event / result` 应能直接推动页面更新；退出再重进后，这些 control-plane 数据仍然存在。

### 3. route / key hard cutover

#### 目标

把 Android 公共路由、页面主键与仓库主锚点从 `threadId` 读法切到 `workspaceId + sessionId`。

#### 本批必须完成

- `TaskMailRoute.SessionDetail` 公开读法只保留 `workspaceId + sessionId`
- `TaskSessionKey` 与页面 contract 不再要求 `threadId`
- `threadId` 只允许继续留在 repository / cache / ingress adapter boundary
- session 列表 identity 不再直接暴露 thread mail 语义

#### 本批暂不要求

- 不要求一次删净历史样本
- 不要求为 cutover 做 repo-wide rename

#### 批次验收

完成后，Android 公共页面与导航已经不能再把 `threadId` 当主主键解释。

### 4. `reply` / `/status` 写路径切主线

#### 目标

复用同一套 `command -> ack -> event -> result` 主线，把 `plain reply` 与 `/status` 从当前 guarded direct/mail closeout 读法切到正式 control-plane 主入口。

#### 本批必须完成

- `reply` 和 `/status` 使用与 `NewTask` 同脊柱的 control-plane sender
- detail 内发送成功后，页面更新不再默认等待 canonical mail
- 成功、拒绝、错误、不可用边界改成 control-plane 读法
- 当前 session target 的 direct evidence 卡逐步退场，避免它继续作为长期主界面契约

#### 本批暂不要求

- 不要求同批扩 quick answer
- 不要求同批扩 attachment continuation
- 不要求同批扩 paused `/resume`

#### 批次验收

完成后，detail 中的 `plain reply` 与 `/status` 不再需要依赖 canonical mail 回流才能构成主流程真相。

### 5. Workbench 收口与 legacy 删除

#### 目标

在写路径与 detail 主链已经切正后，再做首页工作台重排和 legacy seam 删除，避免过早把时间花在“新壳包旧脊柱”上。

#### 本批必须完成

- 首页改成 `session-first + pc-aware`
- `RunTaskMailDirectOrFallback` 等旧 direct/mail 混合 owner seam 进入删除期
- `mail-backed summary` 从产品主界面退出
- `senderAccount / repoPath / workdir` 仅以必要 bridge 或导入边界存在
- 旧 `/relay` new-task sender 不再是公共主流程 owner

#### 本批暂不要求

- 不要求一批里清完全部历史文件
- 不要求同批做所有视觉 polish

#### 批次验收

完成后，Android 公共工作台与公共交互已经整体按 `VPS -> PC / workspace / session / run` 解释，而不是 mail-era thread/product shell。

## 推荐实际分批

如果目标是连续推进并尽量少停顿，建议不要按 5 个细碎提交各自收尾，而是按下面 3 个 campaign 批次组织：

### Batch A

- `1. NewTask 写路径 hard cutover`
- `2. SessionDetail control-plane 承载与缓存收口`

这是第一批必须捆绑的原因：

- 只切写路径，不切 detail，用户仍会觉得结果真相在 mail
- 只切 detail，不切 new task，主创建路径仍在旧链

### Batch B

- `3. route / key hard cutover`
- `4. reply / /status 写路径切主线`

这是第二批必须捆绑的原因：

- route/key 不收口，reply/status 仍会被 thread/mail 身份拖回旧读法
- 只做 route/key 不做 reply/status，公共 session follow-up 主线仍旧未切完

### Batch C

- `5. Workbench 收口与 legacy 删除`

这是最后一批的原因：

- workbench 是主线读法收口，不是第一刀
- 真正删旧必须建立在前两批已经能独立成立之后

## 与现有文档的对应关系

### 与 `taskmail-vps-first-multi-pc-android-skeleton-implementation-plan-v0.1.md`

那份文档继续负责：

- 页面骨架
- DTO 骨架
- 文件级落点

本文额外冻结的是：

- 当目标被收紧为“最快 Android-facing VPS-only”时，owner campaign 应如何跨 slice 重排

### 与 `taskmail-next-session-handoff-2026-03-26.md`

那份 handoff 当前已经明确：

- canonical `new_task` mapper 已有
- 真正 live send 还停在旧 `/relay` compatibility packet

因此本文把 `NewTask 写路径 hard cutover` 放到第一位，不再继续把 mapper-only 视作足够进度。

### 与 `taskmail-next-development-plan-v0.2.md`

总计划继续定义大方向。  
当存在“按骨架顺序推进”和“按 cutover 价值推进”的张力时，**如果当前目标被明确设为尽快切到 Android-facing VPS-only，应以本文为 owner 顺序。**

## 当前明确不优先做的事

在本 campaign 下，下列事项都不应排到前面：

- 继续打磨旧 timeline 外观
- 继续扩大旧 relay evidence card 的可见面积
- 继续为 mail fallback 写新的保活 planning
- 为旧 workspace 首次出现时机单独做 mail-oriented 临时修补
- 先做首页视觉重排，再回头切真正写路径

## 一句话结论

**如果现在的第一目标是尽快切到 Android-facing VPS-only，正确顺序不是“先把所有页面骨架做漂亮”，而是先切 `NewTask` 主提交，再让 `SessionDetail` 吃下 control-plane，然后收 route/key，再切 `reply/status`，最后再重排 workbench 并删旧。**
