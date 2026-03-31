# TASKMAIL Android Session 过程展示规则

## 文档定位

本文定义 TaskMail Android 中 `session detail` 与 `history review` 的过程展示规则。

这是正式规则文档，用来约束：

- 首页如何展示运行过程
- 历史回合页如何展示 round 过程
- 结果与过程的主次关系
- 主视图是否显示 tool 调用

实现方案、切片计划、临时取舍不写在本文中。实施细节见 [taskmail-session-process-display-plan-v0.1.md](/E:/projects/android_task_manager/docs/taskmail/planning/android/taskmail-session-process-display-plan-v0.1.md)。

## 适用范围

本文适用于：

- TaskMail `session detail` 首页
- TaskMail `history review` 历史回合页

不适用于：

- debug / developer-only 视图
- raw protocol inspection
- 后续单独设计的高密度过程调试页

## 总原则

TaskMail Android 的 session 过程展示遵循以下原则：

1. `状态` 与 `过程` 分层展示，不重复承载同一段正文。
2. `结果优先，过程按需展开`；运行中是唯一例外。
3. 主阅读流优先看 `assistant` 输出，不以 `tool` 调用为主。
4. 首页与历史页采用同一套过程默认展开规则。

## 规则一：状态卡不承载运行过程正文

`StatusCard` 只负责回答这些问题：

- 当前状态是什么
- 当前轮到谁行动
- 最近提交 / 最近进展 / 最近活跃时间是什么

`StatusCard` 不应直接承载：

- 当前 assistant 过程正文
- 当前 tool 调用正文
- 当前运行中的“最新一条输出”

运行过程正文应由独立的 `ProcessSection` 承载。

## 规则二：主过程流只展示 assistant

默认主视图中：

- 展示 `assistant` 过程项
- 不展示 `tool` 过程项
- 不展示 `system` 过程项

说明：

- 这里的“不展示”仅指默认主阅读流
- 不等于删除数据
- `tool` / `system` 数据必须在上游 contract 与 Android domain 中保留

补充：

- 如果 raw process 存在，但过滤掉 `tool / system` 后没有 assistant item，主视图仍显示 `ProcessSection`
- 此时统一展示 `暂无 assistant 输出`
- 如果 raw process 本身不存在，则隐藏整个 `ProcessSection`

## 规则三：过程必须按顺序展示

主过程流必须按时间顺序展示，用户需要能看出过程推进，而不是只看到一条覆盖后的最新文本。

要求：

- assistant 过程按从早到晚展示
- 当前最后一段允许持续增长
- 不允许只保留最后一条 assistant 文本作为唯一过程展示

顺序责任：

- canonical 顺序由服务端负责
- 客户端只按返回顺序渲染
- 客户端不自行重排

## 规则四：运行中默认展开，稳定后默认折叠

过程展示的默认展开规则如下。

### 首页 `session detail`

- `Queued / Running`
  - 过程默认展开
  - 过程区读取 `live_process`
- `WaitingUser / Paused / Done / Failed / Killed`
  - 过程默认折叠
  - 过程区读取最近一个 stable round 的 `process_items`
  - 若无任何 process 数据，则隐藏过程区

### 历史页 `history review`

- round 状态为 `Queued / Running`
  - round 内过程默认展开
- round 状态为 `WaitingUser / Paused / Done / Failed / Killed`
  - round 内过程默认折叠

折叠态摘要规则：

- 统一显示最后一条 assistant item 的截断文本
- 若没有 assistant item，但 raw process 存在，则显示 `暂无 assistant 输出`
- 折叠态不展示 tool 明细
- 折叠态不展示 hidden tool 条数

## 规则五：稳定结果出现后，结果优先于过程

一旦某个 session 或 round 已经形成稳定结果：

- `result` 是主信息
- `process` 是次级信息
- 默认应先看到结果，再按需展开过程

不允许在已有稳定结果时，仍让过程区默认占据主视图核心位置。

## 规则六：首页与历史页保持同构

首页和历史页中的过程展示规则必须一致：

- 同样的默认展开逻辑
- 同样的 assistant-first 过滤逻辑
- 同样的结果/过程主次关系

允许样式不同，但不允许在信息规则上互相冲突。

## 规则七：tool 调用不是删除，只是当前不做主展示

当前阶段对 `tool` 调用的处理规则是：

- 保留数据
- 默认不进入主阅读流
- 后续若产品需要更高信息密度，可在独立设计后开放展示

因此：

- 不允许为了降噪删除 `tool` 数据
- 不允许把“默认不展示”实现成“上游不再提供”

## 数据 contract 约束

为了满足上述规则，Android-facing process contract 必须满足：

1. 能区分 `assistant / tool / system`
2. 能表达多个过程项，而不是只有单个聚合字符串
3. 能表达每个过程项的时间信息
4. `live_process.items[]` 与 `history_rounds[].process_items[]` 使用同一套 canonical `ProcessItem` schema

如果 contract 不能满足以上 3 点，则视为不满足正式展示规则。

## 禁止事项

以下做法不应作为正式实现：

1. 在 `StatusCard` 和 `ProcessSection` 同时展示同一段运行中文本。
2. 继续把运行中过程只表示成单个 `liveOutputText` 并作为最终展示模型。
3. 在 Android 端通过猜测或解析纯文本来区分 assistant 与 tool。
4. 首页和历史页分别采用不同的默认展开规则。
5. 折叠态展示 tool 明细或 tool 条数。

## 变更门槛

如果未来要改变以下任一规则，应视为产品规则变更，而不是普通 UI 微调：

- 主过程流是否默认只展示 assistant
- 运行中是否默认展开过程
- 有稳定结果后是否默认折叠过程
- 首页与历史页是否继续保持同构

## 与其他文档的关系

- 本文是“展示规则”正式依据。
- [TASKMAIL-ANDROID-CURRENT-STATUS.md](/E:/projects/android_task_manager/docs/TASKMAIL-ANDROID-CURRENT-STATUS.md) 说明当前做到哪里。
- [taskmail-session-process-display-plan-v0.1.md](/E:/projects/android_task_manager/docs/taskmail/planning/android/taskmail-session-process-display-plan-v0.1.md) 说明本轮如何把实现推进到符合本文。
