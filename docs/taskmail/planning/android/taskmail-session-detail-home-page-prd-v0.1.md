# TaskMail Session Detail 主页页面规格（v0.1）

更新时间：2026-03-30

## 状态

本文是 TaskMail Android 当前 `session detail` 主页的实现导向 companion doc。

它依附于以下文档：

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-MAIL-RULES.md`
- `docs/taskmail/planning/android/taskmail-vps-first-multi-pc-information-architecture-v0.1.md`
- `docs/taskmail/planning/android/taskmail-vps-first-multi-pc-compose-screen-structure-v0.1.md`

它不承担当前实现 truth，也不替代协议文档。

本文回答的问题是：

**当前 Android `session detail` 主页，如果按“用户最关心什么”重组为可开发的页面结构，应该显示哪些卡片、每张卡片里具体放什么、哪些现有内容应被合并、下沉或移除。**

## 目标

本文只冻结以下内容：

1. 当前 `session detail` 主页的页面模式
2. 每种模式从上到下的卡片顺序
3. 每张卡片的字段、优先级、出现条件与交互动作
4. 现有 Compose / ViewModel 结构下的推荐实现方式

本文不冻结：

- 最终视觉设计
- 颜色、排版和 design token
- 所有命名是否一步到位收口

## 设计原则

当前主页改造必须满足以下原则：

1. 首屏先回答三个问题：现在什么状态、最近发生了什么、我现在该做什么。
2. 一个事实只在一个主卡片里表达一次，不在多张卡里重复抄写。
3. `session` 是 UI 主对象，不退回 mail thread 读法。
4. 输入附件、输出附件、历史附件应跟随所属内容显示，不保留独立汇总文件卡。
5. `backend / workspaceId / sessionId / repoPath` 默认属于底部技术上下文，不进入首屏主信息。
6. 成功提交后的第一跳反馈必须是页面内可见状态，而不是只靠 toast。
7. `Paused` 是一等状态；页面要能承接 `Resume`，不能只显示不可用提示。
8. 用户对 PC 响应速度的感知必须分三层承接：首屏状态层、最近有效输出层、完整过程时间线层。

## 时间信息承接原则

用户对“PC 侧响应得快不快”的关注，当前应拆成四类时间点：

1. 我什么时候提交了这次动作
2. 系统最近一次有进展是什么时候
3. 最近一次真正输出结果是什么时候
4. 如果还不确定，再去哪里看完整过程时间线

对应到 UI，时间信息应分三层承接：

| 层级 | 页面位置 | 回答的问题 |
| --- | --- | --- |
| 快读层 | `StatusCard` | 这次动作是不是已经有响应了，最近多久没动 |
| 内容层 | `LatestCard` | 最近一次真正有意义的输出是什么时候产生的 |
| 追踪层 | `ProcessCard` | 每一步具体发生在什么时间 |

时间信息不要求在所有层都重复展示完整时间线。

### 当前可直接使用的时间来源

| 来源 | 当前是否可用 | 用途 |
| --- | --- | --- |
| `TaskSessionPendingSubmission.submittedAt` | 是 | 本地提交时间 |
| `TaskTimelineItem.timestamp` | 是 | timeline 精确记录时间 |
| `TaskSessionDetail.lastProgressAt` | 是 | 最近一次进展时间 |
| `TaskSessionDetail.lastActiveAt` | 是 | 最近一次活跃时间 |
| `ControlPlaneEvent.emittedAt` | 是 | 事件时间 |
| `ControlPlaneResult.generatedAt` | 是 | 最终结果时间 |
| `ControlPlaneCommandAck` 的时间 | 否 | 当前模型未保留 ack 时间，不能显示精确 ack 时间 |

### 时间展示规则

- 首屏默认同时展示：
  - `提交时间` 或 `最近提交状态`
  - `最近进展时间`
- `LatestCard` 只展示“这张卡代表的那次输出是什么时候产生的”
- `ProcessCard` 展开后承接精确时间线
- 如果当前拿不到某个时间，不要伪造一个“PC 已接收于 xx:xx”

### 时间文案建议

- 首屏优先使用相对时间 + 弱绝对时间的形式：
  - `刚刚提交`
  - `2 分钟前有新进展`
  - `最近结果生成于 14:32`
- 过程记录继续使用绝对时间：
  - `yyyy-MM-dd HH:mm`

### 非目标

- 当前这轮页面改造不要求补齐 protocol 里缺失的 ack 时间。
- 如果后续要精确展示“PC 接收时间”，需要单独把 ack message 的时间带入 Android 侧 model。

## 当前状态到页面模式的映射

当前 `TaskMailSessionStatus` 应先收成三种页面模式：

| 原始状态 | 页面模式 | 主读法 |
| --- | --- | --- |
| `Queued` | `ActiveRun` | 我刚发的这一轮正在排队 |
| `Running` | `ActiveRun` | 当前轮正在执行 |
| `WaitingUser` | `AwaitingReply` | 系统在等我回复 |
| `Paused` | `AwaitingReply` | 会话已暂停，先恢复再继续 |
| `Done` | `Terminal` | 本轮已结束 |
| `Failed` | `Terminal` | 本轮失败，查看结果并决定是否继续 |
| `Killed` | `Terminal` | 本轮已被终止 |
| `Unknown` | `Terminal` | 状态未知，先看结果和过程记录 |

说明：

- 当前主页主要先收口 `ActiveRun` 与 `AwaitingReply`。
- `Terminal` 先复用 `AwaitingReply` 大部分结构，但不默认把回复当成主动作。

## 页面级状态模型

建议在 `TaskSessionDetailUiState` 下收成以下子模型，而不是继续往顶层平铺字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `pageMode` | `TaskSessionPageMode` | `ActiveRun / AwaitingReply / Terminal` |
| `statusCard` | `TaskStatusCardUi` | 页面最上方固定卡片 |
| `currentRoundCard` | `TaskCurrentRoundUi?` | 仅 `ActiveRun` 显示 |
| `latestCard` | `TaskLatestCardUi?` | 各模式都可显示，标题和内容不同 |
| `questionCard` | `TaskQuestionCardUi?` | 有 pending questions 时显示 |
| `replyCard` | `TaskReplyCardUi?` | `AwaitingReply` 主操作区 |
| `secondaryActionsCard` | `TaskSecondaryActionsUi?` | 次操作和危险操作 |
| `processCard` | `TaskProcessSummaryUi` | 过程记录摘要与展开区 |
| `sessionMetaCard` | `TaskSessionMetaUi` | 页面底部技术上下文 |
| `inlineSubmissionState` | `TaskInlineSubmissionState?` | 首屏“已提交 / 已排队 / 执行中 / 失败”反馈 |

## 建议新增的 UI model

### 1. TaskStatusCardUi

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `statusLabel` | `String` | UI badge 文案 |
| `headline` | `String` | 用户向主结论 |
| `supportingText` | `String?` | 一句补充说明 |
| `actorHint` | `String?` | 当前轮到谁动作 |
| `lastUpdatedText` | `String?` | 最近进展时间 |
| `submissionState` | `TaskInlineSubmissionState?` | 第一跳动作反馈 |
| `timingRows` | `ImmutableList<TaskTimingRowUi>` | 关键时间摘要 |

### 2. TaskCurrentRoundUi

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `bodyText` | `String` | 当前轮输入正文 |
| `attachments` | `ImmutableList<TaskReplyAttachment>` | 当前轮输入附件 |
| `permissionLabel` | `String?` | 高级选项弱展示 |

### 3. TaskLatestCardUi

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `title` | `String` | `最新进展 / 最新结果 / 上一轮结果` |
| `body` | `TaskTimelineItemUi?` | 主体内容 |
| `emptyBodyText` | `String?` | 无正文时占位说明 |
| `timestampText` | `String?` | 这张卡代表的最近有效输出时间 |
| `headline` | `String` | 结论标题 |
| `supportingText` | `String?` | 结论摘要 |
| `effectiveExecutionSummary` | `String?` | 执行策略摘要 |
| `isCollapsible` | `Boolean` | `上一轮结果` 建议支持折叠 |
| `defaultExpanded` | `Boolean` | 默认展开态 |

### 4. TaskQuestionCardUi

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `title` | `String` | `待回答问题 / 待回答问题（多项）` |
| `questions` | `ImmutableList<TaskQuestionItemUi>` | 问题列表 |

`TaskQuestionItemUi` 建议包含：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `displayIndex` | `Int` | 页面显示顺序 |
| `questionId` | `String` | 仅弱展示或调试用 |
| `questionText` | `String` | 主视觉问题正文 |
| `isRequired` | `Boolean` | 是否必答 |
| `choices` | `ImmutableList<TaskPendingQuestionChoiceUi>` | 可选项 |

### 5. TaskReplyCardUi

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `mode` | `TaskReplyCardMode` | `PlainReply / SingleChoice / MultiQuestionForm / ResumeRequired / Disabled` |
| `replyLabel` | `String` | 输入区标题 |
| `replySupportingText` | `String` | 辅助说明 |
| `draftText` | `String` | 当前草稿 |
| `attachmentRows` | `ImmutableList<TaskReplyAttachment>` | 回复附件 |
| `quickAnswers` | `ImmutableList<TaskPendingQuestionChoiceUi>` | 单题快捷回复 |
| `selectedPermission` | `TaskMailNewTaskPermission` | 高级选项 |
| `showAdvancedOptions` | `Boolean` | 是否展开高级选项 |
| `canSend` | `Boolean` | 是否可发送 |
| `canResume` | `Boolean` | 是否显示恢复主动作 |
| `canQueryStatus` | `Boolean` | 是否可从高级选项触发 `/status` |
| `sendButtonText` | `String` | 主按钮文案 |
| `errorText` | `String?` | 卡内错误 |

### 6. TaskSecondaryActionsUi

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `showGuide` | `Boolean` | `ActiveRun` 下显示 |
| `showViewStatus` | `Boolean` | 次操作 |
| `showStopRunning` | `Boolean` | 危险动作 |
| `showDeactivate` | `Boolean` | 低优先级结束动作 |
| `showResume` | `Boolean` | `Paused` 时优先主动作由 ReplyCard 承担，这里可不显示 |

### 7. TaskProcessSummaryUi

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `title` | `String` | `过程记录 / 运行过程` |
| `supportingText` | `String?` | 卡片说明 |
| `count` | `Int` | 记录数 |
| `previewText` | `String?` | 折叠态最近一条预览 |
| `previewTimestampText` | `String?` | 折叠态最近一条时间 |
| `items` | `ImmutableList<TaskTimelineItemUi>` | 展开态完整记录 |

### 8. TaskSessionMetaUi

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `primaryLabel` | `String` | `workdir` 或 repo 名 |
| `rows` | `ImmutableList<Pair<String, String>>` | 技术上下文 |

### 9. TaskInlineSubmissionState

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `kind` | `Submitted / Queued / Running / Failed` | 第一跳状态 |
| `message` | `String` | 页面内提示文案 |

### 10. TaskTimingRowUi

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `label` | `String` | `已提交 / 最近进展 / 最近结果` |
| `value` | `String` | 时间文案 |
| `importance` | `Primary / Secondary` | 视觉优先级 |

## 页面顺序

### ActiveRun

从上到下固定为：

1. `StatusCard`
2. `CurrentRoundCard`
3. `LatestCard(title=最新进展)`
4. `SecondaryActionsCard`
5. `LatestCard(title=上一轮结果)`，默认弱化，可折叠
6. `ProcessCard`
7. `SessionMetaCard`

### AwaitingReply

从上到下固定为：

1. `StatusCard`
2. `LatestCard(title=最新结果)`
3. `QuestionCard`
4. `ReplyCard`
5. `SecondaryActionsCard`
6. `ProcessCard`
7. `SessionMetaCard`

### Terminal

从上到下固定为：

1. `StatusCard`
2. `LatestCard(title=最终结果)`
3. 可选 `ReplyCard` 禁用态或隐藏
4. `SecondaryActionsCard`
5. `ProcessCard`
6. `SessionMetaCard`

## 卡片规格

### 1. StatusCard

#### 出现条件

- 页面一进入即显示。

#### 字段优先级

P0：

- `status badge`
- `headline`
- `actorHint`

P1：

- `supportingText`
- `submissionState`
- `timingRows`

P2：

- `lastUpdatedText`

#### 字段生成规则

- `headline`
  - `Queued` -> `任务已排队`
  - `Running` -> `任务正在执行当前轮`
  - `WaitingUser` -> `系统正在等待你的回复`
  - `Paused` -> `会话已暂停`
  - `Done` -> `本轮已完成`
  - `Failed` -> `本轮执行失败`
  - `Killed` -> `本轮已终止`
  - `Unknown` -> `当前状态未知`
- `actorHint`
  - `ActiveRun` -> `当前等待系统继续处理`
  - `WaitingUser` -> `当前轮到你回复`
  - `Paused` -> `先恢复后再继续回复`
  - `Terminal` -> 可省略

#### 行为

- 无主要点击行为。

#### 时间信息

`StatusCard` 是用户读取 PC 响应时间的首要入口。

建议最多显示两行时间摘要：

- `已提交`
  - 来源优先 `pendingSubmissions.firstOrNull()?.submittedAt`
- `最近进展`
  - 来源优先 `lastProgressAt`
  - 没有则退到 `lastActiveAt`

若当前没有 pending submission，但有最新结果，也可显示：

- `最近结果`
  - 来源优先 `ControlPlaneResult.generatedAt`
  - 否则退到 `resultBody.timestamp`

不允许在当前模型拿不到 ack 时间时显示：

- `PC 已于 xx:xx 接收`

#### 不显示

- `backend`
- `repoPath`
- `workspaceId`
- `sessionId`

#### 当前实现差距

- 当前没有独立状态卡。
- 提交成功后只有 toast，没有页面内确认态。

### 2. CurrentRoundCard

#### 出现条件

- `pageMode == ActiveRun`

#### 字段优先级

P0：

- 当前轮输入正文

P1：

- 输入附件

P2：

- `Permission`

#### 行为

- 附件支持查看名称和类型。
- 不承担发送操作。

#### 不显示

- 状态 badge
- 上一轮结果
- `Recent context`

#### 当前实现差距

- 现有 `CurrentRoundInputCard` 可复用。
- `status` 和 `permission` 当前视觉权重偏高，应下调。

### 3. LatestCard

#### 出现条件

- `ActiveRun` 下显示 `最新进展` 与 `上一轮结果`
- `AwaitingReply` 下显示 `最新结果`
- `Terminal` 下显示 `最终结果`

#### 字段优先级

P0：

- 主体正文
- 主体附件

P1：

- `headline`
- `supportingText`
- `timestampText`

P2：

- `effectiveExecutionSummary`

#### ActiveRun 下的特殊规则

- `最新进展` 优先承接当前轮最新 assistant/system 输出。
- 如果当前轮尚无新输出，显示占位文案：
  - `当前轮还没有新输出。`
- `上一轮结果` 默认折叠，只显示弱化结论。

#### AwaitingReply / Terminal 下的特殊规则

- `最新结果 / 最终结果` 默认展开正文。
- 正文之后直接跟所属附件，不再拆出独立 `Files` 卡。

#### 时间信息

`LatestCard.timestampText` 用来表达“这张卡代表的最近有效输出是什么时候产生的”。

推荐来源：

1. `ControlPlaneResult.generatedAt`
2. `body.timestamp`
3. `TaskSessionDetail.lastProgressAt`

显示规则：

- 只显示一处，不在卡内重复一串时间字段
- 文案建议：
  - `最近结果生成于 14:32`
  - `最新进展发生于 14:35`

#### 不显示

- 独立 `backend:` 标题
- 独立 `N files` 汇总卡
- 与结果重复的 `Latest session output` 文案卡

#### 正文里的代码引用显示

- Android 端对结果/进展/过程正文里的代码引用做显示层归一化，不改底层文本。
- 已解析的 rich text markdown link 继续优先显示 link label，不回退显示绝对路径目标。
- plain text / summary / code block 里的 markdown 文件引用与裸绝对路径，默认压成文件名或短尾巴；点击后在气泡里查看并复制完整引用。
- 这条规则不作用于用户输入区，也不作用于 `Repository / Workdir / Session ID` 这类技术元信息。

#### 当前实现差距

- `ResultSummaryCard` 可复用。
- 发送态当前只有 `Previous result` 结论，正文承接不完整。

### 4. QuestionCard

#### 出现条件

- `pendingQuestions.isNotEmpty()`

#### 字段优先级

P0：

- 问题正文
- 可选项

P1：

- 是否必答

P2：

- `question_id`

#### 单题规则

- 直接用问题正文作为主视觉。
- 如果有 `choices`，应让用户一眼能看到可选值。

#### 多题规则

- 按问题块呈现。
- 每题显示：
  - `问题 1 / 问题 2` 的顺序提示
  - 问题正文
  - 必答/可选
  - choices

#### 不显示

- `question_id` 作为主标题
- 结构化回复格式说明

#### 当前实现差距

- 现有 `PendingQuestionCard` 问题信息足够。
- `question_id` 当前过于前置，应降权。

### 5. ReplyCard

#### 出现条件

- `pageMode == AwaitingReply`
- `Terminal` 下可按 `canReply` 决定显示禁用态或隐藏

#### 支持模式

- `PlainReply`
- `SingleChoice`
- `MultiQuestionForm`
- `ResumeRequired`
- `Disabled`

#### 字段优先级

P0：

- 主输入区或逐题输入区
- 主按钮

P1：

- 附件区
- `Quick answers`

P2：

- 高级选项
  - `Permission`
  - `/status`

#### 行为

- `PlainReply`
  - 一个输入框
  - 主按钮：`发送回复`
- `SingleChoice`
  - 保留输入框
  - 在卡内显示 `Quick answers`
- `MultiQuestionForm`
  - 不再让用户直接编辑 `Answers:` 模板
  - UI 层按问题逐条输入
  - 发送时在 ViewModel 内转为 `TaskMailDirectSessionActionRequest.Answers`
- `ResumeRequired`
  - 主按钮：`恢复`
  - 恢复成功后回到普通回复模式
- `Disabled`
  - 仅显示不可用原因

#### 不显示

- 独立 `Permission` 卡
- 独立 `Reply unavailable` 大块说明取代实际动作

#### 当前实现差距

- `TaskReplyComposer` 可复用。
- 需要把 `Permission` 合并进卡内高级选项。
- 需要新增 `Resume` 动作。
- 需要把多问题从模板输入改成逐题表单输入。

### 6. SecondaryActionsCard

#### 出现条件

- 有次要或危险动作时显示。

#### ActiveRun 下内容

- `Guide`
- `View status`
- `Stop running`

#### AwaitingReply 下内容

- `View status`
- `Deactivate`

#### Terminal 下内容

- 可选 `Deactivate`
- 可选 `View status`

#### 规则

- 一个动作只保留一个主要入口。
- `View status` 不应同时出现在顶部控制区、回复区、底部详情区三处。
- `Stop running` 和 `Deactivate` 必须以危险动作视觉呈现。

#### 当前实现差距

- `SessionControlRail` 可改造成该卡。
- `DeactivateCard` 需要并入，不再单独成卡。

### 7. ProcessCard

#### 出现条件

- `timeline` 始终可形成该卡；无内容时显示空态。

#### 折叠态

必须显示：

- `count`
- `previewText`
- `previewTimestampText`

`previewText` 生成规则：

- 优先 `timeline.firstOrNull()?.summary`
- 否则取 `timeline.firstOrNull()?.plainText?.take(80)`
- 否则 `暂无过程记录`

`previewTimestampText` 生成规则：

- `timeline.firstOrNull()?.timestamp`
- 没有则省略

#### 展开态

每条记录显示：

- 标题
- 时间
- 摘要
- 正文
- 附件

#### 不显示

- 结论层重复解释

#### 当前实现差距

- 现有 `ProcessFoldCard` 可复用。
- 需要新增折叠态 `previewText`。

### 8. SessionMetaCard

#### 出现条件

- 页面底部固定显示。

#### 字段优先级

P1：

- `workdir` 或 repo 名

P2：

- `repoPath`
- `backend`
- `workspaceId`
- `sessionId`

#### 规则

- 合并现有 `Environment` 与 `Session details`
- 仅承接技术上下文

#### 不显示

- 状态
- `View status`

#### 当前实现差距

- 需要替换 `SessionEnvironmentCard + SessionMetadataCard` 的双卡结构。

## 数据映射规则

### 1. 页面模式

- `Queued, Running -> ActiveRun`
- `WaitingUser, Paused -> AwaitingReply`
- `Done, Failed, Killed, Unknown -> Terminal`

### 2. StatusCard.submissionState

生成优先级：

1. `pendingSubmissions.firstOrNull()`
2. `controlPlaneSnapshot.result`
3. `controlPlaneSnapshot.events.lastOrNull()`
4. `controlPlaneSnapshot.commandAck`
5. `latestDirectSessionActionRecord`

推荐文案：

- 有 pending submission -> `已提交，等待系统接收`
- ack 为 queued -> `已接收，正在排队`
- event/result 为 running -> `系统已开始执行`
- result 为 failed/rejected -> `本次操作未成功`

说明：

- 当前 `ControlPlaneCommandAck` 没有保留时间戳，因此只能表达状态，不应表达精确 ack 时间。

### 2.1 StatusCard.timingRows

推荐最多两行：

1. `已提交`
   - 来源：`pendingSubmissions.firstOrNull()?.submittedAt`
2. `最近进展`
   - 来源优先：
     - `lastProgressAt`
     - `controlPlaneSnapshot.events.lastOrNull()?.emittedAt`
     - `lastActiveAt`

如果当前无 pending submission 且已有结果，可把第二行替换成：

- `最近结果`
  - 来源：
    - `controlPlaneSnapshot.result?.generatedAt`
    - `resultBody.timestamp`

### 3. CurrentRoundCard.bodyText

可直接复用当前 `currentInputBodyText()` 规则：

- 如果正在发送 guide 且有 draft，显示 draft
- 否则显示最近一次 outgoing message
- 否则显示占位文案

### 4. LatestCard.body

- `AwaitingReply / Terminal`
  - 优先 `resultBody`
- `ActiveRun`
  - 应从 timeline 中找“当前轮最新 assistant/system 输出”
  - 如果没有，显示空态而不是强行复用上一轮结果

### 4.1 LatestCard.timestampText

来源优先级：

1. `controlPlaneSnapshot.result?.generatedAt`
2. `body.timestamp`
3. `lastProgressAt`

文案建议：

- `最新进展发生于 {time}`
- `最近结果生成于 {time}`
- `最终结果生成于 {time}`

### 5. QuestionCard.questions

- 直接从 `pendingQuestions` 投影
- `questionId` 仅保留到弱信息层

### 6. ReplyCard.mode

- `Paused` -> `ResumeRequired`
- `pendingQuestions.size > 1` -> `MultiQuestionForm`
- `pendingQuestions.size == 1 && choices.isNotEmpty()` -> `SingleChoice`
- `canReply == true` -> `PlainReply`
- 其余 -> `Disabled`

### 7. ProcessCard.previewText

- `timeline.firstOrNull()?.summary`
- 否则 `timeline.firstOrNull()?.plainText?.take(80)`
- 否则 `暂无过程记录`

### 7.1 ProcessCard.previewTimestampText

- `timeline.firstOrNull()?.timestamp`
- 无值则省略

说明：

- `ProcessCard` 是用户追查 PC 侧响应时间的最终精确来源。
- 即使首屏已有时间摘要，这里仍保留完整绝对时间线。

### 8. SessionMetaCard.primaryLabel

- 优先 `workdir`
- 没有则取 `repoPath` 最后一级名称

## 交互规格

### 1. Guide

- 仅 `ActiveRun` 显示。
- 沿用当前 direct session-action 发送逻辑。

### 2. Stop running

- 仅 `ActiveRun` 显示。
- 发送 `TaskMailDirectSessionActionRequest.Kill`

### 3. Resume

- 仅 `Paused` 显示。
- 新增 `Event.ResumeClicked`
- ViewModel 发送 `TaskMailDirectSessionActionRequest.Resume`
- 恢复成功后：
  - 首屏出现页面内确认态
  - 当状态转回 `WaitingUser / Running` 时重新渲染相应主卡

### 4. View status

- 页面只保留一个主要入口。
- 推荐放在 `SecondaryActionsCard`
- 不再在底部元信息卡重复出现。

### 5. Deactivate

- 仅作为次要危险动作存在。
- 不再单独成卡。

### 6. Quick answers

- 只在 `SingleChoice` 模式下显示。
- 必须放入 `ReplyCard` 内部，而不是另起一张卡。

### 7. 多问题回复

- UI 层提供逐题输入。
- 发送前在 ViewModel 把表单值转成：
  - `TaskMailDirectSessionActionRequest.Answers`

### 8. 成功提交反馈

- 保留 toast
- 但必须在 `StatusCard` 里出现页面内确认态
- 页面内确认态在以下动作后都要出现：
  - `reply`
  - `answers`
  - `status`
  - `guide`
  - `resume`
  - `kill`
  - `end`

## 需要取消的独立卡片

以下卡片应退出主页独立层级：

- `RecentContextCard`
- `ArtifactSection`
- `SessionEnvironmentCard`
- `ReplyPermissionCard`
- `DeactivateCard`

处理方式：

- `Recent context` 的内容分别并入：
  - `CurrentRoundCard`
  - `LatestCard`
  - `StatusCard / QuestionCard`
- `Files` 不再独立汇总，附件跟随所属内容显示
- `Environment + Session details` 合并成 `SessionMetaCard`
- `Permission` 并入 `ReplyCard` 高级选项
- `Deactivate` 并入 `SecondaryActionsCard`

## 实现建议

### 1. 文件影响

首批改动建议集中在：

- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/TaskSessionDetailUiState.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/TaskSessionDetailViewModel.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/TaskSessionDetailContent.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/component/TaskReplyComposer.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/component/ProcessFoldCard.kt`
- 新增：
  - `StatusCard`
  - `SessionMetaCard`
  - `SecondaryActionsCard`

