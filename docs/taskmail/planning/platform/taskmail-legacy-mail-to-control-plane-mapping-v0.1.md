# TaskMail 旧 Mail 语义到控制面映射附录（v0.1）

更新时间：2026-03-25  
状态：平台层 companion appendix；用于冻结 legacy mail 语义如何映射到 `VPS-first` 控制面，不替代 current-truth 文档

> 说明：
>
> 本文回答的是“当前 mail-first 协议里已经验证过的业务语义，迁到 `VPS-first 多 PC 控制面` 后应如何落到 `command / event / result`”。
>
> 本文不声称当前仓库已经完成迁移。
>
> 当前实现事实仍以：
>
> - `docs/TASKMAIL-MAIL-RULES.md`
> - `E:\projects\mail_based_task_manager\docs\current\mail_protocol.md`
>
> 为准。

## 1. 文档目的

本文只回答一个问题：

**哪些旧 mail 语义应被复用进新控制面，分别落到哪些 canonical 字段。**

本文聚焦：

- mail 外壳字段如何处置
- 首封任务、reply、slash command 如何映射到 `command`
- 当前状态邮件标签如何映射到 `event` / `result`
- waiting-state、`Answers:`、附件语义如何进入新协议

本文不负责：

- Android UI 控件设计
- 当前 mail-first 现状说明
- 多用户 ACL 或通知策略

## 2. 固定结论

当前主线应固定以下结论：

1. 应复用的是“任务业务语义”，不是“邮件外壳本身”。
2. `Message-ID / In-Reply-To / References / Re:` 这些字段在新主线里只保留兼容入口与备份邮件价值，不再做主控制面主键。
3. `new_task / reply / status / pause / resume / kill / question / answers / artifacts` 这些业务语义应进入 canonical `command / event / result / artifact`。
4. 第一批协议已冻结的 `command_type` 只覆盖最小闭环；旧 mail 协议里更宽的动作集合可以继续保留为 deferred mapping，但不能混进第一批协议假装已经实现。

## 3. Mail 外壳字段处置表

| 当前 mail 字段 / token | 新主线中的角色 | canonical 去向 |
| --- | --- | --- |
| `Message-ID` | 备份邮件与审计锚点 | 不进入 `command` 主键；若保留，写入 `backup_mail_message_id` 或 mail projection metadata |
| `In-Reply-To` | 兼容入口路由线索 | 只用于 ingress 阶段解析 `session_id`，不进入控制面 canonical payload |
| `References` | 兼容入口路由线索 | 同上；只用于 reply thread 解析 |
| `Subject: [OC] ...` | 新任务 backend 选择 | 映射到 `command_type = new_task` 且 `execution_policy.backend = opencode` |
| `Subject: [CX] ...` | 新任务 backend 选择 | 映射到 `command_type = new_task` 且 `execution_policy.backend = codex` |
| `Subject: [SYNC]` | bootstrap 只读动作 | 映射到 `command_type = sync_project_folders`；不创建 `session/run` |
| `Subject: [KILL] <task_id>` | 旧首封 kill 兼容入口 | 推荐映射到 `command_type = kill`；属于 compatibility-only 入口，不是新主路径 |
| 主题中的 `[S:session_id]` | 兼容入口路由线索 | 只在 ingress 解析时帮助命中 `session_id` |
| `Re:` / `FW:` / `Fwd:` | 邮件投影视图前缀 | 不进入 canonical 控制面字段 |

## 4. Mail 正文字段映射表

| 当前字段 | canonical 字段 | 适用动作 | 说明 |
| --- | --- | --- | --- |
| `Repo:` | `command.payload.repo_path` | `new_task` | `workspace` 选择完成前的 repo 定位输入 |
| `Task:` | `command.payload.task_text` | `new_task`、`reply`、`resume`、`/new` | 在 follow-up 中表示新的 continuation 指令或 override 任务文本 |
| `Workdir:` | `command.payload.workdir` | `new_task`、`/new` | 与 `Repo:` 一起定位 `workspace` |
| `Timeout:` | `command.payload.timeout_seconds` | `new_task`、`reply`、`resume`、`/new` | 控制执行超时 |
| `Mode:` | `command.payload.mode` | `new_task`、`reply`、`resume`、`/new` | 例如 `modify / analysis_only` |
| `Profile:` | `execution_policy.profile` | 同上 | 不再留在 mail sidecar |
| `Permission:` | `execution_policy.permission` | 同上 | 不再留在 mail sidecar |
| `Acceptance:` | `command.payload.acceptance` | `new_task`、`reply`、`/new` | 继续保留为业务参数 |
| 普通 reply 文本 | `command.payload.reply_text` | `reply`、`resume` | 单题等待态可继续复用 free text |
| `Answers:` 结构化正文 | `command.payload.answers` | `reply`、`resume` | 形状建议为 `map<question_id, canonical_answer>` |
| reply 附件 | `command.payload.input_artifacts[]` | `reply`、`resume`、`new_task` | 当前只冻结“进入 canonical payload”，具体上传/hosting 见 artifact 附录 |

## 5. 动作映射表

