# TaskMail Session 过程展示实施清单 v0.1

## 文档定位

本文是 [taskmail-session-process-display-plan-v0.1.md](/E:/projects/android_task_manager/docs/taskmail/planning/android/taskmail-session-process-display-plan-v0.1.md) 的实施清单。

用途：

- 把方案拆成可执行步骤
- 把改动面收敛到文件级
- 明确每一步的验收点

正式展示规则以 [TASKMAIL-ANDROID-SESSION-PROCESS-DISPLAY-RULES.md](/E:/projects/android_task_manager/docs/TASKMAIL-ANDROID-SESSION-PROCESS-DISPLAY-RULES.md) 为准。

## 实施目标

完成后应满足：

1. 首页 `StatusCard` 不再复述运行中过程正文。
2. 首页运行中显示按时间顺序排列的 assistant 过程流。
3. 首页运行中默认展开过程；稳定后默认折叠。
4. 历史页 round 内过程区遵守同一套默认展开规则。
5. 主视图默认不展示 tool 过程，但 tool 数据仍保留。

## 默认决策

本清单按以下默认决策执行：

- 不在 Android 端用字符串解析来猜 assistant/tool。
- process contract 首轮直接升级为结构化 item 列表，不继续扩展单字符串 `live_output`。
- 首页与历史页复用同一套 process section 规则，不各写一套语义。
- `tool` 项默认隐藏，但 contract / domain / projection 中保留。
- 首页稳定态的 `ProcessSection` 读最近一个 stable round 的 `process_items`。
- 折叠态摘要统一显示最后一条 assistant item 的截断文本。
- canonical 顺序由服务端保证，客户端不重排。
- `live_process.status` 与 item status 首轮只使用 `streaming / completed`。
- `live_process.items[]` 与 `history_rounds[].process_items[]` 使用同一套 canonical `ProcessItem` schema。
- 若 raw process 存在但 assistant 过滤后为空，仍显示 `暂无 assistant 输出`；若 raw process 本身不存在，则隐藏整个过程区。

## 阶段 1：冻结 Android-facing contract

### 1.1 新增 live process contract

目标：

- 用 `live_process` 替代“仅单字符串 live output 作为最终展示模型”

建议 contract：

```json
{
  "live_process": {
    "status": "streaming",
    "updated_at": "2026-03-31T10:00:00",
    "items": [
      {
        "item_id": "proc_001",
        "kind": "assistant",
        "text": "I am checking the current implementation.",
        "status": "streaming",
        "created_at": "2026-03-31T09:59:50",
        "updated_at": "2026-03-31T10:00:00"
      }
    ]
  }
}
```

### 1.2 扩展历史 round process item contract

目标：

- `history_rounds[].process_items[]` 与 `live_process.items[]` 使用同一套 canonical `ProcessItem` schema

建议字段：

- `item_id`
- `kind`
- `text`
- `status`
- `created_at`
- `updated_at`
- `status` 首轮枚举冻结为 `streaming / completed`

### 1.3 需要更新的文档

- `E:/projects/mail_based_task_manager/docs/current/android_session_snapshot_facade_contract.md`
- `E:/projects/mail_based_task_manager/docs/current/android_session_updates_facade_contract.md`
- 如有必要，补一份 planning companion doc 到 PC 仓库

### 1.4 阶段验收

- contract 文档中明确：
  - 首页与历史页的 process item 都有 `kind`
  - `live_process` 是结构化列表，不是单字符串
  - 不要求 app 端解析自由文本来区分 item 类型

## 阶段 2：relay / projection 改造

### 2.1 projection store

主要文件：

- `E:/projects/mail_based_task_manager/mail_runner/relay_server/projection_store.py`

需要完成：

- 将 session-scoped 运行中过程从单条 `live_output` owner row 升级为结构化 `live_process`
- 至少能持久化：
  - session 级 process 容器
  - process item 列表
  - item kind
  - item updated_at

建议方向：

- 若当前 row 结构难以自然承载列表，直接新增 session live process item 表
- 不强行把多 item 列表塞回单 row string 字段

### 2.2 runtime 聚合

主要文件：

- `E:/projects/mail_based_task_manager/mail_runner/relay_server/pc_control_runtime.py`

需要完成：

- `output_chunk` 到来后，不再只聚合成“当前整段文本”
- 改为聚合成连续过程项

建议规则：

