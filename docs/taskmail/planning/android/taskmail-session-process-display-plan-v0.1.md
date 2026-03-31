# TaskMail Session 过程展示改造方案 v0.1

## 文档定位

本文是本轮 `session detail / history review` 过程展示改造的临时实施方案。

- 目标：把当前“单条 latest progress + 原始过程折叠区”的实现，收口为更符合用户认知的过程展示模型。
- 范围：TaskMail Android `session detail` 首页、`history review` 历史回合页，以及支撑它们的 Android-facing live process contract。
- 不作为长期规则权威。稳定展示规则见 [TASKMAIL-ANDROID-SESSION-PROCESS-DISPLAY-RULES.md](/E:/projects/android_task_manager/docs/TASKMAIL-ANDROID-SESSION-PROCESS-DISPLAY-RULES.md)。

## 背景

当前实现已经接通了 projection-first `live_output`，用户可以在运行中看到实时文本。但现状有 3 个核心问题：

1. `Status` 卡和 `Latest progress` 同时承载当前运行文本，信息重复。
2. 当前运行文本是单个聚合字符串，用户只能看到“最后一条”，看不到 assistant 输出的推进过程。
3. `tool` 调用与 assistant 输出没有被区分，信息密度对当前产品阶段偏高。

此外，`history review` 页面虽然已经有 round 内 `process items`，但一旦展开 round，过程展开策略并没有和首页统一。

## 本轮目标

本轮要实现的目标是：

1. `Status` 不再复述运行中的过程正文。
2. 运行中的过程主视图只展示 `assistant` 输出。
3. 运行中的过程按时间顺序展示多段 assistant 输出，而不是只展示最后一条。
4. 首页与历史页共享同一套“过程默认展开规则”。

## 非目标

本轮暂不做这些：

- 不在主视图直接展示 `tool` 调用明细。
- 不删除 `tool` 数据，也不从协议中抹掉它。
- 不把 `tool` 调用做成新的高级面板或调试页。
- 不在 Android 端通过解析纯文本硬拆 assistant/tool。

## 本轮默认决策

为避免实现阶段再次分叉，本轮默认直接写死以下决策。

### 1. 首页稳定态的 `ProcessSection` 数据源

首页始终保留 `状态 / 结果 / 过程` 三层。

- `Queued / Running`
  - `ProcessSection` 读取 `live_process`
- `WaitingUser / Paused / Done / Failed / Killed`
  - `ProcessSection` 读取“最近一个 stable round 的 process_items”
  - 默认折叠
- 如果既没有 `live_process`，也没有 stable round process，则隐藏整个 `ProcessSection`

### 2. 折叠态摘要

`ProcessSection` 折叠态摘要统一取：

- 最后一条 `assistant` item 的截断文本

补充规则：

- 如果没有 assistant item，但存在 raw process item，则显示 `暂无 assistant 输出`
- 折叠态不展示 tool 明细
- 折叠态不展示 hidden tool 条数

### 3. 顺序责任

process item 的 canonical 顺序由服务端负责。

- relay 必须保证 items 已经是从早到晚的最终顺序
- Android 客户端只按返回顺序渲染
- Android 不自行重排

### 4. `live_process.status` 与 item status 的冻结语义

首轮只冻结两种枚举：

- `streaming`
- `completed`

具体语义：

- 运行中：
  - `live_process.status = streaming`
  - 最后一个活跃 item 可为 `streaming`
- 收到 terminal result：
  - `live_process.status = completed`
  - 所有已有 item 置为 `completed`
- stable round materialize 后：
  - `session_snapshot.live_process = null`
  - 过程转由 history / stable round 承接

### 5. assistant 分段与 item identity

分段规则写死如下：

- 连续同 `kind` 输出合并为同一个 item
- 一旦 `kind` 切换，立即关闭前一个 item 并新开一个 item
- `system` 也算打断点

item identity 规则：

- `item_id` 必须稳定
- 建议由服务端按 segment ordinal 生成确定性 id
- 不使用临时 UUID 作为最终 item identity

### 6. 过滤掉 tool / system 后没有 assistant item 的处理

首页和历史页统一使用同一规则：

- 如果 raw process 存在，但 assistant 过滤后为空，仍显示 `ProcessSection`
- 此时内容显示 `暂无 assistant 输出`
- 如果 raw process 本身也不存在，则隐藏整个 `ProcessSection`