| 当前动作入口 | 当前语义 | canonical `command_type` | 主要字段 | 当前建议状态 |
| --- | --- | --- | --- | --- |
| `Subject: [OC] ...` | 新建 OpenCode 任务 | `new_task` | `repo_path/workdir/task_text/execution_policy.backend=opencode` | 第一批 |
| `Subject: [CX] ...` | 新建 Codex 任务 | `new_task` | `repo_path/workdir/task_text/execution_policy.backend=codex` | 第一批 |
| `Subject: [SYNC]` | 项目目录同步 | `sync_project_folders` | `payload.roots_scope?` | companion bootstrap，保留 |
| 普通 reply | 继续当前 session 或回答单题 | `reply` | `session_id + reply_text` | 第一批 |
| `Answers:` | 回答多题等待态 | `reply` | `session_id + answers{}` | 第一批 |
| `/status` | 查询当前 session 状态 | `status` | `session_id` | 第一批 |
| `/pause` | 暂停后续 continuation | `pause` | `session_id` | 第一批 |
| `/resume` | 恢复 paused session，必要时附带答案 | `resume` | `session_id + reply_text? + answers?` | 第一批 |
| `/kill` | 终止当前 run/session | `kill` | `session_id` | 第一批 |
| `/new` | 在当前 workspace 下另起 fresh session | 推荐仍映射到 `new_task` | `source_session_id + task_text + workspace_id` | deferred |
| `/continue <session_id>` | same-workspace targeted continuation | 推荐映射到 `reply` | `session_id + reply_text` | deferred |
| `/sessions` | 当前 workspace 会话列表 | `list_sessions` | `workspace_id` | deferred |
| `/rerun` | 重新执行当前 session | `rerun` | `session_id` | deferred |
| `/end` | 改写 lifecycle 为 ended | `end` | `session_id` | deferred |
| `/restart-runner` | 本机 host 控制动作 | `host_control_restart` | `pc_id` | compatibility-only，不属于 TaskMail 任务协议第一批 |
| `Subject: [KILL] <task_id>` | 旧首封 kill 入口 | 推荐映射到 `kill` | legacy `task_id` 到 `session/run` 的兼容解析 | compatibility-only |

补充说明：

- “第一批”表示已进入当前 `PC <-> VPS` 最小协议冻结面。
- “deferred”表示语义保留，但还不在第一批协议骨架里。
- “compatibility-only”表示应继续承认其历史价值，但不应当成新主线默认入口。

## 6. 状态标签映射表

| 当前 mail 标签 | 新主线 canonical 投影 | 说明 |
| --- | --- | --- |
| `[ACCEPTED]` | `event.event_type = accepted` | 这是状态流，不要求单独产出 `result` |
| `[RUNNING]` | `event.event_type = running` | 同上；可附带 `effective_execution_policy` |
| `[QUESTION]` | `event.event_type = awaiting_user_input`，必要时伴随 `result.final_status = awaiting_user_input` | `result.structured_payload.kind` 建议为 `question_set` 或 `session_status_snapshot` |
| `[PAUSED]` | `event.event_type = paused`，必要时伴随 `result.final_status = paused` | `paused` 仍是显式一等状态 |
| `[DONE]` | `result.final_status = done` | terminal result |
| `[FAILED]` | `result.final_status = failed` | terminal result |
| `[KILLED]` | `result.final_status = killed` | terminal result |
| `[STATUS]` | `command_type = status -> result.final_status = 当前 session 状态` | `structured_payload.kind` 建议为 `session_status_snapshot` |
| `[SYNC]` | `command_type = sync_project_folders -> result.structured_payload.kind = sync_project_folders` | 不进入 task session timeline |

补充说明：

- 当前 mail 里的 `replacement / receipt / action_required` 保留语义，属于 projection 层策略，不是控制面状态机本身。
- 新主线只冻结 canonical 状态与结果对象；“哪类结果要不要替换旧邮件”属于 mail exporter 责任。

## 7. Waiting-State 与答案映射表

| 当前 mail 语义 | canonical 去向 | 说明 |
| --- | --- | --- |
| 单题等待态 | `event.event_type = awaiting_user_input` + `question_set.questions[0]` | 可继续允许 free-text 低摩擦回答 |
| 多题等待态 | 同上，但 `question_set.questions[]` 长度 > 1 | 不得降级成单个 free-text choice |
| 单题自由文本回答 | `command_type = reply` 或 `resume` + `payload.reply_text` | 由执行侧在命中 question context 后归一化 |
| 多题 `Answers:` | `command_type = reply` 或 `resume` + `payload.answers{question_id -> canonical_answer}` | 这是 canonical 多题读法 |
| `paused` 后 `/resume` 不带答案 | `command_type = resume` | 如果仍有 pending question set，则恢复为 `awaiting_user_input` |
| `paused` 后 `/resume` 带 `Answers:` | `command_type = resume` + `payload.answers{}` | 答不全则保持 `awaiting_user_input` |
| attachment-only continuation | `command.payload.input_artifacts[]` | 可继续作为普通 continuation 输入 |
| 多题等待态只发附件 | 不合法 | 附件不能替代 `payload.answers{}` |

## 8. 一句话结论

**旧 mail 协议里真正该复用的是任务业务语义：`new_task / reply / status / pause / resume / kill / question / answers / artifacts` 都应收口进 `command / event / result / artifact`；`Message-ID / In-Reply-To / Re:` 等邮件外壳字段则降级成兼容入口和备份投影信息。**
