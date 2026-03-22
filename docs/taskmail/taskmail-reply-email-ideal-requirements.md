# TaskMail Reply 邮件理想要求

## 状态

本文是基于当前 `TaskMail` Android 实现与相关解析逻辑整理出的草案型目标规范。

它描述的是一封 `TaskMail` reply 邮件在 wire 上“理想情况下应当长什么样”，并且在有助于提升 session
稳定性、reply 安全性与下游解析可靠性时，会刻意比当前实现更严格。

## 目的

本文定义 `TaskMail` reply 邮件的目标形态，供以下场景使用：

- reply message generation
- Android UI 中的 reply validation
- 未来 backend / client 互操作
- 跨物理 mail thread 的 session continuity

它不是对“当前行为”的单纯抄录。
如果当前代码与本文不一致，应把本文视为目标产品形态，并将差异记录为实现债务。

## 适用范围

本文适用于用户发出的 `TaskMail` reply，包括：

- free-text reply
- quick-answer reply
- structured-answer reply
- `/status` reply

它不定义系统生成的 status 邮件或 question 邮件格式。

## 理想的 Reply 邮件形态

### 1. Subject

`TaskMail` reply 邮件应在主题中保留任务身份标识。

必需：

- wire subject 应使用 canonical reply 前缀 `Re:`
- 主题应保留原始 `TaskMail` 任务标题文本
- 只要 session ID 已知，主题就应保留或补出稳定的逻辑 session token `[S:<session_id>]`
- 如果 backend token 已知，应保留，例如 `[CX]` 或 `[OC]`
- 如果锚点主题本来就带有 `TaskMail` 状态 token，也应保留，例如 `[QUESTION]`、`[DONE]` 或 `[RUNNING]`

约束：

- reply 主题改写不能丢掉后续 session 检测所需的 `TaskMail` 身份 token
- 规范化后的 canonical subject 不应依赖本地化 reply 前缀，如 `AW:`、`FW:`、`Fwd:`，或中文回复前缀

推荐示例：

```text
Re: [QUESTION] [S:session-42] [CX] Analyze floor_shear
Re: [DONE] [S:thread_026] [OC] Timeline test
```

### 2. Threading Headers

`TaskMail` reply 邮件应是真实的 mail-thread reply，而不是松散关联的新邮件。

必需：

- `In-Reply-To` 必须指向所选 reply anchor 的 message ID
- `References` 必须包含既有 reference chain 以及所选 reply anchor 的 message ID
- reply anchor 应取自同一逻辑 `TaskMail` session 中最新、且仍可安全用于 reply 的消息

约束：

- 当一个逻辑 session 横跨多个 account 时，不应允许 reply
- 当 anchor message 无法解析为稳定的本地 message reference 时，不应允许 reply
- 选择 anchor 时应取最新 logical-session message，而不是仅仅取最新 physical thread copy

### 3. Addressing

必需：

- sender identity 应尽量使用最初接收 anchor message 的那个 identity
- 主 reply 目标应遵循普通 reply 解析规则：`Reply-To`，然后 `List-Post`，然后 `From`
- `TaskMail` reply 默认应按普通 reply 发送，而不是 reply-all

推荐：

- 只有在解析出的 reply 目标明确要求时才带 `Cc`
- `Bcc` 应保持为空，除非未来有明确产品规则要求

### 4. Body 格式

必需：

- reply body 应以 `text/plain` 发送
- body 只应包含这次 reply 的用户有效载荷
- 默认不应附带引用的原邮件正文
- 不应附带机器可读的 `TaskMail` state 或 question capsules

理由：

- 当前 detector 有意把 reply-like messages 当作用户消息处理
- 引用块、元数据块与 capsules 会增加解析噪音，降低 timeline 抽取可靠性

### 5. 按 Reply 类型区分的 Body 内容

#### 5.1 Free-Text Reply

必需：

- body 可以是用户输入的任意纯文本
- 除非 transport 层有强制规范化要求，否则应保留前导、内部与换行空白

允许：

- 可以带附件
- 如果产品要求允许文件作为完整响应，则 free-text reply 可以是 attachment-only

#### 5.2 Quick-Answer Reply

必需：

- body 应是用户选中的精确 choice value
- 只有当 session 当前恰好存在一组单题 pending question 时，才应暴露 quick-answer reply

允许：

- 如果产品把附件视为补充上下文，可以附带附件