- 连续同 `kind` 输出合并为同一个 item
- assistant 继续增长时更新最后一个 assistant item
- 一旦 `kind` 切换，立即关闭前一个 item 并新开一个 item
- `system` 也算打断点
- tool 单独保留为 `kind=tool`
- `item_id` 使用稳定、确定性的服务端生成规则，不使用临时 UUID

### 2.3 Android-facing facade builder

主要文件：

- `E:/projects/mail_based_task_manager/mail_runner/relay_server/android_projection_store_facade.py`

需要完成：

- `session_snapshot` 返回 `live_process`
- `history_rounds[].process_items[]` 返回结构化 kind
- 继续保证 snapshot payload 指纹变化能推动 websocket push

### 2.4 阶段验收

- 运行中 snapshot 能返回多个 process items
- 至少可以区分 `assistant` 与 `tool`
- websocket 在 assistant 文本增长时继续推送更新

## 阶段 3：Android domain / repository / cache

### 3.1 domain model

主要文件：

- [TaskSessionLiveOutput.kt](/E:/projects/android_task_manager/feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/model/TaskSessionLiveOutput.kt)
- [TaskSessionHistorySnapshot.kt](/E:/projects/android_task_manager/feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/model/TaskSessionHistorySnapshot.kt)
- [TaskSessionDetail.kt](/E:/projects/android_task_manager/feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/model/TaskSessionDetail.kt)

需要完成：

- 用结构化 process 模型替换单字符串 `TaskSessionLiveOutput`
- 历史 round process item 增加 `kind`

建议新增模型：

- `TaskSessionLiveProcess`
- `TaskSessionProcessItem`
- `TaskSessionProcessItemKind`

### 3.2 facade repository

主要文件：

- [OkHttpTaskSessionHistorySnapshotFacadeRepository.kt](/E:/projects/android_task_manager/feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/data/facade/OkHttpTaskSessionHistorySnapshotFacadeRepository.kt)
- [OkHttpTaskSessionUpdatesFacadeRepository.kt](/E:/projects/android_task_manager/feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/data/facade/OkHttpTaskSessionUpdatesFacadeRepository.kt)

需要完成：

- 解析新的 `live_process`
- 解析带 `kind` 的 `process_items`

### 3.3 local cache codec

主要文件：

- [TaskSessionDetailJsonCodec.kt](/E:/projects/android_task_manager/feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/data/cache/TaskSessionDetailJsonCodec.kt)

需要完成：

- 本地缓存能持久化新的 process 模型
- 避免 app 重启后丢失运行中过程结构

### 3.4 snapshot -> detail mapper

主要文件：

- [TaskSessionHistorySnapshotDetailMapper.kt](/E:/projects/android_task_manager/feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/data/facade/TaskSessionHistorySnapshotDetailMapper.kt)

需要完成：

- 将 snapshot 中的新 process 模型映到 `TaskSessionDetail`
- 不再把运行态只收口成单个 `liveOutputText`

### 3.5 阶段验收

- Android domain 能拿到结构化运行过程
- 缓存恢复后仍能读到 process items
- 首页与历史页都能使用同一套 process 数据

## 阶段 4：UI state 与共享过程组件

### 4.1 新增共享 UI 模型

主要文件：

- [TaskSessionDetailUiState.kt](/E:/projects/android_task_manager/feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/TaskSessionDetailUiState.kt)

需要完成：

- 引入共享的 process section UI state

建议新增：

- `TaskProcessItemUi`
- `TaskProcessSectionUi`

建议字段：

- `visibleItems`
- `hiddenToolCount`
- `defaultExpanded`
- `isExpandable`
- `title`
- `supportingText`

### 4.2 ViewModel 映射

主要文件：

- [TaskSessionDetailViewModel.kt](/E:/projects/android_task_manager/feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/TaskSessionDetailViewModel.kt)

需要完成：

- 首页 `ActiveRun` 时构建 session 级 process section
- 历史页 round detail 构建 round 级 process section
- 过滤规则：
  - 主视图只输出 `assistant`
  - 记录 `hiddenToolCount`
  - 若 raw process 存在但 assistant 过滤后为空，生成 `暂无 assistant 输出`

### 4.3 过程区默认展开规则

统一规则函数建议集中定义：

- `Queued / Running -> expanded`
- `WaitingUser / Paused / Done / Failed / Killed -> collapsed`

不要让首页和历史页各自写一份独立判断。

### 4.4 阶段验收

- UI state 层已经不再依赖单个 `liveOutputText` 做完整过程展示
- 首页与历史页共享同一套默认展开逻辑

