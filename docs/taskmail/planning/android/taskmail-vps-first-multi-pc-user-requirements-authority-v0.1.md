# TaskMail VPS-First 多 PC 用户需求 Authority（v0.1）

更新时间：2026-03-25

## 状态

本文是 `VPS-first 多 PC 控制面` 主线下，Android 侧关于“用户怎么使用 TaskMail”的 authority。

它负责冻结以下内容：

- Android 在这条主线中的用户角色
- 用户如何理解 `pc / workspace / session`
- 新任务、follow-up、结果查看的主流程
- `execution_policy` 在用户侧的读法
- 哪些功能当前不应被做成主产品逻辑

它不替代以下文档：

- 当前实现真相：`docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- 当前 mail 协议真相：`docs/TASKMAIL-MAIL-RULES.md`
- 主线方向 authority：`docs/taskmail/planning/android/taskmail-vps-first-multi-pc-authority-v0.1.md`
- 平台设计草案：`docs/taskmail/planning/platform/taskmail-multi-pc-control-plane-v0.1.md`
- `PC <-> VPS` 协议草案：`docs/taskmail/planning/platform/taskmail-pc-vps-control-protocol-v0.1.md`

这些文档仍然分别回答“今天实现了什么”“平台边界是什么”“底层协议怎么走”。
本文回答的是：**Android 以后应按什么用户逻辑来组织产品与实现。**

## 目的

在 `VPS-first 多 PC` 主线下，Android 很容易只按协议对象做规划，结果把产品做成：

- 节点调试面板
- 协议字段编辑器
- 继续围绕 mail thread 的兼容 UI

本文的作用，就是提前冻结 Android 侧的用户心智与主流程，避免后续执行过程中不断回到“到底该按协议还是按用户逻辑做”的争论。

## Authority 读法

从 2026-03-25 起，对 Android 侧未来主线的用户需求讨论，默认按以下优先级读取：

1. 当前实现事实：`docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
2. 当前 mail 兼容事实：`docs/TASKMAIL-MAIL-RULES.md`
3. 本文
4. `docs/taskmail/planning/android/taskmail-vps-first-multi-pc-authority-v0.1.md`
5. `docs/taskmail/planning/platform/taskmail-multi-pc-control-plane-v0.1.md`
6. `docs/taskmail/planning/platform/taskmail-pc-vps-control-protocol-v0.1.md`
7. `docs/taskmail/planning/android/taskmail-next-development-plan-v0.2.md`

如果发生冲突：

- “今天已经实现了什么”以 current-truth 文档为准
- “Android 未来应如何被用户使用”以本文为准
- “底层消息如何编码”以协议草案为准

## 一句话结论

Android 不再被规划成 `mail 客户端 + 少量 direct 例外`。

Android 的目标角色是：

**一个面向 `VPS` 统一控制面的多 PC TaskMail 工作台。**

对用户来说，主流程应当是：

- 先选在哪台 `PC` 的哪个 `workspace` 上开始工作
- 然后长期围绕同一条 `session` 继续推进
- 在 `session` 中查看状态、过程直播、最终结果和文件

而不是持续围绕：

- mail thread
- subject token
- reply header
- 底层 websocket frame

## 当前固定用户假设

除非后续有新的 authority 文档重新打开，以下用户逻辑现在已经固定：

1. Android 的主对象应是 `session`，不是 mail thread。
2. 用户只在新建任务时选择一次 `pc + workspace`。
3. `session` 一旦创建，后续 follow-up 不再让用户重新做路由决策。
4. Android 对运行过程的主读法应是 `状态 + 直播 + 结果 + 文件`，而不是“解析一封邮件正文”。
5. Session 页默认面向“当前态”，前续对话通过 `最近上下文摘要 + 历史上下文层` 进入，而不是整页长聊天记录。
6. `event` 负责状态，`output_chunk` 负责过程直播，`result` 负责最终收口。
7. `artifact` 应作为结果的一部分被直接展示，而不是隐藏在调试层。
8. `mail` 如果保留，长期角色应是 backup / export / notification / compatibility，而不是主工作流。
9. `execution_policy = backend / profile / permission / backend_transport` 是执行策略，不是账号权限系统。
10. 第一版不把多用户 ACL、角色权限中心、跨 PC session 热迁移带入 Android 主产品逻辑。

