# TaskMail Android Phase 1 规划

## 1. 目的

这份文档把现有 TaskMail 规则清单进一步落成 Android 侧的实现规划，重点回答三个问题：

- Android 侧需要哪些数据模型
- 页面应该如何分层
- Phase 1 具体要新建和修改哪些文件

本文档与以下文档配套阅读：

- `docs/taskmail_project_overview.md`
- `docs/taskmail-phase0-research.md`
- `docs/TASKMAIL-MAIL-RULES.md`

如果本文档与后端当前邮件协议冲突，仍以 `docs/TASKMAIL-MAIL-RULES.md` 记录的规则为准。

---

## 2. Phase 1 范围

Phase 1 只做 **TaskMail Android 的最小可落地骨架**，不追求完整功能闭环。

本阶段目标：

- 建立 `feature:taskmail:api` 和 `feature:taskmail:internal`
- 定义 Android 侧使用的核心模型
- 建立邮件状态块解析链
- 建立 `workspace -> session` 的 UI 结构
- 做静态页面、Preview、假数据和单元测试

本阶段明确不做：

- 抽屉入口接线
- 与真实邮件仓库的完整数据桥接
- 复用普通 message list / message view
- Markdown 正式渲染
- 回复发信与 slash command 执行

这些内容应后置到：

- `Phase 1.5`：真实数据桥接
- `Phase 2`：抽屉入口和实际导航接线
- `Phase 3+`：详情增强、Markdown、回复动作

---

## 3. 设计原则

- TaskMail 首页不是扁平邮件列表，而是 `workspace -> session` 树视图
- `workspace_id` 和 `session_id` 是主分组标识
- `thread_id` 必须保留，但不作为唯一 UI 主键
- `backend_session_id` 只作后端 resume 辅助，不进入 UI 主分组
- 新功能放在 `feature:taskmail:*`，不要把新逻辑塞进 `legacy:*`
- 新 UI 使用 Compose + MVI，并优先使用现有设计系统能力

---

## 4. 模块边界

## 4.1 `:feature:taskmail:api`

`api` 模块保持很薄，只放跨模块稳定契约：

- `TaskMailRoute`
- `TaskMailNavigation`
- 未来可选的 `TaskMailLauncher`

`api` 模块当前不应该承载大批 TaskMail DTO。  
原因是 TaskMail 还在收敛期，模型更适合先放在 `internal` 里迭代。

## 4.2 `:feature:taskmail:internal`

`internal` 模块承载真正实现：

- 邮件规则映射
- 状态块解析
- Android 领域模型
- UI 状态与界面
- 假数据与 Preview
- 未来的数据仓库实现

---

## 5. 数据模型清单

## 5.1 输入层模型

这些模型对应“从邮件世界进入 TaskMail”的第一层数据。

| 模型 | 用途 | 备注 |
| --- | --- | --- |
| `TaskMailEnvelope` | Android 侧读取到的一封原始邮件 | 可以先做 feature 内部模型，不急着公开 |
| `TaskMailParsedSubject` | 主题解析结果 | 包含 backend、status label、subject text、`[S:session_id]` |
| `TaskStateCapsule` | `TASK-STATE` 解析结果 | 对应 `workspace_id`、`session_id`、`status` 等 |
| `TaskQuestionCapsule` | `TASK-QUESTION` 解析结果 | 用于等待回答态 |

建议字段：

### `TaskMailEnvelope`

- `messageId: String`
- `subject: String`
- `fromAddress: String`
- `timestamp: Long`
- `inReplyTo: String?`
- `references: List<String>`
- `plainTextBody: String`

### `TaskMailParsedSubject`

- `backend: TaskMailBackend?`
- `statusLabel: TaskMailStatusLabel?`
- `sessionIdFromSubject: String?`
- `subjectText: String`
- `isReplyLike: Boolean`

### `TaskStateCapsule`

- `threadId: String`
- `workspaceId: String?`
- `sessionId: String?`
- `sessionName: String?`
- `taskId: String?`
- `backend: TaskMailBackend?`
- `repoPath: String?`
- `workdir: String?`
- `mode: String?`
- `status: TaskMailSessionStatus?`
- `lastSummary: String?`