## 目标态信息架构

### 1. 首页 `session detail`

首页应把信息分成 3 层：

- `状态`
- `结果`
- `过程`

规则如下：

- `StatusCard` 只回答：
  - 当前状态
  - 当前轮到谁行动
  - 最近提交 / 最近进展 / 最近活跃时间
- `StatusCard` 不再展示运行中的原始 assistant 文本。
- 取消当前“单条 Latest progress 作为主过程承载”的定位。
- 新增统一的 `ProcessSection`，作为运行过程的唯一主承载区。

`ProcessSection` 在首页的默认行为：

- `Queued / Running`
  - 默认展开
  - 直接展示 assistant 过程流
  - 数据源为 `live_process`
- `WaitingUser / Paused / Done / Failed / Killed`
  - 默认折叠
  - 数据源为“最近一个 stable round 的 process_items”
  - 先显示最后一条 assistant item 的截断摘要，点击后再展开过程
  - 若只有 raw process 而无 assistant item，则显示 `暂无 assistant 输出`
  - 若没有任何 process 数据，则不显示过程区

### 2. 历史页 `history review`

历史页每个 round 仍保留现在的结构：

- Input
- Process
- Result
- Attachments

但 `Process` 的默认展开规则调整为：

- round 状态是 `Queued / Running`
  - 默认展开
- round 状态是 `WaitingUser / Paused / Done / Failed / Killed`
  - 默认折叠

也就是说：

- `round` 本身是否展开，仍由用户控制
- `round` 内的 `process` 是否默认展开，改为由 round 状态决定
- round 折叠态摘要同样取最后一条 assistant item 的截断文本
- 若 raw process 存在但 assistant 过滤后为空，则显示 `暂无 assistant 输出`
- 若 raw process 本身不存在，则不显示该 round 的过程区

## 目标态展示规则

### 1. 主视图只看 assistant

当前产品阶段，首页和历史页的主过程流只展示 `assistant` 项。

这样做的原因：

- 用户当前主要需要看到“系统在怎么思考/生成”
- `tool` 调用密度高，容易压过主线内容
- 当前还没有针对高密度过程信息的完整产品设计

约束：

- `tool` 项不能丢
- `tool` 项应继续保留在 contract / domain / projection 中
- 未来若要提高信息密度，可直接在现有结构上增加展示模式，而不是回炉重做

### 2. 过程按顺序展示

首页与历史页的 assistant 过程都按时间顺序展示。

默认顺序：

- 从早到晚
- 最后一段允许持续增长

顺序责任：

- 服务端输出 canonical 顺序
- 客户端只按返回顺序渲染
- 客户端不自行重排

这样用户能看出“工作是怎么一步步推进的”，而不是只看到一个覆盖后的最终字符串。

### 3. 结果优先，过程按需展开

统一产品原则：

- 运行时：过程直接可见
- 有稳定结果后：结果优先，过程默认折叠

这是首页和历史页必须共享的规则，避免用户在两个页面之间切换时产生认知冲突。

## 当前 contract 不足

当前 Android-facing live output contract 只有：

- `text`
- `updated_at`
- `status`

这只能表达“当前整段文本”，不能表达：

- 多段 assistant 输出
- assistant 与 tool 的区分
- 同一轮过程中 assistant 与 tool 的交替

因此，本轮不建议继续沿用单字符串 `live_output` 做 UI 打补丁。

## 建议 contract 演进

### 1. live_output 升级为 live_process

建议将 Android-facing snapshot 中的运行中过程，从单块文本升级为结构化过程流。

建议目标结构：

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

其中：

- `kind` 首轮至少支持 `assistant / tool / system`
- Android 主视图首轮只渲染 `assistant`

### 2. 历史 round 的 process_items 也补 kind

`live_process.items[]` 与 `history_rounds[].process_items[]` 使用同一套 canonical `ProcessItem` schema。

首轮统一字段至少包括：

- `item_id`
- `kind`
- `text`
- `status`
- `created_at`
- `updated_at`

这样首页与历史页可以复用同一个 process renderer。

## 上游聚合建议

### 1. 以过程项列表为投影目标

relay projection store 不再只输出一条聚合文本，而是输出结构化过程项列表。

