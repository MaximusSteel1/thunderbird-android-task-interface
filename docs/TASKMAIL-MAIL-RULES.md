# TaskMail 邮件规则清单

## Status

- Last updated: 2026-03-18
- Scope: Android 侧当前应消费和遵守的 TaskMail 邮件协议视图
- Cross-repo alignment basis:
  - `E:\projects\mail_based_task_manager\docs/current/mail_protocol.md`
  - `E:\projects\mail_based_task_manager\docs/current/android_reply_method_rules.md`
  - `E:\projects\mail_based_task_manager\docs/current/task_view_mail_parsing_rules.md`

## 1. 目的

这份文档用于记录 **TaskMail Android 客户端当前应遵守的邮件协议规则**，方便后续统一：

- `workspace / session / detail` 读取模型
- timeline 展示与正文抽取
- reply 路由
- question / answer 语义
- attachment 行为边界

本文档回答的是：

> Android 应该按什么协议理解 TaskMail 邮件，并按什么规则序列化回复邮件？

它不直接回答：

- Android 仓库里当前到底实现到了哪一步
- 哪些能力已经被重新验证过
- 长期产品规划应该如何分阶段推进

这些问题分别由以下文档承担：

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/taskmail/planning/README.md`

## 2. 文档角色与 Authority

当文档发生冲突时，优先级如下：

1. `E:\projects\mail_based_task_manager` 中的当前实现
2. `E:\projects\mail_based_task_manager\docs/current/mail_protocol.md`
3. `E:\projects\mail_based_task_manager\docs/current/android_reply_method_rules.md`
4. `E:\projects\mail_based_task_manager\docs/current/task_view_mail_parsing_rules.md`
5. 本文件
6. `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
7. Android 侧 planning / phase 文档

说明：

- `CURRENT-STATUS` 用来回答“安卓仓库当前已经实现了什么”
- `VALIDATION-LEDGER` 用来回答“哪些能力有可执行验证证据”
- 本文件用来回答“Android 应该如何理解和发送 TaskMail 邮件”
- planning / phase 文档可以描述后续方向，但不能覆盖当前协议事实

`E:\projects\mail_based_task_manager\docs\plans\coding_backlog.md` 里的内容目前只应视为 PC 侧下一阶段计划，
不能直接当作 Android 当前协议 authority。

但下面这些事项现在已经进入 PC 侧 `docs/current/*`，因此应视为 **当前 PC-side 协议 / runtime 事实**，而不是
Android 文档里的未来 watchpoint：

- `active` / `ended` lifecycle split
- `/end`
- `last_active_at`
- `last_progress_at`
- forced `active <= 4`
- observe-facing health visibility
- revised live-mailbox retention semantics for `[DONE]` / `[FAILED]` / `[KILLED]`

这些事项是否已经被 Android 仓库完整解析、显式展示或重新验证，仍应分别由
`docs/TASKMAIL-ANDROID-CURRENT-STATUS.md` 和 `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md` 回答。

## 3. Android 当前 UI 暴露范围 vs 协议能力

当前 PC 侧 mail control plane 的协议能力大于 Android 当前显式 UI 暴露范围。

### 3.1 当前协议可识别动作集合

协议层当前支持：

- 新任务首封邮件
- 普通 reply continuation
- `/pause`
- `/resume`
- `/end`
- `/new`
- `/sessions`
- `/status`
- `/rerun`
- `/kill`

reply 正文中还支持结构化覆盖字段：

- `Profile:`
- `Permission:`
- `Timeout:`
- `Mode:`
- `Task:`
- `Acceptance:`

### 3.2 当前 Android 仓库中的显式交互面

当前 Android 仓库内已经显式暴露的交互面是较窄子集：

- plain-text reply
- reply attachments
- 单题 quick answer
- 多题结构化 `Answers:` 模板回复
- `/status`
- `paused` 会话的显式恢复语义（发送时自动在正文前加 `/resume`）

### 3.3 当前未做成显式 UI 控件的协议能力

当前 Android UI 还没有专门做成显式控件的协议能力包括：

- `Permission`
- `Profile`
- `Timeout`
- `Mode`
- `/pause`
- `/end`
- `/new`
- `/sessions`
- `/rerun`
- `/kill`

这不等于协议不存在这些能力。

如果用户通过自由文本输入符合协议的正文，Android 仍然不应破坏这些协议内容。

## 4. 核心标识

