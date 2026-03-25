# TaskMail `command / event` Payload 附录（v0.1）

更新时间：2026-03-25  
状态：平台层 companion appendix；用于冻结 `command.payload` 与 `event.payload` 的字段读法，不替代 current-truth 文档

> 说明：
>
> 本文回答的是：在 `VPS-first 多 PC 控制面` 下，不同 `command_type` 和 `event_type` 各自应该携带哪些结构化字段。
>
> 本文不声称当前代码已经全部实现这些 payload。

## 1. 文档目的

本文只回答两个问题：

1. 每个 `command_type` 的 `payload` 应长什么样
2. 每个 `event_type` 的 `payload` 应长什么样

本文聚焦：

- 顶层 envelope 之内的业务 payload
- 必需字段、可选字段、继承规则
- 第一批协议与 deferred 动作的边界

本文不负责：

- `execution_policy` 的详细语义
- `result` / `artifact` / `error_code` 的详细字段
- 当前 mail-first 行为说明

## 2. 固定结论

当前主线应固定以下结论：

1. `command_type` 决定 `command.payload` 的形状。
2. `event_type` 决定 `event.payload` 的形状。
3. `command.payload` 不应再是“自由文本 + 隐式猜测”。
4. `event.payload` 不应再只有一句松散文本；等待态、paused、失败等都应有结构化字段。
5. `execution_policy` 继续放在 `command` / `event` / `result` 的并列字段里，不混进业务 payload。

## 3. 通用规则

### 3.1 `command`

每个 `command` 仍共享这些顶层字段：

- `command_id`
- `command_type`
- `pc_id`
- `workspace_id`
- `session_id`
- `execution_policy`
- `payload`
- `issued_at`
- `issuer_id`

规则：

- `new_task` 通常需要 `workspace_id`；若控制面支持“先按 `repo_path + workdir` 选 workspace”，则 `workspace_id` 也可在路由前留空。
- follow-up 动作通常必须有 `session_id`。
- `execution_policy` 若省略局部字段，按当前 session 继承规则处理。

### 3.2 `event`

每个 `event` 仍共享这些顶层字段：

- `event_id`
- `command_id`
- `workspace_id`
- `session_id`
- `run_id`
- `event_type`
- `payload`
- `emitted_at`

规则：

- `event` 必须尽量回答“现在发生了什么”，而不是只给日志文本。
- 若某个 `event_type` 不需要业务 payload，也建议显式使用空对象 `{}`，避免不稳定的“有时 null、有时缺省”。

## 4. `command_type` Payload 表

### 4.1 `new_task`

用途：

- 创建一个 fresh session，并触发首轮执行

建议 `payload` 字段：

| 字段 | 是否必需 | 说明 |
| --- | --- | --- |
| `repo_path` | 条件必需 | 在 `workspace_id` 尚未预先选定时必需 |
| `workdir` | 可选 | 与 `repo_path` 一起定位 workspace |
| `task_text` | 必需 | 用户任务正文 |
| `mode` | 可选 | 例如 `modify / analysis_only` |
| `timeout_seconds` | 可选 | 执行超时 |
| `acceptance` | 可选 | 验收标准 |
| `input_artifacts[]` | 可选 | 首封任务附带输入附件 |
| `source` | 可选 | 例如 `android / mail_import / compatibility_bridge` |

补充规则：

- `execution_policy.backend` 对 `new_task` 必须显式给出。
- 若 `workspace_id` 已由控制面显式选择，`repo_path/workdir` 可只保留作审计镜像，甚至省略。

### 4.2 `reply`

用途：

- 对当前 session 做 continuation
- 回答单题或多题等待态

建议 `payload` 字段：

| 字段 | 是否必需 | 说明 |
| --- | --- | --- |
| `reply_text` | 条件必需 | 普通 continuation 或单题回答时常见 |
| `answers` | 条件必需 | 多题等待态或结构化回答时使用；建议 `map<question_id, canonical_answer>` |
| `timeout_seconds` | 可选 | 本轮 override |
| `mode` | 可选 | 本轮 override |
| `acceptance` | 可选 | 本轮 override |
| `input_artifacts[]` | 可选 | 附加输入附件 |

补充规则：

- `reply_text` 与 `answers` 至少应有一个。
- 单题等待态允许只给 `reply_text`。
- 多题等待态建议要求 `answers`，并由发送侧校验 required 问题是否齐全。

### 4.3 `status`

用途：

- 查询当前 session 状态

建议 `payload` 字段：

| 字段 | 是否必需 | 说明 |
| --- | --- | --- |
| `include_question_set` | 可选 | 是否在结果里带当前题集 |
| `include_latest_reply_text` | 可选 | 是否带当前可展示最新回复 |
| `include_artifacts` | 可选 | 是否带最小 artifact 引用摘要 |

补充规则：

- `status` 不应触发新的 backend run。
- `status` 的主结果通常落成 `result.structured_payload.kind = session_status_snapshot`。

### 4.4 `pause`

用途：

- 将当前 session 置为 paused

建议 `payload` 字段：

| 字段 | 是否必需 | 说明 |
| --- | --- | --- |
| `reason` | 可选 | 用户或系统暂停原因 |

### 4.5 `resume`

用途：

- 恢复 paused session
- 必要时附带 free-text 或 structured answers

建议 `payload` 字段：

| 字段 | 是否必需 | 说明 |
| --- | --- | --- |
| `reply_text` | 可选 | 普通 continuation 或单题回答 |
| `answers` | 可选 | 多题等待态结构化回答 |
| `timeout_seconds` | 可选 | 本轮 override |
| `mode` | 可选 | 本轮 override |
| `acceptance` | 可选 | 本轮 override |
| `input_artifacts[]` | 可选 | 附加输入附件 |

