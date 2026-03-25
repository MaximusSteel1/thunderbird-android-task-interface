# TaskMail VPS-First 多 PC 信息架构（v0.1）

更新时间：2026-03-25

## 状态

本文是 `VPS-first 多 PC` 主线下，Android 侧的信息架构 companion doc。

它依附于以下文档：

- `docs/taskmail/planning/android/taskmail-vps-first-multi-pc-authority-v0.1.md`
- `docs/taskmail/planning/android/taskmail-vps-first-multi-pc-user-requirements-authority-v0.1.md`
- `docs/taskmail/planning/platform/taskmail-multi-pc-control-plane-v0.1.md`

它不承担当前实现 truth，也不替代底层协议文档。

本文回答的问题是：

**如果 Android 最终是一个 `VPS-first` 的多 PC TaskMail 工作台，那么页面结构、导航层次和页面职责应当如何组织。**

## 目标

本文只冻结三件事：

1. 用户进入 Android 后会看到哪些一级页面
2. 每个页面的默认职责是什么
3. 哪些对象应被当作主 UI 对象，哪些只应停留在内部实现层

## 设计原则

在这条主线下，信息架构必须满足以下原则：

1. 主对象是 `session`，不是 mail thread。
2. 路由选择只发生在新建任务时，不在 follow-up 时重复出现。
3. `pc` 与 `workspace` 用于表达“在哪工作”，不是为了暴露节点调试面板。
4. `event / output_chunk / result / artifact` 必须在 UI 上分层呈现。
5. `execution_policy` 是执行策略，属于任务创建和运行设置，不是账户权限中心。
6. `mail` 只保留在兼容、备份、导出和通知位置，不再主导信息架构。

## 一级结构

建议 Android TaskMail 的一级结构固定为：

1. 工作台首页
2. 新任务页
3. Session 详情页
4. 结果 / 文件查看
5. 设置与诊断

这五类页面足以承载当前主线，不需要额外引入 mail 收件箱或节点协议面板作为一级入口。

## 1. 工作台首页

### 目标

首页首先回答三个问题：

1. 哪些 `PC` 当前可用
2. 这些 `PC` 上有哪些可工作的 `workspace`
3. 我有哪些 `session` 正在进行、等待输入或最近结束

### 建议内容块

- `PC` 摘要区
- 常用或最近 `workspace` 区
- 需要我处理的 `session` 区
- 进行中的 `session` 区
- 最近结束的 `session` 区
- 新建任务入口

### 首页不应承载的职责

- 编辑底层 command payload
- 直接展示原始协议 frame
- 展示 `connection_epoch`、`trace_id`、replay cursor 等细节
- 作为 mail 收件箱替代品

### 首页的核心读法

首页是“我的工作台”，不是“协议状态页”。

## 2. 新任务页

### 目标

新任务页负责完成一次新的工作启动。

用户逻辑固定为：

1. 选择 `PC`
2. 选择 `workspace`
3. 输入任务内容
4. 可选调整执行策略
5. 提交

### 页面核心区域

- `PC` 选择器
- `workspace` 选择器
- 任务输入区
- 高级执行设置
- 提交按钮

### 高级执行设置

高级执行设置当前只承载：

- `backend`
- `profile`
- `permission`
- 可选 `backend_transport`

它不应扩展成：

- 用户角色权限页
- raw model id 手工输入页
- backend 调试面板

### 提交后的跳转

一旦命令被接受，新任务页应把用户送入对应的 `session` 详情页，而不是停留在“发送成功”中间态。

## 3. Session 详情页

### 目标

Session 页是这条主线中最重要的页面。

它的目标是让用户围绕一条持续任务长期工作，而不是每次都重新理解系统路由。

### 固定内容结构

建议 Session 页按以下结构组织：

1. Session 头部摘要
2. 当前状态区
3. 最近上下文摘要区
4. 过程直播区
5. 结果区
6. 文件区
7. Follow-up 输入区

### 头部摘要应包含

- `session_id`
- 绑定的 `PC`
- 绑定的 `workspace`
- 当前状态
- 当前或最近 `run`

### 当前状态区

当前状态区用于呈现：

- `queued`
- `accepted`
- `running`
- `awaiting_user_input`
- `paused`
- `done`
- `failed`
- `killed`

其中 `awaiting_user_input` 必须高亮成“需要我处理”，而不是普通状态标签。