| 字段 | 含义 | Android 侧用途 | 备注 |
| --- | --- | --- | --- |
| `workspace_id` | 工作区 ID | 作为一级分组 key | 后端内部由 `repo_path + workdir` 派生 |
| `repo_path` | 仓库根路径 | 展示与兜底分组 | 人类可读 |
| `workdir` | 仓库内工作目录 | 展示与兜底分组 | 可为空；repo 根目录可表现为空或 `.`，客户端应视为同一 workspace |
| `session_id` | 应用层 session ID | 作为二级分组 key | Android UI 的主会话标识 |
| `session_name` | 会话标题 | 列表展示 | 来自规范化后的任务标题 |
| `thread_id` | 邮件线程 ID | 邮件侧关联与兼容字段 | 当前实现中常与 `session_id` 接近，但客户端不要假定二者永远相等 |
| `task_id` | 某次执行快照 ID | 详情页和状态展示 | 不是分组 key |
| `backend` | `opencode` / `codex` | 展示、筛选 | 来自主题前缀或状态邮件 |
| `backend_session_id` | 原生 CLI session token | 不作为 UI 主键 | 用于后端 resume，不等于 `session_id` |

### 4.1 关键语义

- `workspace` 是 `repo_path + workdir` 这一层
- `session` 是工作区内的长期任务上下文
- `run` 是某个 session 的一次具体执行
- Android 首页默认应按 `workspace -> session` 展示，而不是把所有邮件平铺成一个列表

### 4.2 重要约束

- `session_id` 是应用层稳定标识，不要拿 `backend_session_id` 代替
- 当前后端很多地方仍使用 `session_id or thread_id`，所以 Android 需要同时保留这两个字段
- 同一个 `workspace` 下允许有多个 `session`
- 分组主键必须优先使用协议标识，而不是主题文本

## 5. 主题与状态标签规则

### 5.1 新任务主题

支持的新任务主题前缀：

- `[OC]` -> OpenCode backend
- `[CX]` -> Codex backend
- `[KILL] <task_id>` -> direct kill request

前缀之后的文本是人类可读任务标题，也是 `session_name` 的重要来源。

示例：

```text
Subject: [OC] Refactor floor_shear
Subject: [CX] Analyze floor_shear
```

### 5.2 状态邮件主题

状态邮件主题格式为：

```text
[STATUS_LABEL][S:session_id] <original subject>
```

当前用户可见状态标签包括：

- `[ACCEPTED]`
- `[RUNNING]`
- `[DONE]`
- `[FAILED]`
- `[KILLED]`
- `[PAUSED]`
- `[STATUS]`
- `[QUESTION]`

### 5.3 Reply 主题兼容

回复主题应兼容常见前缀：

- `Re:`
- `FW:`
- `Fwd:`
- `AW:`
- `回复:`
- `回复：`
- `答复:`
- `答复：`

### 5.4 Reply 主题身份标识保留规则

Android 在规范化 reply 主题时，必须保留 canonical TaskMail 身份标识，包括：

- 当前状态标签，例如 `[DONE]`、`[PAUSED]`
- `[S:session_id]`
- backend 前缀，例如 `[OC]`、`[CX]`
- 其余后端路由 token（如果原主题中存在）

允许做的事情：

- 统一 leading reply prefix，例如补上标准 `Re:`

不允许做的事情：

- 因为“看起来更整洁”而删掉 `[S:session_id]`
- 把 `[PAUSED]`、`[QUESTION]` 之类状态 token 当噪音去掉
- 靠“同主题”自动推断命中旧 session

## 6. 首封任务邮件规则

### 6.1 结构

首封任务邮件正文采用半结构化格式。

当前核心字段：

- `Repo:`
- `Task:`

可选字段：

- `Workdir:`
- `Timeout:`
- `Mode:`
- `Profile:`
- `Permission:`
- `Acceptance:`

示例：

```text
Repo: D:\proj\my_repo
Workdir: src\postprocess
Timeout: 60
Mode: modify
Profile: strong
Permission: highest

Task:
把 floor_shear.py 重构为 dataclass 风格，保持现有输出不变

Acceptance:
1. pytest tests/test_floor_shear.py 通过
2. 不改 public API
3. 输出简短修改说明
```

### 6.2 字段要求

- `Repo:` 必填
- `Task:` 必填
- `Workdir:` 可选
- `Timeout:` 可选，缺失时用默认值
- `Mode:` 可选，缺失时默认 `modify`
- `Profile:` 可选
- `Permission:` 可选，允许值为 `default` / `highest`
- `Acceptance:` 可为空列表

### 6.3 `Permission` 字段规则

- 首封任务省略 `Permission:`：使用后端默认权限
- `Permission: default`：显式恢复为后端默认权限
- `Permission: highest`：请求当前仓库支持的最高权限执行模式

