# TaskMail VPS-First 多 PC Session 骨架 Slice 1 文件计划（v0.1）

更新时间：2026-03-25

## 状态

本文是 `taskmail-vps-first-multi-pc-android-skeleton-implementation-plan-v0.1.md` 中 `Slice 1：Session 页骨架先落地` 的文件级实施计划。

它依附于以下文档：

- `docs/taskmail/planning/android/taskmail-vps-first-multi-pc-user-requirements-authority-v0.1.md`
- `docs/taskmail/planning/android/taskmail-vps-first-multi-pc-viewstate-action-mapping-v0.1.md`
- `docs/taskmail/planning/android/taskmail-vps-first-multi-pc-compose-screen-structure-v0.1.md`
- `docs/taskmail/planning/android/taskmail-vps-first-multi-pc-android-skeleton-implementation-plan-v0.1.md`

本文回答的问题是：

**如果现在就开始写 Kotlin，`TaskSessionDetail` 这一刀应该先改哪些文件、先长哪些字段、先补哪些测试。**

## Slice 1 的目标

这一步的目标不是把 Session 页完全切成 VPS-first。

这一步只要求做到：

1. 主页结构从“header + workspace + pending question + reply + timeline”扩成“状态 + 最近上下文 + 直播 + 结果 + 文件 + follow-up”
2. 历史上下文层开始有状态承载和 UI 入口
3. 结果区与文件区在代码里有明确落点
4. 旧 timeline、pending question、reply 仍可继续工作

## 这一步的非目标

- 不要求正式接入 PC Phase 1 API
- 不要求删除旧 timeline
- 不要求删除 direct evidence card
- 不要求引入新 route
- 不要求一次性把 ViewModel 改成新的 projector 体系

## 文件级改动范围

### 必改文件

- `feature/taskmail/internal/ui/detail/TaskSessionDetailContract.kt`
- `feature/taskmail/internal/ui/detail/TaskSessionDetailUiState.kt`
- `feature/taskmail/internal/ui/detail/TaskSessionDetailContent.kt`

### 优先演进的既有组件

- `feature/taskmail/internal/ui/detail/component/SessionHeader.kt`
- `feature/taskmail/internal/ui/detail/component/TaskStateCard.kt`
- `feature/taskmail/internal/ui/detail/component/TaskReplyComposer.kt`
- `feature/taskmail/internal/ui/detail/component/PendingQuestionCard.kt`
- `feature/taskmail/internal/ui/detail/component/TimelineList.kt`

### 建议新增组件

- `feature/taskmail/internal/ui/detail/component/RecentContextCard.kt`
- `feature/taskmail/internal/ui/detail/component/ResultSummaryCard.kt`
- `feature/taskmail/internal/ui/detail/component/ArtifactSection.kt`
- `feature/taskmail/internal/ui/detail/component/HistoryContextSheet.kt`
- `feature/taskmail/internal/ui/detail/component/HistoryRoundCard.kt`

## 1. `TaskSessionDetailContract.kt`

### 当前问题

当前 `State` 顶层只承载：

- loading / refresh
- draft / send
- latest direct evidence
- attachments
- `detail`

这会让新结构继续挤进 `detail` 或继续堆在顶层。

### 本次目标

在不破坏现有调用点的前提下，把 `State` 长成可容纳新 Session 工作面的骨架。

### 建议新增字段

- `isHistoryVisible: Boolean = false`
- `historyLoadError: String? = null`
- `historyPreview: ImmutableList<TaskHistoryRoundUi> = persistentListOf()`

### 建议新增事件

- `HistoryEntryClicked`
- `HistoryDismissed`
- `HistoryRoundExpanded(val roundId: String)`
- `HistoryRoundCollapsed(val roundId: String)`

### 这一步不建议做的事

- 不要把历史层打开/关闭做成 effect
- 不要现在就引入新的 route 参数

## 2. `TaskSessionDetailUiState.kt`

### 当前问题

当前 `TaskSessionDetailUiState` 仍是旧 detail page 读法：

- `backend`
- `status`
- `repoPath`
- `lastSummary`
- `pendingQuestions`
- `timeline`

缺少：

- 状态卡模型
- 最近上下文模型
- 结果模型
- artifact 模型

### 本次目标

把 `TaskSessionDetailUiState` 从单块 detail 对象拆成可扩展的嵌套 UI model。

### 建议新增嵌套 UI model

- `TaskSessionHeaderUi`
- `TaskSessionStatusUi`
- `TaskRecentContextUi`
- `TaskResultSummaryUi`
- `TaskArtifactItemUi`
- `TaskHistoryRoundUi`

### 建议的阶段性兼容策略

第一步不要求删掉旧字段。

更稳的做法是：

1. 先新增新的嵌套 UI model 字段
2. 旧字段短期继续保留
3. `TaskSessionDetailContent` 逐步改读新字段
4. 最后再删旧字段

### 推荐新增字段

- `header: TaskSessionHeaderUi`
- `statusCard: TaskSessionStatusUi`
- `recentContext: TaskRecentContextUi? = null`
- `resultSummary: TaskResultSummaryUi? = null`
- `artifacts: ImmutableList<TaskArtifactItemUi> = persistentListOf()`

### `TaskRecentContextUi` 最少字段

- `lastUserActionSummary`
- `latestConclusionSummary`
- `awaitingUserInputHint`
- `historyEntryLabel`