### `TaskQuestionCapsule`

- `questionId: String`
- `questionText: String`
- `choices: List<String>`

## 5.2 领域层模型

这些模型是 TaskMail UI 真正消费的稳定形态。

| 模型 | 用途 |
| --- | --- |
| `TaskWorkspaceKey` | `workspace_id` 或 `repo_path + workdir` 的稳定 key |
| `TaskSessionKey` | `session_id` 或 `thread_id` 的稳定 key |
| `TaskWorkspaceSummary` | 首页一级节点 |
| `TaskSessionSummary` | 首页二级节点 |
| `TaskSessionDetail` | 详情页主体 |
| `TaskTimelineItem` | 详情页时间线项 |
| `TaskMessageBody` | 详情页消息正文数据 |

建议字段：

### `TaskWorkspaceKey`

- `workspaceId: String?`
- `repoPath: String`
- `workdir: String?`

### `TaskSessionKey`

- `sessionId: String?`
- `threadId: String`

### `TaskWorkspaceSummary`

- `key: TaskWorkspaceKey`
- `title: String`
- `subtitle: String?`
- `backendSet: Set<TaskMailBackend>`
- `activeSessionId: String?`
- `sessionCount: Int`
- `sessions: List<TaskSessionSummary>`

### `TaskSessionSummary`

- `key: TaskSessionKey`
- `sessionName: String`
- `status: TaskMailSessionStatus`
- `backend: TaskMailBackend`
- `lastSummary: String?`
- `lastUpdatedAt: Long`
- `pendingQuestion: Boolean`

### `TaskSessionDetail`

- `key: TaskSessionKey`
- `workspace: TaskWorkspaceKey`
- `sessionName: String`
- `backend: TaskMailBackend`
- `status: TaskMailSessionStatus`
- `repoPath: String`
- `workdir: String?`
- `lastSummary: String?`
- `question: TaskQuestionCapsule?`
- `timeline: List<TaskTimelineItem>`

### `TaskTimelineItem`

- `id: String`
- `timestamp: Long`
- `direction: TaskTimelineDirection`
- `statusLabel: TaskMailStatusLabel?`
- `summary: String?`
- `body: TaskMessageBody`

### `TaskMessageBody`

- `plainText: String`
- `markdownCandidate: Boolean`

## 5.3 UI 层模型

UI 层可以再做轻量投影，避免 Compose 直接吃领域对象：

- `TaskWorkspaceUiState`
- `TaskWorkspaceItemUi`
- `TaskSessionItemUi`
- `TaskSessionDetailUiState`
- `TaskTimelineItemUi`

这样后续真实数据接进来时，领域模型和 UI 结构仍然可以各自演进。

## 5.4 枚举和值对象

建议在 Phase 1 一次性固定这些基础类型：

- `TaskMailBackend`
  - `OpenCode`
  - `Codex`
- `TaskMailStatusLabel`
  - `Accepted`
  - `Running`
  - `Done`
  - `Failed`
  - `Status`
  - `Killed`
  - `Question`
- `TaskMailSessionStatus`
  - `Queued`
  - `Running`
  - `WaitingUser`
  - `Done`
  - `Failed`
  - `Killed`
  - `Unknown`
- `TaskTimelineDirection`
  - `Incoming`
  - `Outgoing`
  - `System`

---

## 6. 页面树

TaskMail 作为一个独立 feature，页面树建议如下：

```text
TaskMail
├─ TaskWorkspaceRoute
│  └─ TaskWorkspaceScreen
│     ├─ WorkspaceHeader
│     ├─ WorkspaceCard
│     ├─ SessionRow
│     ├─ LoadingState
│     ├─ EmptyState
│     └─ ErrorState
└─ TaskSessionDetailRoute
   └─ TaskSessionDetailScreen
      ├─ SessionHeader
      ├─ TaskStateCard
      ├─ PendingQuestionCard
      ├─ TimelineList
      ├─ TimelineMessageCard
      └─ PlainTextBody
```

## 6.1 首页：`TaskWorkspaceScreen`

首页不是“所有任务列表”，而是“工作树”：