当前 Android UI 还没有“新建任务”入口，也没有专门的 `Permission` 控件；这里记录的是协议事实。

## 7. Reply 路由与动作规则

### 7.1 Reply 命中优先级

reply 邮件只能命中已有 session，优先级固定为：

1. `In-Reply-To`
2. `References`
3. 邮件正文里的 state capsule
4. 主题中的 `[S:session_id]`

额外约束：

- 不再使用“同主题自动接旧 session”的兜底策略
- reply 不允许在同一线程里分叉出新的 session

### 7.2 普通 reply

普通自然语言 reply 默认含义是：

- 继续当前 session
- 追加上下文
- 或回答当前问题

如果当前线程处于 `[QUESTION] / awaiting_user_input`，允许直接回答，不强制要求写 `/resume`。

### 7.3 Slash 命令

当前 reply 支持以下命令：

| 命令 | 含义 |
| --- | --- |
| `/pause` | 暂停当前 session 的后续 continuation |
| `/resume` | 继续当前 session |
| `/end` | 将当前非运行中 session 标记为 `ended` |
| `/new` | 在当前 workspace 下发起新 session |
| `/sessions` | 查看当前 workspace 下的 session 列表 |
| `/status` | 查询当前 session 状态 |
| `/rerun` | 重新执行当前 session |
| `/kill` | 终止当前 session |

说明：

- `/new` 是少数允许显式创建 fresh session 的 reply 行为
- 除 `/new` 外，其余 reply 动作都应指向已存在 session
- `/end` 只对非运行中的 thread/session 生效；它只改变 `lifecycle`，不改写上一轮 `done` / `failed` /
  `killed` / `paused` 结果
- 上表描述的是协议可识别动作集合，不等于 Android 首期 UI 必须全部显式暴露

### 7.4 `paused` 的状态约束

`paused` 是一等状态，不是普通状态文案。

当前规则：

- thread 进入 `paused` 后，普通 reply 不会隐式恢复，必须显式 `/resume`
- `/pause` 只暂停邮件控制面的后续 continuation，不暂停已经在跑的底层 CLI 进程
- 如果线程仍处于 `accepted/running`，应提示用户等待或使用 `/kill`
- 如果 `paused` 线程仍有 pending question set：
  - `/resume` 不带答案：退出 `paused`，恢复成 `[QUESTION]`
  - `/resume` 带答案：按正常 answer flow 继续解析；答不全则保持 `[QUESTION]`
- 如果 `paused` 线程没有 pending question set：
  - `/resume` 恢复为普通 continuation / native resume 语义

### 7.5 Reply 正文中的结构化覆盖字段

普通 reply、`/new`、`/resume` 后的正文可以带这些结构化字段：

- `Profile:`
- `Permission:`
- `Timeout:`
- `Mode:`
- `Task:`
- `Acceptance:`

当前 Android UI 还没有这些字段的专门表单，但不应破坏用户通过自由文本输入的协议内容。

### 7.6 Dual-mailbox boundary

Android remains a reply client on the current dual-mailbox model:

- user mailbox sends to bot mailbox
- Android should read and reply from user-mailbox TaskMail traffic
- if both mailboxes are configured locally, TaskMail session projection should prefer the user-mailbox copy so service-mailbox duplicates do not dominate reply eligibility
- `[SYNC]` remains a low-input bootstrap action outside TaskMail session projection
- TaskMail reply transport should resolve the outbound destination from configured `bot mailbox`, not from generic mail
  reply-recipient heuristics

## 8. Reply 传输与正文序列化规则

### 8.1 传输层要求

所有“继续当前任务”的动作都必须发成真正的 reply mail。

必须满足：

- `In-Reply-To` 指向当前会话最新一封锚点消息的 `Message-ID`
- `References` 继承当前线程引用链
- `Subject` 保持原线程主题，并保留 `[S:session_id]` 等身份 token
- `To` 应解析为配置的 TaskMail `bot mailbox`
- `Cc` 应保持为空，除非未来协议另有明确要求
- 正文必须始终包含 `text/plain`

`text/html` 可以作为展示镜像，但不能成为唯一事实来源。

Android 当前 rich-text body slice 额外冻结以下展示侧消费规则：

- Android 只把 `article.task-mail` 视为可消费的 HTML 正文单元；其外层 mail-safe wrapper 视为 transport 包装
- 若 HTML 中不存在 `article.task-mail`，或 HTML 不安全 / 不可投影，则回退到 plain text
- HTML 允许比 Android v1 renderer 更宽的标签子集；未支持标签可以安全降级为 plain text 或 unsupported block，不视为协议失败