## 用户心智模型

Android 侧以后应默认服务下面这条心智链：

1. 我想在哪台电脑的哪个项目上做事。
2. 我已经有一条正在推进的任务。
3. 我现在想看它到了哪一步。
4. 如果它在等我，我继续回复。
5. 如果它跑完了，我看最终结果和文件。

因此，用户视角下几个核心对象应这样被理解：

- `pc`：一台可工作的电脑
- `workspace`：这台电脑上的一个可执行项目目录
- `session`：围绕一个持续任务形成的工作线程
- `run`：session 中某次具体执行
- `execution_policy`：这次运行怎么跑

## Android 侧选定的产品边界

Android 主产品逻辑应围绕以下页面与流程组织：

- 首页 / 工作台
- 新任务页
- Session 详情页
- 结果 / 文件查看
- 设置与通知

它不应首先被组织成：

- mail 收件箱
- 协议字段测试面板
- PC 节点管理控制台

## 首页 / 工作台需求

首页首先要回答三个用户问题：

1. 现在有哪些可用的 `PC`
2. 这些 `PC` 上有哪些可工作的 `workspace`
3. 我有哪些 `session` 正在进行、等待输入或已经结束

因此首页至少应支持：

- `PC` 列表或分组视图
- `workspace` 入口
- `session` 列表
- 按 `PC / workspace / session state` 的基础过滤
- 新建任务入口

首页不应优先暴露：

- `connection_epoch`
- replay cursor
- 原始 envelope
- protocol type 名称

这些属于控制面内部细节，不是默认用户信息。

## 新任务流需求

新任务流的用户逻辑应固定为：

1. 选择目标 `PC`
2. 选择目标 `workspace`
3. 输入任务内容
4. 可选调整本次执行策略
5. 提交并进入 session

新任务页的关键是“选工作地点 + 说要做什么”，而不是“编辑底层命令对象”。

### 新任务页的执行策略要求

`execution_policy` 在新任务页应以“高级执行设置”出现，而不是系统权限设置页。

用户侧读法固定为：

- `backend`：使用哪个执行后端
- `profile`：运行档位 / 模型档位
- `permission`：执行权限档位
- `backend_transport`：实现细节，默认不应强暴露

固定规则：

- `backend` 对 `new_task` 应显式给出
- `profile` 使用稳定 label，不直接让用户填 raw model id
- `permission` 当前只读作 `default | highest`
- `permission` 不等于“账号权限”
- `permission=highest` 也不等于自动打开所有全局关闭能力
- 可选项默认折叠，避免每次创建任务都让用户重选全部执行参数

## Session 详情页需求

一旦 `session` 创建完成，Android 的主界面应切到“围绕 session 工作”，而不是继续围绕 mail 或重新选路由目标。

Session 页至少应稳定展示：

- 当前 `session` 绑定的 `PC`
- 当前 `workspace`
- 当前状态
- 当前或最近 `run`
- 最近一次 summary
- 最近上下文摘要
- 过程直播
- 最终结果
- 相关文件

Session 页的核心任务是回答四个问题：

1. 它现在是什么状态
2. 它正在做什么
3. 我现在能不能继续说
4. 它最终给了我什么

## 前续对话与历史上下文需求

Session 页不应默认长成“从第一轮开始无限向下滚”的聊天记录。

固定读法应是：

- 默认主视图先回答当前态
- 主页保留一块最近上下文摘要
- 更完整的前续对话通过 `查看前续` 或 `历史上下文` 入口进入

### 最近上下文摘要

Session 主页至少应稳定展示以下上下文：

- 上一轮用户输入或用户动作
- 最近一次系统结论
- 如果当前在等用户，系统到底在等什么

这块摘要的作用，是让用户很多时候不必先展开完整历史，也能直接继续 follow-up。

### 历史上下文层

完整前续对话应进入单独的历史上下文层，而不是直接占据 Session 主视图。

推荐形态：

- 全屏二级页
- 大抽屉 / 半页层

无论最终采用哪种形态，都应满足：

- 历史以“回合”组织，而不是按 mail thread 原样堆叠
- 每个回合优先展示：用户输入、关键状态变化、该轮结果摘要、相关文件
- 更早的回合可默认折叠
- 需要时才展开完整过程直播

## Follow-up 需求