- 一级：`workspace`
- 二级：`session`

首页职责：

- 展示 `repo + workdir`
- 展示该工作区下的 session 列表
- 显示 session 当前状态、后端和摘要
- 进入详情页

首页暂不负责：

- 直接回复
- 直接 kill / rerun
- 打开普通邮件详情

## 6.2 详情页：`TaskSessionDetailScreen`

详情页职责：

- 展示 session 的当前状态
- 展示最近摘要
- 展示等待回答态
- 展示 timeline
- 先做 plain text 正文呈现

详情页暂不负责：

- Markdown 富渲染
- 回复编辑器
- slash command 快捷操作

## 6.3 导航结构

建议路由只保留两层：

- `workspace`
- `sessionDetail(sessionKey)`

不要在 Phase 1 里引入复杂的三级嵌套路由。

---

## 7. Phase 1 文件清单

下面的清单是“按当前仓库结构，最适合直接开工的文件路径”。

## 7.1 需要修改的现有文件

### 必改

- `settings.gradle.kts`
  - 加入 `:feature:taskmail:api`
  - 加入 `:feature:taskmail:internal`

### 当前阶段不改

- `legacy/ui/legacy/src/main/java/com/fsck/k9/activity/MessageHomeActivity.kt`
- `feature/navigation/drawer/dropdown/...`
- `app-common/...`

这些接线动作留到 `Phase 2`。

## 7.2 新建 `:feature:taskmail:api`

建议新增：

- `feature/taskmail/api/build.gradle.kts`
- `feature/taskmail/api/src/main/kotlin/net/thunderbird/feature/taskmail/TaskMailRoute.kt`
- `feature/taskmail/api/src/main/kotlin/net/thunderbird/feature/taskmail/TaskMailNavigation.kt`

可选：

- `feature/taskmail/api/src/test/kotlin/...`

## 7.3 新建 `:feature:taskmail:internal`

建议新增：

- `feature/taskmail/internal/build.gradle.kts`

### `domain/model`

- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/model/TaskMailBackend.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/model/TaskMailStatusLabel.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/model/TaskMailSessionStatus.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/model/TaskTimelineDirection.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/model/TaskWorkspaceKey.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/model/TaskSessionKey.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/model/TaskWorkspaceSummary.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/model/TaskSessionSummary.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/model/TaskSessionDetail.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/model/TaskTimelineItem.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/model/TaskMessageBody.kt`

### `domain/parser`

- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/parser/TaskMailEnvelope.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/parser/TaskMailParsedSubject.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/parser/TaskStateCapsule.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/parser/TaskQuestionCapsule.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/parser/TaskMailSubjectParser.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/parser/TaskStateCapsuleParser.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/parser/TaskQuestionCapsuleParser.kt`

### `domain/repository`

- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/repository/TaskMailRepository.kt`

### `ui/workspace`

- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/workspace/TaskWorkspaceContract.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/workspace/TaskWorkspaceScreen.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/workspace/TaskWorkspaceUiState.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/workspace/component/WorkspaceCard.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/workspace/component/SessionRow.kt`

### `ui/detail`

- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/TaskSessionDetailContract.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/TaskSessionDetailScreen.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/TaskSessionDetailUiState.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/component/TaskStateCard.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/component/PendingQuestionCard.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/component/TimelineMessageCard.kt`

### `navigation`

- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/navigation/TaskMailNavHost.kt`

### `preview`

- `feature/taskmail/internal/src/debug/kotlin/net/thunderbird/feature/taskmail/internal/ui/workspace/TaskWorkspacePreview.kt`
- `feature/taskmail/internal/src/debug/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/TaskSessionDetailPreview.kt`

### `test`

- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/domain/parser/TaskMailSubjectParserTest.kt`
- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/domain/parser/TaskStateCapsuleParserTest.kt`
- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/domain/parser/TaskQuestionCapsuleParserTest.kt`

## 7.4 Phase 1.5 再新增的文件

等进入真实数据桥接时，再补这些文件会更稳：

- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/data/LegacyTaskMailMessageSource.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/data/LegacyTaskMailBodyExtractor.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/data/DefaultTaskMailRepository.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/di/TaskMailModule.kt`