### 最近上下文摘要区

最近上下文摘要区用于回答：

- 我上一轮说了什么
- 系统最近一次得出了什么结论
- 如果它在等我，它到底在等什么

这一块应附带明确入口：

- `查看前续`
- `历史上下文`

### 过程直播区

过程直播区只负责让用户看到“它正在做什么”。

它主要承载：

- assistant 文本流
- system 提示流
- stderr / 错误流

这个区域是“过程感知”，不是最终真相。

### 历史上下文层

完整前续对话不应默认和直播区混成一个无限长主列表。

信息架构上应把它收进单独的历史上下文层，推荐为：

- Session 页里的二级全屏页
- 或者可展开的大抽屉

历史上下文层的固定读法：

- 按回合组织
- 最新回合默认展开
- 更早的回合默认折叠
- 需要时再进入完整直播明细

### 结果区

结果区负责承载最终收口信息：

- summary
- final_status
- 结构化结果摘要
- 本次生效的执行策略摘要

### 文件区

文件区负责承载：

- 产物列表
- 文件类型与大小
- 查看 / 下载入口

### Follow-up 输入区

Follow-up 区当前最小应支持：

- `reply`
- `status`

后续如果执行侧支持，再扩展：

- `pause`
- `resume`
- `kill`

### Session 页不应做成什么

- 不做 mail timeline viewer
- 不做 raw event log viewer 作为默认视图
- 不做节点调试面板
- 不让用户重新选择 `PC` 或 `workspace`

## 4. 结果 / 文件查看

### 目标

结果与文件可以在 Session 页中内联展示，也可以下钻成独立结果页或文件查看页。

但无论最终是单页还是双页，都必须保持以下职责稳定：

- 结果页看结论
- 文件页看产物

### 结果页的主问题

- 这次运行最后得到了什么
- 为什么结束在这个状态
- 最终用了什么执行策略

### 文件页的主问题

- 生成了哪些文件
- 哪些是最重要的结果文件
- 我能否直接打开或下载

## 5. 设置与诊断

### 目标

设置页只承载客户端使用设置与少量诊断，不承载主工作流。

### 推荐内容

- VPS 地址与账户接入信息
- 默认 `PC`
- 默认 `workspace`
- 通知开关
- 备份 mail 开关
- Debug / 诊断入口

### 当前不应放入设置页的内容

- principal 级权限管理
- `PC` token 管理
- raw model id 编辑器
- 任意 permission 模式编辑器

## 推荐导航关系

推荐把导航固定为：

- 首页 -> 新任务页
- 首页 -> Session 页
- Session 页 -> 结果 / 文件查看
- 首页 / Session 页 -> 设置与诊断

不建议新增以下默认导航：

- 首页 -> Mail 收件箱
- 首页 -> 原始协议监控页
- 首页 -> PC 节点配置页

## 关键用户流

### 流 1：新建任务

1. 从首页进入新任务页
2. 选择 `PC`
3. 选择 `workspace`
4. 输入任务
5. 可选调整执行策略
6. 提交
7. 进入对应 Session 页

### 流 2：继续已有任务

1. 从首页打开某个 `session`
2. 查看当前状态与最近上下文摘要
3. 需要时进入历史上下文层查看前续回合
4. 发送 `reply` 或 `status`
5. 留在当前 Session 页继续观察

### 流 3：查看结果和文件

1. 打开已结束或进行中的 `session`
2. 查看结果区
3. 打开文件区
4. 查看或下载目标文件

### 流 4：处理等待输入的任务

1. 首页识别 `awaiting_user_input`
2. 进入对应 Session 页
3. 先看最近上下文摘要
4. 必要时展开历史上下文层
5. 直接 follow-up

## 信息架构 guardrails

后续做 UI 设计时，若出现以下情况，应判定为偏离当前主线：

- 页面主对象重新退回 mail thread
- follow-up 仍要求重新选 `PC / workspace`
- `profile / permission` 被做成系统权限中心
- 结果页被流式输出完全替代
- 文件结果被埋进调试层
- 首页主要展示协议或节点内部字段

## 与后续文档的关系

本文回答“页面怎么分”。

后续还需要单独冻结：

- 页面状态该长什么样
- 页面动作如何映射到 `VPS` 领域对象与交互动作

这些内容应由 companion 文档承担，而不是继续塞回 authority 本体。