对已有 `session` 的 follow-up，Android 必须固定采用“继续这条任务”的用户逻辑。

这意味着：

- follow-up 不再重新选择 `PC`
- follow-up 不再重新选择 `workspace`
- follow-up 默认只针对当前 `session_id`

第一版最小 follow-up 动作应至少包括：

- `reply`
- `status`

若后续执行侧支持，再扩展：

- `pause`
- `resume`
- `kill`

这些动作在 UI 中应表现为“继续说”“查看状态”“暂停”“恢复”“停止”，而不是协议字段名。

### Follow-up 的执行策略规则

对 follow-up：

- 默认继承当前 session 的 `backend / profile / permission`
- 可选只覆盖 `profile`
- 可选只覆盖 `permission`
- 不建议在 V1 中途切换 `backend`

如果允许覆盖，也应放在高级区域，不应抢占主输入区。

## 状态、直播与结果的固定读法

Android 侧对运行信息的读法固定如下：

- `event`：结构化状态变化
- `output_chunk`：实时文本流
- `result`：最终结果

这三层不可混淆。

具体要求：

- `event` 是状态真相
- `output_chunk` 提供实时感知，但不能替代状态机
- `result` 才是最终业务收口
- 不允许通过“拼接流式文本”反推最终结果
- 不允许把 mail 正文当作新的 canonical result

## 等待用户输入的读法

`awaiting_user_input` 不应只是一个普通状态标签。

当 session 进入这类状态时，Android 应把它明确投影为：

- 系统当前在等用户
- 当前 session 可以继续回复
- 用户需要优先关注这条任务

因此首页与 session 页都应把这类状态显式抬高，而不是把它埋在普通时间线里。

## 文件与结果需求

用户不会把 `artifact_manifest` 理解成协议对象。

用户理解的是：

- 生成了哪些文件
- 这些文件属于哪次结果
- 我能不能直接查看或下载

因此：

- 文件应作为结果区的一部分稳定呈现
- 结果页应能展示这次运行的摘要与文件
- 文件入口不应隐藏在调试面板或原始日志页中

## 离线与异常状态需求

当目标 `PC` 不在线时，Android 不应把问题表现为“协议断了”。

正确的用户读法应是：

- 这台 `PC` 当前不可用
- 这条 `session` 的历史仍然存在
- 当前暂时不能继续下发新动作
- 待节点恢复后可继续

因此：

- 离线不应等价于 session 消失
- 首页和 session 页都应区分“历史存在”和“当前不可继续”

## 设置需求

Android 侧第一版设置项应主要围绕客户端使用体验，而不是复杂权限中心。

推荐保留：

- VPS 地址与登录信息
- 默认 `PC`
- 默认 `workspace`
- 通知开关
- 备份 mail 开关
- Debug / 诊断开关

当前不应做成主线设置项：

- principal 级角色权限管理
- `PC` 节点 token 管理
- 自定义 raw model id 输入
- 任意 permission string 编辑

## Mail 的长期角色

如果 mail 继续保留，它在 Android 侧的用户意义应固定为：

- 备份通知
- 导出或留档
- 主控制面不可用时的兼容入口

它不应继续承担：

- 主首页
- 主 session 时间线
- 主结果真相

## 明确不做的事

在当前主线下，Android 侧不应把以下方向偷带进来：

- 不把 `permission` 做成“账号权限中心”
- 不把 `profile` 做成 raw model picker
- 不把 `PC <-> VPS` 节点协议直接当用户 UI
- 不要求用户对已有 `session` 反复选择路由目标
- 不把流式输出当最终结果
- 不把 mail thread 当长期主对象
- 不把跨 PC 热迁移当第一版默认能力

## 对后续执行的直接约束

从本文生效后，Android 侧后续执行过程中的 planning、需求讨论与 UI 抽象，默认都应回答以下检查项：

1. 这项设计是否围绕 `session` 而不是 mail thread
2. 这项设计是否只在新任务时暴露 `pc + workspace` 路由选择
3. 这项设计是否把 `profile / permission` 正确读成执行策略，而不是账号权限
4. 这项设计是否把 `event / output_chunk / result / artifact` 分层处理
5. 这项设计是否把 mail 留在 backup / export / compatibility 位置

如果不能通过这些检查，就不应被视作符合当前主线 authority。