### 2. 现有组件复用策略

| 现有组件 | 处理 |
| --- | --- |
| `CurrentRoundInputCard` | 复用并降权状态/permission |
| `ResultSummaryCard` | 复用为 `LatestCard` 基础 |
| `TaskReplyComposer` | 复用并扩展高级选项、Resume、多问题表单 |
| `ProcessFoldCard` | 复用并新增 `previewText` |
| `TimelineMessageCard` | 直接复用 |
| `RecentContextCard` | 停止独立渲染 |
| `ArtifactSection` | 停止独立渲染 |
| `SessionEnvironmentCard + SessionMetadataCard` | 合并替换 |
| `ReplyPermissionCard` | 移入 `TaskReplyComposer` |
| `DeactivateCard` | 合并到 `SecondaryActionsCard` |
| `SessionControlRail` | 演进成 `SecondaryActionsCard` |

### 3. 建议实现顺序

1. 在 `UiState` 中引入 `pageMode` 和新卡片模型，先不删旧字段。
2. 在 `ViewModel.toUiState()` 中完成 `statusCard / latestCard / questionCard / sessionMetaCard` 的映射。
3. 新建 `StatusCard / SessionMetaCard / SecondaryActionsCard`。
4. 重排 `TaskSessionDetailContent`，停止渲染 `RecentContextCard / ArtifactSection / ReplyPermissionCard / SessionEnvironmentCard`。
5. 在 `ReplyCard` 中补 `Resume` 和高级选项。
6. 最后补多问题逐题表单，再删除旧字段与旧测试假设。

## 验收标准

### 页面结构

- `Queued / Running` 首屏只保留一处状态主结论、一处当前轮输入、一处最新进展、一处主操作区。
- `WaitingUser / Paused` 首屏必须先看到最新结果，再看到待回答问题，再看到回复入口。
- 页面上不再存在独立 `Recent context` 与独立 `Files` 卡。
- `View status` 页面上只有一个主要入口。

### 交互

- reply/status/guide/kill/end/resume 成功后，页面内出现非 toast 的确认态。
- `Paused` 时存在可点击的 `Resume` 主动作。
- 多问题不再要求用户手写 `Answers:` 模板。
- 用户无需展开 `ProcessCard`，也能在首屏知道：
  - 最近一次提交是什么时候
  - 最近一次进展是什么时候
- 如果当前无法获得精确 ack 时间，页面不会伪造“PC 已接收于 xx:xx”。

### 信息优先级

- `question_id` 不再是问题卡主视觉。
- `backend / workspaceId / sessionId / repoPath` 默认只出现在底部元信息卡。

## 非目标

当前这轮页面改造不要求：

- 重写底层 repository 或 transport
- 先做 route rename
- 先把所有 detail 逻辑全量拆成新文件族
- 在本轮内完成最终视觉 polish
