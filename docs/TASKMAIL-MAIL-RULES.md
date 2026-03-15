# TaskMail 邮件规则清单

## 1. 目的

这份文档用于记录 **TaskMail Android 客户端会依赖的邮件协议规则**，方便后续做：

- `repo / workdir / session` 树视图
- 任务线程详情页
- 状态块解析
- 回复动作映射

本文档以当前两侧现状为准：

- Android 仓库中的 `docs/taskmail_project_overview.md`
- 后端仓库 `mail_based_task_manager` 的当前 README 与实现
- 尤其参考后端中的：
  - `mail_runner/state_capsule.py`
  - `mail_runner/reporter.py`
  - `mail_runner/thread_store.py`

如果设计文档与后端实现冲突，**以当前后端实际邮件协议为准**。

---

## 2. 核心标识

| 字段 | 含义 | Android 侧用途 | 备注 |
| --- | --- | --- | --- |
| `workspace_id` | 工作区 ID | 作为一级分组 key | 后端内部由 `repo_path + workdir` 派生 |
| `repo_path` | 仓库根路径 | 展示与兜底分组 | 人类可读 |
| `workdir` | 仓库内工作目录 | 展示与兜底分组 | 可为空 |
| `session_id` | 应用层 session ID | 作为二级分组 key | 这是 TaskMail UI 的主会话标识 |
| `session_name` | 会话标题 | 列表展示 | 来自规范化后的任务标题 |
| `thread_id` | 邮件线程 ID | 邮件侧关联与兼容字段 | 当前实现中常与 `session_id` 相同，但客户端不要假定二者永远相等 |
| `task_id` | 某次执行快照 ID | 详情页和状态展示 | 不是分组 key |
| `backend` | `opencode` / `codex` | 展示、筛选 | 来自主题前缀或状态邮件 |
| `backend_session_id` | 原生 CLI session token | 不作为 UI 主键 | 用于后端 resume，不等于 `session_id` |

### 2.1 关键语义

- `workspace` 是 `repo_path + workdir` 这一层。
- `session` 是工作区内的长期任务上下文。
- `run` 是某个 session 的一次具体执行。
- Android 首页默认应该按 `workspace -> session` 展示，而不是把所有邮件平铺成一个列表。

### 2.2 重要约束

- `session_id` 是应用层稳定标识，**不要**拿 `backend_session_id` 代替。
- 当前后端很多地方仍使用 `session_id or thread_id`，所以 Android 需要同时保留这两个字段。
- 同一个 `workspace` 下允许有多个 `session`。
- 同一个 `workspace` 同时只允许 1 个 active session。

---

## 3. 主题规则

### 3.1 新任务主题

新任务邮件主题必须以以下前缀之一开头：

- `[OC]` -> `opencode`
- `[CX]` -> `codex`

前缀之后的文本是人类可读任务标题，也是 `session_name` 的重要来源。

示例：

```text
Subject: [OC] Refactor floor_shear
Subject: [CX] Analyze floor_shear
```

### 3.2 状态邮件主题

状态邮件主题格式为：

```text
[STATUS_LABEL][S:session_id] <original subject>
```

当前状态标签包括：

- `[ACCEPTED]`
- `[RUNNING]`
- `[DONE]`
- `[FAILED]`
- `[STATUS]`
- `[KILLED]`
- `[QUESTION]`

其中：

- `[S:session_id]` 是 session fallback 标识
- `<original subject>` 通常保留原任务标题

### 3.3 Reply 主题兼容

回复主题应兼容常见前缀：

- `Re:`
- `FW:`
- `Fwd:`
- `回复:`
- `回复：`
- `答复:`
- `答复：`

### 3.4 禁止的推断

- **不要**用“相同主题”自动串接旧 session。
- 主题归一化只用于辅助，不是主路由依据。
- reply 解析优先级必须稳定且显式，见第 5 节。

### 3.5 旧设计说明

早期方案中出现过 `[KILL] <task_id>` 这种独立主题控制方式。当前后端实际协议已经以 **reply + slash command** 为主，因此 Android 侧应优先跟随后端当前实现：

- `/status`
- `/resume`
- `/new`
- `/sessions`
- `/rerun`
- `/kill`

---

## 4. 首封任务邮件规则

### 4.1 结构

首封任务邮件正文采用半结构化格式，典型字段如下：