#### 5.3 Structured-Answer Reply

当同时存在多道 pending question 时，structured reply 是首选格式。

必需：

- body 至少应包含一条 structured answer
- 每条 answer 都应以稳定、机器友好的形式表达
- 推荐的单行形式是：

```text
question_id: value
```

- 推荐的多答案模板应以以下内容起始：

```text
Answers:
```

推荐示例：

```text
Answers:
phase2_entry_position: below
phase2_icon_strings: reuse
```

允许的兼容形式：

- `question_id: <known_question_id>` 后一行再写答案
- 在输入法行为需要时，使用全角冒号作为分隔符

约束：

- 附件可以作为 structured reply 的补充，但不应完全替代 structured answers
- 如果当前 reply 类型要求 structured answer，那么 attachment-only 提交应视为不完整

#### 5.4 Status Query Reply

必需：

- body 必须严格等于 `/status`
- `/status` 前后都不应再加额外说明文字

约束：

- `/status` reply 不得带附件

### 6. Attachments

必需：

- reply attachments 应作为普通外发附件发送
- reply attachments 不应被转换成引用型 inline artifacts

允许：

- free-text、quick-answer 与 structured-answer replies 可以带附件

不允许：

- `/status` reply 带附件

### 7. 不应包含的内容

除非未来协议明确要求，否则理想的 `TaskMail` reply 邮件应避免包含以下内容：

- 引用原邮件正文的块
- `On ... wrote:` 这类引用前缀块
- `-----Original Message-----` 这类块
- 本地化的 original-message quote blocks
- `---TASK-STATE-BEGIN--- ... ---TASK-STATE-END---`
- `---TASK-QUESTION-BEGIN--- ... ---TASK-QUESTION-END---`
- 拷贝出来的结构化元数据，例如 `Session ID`、`Thread ID`、`Repo`、`Workdir`、`Backend`、`Status`

## 理想验证规则

Android 客户端在发送前应执行以下验证规则：

- 只有当存在有效 reply context 时，reply 才可用
- free-text reply 在 body 非空，或至少选中一个附件时可发送
- quick-answer reply 只有在恰好存在一组单题 choice set，且所选 choice 属于该集合时才可发送
- structured reply 只有在至少存在一条有效 structured answer 时才可发送
- `/status` 只有在未选择任何附件时才可发送
- 当 session 跨 account 且没有单一安全 reply identity 时，应阻止发送

## 与当前代码的一致部分

当前实现已经满足这个目标中的一部分：

- replies 会构造成真实的 mail-thread replies，并带 `In-Reply-To` 与 `References`
- `TaskMail` reply 发送 `text/plain` body
- `TaskMail` reply 默认关闭 quoted text
- `/status` 已作为独立 reply kind 建模
- reply context 有意锚定到最新的 logical-session message

## 相对这份理想规范的已知缺口

基于当前代码，已知仍有以下缺口：

- 目前 reply subject generation 只会在加 `Re:` 前剥掉德语 `AW:` 前缀，尽管解析侧接受更多 reply-like prefixes
- 当前 reply subject generation 直接复用源主题；当 anchor subject 不完整时，不会主动补出缺失的 session 或
  backend tokens
- structured reply validation 在某些场景下仍允许 attachment-only send，而本文要求至少有一条 structured answer
- 当前 reply request context 还不携带 canonical session metadata，如 `session_id`、backend token 或 canonical
  subject，这限制了 subject normalization

## 建议的后续工作

如果要把实现推进到这份目标，客户端大概率需要：

- 一个理解 session ID、backend 与 anchor subject 的 canonical `TaskMail` reply-subject builder
- 更严格的 UI 层 structured-reply validation
- 把 canonical reply metadata 与原始 anchor-message data 更清晰地分离开

## 来源依据

本文目标整理自当前行为与以下契约：

- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/data/LegacyTaskMailMimeMessageFactory.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/data/DefaultTaskMailRepository.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/parser/TaskMailSubjectParser.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/parser/TaskMailMessageDetector.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/TaskSessionDetailUiState.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/TaskSessionDetailViewModel.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/data/LegacyTaskMailBodyExtractor.kt`
- `legacy/core/src/main/java/com/fsck/k9/helper/ReplyToParser.java`
- `legacy/core/src/main/java/com/fsck/k9/helper/IdentityHelper.kt`