详细开工清单见：

- `docs/TASKMAIL-ANDROID-PHASE1-5.md`

---

## 8. 推荐实施顺序

1. 增加模块和 Gradle wiring
2. 先写 parser 与 model
3. 先把 `workspace -> session` 假数据组装出来
4. 再写静态 Compose 页面与 Preview
5. 最后补 Phase 1 单元测试

这个顺序能保证我们先把协议和模型钉住，再去做 UI。

---

## 8.1 单线程推进版执行清单

下面这份清单用于 **单线程连续推进**。原则是只维护一条上下文链路：

`协议规则 -> parser -> domain model -> fake repository -> ViewModel -> UI -> navigation -> test`

这样可以减少“UI 先写了、模型又回头改”的返工。

### Step 0：先补文档并冻结范围

- [ ] 在开始编码前，先更新本文档中的文件清单、实施顺序和测试点
- [ ] 明确 Phase 1 只做 `feature:taskmail:api` 与 `feature:taskmail:internal`
- [ ] 明确不接 `app-common`、不改抽屉入口、不改 `MessageHomeActivity`
- [ ] 明确不做真实邮件桥接、Markdown 渲染、回复发信、slash command

完成标准：

- 本文档可以直接作为 Phase 1 开工说明
- 之后实现中如果发生偏差，只回写发生变化的部分

### Step 1：建立模块骨架

- [ ] 修改 `settings.gradle.kts`，加入 `:feature:taskmail:api`
- [ ] 修改 `settings.gradle.kts`，加入 `:feature:taskmail:internal`
- [ ] 新建 `feature/taskmail/api/build.gradle.kts`
- [ ] 新建 `feature/taskmail/internal/build.gradle.kts`
- [ ] 新建 `TaskMailRoute`
- [ ] 新建 `TaskMailNavigation`

实施要点：

- `api` 模块只放稳定导航契约
- `internal` 模块承载 parser、model、UI、假数据、Preview
- 包名遵循 ADR-0009，使用 `net.thunderbird.feature.taskmail.internal...`

测试点：

- 模块能被 Gradle 正确识别
- `api` 不依赖 `internal`
- `internal` 只依赖允许的 `api/core` 模块

### Step 2：完成协议输入层与 parser

- [ ] 新建 `TaskMailEnvelope`
- [ ] 新建 `TaskMailParsedSubject`
- [ ] 新建 `TaskStateCapsule`
- [ ] 新建 `TaskQuestionCapsule`
- [ ] 新建 `TaskMailSubjectParser`
- [ ] 新建 `TaskStateCapsuleParser`
- [ ] 新建 `TaskQuestionCapsuleParser`

实施要点：

- 主题解析支持 `[OC]`、`[CX]`
- 状态主题支持 `[STATUS_LABEL][S:session_id]`
- reply 前缀兼容 `Re:`、`Fwd:`、`回复:`、`答复:`
- 状态块与问题块都只解析最后一个完整块

测试点：

- [ ] `TaskMailSubjectParserTest` 覆盖新任务主题、状态主题、reply 前缀、非法主题回退
- [ ] `TaskStateCapsuleParserTest` 覆盖完整块、不完整块、多块取最后一个、多行压平
- [ ] `TaskQuestionCapsuleParserTest` 覆盖 `choices` 拆分、无块返回 `null`、多块取最后一个

### Step 3：固定领域模型与分组规则

- [ ] 新建基础枚举和值对象
- [ ] 新建 `TaskWorkspaceKey`
- [ ] 新建 `TaskSessionKey`
- [ ] 新建 `TaskWorkspaceSummary`
- [ ] 新建 `TaskSessionSummary`
- [ ] 新建 `TaskSessionDetail`
- [ ] 新建 `TaskTimelineItem`
- [ ] 新建 `TaskMessageBody`
- [ ] 新建领域 mapper

建议新增文件：

- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/mapper/TaskWorkspaceSummaryMapper.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/mapper/TaskSessionDetailMapper.kt`

实施要点：

- `workspace` 主键优先用 `workspace_id`
- 缺少 `workspace_id` 时退化到 `repo_path + workdir`
- `session` 主键优先用 `session_id`
- 缺少 `session_id` 时退化到 `thread_id`
- `backend_session_id` 不进入 UI 主键

测试点：

- [ ] workspace 分组正确
- [ ] session 分组正确
- [ ] timeline 按邮件时间排序
- [ ] `pendingQuestion` 判定正确
- [ ] status label 到 session status 的映射正确

### Step 4：补 fake repository 和 use case

- [ ] 新建 `TaskMailRepository`
- [ ] 新建 `GetTaskWorkspaceSummaries`
- [ ] 新建 `GetTaskSessionDetail`
- [ ] 新建 fake repository
- [ ] 新建 Preview/Fake data

建议新增文件：

- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/data/fake/InMemoryTaskMailRepository.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/preview/TaskMailPreviewData.kt`

实施要点：

- Phase 1 不接真实邮件仓库
- 先保证 parser -> mapper -> repository -> use case 这条链可以跑通
- UI 不直接依赖零散假对象，统一通过 fake repository 或 preview data 供给

测试点：

- [ ] `GetTaskWorkspaceSummariesTest` 覆盖 workspace/session 树组装
- [ ] `GetTaskSessionDetailTest` 覆盖 detail、timeline、question 聚合
- [ ] 空数据场景可支持 empty state

### Step 5：实现 Workspace 页 ViewModel

- [ ] 新建 `TaskWorkspaceContract`
- [ ] 新建 `TaskWorkspaceViewModel`
- [ ] 新建 `TaskWorkspaceUiState`

建议事件与效果：

- `Event.LoadData`
- `Event.SessionClicked`
- `Event.RetryClicked`
- `Effect.OpenSessionDetail`

测试点：

- [ ] 初始加载进入 loading
- [ ] 成功加载进入 content
- [ ] 空数据进入 empty
- [ ] 失败进入 error
- [ ] 点击 session 发出 detail 导航 effect

### Step 6：实现 Workspace 页 Compose

- [ ] 新建 `TaskWorkspaceScreen`
- [ ] 新建 `TaskWorkspaceContent`
- [ ] 新建 `WorkspaceCard`
- [ ] 新建 `SessionRow`

实施要点：

- 首屏只做 loading / empty / error / content 四种状态
- 使用现有 Compose 设计系统能力，不引入独立视觉体系
- 不在 Phase 1 中加入搜索、筛选和快捷动作

测试点：

- [ ] Screen 首次进入触发 `LoadData`
- [ ] empty state 可见
- [ ] workspace 标题与副标题可见
- [ ] session 行点击事件能正确分发

### Step 7：实现 Detail 页 ViewModel

- [ ] 新建 `TaskSessionDetailContract`
- [ ] 新建 `TaskSessionDetailViewModel`
- [ ] 新建 `TaskSessionDetailUiState`

建议事件与效果：

- `Event.LoadDetail`
- `Event.BackClicked`
- `Effect.NavigateBack`

测试点：

- [ ] detail 正常加载
- [ ] question 存在时 UI state 正确
- [ ] timeline 顺序正确
- [ ] 返回事件发出 back effect

### Step 8：实现 Detail 页 Compose

- [ ] 新建 `TaskSessionDetailScreen`
- [ ] 新建 `TaskSessionDetailContent`
- [ ] 新建 `SessionHeader`
- [ ] 新建 `TaskStateCard`
- [ ] 新建 `PendingQuestionCard`
- [ ] 新建 `TimelineList`
- [ ] 新建 `TimelineMessageCard`
- [ ] 新建 `PlainTextBody`

实施要点：

- 只做状态卡、摘要、问题卡、timeline 和纯文本正文
- Markdown 富渲染留到后续 phase
- 回复编辑器和 slash command 操作留到后续 phase

测试点：

- [ ] 状态卡渲染正确
- [ ] question 卡渲染正确
- [ ] timeline item 渲染正确
- [ ] 空 timeline 的回退状态正确

### Step 9：补 feature 内导航宿主

- [ ] 新建 `DefaultTaskMailNavigation`
- [ ] 新建 `TaskMailNavHost`