### `TaskResultSummaryUi` 最少字段

- `finalStatus`
- `summary`
- `effectiveExecutionSummary`
- `isTerminal`

### `TaskArtifactItemUi` 最少字段

- `artifactId`
- `name`
- `contentType`
- `sizeLabel`
- `isOpenAvailable`
- `isDownloadAvailable`

### `TaskHistoryRoundUi` 最少字段

- `roundId`
- `title`
- `userInputSummary`
- `resultSummary`
- `isExpanded`
- `streamPreview`

## 3. `TaskSessionDetailContent.kt`

### 当前结构

当前 loaded content 的顺序是：

1. `SessionHeader`
2. `TaskStateCard`
3. `PendingQuestionCard`
4. `TaskReplyComposer`
5. `timeline`

### 本次目标结构

第一步建议改成：

1. `SessionHeader`
2. `TaskStateCard`
3. `RecentContextCard`
4. `PendingQuestionCard`
5. `TaskReplyComposer`
6. `Timeline`
7. `ResultSummaryCard`
8. `ArtifactSection`
9. `HistoryContextSheet`

### 为什么顺序这样定

- 先保留现有 header 和状态卡，降低风险
- 把最近上下文插到 reply 前，让用户先找回当前态
- reply 保持在中上部，不必先大改交互习惯
- 结果与文件先落在 timeline 后，先把结构位占住
- 历史层作为 overlay/sheet，不打断主列表

### 具体改法建议

#### `overviewItems`

当前 `overviewItems()` 可先演进为：

- `SessionHeader`
- `TaskStateCard`
- `RecentContextCard`
- 可选 `PendingQuestionCard`

#### `replyItem`

短期保留不动，只做文案轻调即可。

#### `timelineItems`

短期保留：

- section title
- timeline list

但要把它明确降成“过程直播 / 旧 timeline 区”，而不是整个页面真相。

#### 新增 `resultItem`

当 `detail.resultSummary != null` 时渲染 `ResultSummaryCard`。

#### 新增 `artifactItem`

当 `detail.artifacts.isNotEmpty()` 时渲染 `ArtifactSection`。

#### 新增 `HistoryContextSheet`

由 `state.isHistoryVisible` 驱动显示。

## 4. 组件级任务拆分

### `SessionHeader.kt`

本次只做轻量演进：

- 保持 header identity
- 可增加 `session id / workspace / pc` 的轻量 badge 位

不建议这一刀就做大改。

### `TaskStateCard.kt`

当前更像 “workspace + latest summary”。

本次建议：

- 保留 workspace 信息
- 增加更明确的状态主标题位
- 为后续 `effectiveExecutionSummary` 预留一行摘要位

### `RecentContextCard.kt`

这是本 slice 新增重点组件。

最少应展示：

- 上一轮用户输入摘要
- 最近一次系统结论
- 当下是否在等用户
- `查看前续` 按钮

### `ResultSummaryCard.kt`

最少应展示：

- 结果状态
- summary
- 实际执行摘要

### `ArtifactSection.kt`

最少应展示：

- 文件名
- 类型或大小
- 打开 / 下载入口占位

### `HistoryContextSheet.kt`

最少应展示：

- 历史标题
- round 列表
- 每个 round 的摘要
- 展开 / 折叠状态

第一步不要求做复杂交互动画。

## 5. ViewModel 最小支撑要求

虽然本 slice 主要先动 UI 骨架，但 `TaskSessionDetailViewModel` 至少要支撑：

- 打开历史层
- 关闭历史层
- 提供一组假的或由现有 detail 派生的 `historyPreview`
- 提供 `recentContextSummary`
- 提供一个最小 `resultSummary`

### 推荐的过渡来源

在正式 control-plane projector 还没落地前，可以先从现有 `detail.timeline + lastSummary + pendingQuestions` 派生：

- `recentContext`
- `historyPreview`
- `resultSummary`

这样能先把 UI 结构长出来，而不用等 Phase 1 API。

## 6. 测试改动范围

### 必跟的现有测试

- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/TaskSessionDetailScreenKtTest.kt`
- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/TaskSessionDetailViewModelTest.kt`
- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/TaskSessionDetailUiStateStructuredReplyValidationTest.kt`

### 建议新增或补点的测试方向

#### Screen test

至少新增：

- 最近上下文卡片可见
- 点击 `查看前续` 后历史层显示
- 结果区出现时可滚动看到
- artifact 区出现时可滚动看到

#### ViewModel test

至少新增：

- `HistoryEntryClicked` 使 `isHistoryVisible=true`
- `HistoryDismissed` 使 `isHistoryVisible=false`
- 现有 detail 能派生出非空 recent context

#### UiState test

至少新增：

- `recentContext` 缺省时页面仍可工作
- `resultSummary` 缺省时不影响 reply / timeline 现有逻辑

## 7. 验收标准

当 Slice 1 完成时，应满足：

- Session 页主列表中已出现 `RecentContextCard`
- 历史上下文层可通过 state 打开
- `ResultSummaryCard` 和 `ArtifactSection` 有明确渲染位置
- 旧 reply / pending question / timeline 仍可工作
- 现有 detail 测试经过更新后仍能稳定跑通

## 8. 一句话结论

**Slice 1 的正确做法不是重写整个 Session 页，而是在现有 `TaskSessionDetail` 结构上，先把“最近上下文 / 历史层 / 结果 / 文件”四块骨架稳稳插进去。**