如果 TaskMail `bot mailbox` 未配置、无效，或不能唯一解析成单个地址，Android 应直接使发送失败，而不是回退到普通邮件
reply-recipient 推断逻辑。

### 8.2 一般正文规则

- 第一屏只包含“本次新增内容”
- 历史引用正文如果保留，应放在后面
- 不要在 Android 侧生成新的 `TASK-STATE` / `TASK-QUESTION` 块

### 8.3 Slash 命令规则

如果本次回复是命令：

- 第一条非空行必须是 slash command
- 命令后的正文是参数或附加说明
- 不要把命令写在第二段或末尾

### 8.4 当前 Android UI 的具体发送语义

当前 Android 仓库内的显式发送语义应理解为：

- plain-text reply -> 继续当前 session
- 单题 quick answer -> 发送 canonical choice value，不发送展示 label
- 多题 -> 发送结构化 `Answers:` 正文
- `paused` 会话 -> 发送时在正文前显式加 `/resume`
- `/status` -> 发送 `/status`，且不带附件

多题推荐格式：

```text
Answers:
phase2_entry_position: below
phase2_icon_strings: provide
```

`paused` + 多题推荐格式：

```text
/resume
Answers:
phase2_entry_position: below
phase2_icon_strings: provide
```

## 9. 展示层正文抽取规则

### 9.1 User message 展示抽取

Android 侧对 user reply 的展示正文应遵循“只取本轮新增内容”的思路：

1. 统一换行符
2. 先移除正文里的：
   - `TASK-STATE` 块
   - `TASK-QUESTION` 块
3. 再按常见引用分隔符截断，只保留引用前的新内容

当前已知会触发截断的典型模式包括：

- `On ... wrote:`
- `回复:` / `答复:`
- `-----Original Message-----`
- `-----原始邮件-----`
- `---原始邮件---`
- `From / Sent / To / Subject` 头部引用块
- 以 `>` 开头的引用行

### 9.2 System message 展示抽取

Android 侧对 system message 正文建议采用如下提取顺序：

1. 先移除：
   - `TASK-STATE` 块
   - `TASK-QUESTION` 块
2. 如果正文包含 `Reply:` 段，优先取 `Reply:` 之后的内容
3. 否则如果存在 `Summary:` 行，优先取 `Summary:` 的值
4. 否则如果 `Status:` 行前存在一段前置说明文本，优先取该段说明
5. 否则如果存在 `Question:`，展示 `Question:`，如存在再附带 `Choices:`
6. 否则如果存在 `Status:`，展示 `Status: ...`
7. 如果以上都没有命中，则回退为剥离 capsule 后的正文

这条规则服务于 timeline 的人类可读正文，不替代 session 路由或状态判断。

## 10. State Capsule 规则

### 10.1 当前状态块格式

每封状态邮件底部应包含机器可读块：

```text
---TASK-STATE-BEGIN---
thread_id: thread_001
workspace_id: workspace_001
session_id: thread_001
session_name: Demo task
task_id: task_001
backend: opencode
repo_path: D:\repo
workdir: src
mode: modify
status: paused
paused_from_status: awaiting_user_input
last_summary: Waiting for your answer
---TASK-STATE-END---
```

### 10.2 当前字段清单

- `thread_id`
- `workspace_id`
- `session_id`
- `session_name`
- `task_id`
- `backend`
- `repo_path`
- `workdir`
- `mode`
- `status`
- `paused_from_status`
- `last_summary`

当前 PC 侧 runtime 还已经引入了以下跨仓事实：

- `lifecycle`
- `last_active_at`
- `last_progress_at`
- observe-derived `health` / `health_reason`

这些字段不应再被 Android 文档视为 planning-only；但它们是否已经进入 Android 当前最低解析集，仍由本节下一小节
和 `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md` 共同界定。

### 10.3 解析规则

- 只解析最后一个完整出现的状态块
- 字段值按单行处理
- 多行文本会被压平为单行空格分隔文本
- 若状态块不存在或不完整，则返回 `None`，再回退到其他线索

### 10.4 Android 侧最低解析要求

Android 客户端至少要解析并保留：

- `workspace_id`
- `session_id`
- `session_name`
- `thread_id`
- `task_id`
- `backend`
- `repo_path`
- `workdir`
- `status`
- `lifecycle`
- `paused_from_status`
- `last_active_at`
- `last_progress_at`
- `last_summary`

当上游状态块未来携带更宽字段时，Android 也不应因为出现以下字段就把整个状态块判为无效：

- `health`
- `health_reason`

## 11. Question Capsule / Waiting-State 规则

### 11.1 多问题等待态