```text
Repo: D:\proj\my_repo
Workdir: src\postprocess
Timeout: 60
Mode: modify
Profile: strong

Task:
把 floor_shear.py 重构为 dataclass 风格，保持现有输出不变

Acceptance:
1. pytest tests/test_floor_shear.py 通过
2. 不改 public API
3. 输出简短修改说明
```

### 4.2 字段要求

- `Repo:` 必填
- `Task:` 必填
- `Workdir:` 可选
- `Timeout:` 可选，缺失时用默认值
- `Mode:` 可选，缺失时默认为 `modify`
- `Profile:` 可选
- `Acceptance:` 可为空列表

### 4.3 解析要求

- 解析器应宽容大小写和常见空行变化。
- 邮件正文优先取 `text/plain`。
- 如果 plain part 为空，可回退解析 `text/html`。

---

## 5. Reply 路由与动作规则

### 5.1 Reply 命中优先级

reply 邮件只能命中已有 session，优先级如下：

1. `In-Reply-To`
2. `References`
3. 邮件正文里的 `state capsule`
4. 主题中的 `[S:session_id]`

额外约束：

- 不再使用“同主题自动接旧 session”的兜底策略
- reply 不允许在同一线程里分叉出新的 session

### 5.2 普通 reply

普通自然语言 reply 默认含义是：

- 继续当前 session
- 追加上下文
- 或回答当前问题

如果当前线程处于 `[QUESTION] / awaiting_user_input`，允许直接回答，不强制要求写 `/resume`。

### 5.3 Slash 命令

当前 reply 支持以下命令：

| 命令 | 含义 |
| --- | --- |
| `/resume` | 继续当前 session |
| `/new` | 在当前 workspace 下发起新 session |
| `/sessions` | 查看当前 workspace 下的 session 列表 |
| `/status` | 查询当前 session 状态 |
| `/rerun` | 重新执行当前 session |
| `/kill` | 终止当前 session |

说明：

- `/new` 是少数允许显式创建 fresh session 的 reply 行为。
- 除 `/new` 外，其余 reply 动作都应指向已存在 session。

### 5.4 Reply 正文抽取规则（展示 / transcript 侧）

后端当前对 user reply 的正文抽取，采用“**只取本轮新增内容**”的思路：

1. 先统一换行符
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

Android 侧建议：

- timeline 中展示 user message 时，优先展示这类“reply delta”
- 若无法可靠抽取 delta，再回退到规范化后的完整正文
- 这条规则属于**展示层正文抽取**，不替代第 5.1 节中的 session 路由规则

---

## 6. 状态邮件正文规则

状态邮件正文会尽量包含以下信息：

- `Status`
- `Session ID`
- `Thread ID`
- `Task ID`
- `Backend`
- `Repo`
- `Workdir`
- `Summary` 或 `Reply`

如果当前处于提问态，正文还会包含：

- `Question Set ID`
- `Question ID`
- `Question`
- `Choices`
- `Received Answers`
- `Allowed values`
- `Answers:` 模板

Android 侧可以把这部分当作人类可读摘要，但**机器解析应优先使用状态块**。

### 6.1 状态邮件正文展示抽取规则

后端当前在 transcript/export 场景中，对 system message 正文采用如下展示提取顺序：

1. 先移除：
   - `TASK-STATE` 块
   - `TASK-QUESTION` 块
2. 若正文中包含 `Reply:` 段，则优先取 `Reply:` 之后的内容
3. 否则若存在 `Summary:` 行，则优先取 `Summary:` 的值
4. 否则若 `Status:` 行前存在一段前置说明文本，则优先取该段说明
5. 否则若存在 `Question:`，则展示：
   - `Question: ...`
   - 如存在，再附带 `Choices: ...`
6. 否则若存在 `Status:`，则展示 `Status: ...`
7. 若以上都没有命中，则回退为剥离 capsule 后的正文

Android 侧建议：

- detail timeline 中展示 assistant / system message 时，优先复用这套展示提取顺序
- 这条规则主要服务于**人类可读的 timeline 正文**
- session 聚合、状态判断、question 判断仍应优先依赖 capsule

---

## 7. State Capsule 规则

### 7.1 当前状态块格式