补充规则：

- `resume` 可以只恢复状态，不附带正文。
- 若当前 paused session 仍有 pending question set，则 `answers` 的规则与 `reply` 相同。

### 4.6 `kill`

用途：

- 终止当前 run 或 session

建议 `payload` 字段：

| 字段 | 是否必需 | 说明 |
| --- | --- | --- |
| `reason` | 可选 | 用户终止原因 |
| `target` | 可选 | `active_run` 或 `session`；第一版可默认 `active_run` |

### 4.7 `sync_project_folders`

用途：

- 返回当前 PC 上可见的项目目录列表

建议 `payload` 字段：

| 字段 | 是否必需 | 说明 |
| --- | --- | --- |
| `roots_scope[]` | 可选 | 指定要扫描的根集合；未给则使用 runner 默认配置 |

### 4.8 Deferred `command_type`

以下动作暂时不在第一批协议骨架里，但建议先冻结 payload 读法：

| `command_type` | 建议 `payload` |
| --- | --- |
| `list_sessions` | `include_ended?`、`limit?` |
| `rerun` | `reuse_last_execution_policy?`、`override_task_text?` |
| `end` | `reason?` |
| `host_control_restart` | `reason?`、`requested_by?` |

## 5. `event_type` Payload 表

### 5.1 `queued`

用途：

- 命令已进入目标 PC 的本地队列

建议 `payload` 字段：

| 字段 | 是否必需 | 说明 |
| --- | --- | --- |
| `queue_position` | 可选 | 当前排队位置 |
| `queue_name` | 可选 | 若存在多队列，可用于调试与展示 |

### 5.2 `accepted`

用途：

- 命令已被执行侧接纳

建议 `payload` 字段：

| 字段 | 是否必需 | 说明 |
| --- | --- | --- |
| `effective_execution` | 推荐 | 当前已确认的执行策略 |
| `accepted_at` | 可选 | 若与 `emitted_at` 语义分开，可显式回报 |

### 5.3 `running`

用途：

- 当前 run 已开始执行

建议 `payload` 字段：

| 字段 | 是否必需 | 说明 |
| --- | --- | --- |
| `effective_execution` | 推荐 | 实际执行策略 |
| `backend_session_id` | 可选 | backend/native session 锚点 |
| `step_summary` | 可选 | 当前阶段简述 |

### 5.4 `awaiting_user_input`

用途：

- 当前 run 需要用户进一步输入

建议 `payload` 字段：

| 字段 | 是否必需 | 说明 |
| --- | --- | --- |
| `question_set` | 必需 | 当前等待题集 |
| `last_summary` | 可选 | 当前等待态摘要 |
| `effective_execution` | 可选 | 若需要保留审计链，可继续带上 |

### 5.5 `paused`

用途：

- 当前 session 进入 paused

建议 `payload` 字段：

| 字段 | 是否必需 | 说明 |
| --- | --- | --- |
| `paused_from_status` | 推荐 | 例如 `awaiting_user_input / done / failed / killed` |
| `reason` | 可选 | 用户或系统暂停原因 |
| `question_set` | 可选 | 若 paused 前仍有 pending question set，可附带当前题集 |

### 5.6 `done`

用途：

- 当前 run 正常完成

建议 `payload` 字段：

| 字段 | 是否必需 | 说明 |
| --- | --- | --- |
| `summary` | 推荐 | 简短完成摘要 |
| `result_preview` | 可选 | 轻量结果摘要；完整收口仍以 `result` 为准 |

### 5.7 `failed`

用途：

- 当前 run 失败结束

建议 `payload` 字段：

| 字段 | 是否必需 | 说明 |
| --- | --- | --- |
| `error_code` | 推荐 | machine-readable 失败分类 |
| `error_type` | 可选 | backend/runtime 错误类型 |
| `error_message` | 可选 | 人类可读错误细节 |
| `summary` | 可选 | 简短失败摘要 |

### 5.8 `killed`

用途：

- 当前 run 被显式终止

建议 `payload` 字段：

| 字段 | 是否必需 | 说明 |
| --- | --- | --- |
| `reason` | 可选 | 终止原因 |
| `killed_by` | 可选 | `user / operator / system` |

## 6. `question_set` 复用形状

为了避免 `awaiting_user_input`、`paused`、`result.structured_payload.question_set` 三处各定义一套，建议统一使用同一结构：

| 字段 | 是否必需 | 说明 |
| --- | --- | --- |
| `question_set_id` | 推荐 | 同一组问题主键 |
| `questions[]` | 必需 | 问题数组 |

每个 `question` 至少包含：

- `question_id`
- `question_type`
- `required`
- `question_text`
- `choices`
- `choice_labels`

## 7. `input_artifacts[]` 建议形状

`command.payload.input_artifacts[]` 当前建议只冻结最小引用读法：

| 字段 | 是否必需 | 说明 |
| --- | --- | --- |
| `artifact_id` | 条件必需 | 若该输入已进入控制面 artifact 空间 |
| `name` | 推荐 | 展示名 |
| `content_type` | 推荐 | MIME / content type |
| `size` | 推荐 | 字节大小 |
| `download_ref` | 条件必需 | 若内容不 inline，则给出可取内容引用 |

补充说明：

- 第一版不要求输入附件必须先进入统一 `artifact_manifest`。
- 但 payload 里也不应只放一个“本地路径字符串”。

## 8. 一句话结论

**下一阶段不是再发明新的 `command`/`event` 词，而是把现有业务动作的 payload 形状固定下来：`command_type` 决定输入结构，`event_type` 决定过程状态结构，`execution_policy` 与 `result/artifact/error_code` 则继续作为并列层解耦。**