建议路由：

- `TaskMailRoute.Workspace`
- `TaskMailRoute.SessionDetail(...)`

实施要点：

- 只做 feature 内部路由与返回栈
- 不在 Phase 1 接入抽屉、宿主 activity 或 `app-common`

测试点：

- [ ] route 参数可传递
- [ ] workspace 可跳转到 detail
- [ ] detail 返回可回到 workspace

### Step 10：补 Preview、收尾和统一验证

- [ ] 新建 `TaskWorkspacePreview`
- [ ] 新建 `TaskSessionDetailPreview`
- [ ] 收紧 `internal` 可见性
- [ ] 回写本文档中的实际落地差异

建议最终验证命令：

- [ ] `./gradlew test`
- [ ] `./gradlew lint`
- [ ] `./gradlew detekt`
- [ ] `./gradlew spotlessCheck`

如果环境允许，再补充：

- [ ] `./gradlew connectedAndroidTest`

---

## 8.2 当前实现状态（2026-03-13）

截至 2026-03-13，Phase 1 已完成的内容如下：

- 已建立 `feature:taskmail:api` 与 `feature:taskmail:internal`
- 已接入 `settings.gradle.kts`
- 已完成 `TaskMailRoute`、`TaskMailNavigation` 及其基础路由测试
- 已完成主题解析、`TASK-STATE` 解析、`TASK-QUESTION` 解析
- 已完成基础领域模型、fake repository、preview data、use case
- 已完成 `workspace -> session` 首页结构与 `session detail` 页面结构
- 已完成 `Workspace` / `Detail` 的 MVI `Contract + UiState + ViewModel`
- 已完成 feature 内部导航：`DefaultTaskMailNavigation`、`TaskMailNavHost`
- 已完成 debug preview
- 已完成 parser、use case、ViewModel、Compose screen 的单元测试
- 已补充 debug 专用 `TaskMailDebugActivity`，可用于设备/模拟器冒烟验证

当前实际落地文件，除原计划外，额外包含以下收尾项：

- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/workspace/TaskWorkspaceContent.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/TaskSessionDetailContent.kt`
- `feature/taskmail/internal/src/debug/kotlin/net/thunderbird/feature/taskmail/internal/debug/TaskMailDebugActivity.kt`
- `feature/taskmail/internal/src/debug/AndroidManifest.xml`
- `feature/taskmail/api/src/test/kotlin/net/thunderbird/feature/taskmail/api/TaskMailNavigationTest.kt`
- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/ui/workspace/TaskWorkspaceScreenKtTest.kt`
- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/TaskSessionDetailScreenKtTest.kt`

当前仍未进入 Phase 1 的内容：

- `app-common` 正式接线
- 抽屉入口与宿主页面入口
- 真实邮件仓库桥接
- Markdown 渲染
- 回复发送与 slash command

已完成的本地验证：

- `./gradlew :feature:taskmail:api:testDebugUnitTest`
- `./gradlew :feature:taskmail:internal:compileDebugKotlin`
- `./gradlew :feature:taskmail:internal:testDebugUnitTest`

已完成的手工验证：

- 通过 `TaskMailDebugActivity` 在设备上完成一次最小冒烟验证
- 确认 `workspace` 页面可打开
- 确认 `session` 点击可进入 detail
- 确认 detail 页面可返回

建议将 Phase 1 视为“功能骨架完成、真实数据未接入”的状态，后续进入 `Phase 1.5` 时再补邮件数据桥接与正式入口设计。

`Phase 1.5` 的详细执行清单见：

- `docs/TASKMAIL-ANDROID-PHASE1-5.md`

---

## 9. 当前最重要的边界提醒

- `TaskMailActivity` 不是 Phase 1 必需项，先用 feature 内部 nav host 即可
- 抽屉入口不是 Phase 1 必需项，留给 Phase 2
- `MessageViewInfo` 不是 TaskMail 正文解析的理想输入，不要把它当 Markdown 原文源
- 如果后端邮件协议继续演进，优先更新 `docs/TASKMAIL-MAIL-RULES.md`，再同步更新本文件