每封状态邮件底部应包含如下机器可读块：

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
status: done
last_summary: Finished successfully
---TASK-STATE-END---
```

### 7.2 当前字段清单

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
- `last_summary`

### 7.3 解析规则

- 只解析 **最后一个完整出现** 的状态块
- 字段值按单行处理
- 多行文本会被压平为单行空格分隔文本
- 若状态块不存在或不完整，则返回 `None`，再回退到其他线索

### 7.4 Android 侧要求

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
- `last_summary`

客户端分组建议：

- 一级分组：`workspace_id`
- 二级分组：`session_id`
- 展示字段：`session_name`、`status`、`last_summary`

---

## 8. Question Capsule 规则

### 8.1 多问题等待态

等待用户回答时，状态邮件可以追加一个或多个问题块。

如果同一封状态邮件里存在多个问题块，它们必须共享同一个 `question_set_id`。

示例：

```text
---TASK-QUESTION-BEGIN---
question_set_id: phase2_clarifications
question_id: phase2_entry_position
question_type: single_choice
required: true
question_text: Where should the Tasks drawer entry be placed?
choices: top | below | section
choice_labels: top=Account list top | below=Account list bottom | section=Standalone section
---TASK-QUESTION-END---
---TASK-QUESTION-BEGIN---
question_set_id: phase2_clarifications
question_id: phase2_icon_strings
question_type: single_choice
required: false
question_text: Who provides icon and string resources?
choices: provide | reuse | placeholder
choice_labels: provide=You provide | reuse=Reuse existing | placeholder=Temporary placeholder
---TASK-QUESTION-END---
```

### 8.2 字段说明

- `question_set_id`
  - 多题等待态的稳定分组标识
- `question_id`
  - 单题标识
- `question_type`
  - 当前后端至少会出现 `single_choice`，后续也可能出现 `boolean` / `short_text`
- `required`
  - 是否必答；缺失时按 `true` 处理更安全
- `question_text`
  - 人类可读问题内容
- `choices`
  - 允许值列表，使用 ` | ` 分隔
- `choice_labels`
  - canonical key 到展示文案的映射，使用 `key=value | ...` 形式

### 8.3 Android 侧读取要求

- 解析 **所有完整问题块**，并保留原始顺序
- 如果同一封邮件里出现多个不同的 `question_set_id`，视为协议冲突，不做乐观拼接
- `choices` 继续使用 ` | ` 分隔
- `choice_labels` 需要按 `key=value` 形式解析并保留
- 如果只有 1 个问题块且没有 `question_set_id`，按 legacy 单题兼容
- 详情页可以继续展示一个“主问题”，但底层模型必须保留完整问题列表

### 8.4 回复语义约束

- 如果当前只存在 1 个待回答问题，Android 可以继续支持自然语言回复或单个 choice 快捷回答
- 如果当前存在 2 个及以上待回答问题，后端协议要求使用结构化回答
- 推荐格式如下：

```text
Answers:
phase2_entry_position: below
phase2_icon_strings: provide
```

- 也兼容真实邮箱常见的两行写法：

```text
question_id: phase2_entry_position
账户列表下方（设置附近）
question_id: phase2_icon_strings
你提供
```

- Android 不应把多题等待态错误降级成“点一个选项直接发出整组答案”

---

## 9. Workspace / Session 展示规则

### 9.1 首页结构

TaskMail 首页默认采用如下结构：

```text
Repo / Workdir
  -> Session
    -> Messages
```

更具体地说：

- `workspace` 层显示 `repo_path + workdir`
- `session` 层显示 `session_name`
- 邮件消息层显示各轮状态邮件与用户 reply

### 9.2 分组主键

推荐顺序：

1. `workspace_id`
2. 若缺失，再退化为 `repo_path + workdir`

子分组主键：

1. `session_id`
2. 若缺失，再临时退化为 `thread_id`

### 9.3 明确不要做的事情

- 不要只按邮件主题分组
- 不要把不同 repo 的 session 混在一个扁平线程列表里
- 不要把 `backend_session_id` 当成 UI 树节点 ID

---

## 10. 当前已知缺口

当前邮件协议已经足够支撑 `workspace -> session` 树视图，但仍有两个值得记录的缺口：

1. `updated_at` / `last_event_at` 还没有进入 `state capsule`
   - Android 目前更适合按邮件日期排序
2. `backend_session_id` 目前是后端持久化字段，不是邮件层稳定暴露字段
   - Android 不应依赖它做主路由

---

## 11. 对 Android Phase 1 的直接影响

基于当前协议，Android 侧 Phase 1 可以直接做：

- `state capsule` 解析
- `workspace -> session` 分组模型
- `TaskWorkspaceScreen` 静态 UI 与假数据预览
- `TaskSessionDetailScreen` 的状态卡片骨架

当前不需要额外发明一套新的 session 标识协议。