等待用户回答时，状态邮件可以追加一个或多个问题块。

如果同一封状态邮件里存在多个问题块，它们必须共享同一个 `question_set_id`。

### 11.2 字段说明

- `question_set_id`
- `question_id`
- `question_type`
- `required`
- `question_text`
- `choices`
- `choice_labels`

### 11.3 Android 侧读取要求

- 解析所有完整问题块，并保留原始顺序
- 如果同一封邮件里出现多个不同的 `question_set_id`，视为协议冲突，不做乐观拼接
- `choice_labels` 需要按 `key=value` 形式解析并保留
- 如果只有 1 个问题块且没有 `question_set_id`，按 legacy 单题兼容
- 详情页可以继续展示一个“主问题”，但底层模型必须保留完整问题列表

### 11.4 回复语义约束

- 如果当前只存在 1 个待回答问题，Android 可以支持自然语言回复或单个 choice 快捷回复
- 如果当前存在 2 个及以上待回答问题，必须使用结构化回答
- UI 可以向用户显示友好 label，但发信时必须发送 canonical key；Android 当前本地发送前校验也会拒绝把
  display label 或其他非 canonical 值当成 choice 答案发出
- Android 当前本地发送前校验会拒绝 required 问题未答完的多题草稿
- Android 当前本地发送前校验会拒绝包含未知 `question_id` 的多题草稿
- 不应把多题等待态错误降级成“点一个选项直接发出整组答案”

协议兼容格式包括：

```text
Answers:
phase2_entry_position: below
phase2_icon_strings: provide
```

也兼容真实邮箱常见的两行写法：

```text
question_id: phase2_entry_position
账户列表下方（设置附近）
question_id: phase2_icon_strings
你提供
```

Android 当前也兼容这两类结构化格式：

- 单行 `question_id: answer`
- legacy 两行 `question_id:` 换行 `answer`

## 12. Attachment 规则

### 12.1 读取侧

- timeline 只应显示真实附件，不应把 `multipart/*` 容器当成可展示附件
- 附件展示应保留文件名、类型、inline/image 等元数据
- timeline attachment 的 `Open` / `Save` 只应在本地邮件附件真实可用时提供
- inline HTML 图片与附件的绑定只认 `contentId`
- Android 侧可以去掉 `Content-ID` 外层尖括号和首尾空白做匹配，但不应再按文件名、顺序、caption 猜测
- `cid:` 无法匹配到附件时，不显示 inline preview，但附件仍按普通 attachment 展示
- static attached `image/svg+xml` 走与其他 inline image 相同的预览路径；不引入公式专用协议字段
- externally delivered files 不是当前邮件正文里的 inline body image

### 12.2 发送侧

- outgoing attachments 可以作为 reply 的补充输入
- 允许 free-text continuation 携带附件
- 单题回答可以携带附件
- 多题回答可以携带附件，但附件不能替代结构化 `Answers:` 正文
- `/status` 不能携带附件

### 12.3 允许与禁止

允许：

- attachment-only 的普通 continuation reply
- attachment-only 的部分单题等待态 reply

禁止：

- 多题等待态只发附件不发结构化答案
- `/status` 携带附件

## 13. Workspace / Session 展示规则

### 13.1 首页结构

TaskMail 首页默认采用：

```text
Repo / Workdir
  -> Session
    -> Messages
```

更具体地说：

- `workspace` 层显示 `repo_path + workdir`
- `session` 层显示 `session_name`
- 邮件消息层显示状态邮件、用户 reply 与附件上下文

### 13.2 分组主键

推荐顺序：

1. `workspace_id`
2. 若缺失，再退化为 `repo_path + workdir`

子分组主键：

1. `session_id`
2. 若缺失，再临时退化为 `thread_id`

### 13.3 明确不要做的事情

- 不要只按邮件主题分组
- 不要把不同 repo 的 session 混在一个扁平线程列表里
- 不要把 `backend_session_id` 当成 UI 树节点 ID
- 不要把 `paused` 当成普通文案而丢掉显式恢复语义

## 14. 当前 Android Guardrails

- preserve mode-driven reply semantics，不要把所有回复都降级成 generic free text
- 单题和多题等待态必须分开处理
- 显示给用户的是 label，发出去的是 canonical key
- 多题本地发送前校验必须拦截 required 缺失、未知 `question_id`、以及非 canonical 的 choice 值
- `paused` 是一等状态，需要显式 `/resume`
- 回复主题必须保留 `[S:session_id]`、状态标签和 backend token
- 若后端协议变化，先更新 PC 侧 canonical 文档，再更新本文件与 Android 实现
