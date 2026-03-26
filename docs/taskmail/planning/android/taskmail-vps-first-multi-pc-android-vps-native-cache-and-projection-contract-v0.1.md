# TaskMail VPS-First 多 PC Android VPS-Native 缓存与投影契约（v0.1）

更新时间：2026-03-26

## 状态

本文是 `Batch D` 的窄契约文档。

它回答的问题只有一个：

**当 Android 要把 `workspace/detail` 的用户可见最终结果切成 `VPS-only` 主读法时，本地到底应该缓存什么、UI 应该读什么、`mail` 应该退到哪一层。**

本文不替代：

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/TASKMAIL-MAIL-RULES.md`
- `docs/taskmail/planning/android/taskmail-vps-first-multi-pc-android-vps-only-cutover-campaign-plan-v0.1.md`
- `docs/taskmail/planning/android/taskmail-next-session-handoff-2026-03-26.md`

本文冻结的是 `Batch D` 的：

- 本地缓存职责
- VPS-native projection 读法
- compatibility fallback 边界
- 最小验收口径

## 一句话结论

Android 不应把原始 `VPS` wire event 当成公共 UI 主数据源，也不应继续让 `mail` 做 `workspace/detail` 的真相层。

`Batch D` 的正确落法是：

1. `VPS` 推送进入 Android
2. 先投影成 render-ready 的本地 `workspace/session/detail` 读模型
3. 读模型落本地缓存
4. UI 只读这份缓存
5. `mail` 退成 compatibility fallback / repair / import 边界

## 背景

当前 Android 已具备的能力：

- `new_task` 主写路径已切到 Android-facing `create-session facade`
- detail 已能承接 current-session 的 `/control ack/result`
- detail 已有 direct session observation / projection 骨架

当前 Android 仍未完成的事：

- `workspace/detail` 的用户可见最终结果仍主要由 `mail` cache / repository closeout 提供
- `workspace` 还没有与 detail 对等的 VPS-native projection 主读链
- `mail` rebuild 仍会在刷新链上回收主真相层

因此，`Batch D` 不是单纯“再接几个事件”，而是把 **公共读模型的真相层** 从 `mail` 切到 `VPS-native projection`。

## 目标

`Batch D` 的目标不是页面美化，而是冻结以下三件事：

1. 安卓本地缓存的主对象是什么
2. UI 从哪里读取首屏和增量更新
3. `mail` 还剩哪些合法职责

完成后，`workspace/detail` 应该可以被解释为：

- `VPS` 是主状态输入
- 本地 projection cache 是主渲染输入
- `mail` 只是兼容修复层，不再是默认真相层

## 设计原则

### 1. 主缓存必须是 render-ready 读模型

本地缓存的主要职责是：

- 冷启动快速出首屏
- 前后台切换时快速恢复
- 前台增量更新时避免 UI 重新解释原始 wire payload

因此主缓存应该存：

- 已投影好的 `workspace/session/detail` 结构
- 已降噪、已归一、可直接渲染的字段

而不是把原始 event/result 包直接交给页面现算。

### 2. 不能只存 UI 字段，还要存最小续传元数据

如果只缓存“渲染字段”，一旦断线重连，就无法判断：

- 上次已经处理到哪里
- 哪些 event 已经吃过
- 哪些 result 需要去重
- 从哪个 sequence 继续订阅

因此除了 render-ready 字段，还必须保留最小的同步元数据。

### 3. `mail` 只能做 compatibility fallback

`Batch D` 之后，`mail` 仍可存在，但只能留在这些边界：

- 冷启动兜底导入已有历史会话
- 兼容老 session / 老 thread 的初始修复
- VPS event gap / artifact 缺口时的 repair source
- 对照验证与异常排障

`mail` 不再负责：

- 决定 workspace 是否出现
- 决定 detail 最终状态是否刷新
- 作为 reply/status/new_task 的默认 closeout 真相层

### 4. 公共 UI 只读 projection cache

`workspace` 和 `detail` 的 Compose / ViewModel 不应直接依赖：

- 原始 `VPS` wire event
- 原始 `mail` message rebuild
- 运行时临时 overlay 才能成立的状态

公共 UI 只应该读取：

- `WorkspaceProjection`
- `SessionSummaryProjection`
- `SessionDetailProjection`

这三类本地缓存对象。

## 本地缓存模型

### A. SessionDetailProjection

这是 detail 页的主缓存对象。

建议至少包含：

- `workspaceId`
- `sessionId`
- `threadId`
- `taskId`
- `sessionName`
- `backend`
- `status`
- `lifecycle`
- `pausedFromStatus`
- `repoPath`
- `workdir`
- `lastSummary`
- `lastActiveAt`
- `lastProgressAt`
- `pendingQuestions`
- `replyContext`
- `timeline`
- `controlPlaneSnapshot`

这些字段的特点是：

- 已经是页面直读字段
- 不需要 UI 再理解 wire protocol
- 适合冷启动、切前台和退出重进

### B. WorkspaceProjection

这是 workspace / workbench 的主缓存对象。

建议至少包含：

- `workspaceId`
- `repoPath`
- `workdir`
- `title`
- `subtitle`
- `activeSessionId`
- `sessionCount`
- `sessions`
- `lastUpdatedAt`

其中 `sessions` 应为轻量 `SessionSummaryProjection` 列表，至少包含：

- `workspaceId`
- `sessionId`
- `sessionName`
- `status`
- `lifecycle`
- `backend`
- `lastSummary`
- `lastActiveAt`
- `lastUpdatedAt`
- `pendingQuestion`

workspace 页面不应靠重新扫 mail 才知道 session 是否存在。

### C. ProjectionSyncMetadata

这是 `VPS-native` 缓存必须额外保存的一层最小同步元数据。

建议至少包含：

- `workspaceId`
- `sessionId`
- `threadId`
- `lastSequence`
- `lastEventId` 或同等去重锚点
- `lastResultId` 或同等去重锚点
- `lastProjectionUpdatedAt`
- `subscriptionStatus`
- `dataSource`

`dataSource` 只需要区分：

- `vps_native`
- `mail_compatibility_import`
- `mixed_repair`

它的职责是让系统知道：

- 当前这条 detail/workspace 主要由谁供数
- 断线后从哪里继续
- 当前展示是否仍在兼容修复期

## 必须缓存的内容

`Batch D` 的主缓存必须缓存：

- 已处理后的 session/detail 投影
- 已处理后的 workspace/workbench 投影
- `controlPlaneSnapshot` 的可见结果面
- `lastSequence` 等最小续传元数据

如果 `VPS` 新内容推送到安卓，正确落法应该是：

1. 先更新 projection
2. 再持久化 projection
3. 最后通知 UI 刷新

而不是：

1. 先把原始 event 丢给 UI
2. UI 临时算出状态
3. 再等下一轮 mail / reload 才稳定

## 不应作为主缓存的内容

以下内容不应成为公共主缓存的主体：

- 大量原始 wire packet
- 原始 HTML / MIME body
- 需要每次页面打开时重新解释的未归一 payload
- 仅供 debug 的瞬时 transport 证据

这些内容如果有价值，只能放到：

- debug / observability 存储
- compatibility import 边界
- 异常排障工具

它们不应阻塞首屏渲染，也不应决定页面主状态。

## UI 读法契约

`Batch D` 之后，UI 要遵守下面的读法：

### workspace

- 首屏直接读取本地 `WorkspaceProjection`
- 新 session 出现时先由 VPS projection cache 驱动显示
- `mail` arrival 不再是 workspace 首次出现的必要条件

### detail

- 首屏直接读取本地 `SessionDetailProjection`
- 收到新 event/result 后，优先更新本地 projection cache
- 退出重进后仍读到同一份 projection，而不是重新等 mail 重建

### loading / reconnect

如果订阅断开或 sequence 出现 gap：

- UI 可以显示 `reconnecting / recovering`
- 但页面仍保留最近一次本地 projection
- 不应因为短暂掉线就把 detail/workspace 回滚成空白或 mail-only

## `mail` 的合法职责

`Batch D` 后，`mail` 只保留以下职责：

### 1. 历史导入

对于还没有 VPS-native snapshot 的历史 session，允许从 `mail` 导入初始 detail/workspace。

### 2. gap repair

如果 `VPS` 推送中断、sequence 跳跃、artifact 丢失，允许用 `mail` 作为 repair source。

### 3. 对照校验

在 cutover 初期，允许保留 `mail` 与 VPS projection 的一致性对照能力，用于 validation 和 debug。

### 4. legacy open fallback

当用户打开的是旧 `threadId` 时代的 session，允许用 `mail` 帮助定位历史记录。

除此之外，`mail` 不再对公共页面承担主真相职责。

## 与现有实现的衔接

当前仓内已经有两块可复用资产：

### 1. `TaskSessionDetail` 持久化缓存

现有 `TaskSessionDetailRepository` 与 `FileBackedTaskSessionDetailRepository` 已经能落本地 `detail` 级缓存。

这说明 `Batch D` 不必从零发明“本地 session 缓存”。

真正要做的是：

- 把它从 `mail projector` 主产物升级成 `VPS-native projection` 主产物
- 为 workspace 增加对等的 projection 输入
- 补上 sequence / source / reconnect 元数据

### 2. direct session projection 骨架

现有 `TaskMailDirectSessionProjector` 已经能把 `RelaySessionUpdate` 投影成 `TaskMailDirectSessionProjection`。

这说明 `Batch D` 不必从零发明“direct detail 解释器”。

真正要做的是：

- 把现有 direct projection 正式落到 repository / cache 主链
- 从 detail-only 扩展到 workspace/workbench 可消费的汇总层
- 不再让它只是 mail cache 上面的临时 overlay

## 推荐实现顺序

### 1. 先冻结缓存结构

先决定：

- `SessionDetailProjection` 是否直接复用 `TaskSessionDetail`
- `WorkspaceProjection` 是否复用现有 summary 结构还是新建专用模型
- `ProjectionSyncMetadata` 放哪一层持久化

### 2. 再切 detail 主读链

让 detail 真正以 VPS-native projection cache 为主读，不再每次刷新回退成 mail rebuild。

### 3. 再切 workspace 主读链

让 workspace/session 列表从 projection cache 出数，不再依赖 mail 先到。

### 4. 最后把 mail 降级

把 `mail` 从主读链降成：

- import
- repair
- debug

## 最小验收

`Batch D` 至少要满足下面 5 条：

1. 冷启动时，workspace/detail 能直接从本地 projection cache 出首屏。
2. `VPS` 推来新的 session event/result 后，workspace/detail 能先于 mail 更新。
3. detail 退出重进后，上一轮 `VPS` result / event 仍在本地缓存里。
4. 断线重连时，系统知道从哪个 `lastSequence` 继续，不会每次都全量回放。
5. `mail` 被关闭或延迟时，workspace/detail 仍能在主流程上成立。

## 明确不做的事

本文不要求在 `Batch D` 同时完成：

- 全新的视觉改版
- artifact 下载全闭环
- output chunk 实时直播
- 所有 legacy mail 代码删除干净
- live `pc-control` raw websocket 的最终形态冻结

`Batch D` 的重点只有一个：

**先把 Android 公共读模型的真相层切到 VPS-native projection cache。**

## 一句话结论

如果目标是“先把 `VPS-only` 立稳，再慢慢改 UI”，那 Android 本地应该缓存的不是大堆原始包，而是：

- 可直接渲染的 `workspace/session/detail` 投影
- 外加最小必要的续传与修复元数据

这样才能同时得到：

- 冷启动快
- 页面切换快
- 增量更新稳
- 断线恢复可控