## 阶段 5：首页 UI 重构

### 5.1 StatusCard 去重

主要文件：

- [TaskSessionDetailContent.kt](/E:/projects/android_task_manager/feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/TaskSessionDetailContent.kt)
- [TaskSessionStatusCard.kt](/E:/projects/android_task_manager/feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/component/TaskSessionStatusCard.kt)

需要完成：

- `StatusCard` 不再展示运行中过程正文
- 保留：
  - headline
  - actor hint
  - submission / timing rows

### 5.2 移除 `Latest progress` 的主过程职责

主要文件：

- [TaskLatestProgressCard.kt](/E:/projects/android_task_manager/feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/component/TaskLatestProgressCard.kt)
- [TaskSessionDetailContent.kt](/E:/projects/android_task_manager/feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/TaskSessionDetailContent.kt)

需要完成：

- 不再把“单条最新进展”作为首页主过程展示
- 可以删除该卡，也可以将其重构为新的 `ProcessSection`

建议：

- 直接用统一 `ProcessSection` 取代

### 5.3 运行态过程区

需要完成：

- `ActiveRun` 首页默认展开过程区
- 按时间顺序展示 assistant items
- 若当前没有 assistant item，显示空态文案

### 5.4 稳定状态过程区

需要完成：

- `WaitingUser / Paused / Terminal` 默认折叠
- 折叠态显示：
  - 最后一条 assistant item 的截断文本
  - 若无 assistant item 但 raw process 存在，则显示 `暂无 assistant 输出`
  - 不显示 tool 明细
  - 不显示 hidden tool 条数

### 5.5 阶段验收

- 首页不再出现 `Status + Latest progress` 双重复述
- 运行中能看见连续 assistant 过程
- 已有结果时过程默认折叠

## 阶段 6：历史页 UI 重构

### 6.1 round 展开与 process 展开分离

主要文件：

- [TaskSessionHistoryContent.kt](/E:/projects/android_task_manager/feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/history/TaskSessionHistoryContent.kt)

需要完成：

- 保留用户控制 round 是否展开
- round 内 process 是否默认展开，改为由 round 状态决定
- round 折叠态摘要与首页保持一致

### 6.2 process renderer 统一

主要文件：

- [TaskSessionHistorySnapshotRoundMapper.kt](/E:/projects/android_task_manager/feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/history/TaskSessionHistorySnapshotRoundMapper.kt)
- 共享 process section composable 文件

需要完成：

- round 内 process items 过滤成 assistant 主流
- 按服务端返回顺序展示，不自行重排
- 保留隐藏 tool 数量

### 6.3 默认展开规则

需要完成：

- 历史页 round 内 process 默认展开规则与首页一致

### 6.4 阶段验收

- 历史页 round 打开后，运行中的 round 过程直接可见
- 稳定 round 的过程默认折叠
- 首页与历史页的过程规则一致

## 阶段 7：测试

### 7.1 Android 单测 / UI 测试

优先补这些：

- snapshot facade repository 对新 contract 的解析测试
- updates facade repository 对新 contract 的解析测试
- `TaskSessionDetailJsonCodec` round-trip 测试
- `TaskSessionDetailViewModel`：
  - assistant 过滤
  - hidden tool count
  - 默认展开规则
- `TaskSessionDetailScreenKtTest`
  - 运行态默认展开
  - 稳定态默认折叠
  - `StatusCard` 不再出现重复正文
- `TaskSessionHistory` 相关 screen test
  - round 内 process 默认折叠/展开规则

### 7.2 relay / PC 测试

优先补这些：

- projection store 持久化多 item process
- runtime 聚合 assistant/tool item 的测试
- session snapshot / updates facade 返回 `live_process`
- websocket 在 process items 增长时继续推送

## 建议开发顺序

按最稳的顺序建议这样推进：

1. 冻结 contract
2. 改 relay projection / snapshot facade
3. 改 Android domain / repository / codec
4. 改首页 UI
5. 改历史页 UI
6. 补测试

## 完成定义

满足以下条件才算这条改造闭环：

1. 首页与历史页都改成“运行中默认展开、稳定后默认折叠”。
2. 首页与历史页主过程流都只看 assistant。
3. process 已经是结构化 items，不再只依赖单字符串 live output。
4. tool 数据仍保留，没有被丢弃。
5. 相关 contract、实现、缓存、测试已同步更新。