### 2. assistant 分段聚合规则

建议规则：

- 连续同 `kind` 输出聚合为同一个过程项
- assistant 文本持续增长时，更新最后一个 assistant 项
- 一旦 `kind` 切换，立即关闭前一个 item 并新开一个 item
- `system` 也算打断点
- tool 数据保留为独立 `kind=tool` 项
- `item_id` 必须稳定，建议服务端按 segment ordinal 生成确定性 id

这样 Android 才能自然展示“assistant 的工作过程”。

## Android 落地建议

### 1. 共享 UI 模型

不要分别在首页和历史页各做一套过程 UI。

建议新增统一模型：

- `TaskProcessItemUi`
- `TaskProcessSectionUi`

建议字段：

- `items`
- `hiddenToolCount`
- `defaultExpanded`
- `showExpandToggle`
- `emptyMessage`

### 2. 首页承接

首页改造方向：

- 删除 `Latest progress` 作为主过程卡的角色
- `StatusCard` 去掉运行中正文复述
- 新增统一 `ProcessSection`

### 3. 历史页承接

历史页改造方向：

- round 级别继续由用户决定是否打开
- round 内 `ProcessFoldCard` 的默认展开值改为按 round 状态决定
- 展开后使用同一套 `ProcessSection` / `ProcessItem` renderer

## 需要修改的主要代码面

### Android

- `feature/taskmail/internal/.../domain/model/TaskSessionLiveOutput.kt`
- `feature/taskmail/internal/.../domain/model/TaskSessionHistorySnapshot.kt`
- `feature/taskmail/internal/.../data/facade/OkHttpTaskSessionHistorySnapshotFacadeRepository.kt`
- `feature/taskmail/internal/.../data/facade/OkHttpTaskSessionUpdatesFacadeRepository.kt`
- `feature/taskmail/internal/.../data/cache/TaskSessionDetailJsonCodec.kt`
- `feature/taskmail/internal/.../ui/detail/TaskSessionDetailViewModel.kt`
- `feature/taskmail/internal/.../ui/detail/TaskSessionDetailUiState.kt`
- `feature/taskmail/internal/.../ui/detail/TaskSessionDetailContent.kt`
- `feature/taskmail/internal/.../ui/history/TaskSessionHistoryContent.kt`
- `feature/taskmail/internal/.../ui/history/TaskSessionHistorySnapshotRoundMapper.kt`

### relay / PC

- `E:/projects/mail_based_task_manager/mail_runner/relay_server/projection_store.py`
- `E:/projects/mail_based_task_manager/mail_runner/relay_server/pc_control_runtime.py`
- `E:/projects/mail_based_task_manager/mail_runner/relay_server/android_projection_store_facade.py`
- Android-facing snapshot / updates contract 文档

## 实施顺序

### 第一阶段：规则收口

1. 正式冻结展示规则
2. 确认首页和历史页共享同一套过程展开规则

### 第二阶段：contract 演进

1. 设计 `live_process`
2. 设计 `process_items.kind`
3. 更新 relay projection / Android-facing snapshot

### 第三阶段：Android domain 与缓存

1. 替换单字符串 `liveOutputText`
2. 接入结构化 `live_process`
3. 更新本地 cache codec

### 第四阶段：UI 重构

1. 首页移除 `Latest progress` 的主过程职责
2. 引入统一 `ProcessSection`
3. 历史页 round 内过程默认展开规则改为按状态决定

## 验收标准

满足以下条件才算完成：

1. 首页 `StatusCard` 不再复述运行中过程正文。
2. 首页运行中可以看到多段 assistant 输出，而不是只有一条最新文本。
3. 首页运行中默认展开过程；有稳定结果后默认折叠。
4. 历史页 round 详情中的过程区也遵守相同规则。
5. 主过程流默认不展示 tool 调用。
6. tool 数据仍保留在 contract / domain 中，没有被删除。

## 与现有文档的关系

- 当前 [taskmail-session-live-output-via-projection-plan-v0.1.md](/E:/projects/android_task_manager/docs/taskmail/planning/android/taskmail-session-live-output-via-projection-plan-v0.1.md) 解决的是“把运行中内容接进 Android”。
- 本文解决的是“接进来以后，以什么形态展示给用户”。

也就是说：

- 前者是“接入”
- 本文是“展示收口”
