# TaskMail `result / artifact / error_code` 字段附录（v0.1）

更新时间：2026-03-25  
状态：平台层 companion appendix；用于冻结 `result / artifact / error_code` 的字段读法，不替代 current-truth 文档

> 说明：
>
> 本文回答的是：在 `VPS-first 多 PC 控制面` 下，`result`、`artifact_manifest` 和 machine-readable `error_code` 应长什么样。
>
> 本文不声称当前代码已经全部实现这些字段。

## 1. 文档目的

本文只回答三个问题：

1. `result` 顶层字段应如何读
2. `artifact_manifest.artifacts[]` 应如何稳定描述文件
3. `error_code / error_message / error_type` 这三层应如何分工

## 2. 固定结论

当前主线应固定以下结论：

1. `command_ack` 只回答“是否接单”，`result` 才回答“业务结果是什么”。
2. `result.final_status` 表示命令执行后的 canonical 状态，不等于 transport 成功与否。
3. `structured_payload` 应始终带稳定的 `kind` discriminator。
4. `artifact_id` 是逻辑 artifact 身份，不能与底层 `file_id` 混成一个字段。
5. `error_code` 是 machine-readable 分类；`error_message` 是人类可读细节；`error_type` 是 backend/runtime 侧错误类名或域内分类。

## 3. `result` 顶层字段表

| 字段 | 是否必需 | 说明 | 备注 |
| --- | --- | --- | --- |
| `result_id` | 必需 | 本次结果主键 | 去重键 |
| `command_id` | 必需 | 对应哪次用户动作 | 与 `command_dispatch` 对齐 |
| `workspace_id` | 条件必需 | 结果归属 workspace | `sync_project_folders` 这类 bootstrap 结果可为空 |
| `session_id` | 条件必需 | 结果归属 session | bootstrap/system 结果可为空 |
| `run_id` | 条件必需 | 结果归属 run | `status`、`list_sessions` 一类读操作可为空 |
| `final_status` | 必需 | canonical 结果状态 | 见下一节枚举 |
| `summary` | 必需 | 人类可读摘要 | 不能只靠 `structured_payload` 才能读懂 |
| `effective_execution` | 条件必需 | 实际生效的执行策略 | 发生 backend 执行或需要审计时应回传 |
| `structured_payload` | 必需 | 结构化结果对象 | 建议至少包含 `kind` |
| `generated_at` | 必需 | 结果生成时间 | 审计字段 |

补充规则：

- `result` 不应复用 `command_ack` 的 `accepted/rejected` 语义。
- `result` 若属于纯 bootstrap/system action，可让 `workspace_id/session_id/run_id` 为空，但仍要保留 `command_id + result_id`。

## 4. `final_status` 枚举表

| `final_status` | 含义 | 典型来源 |
| --- | --- | --- |
| `accepted` | 命令已被接纳，但当前结果面只是在做状态快照 | 主要用于 `status` 查询命中 `accepted` session |
| `running` | 命令结果是“当前仍在运行” | 主要用于 `status` 查询 |
| `awaiting_user_input` | 需要用户进一步回答 | `new_task / reply / resume / status` |
| `paused` | 当前 session 进入 paused | `pause / resume / status` |
| `done` | 正常完成 | terminal result |
| `failed` | 失败结束 | terminal result |
| `killed` | 被显式终止 | terminal result |

补充说明：

- 对真正的 run 终态，最常见的是 `awaiting_user_input / paused / done / failed / killed`。
- `accepted / running` 主要服务于 `status` 这类“当前态快照结果”，否则 `status` 命令无法稳定表达当前 live 状态。

## 5. `structured_payload.kind` 表

### 5.1 推荐 `kind` 集合

| `kind` | 适用命令 | 说明 |
| --- | --- | --- |
| `task_outcome` | `new_task / reply / resume / rerun / kill` | 面向一次任务执行的结果对象 |
| `session_status_snapshot` | `status` | 面向当前 session 快照 |
| `question_set` | `new_task / reply / resume / status` | 当结果面需要单独返回等待题集时使用 |
| `sync_project_folders` | `sync_project_folders` | bootstrap 项目目录同步 |

### 5.2 `task_outcome` 建议字段

| 字段 | 说明 | 备注 |
| --- | --- | --- |
| `reply_text` | 用户最终在工作台里看到的主要回复正文 | 对应 mail 里的 `Reply:` 主体 |
| `changed_files[]` | 改动文件列表 | 复用当前 `RunResult` 语义 |
| `tests_passed` | 测试是否通过 | 可空 |
| `error_code` | 控制面 machine-readable 失败码 | 可空 |
| `error_type` | backend/runtime 错误分类 | 可空 |
| `error_message` | 人类可读错误细节 | 可空 |
| `question_set` | 若结果是等待态，内嵌题集 | 与 `final_status = awaiting_user_input` 配合 |
| `artifacts[]` | 与本次结果强相关的 artifact 引用 | 可与独立 `artifact_manifest` 并存 |

### 5.3 `session_status_snapshot` 建议字段

| 字段 | 说明 |
| --- | --- |
| `current_state` | 当前 session 状态 |
| `last_summary` | 当前最新摘要 |
| `latest_reply_text` | 当前可展示的最新回复文本 |
| `paused_from_status` | 若当前是 paused，记录从哪个主状态流转而来 |
| `question_set` | 若当前在等待态，附带当前题集 |

### 5.4 `question_set` 建议字段

| 字段 | 说明 |
| --- | --- |
| `question_set_id` | 同一组问题的共享主键 |
| `questions[]` | 问题数组，保持原始顺序 |

每个 `question` 建议至少包含：

- `question_id`
- `question_type`
- `required`
- `question_text`
- `choices`
- `choice_labels`

### 5.5 `sync_project_folders` 建议字段

| 字段 | 说明 |
| --- | --- |
| `roots[]` | 根路径及其一级子目录列表 |
| `canonical_body_text` | 与旧 `[SYNC] Project Folder List` 同语义的人类可读正文 |

## 6. `artifact_manifest.artifacts[]` 字段表

| 字段 | 是否必需 | 说明 | 备注 |
| --- | --- | --- | --- |
| `artifact_id` | 必需 | 逻辑 artifact 主键 | 不等于 `file_id` |
| `name` | 必需 | 展示名称 | 例如 `summary.md` |
| `kind` | 必需 | 逻辑类型 | 推荐 `file | image | text | json` |
| `role` | 推荐 | 用途角色 | 推荐 `attachment | input | output` |
| `content_type` | 必需 | MIME / content type | canonical 字段名用 `content_type` |
| `size` | 必需 | 字节大小 | canonical 字段名用 `size` |
| `sha256` | 推荐 | 内容哈希 | 方便去重与审计 |
| `download_ref` | 条件必需 | 下载或取内容的引用 | 对用户可消费 artifact 应提供 |
| `inline_preview` | 可选 | 小体积预览对象 | 图片或文本摘要可用 |
| `caption` | 可选 | 附加说明 | 例如图表标题 |

补充说明：

- 旧 `/v1/files` 文档里常见的 `mime_type / byte_size`，在新控制面里建议统一归一到 `content_type / size`。
- 当前 live `/v1/files` compatibility 仍偏向 `kind = image | file`；但主线目标文档允许更宽 `text/json` 逻辑类型。

## 7. `download_ref` 读法表

第一版建议把 `download_ref` 固定为对象，而不是裸字符串。

| `download_ref.kind` | 建议字段 | 说明 |
| --- | --- | --- |
| `vps_file` | `file_id`、`metadata_url`、`content_url` | 首选读法；对应 VPS file surface |
| `external_url` | `url` | 外部直链，例如 COS 或其他对象存储 |
| `inline_data` | `content_type`、`encoding`、`data` | 只适合小预览，不适合大文件 |

明确禁止：

- 把原始本地磁盘路径直接当成 canonical `download_ref`
- 用一个无类型裸字符串同时承载 `file_id`、URL 和 debug hint

## 8. `error_code / error_message / error_type` 分工

### 8.1 分工规则

| 字段 | 角色 | 说明 |
| --- | --- | --- |
| `error_code` | machine-readable | 稳定用于程序判断和 UI 分类 |
| `error_message` | human-readable | 给用户或 operator 看的细节文本 |
| `error_type` | backend/runtime-specific | 保留底层错误类别，例如异常类名或 backend 自有错误型别 |

### 8.2 `command_ack.error_code` 首批建议

| `error_code` | 含义 |
| --- | --- |
| `unsupported_command_type` | 目标 PC 不支持该命令 |
| `unsupported_backend` | 不支持该 backend |
| `unsupported_profile` | 不支持该 profile |
| `unsupported_permission` | 不支持该 permission |
| `unsupported_backend_transport` | 不支持该 transport |
| `workspace_not_found` | 找不到目标 workspace |
| `workspace_not_owned_by_pc` | workspace 不从属于当前 PC |
| `session_not_found` | 找不到目标 session |
| `invalid_command_payload` | 结构化 payload 无效 |
| `profile_model_unresolved` | profile 无法解析到本地模型 |

### 8.3 `result.structured_payload.error_code` 首批建议

| `error_code` | 含义 |
| --- | --- |
| `backend_launch_failed` | backend 无法启动 |
| `backend_runtime_failed` | backend 运行期失败 |
| `no_active_run` | 命令要求 active run，但当前没有 |
| `artifact_not_found` | 指定 artifact 不存在 |
| `artifact_upload_failed` | artifact 上传失败 |
| `file_surface_unavailable` | 当前 file surface 不可用 |
| `download_ref_unavailable` | 结果里无法生成可消费的下载引用 |

### 8.4 不是 `error_code` 的东西

以下对象不应混成 `error_code`：

- `done / failed / killed / paused / awaiting_user_input`
- `accepted / running`
- `profile`、`permission` 之类策略字段

它们分别属于：

- `final_status`
- `execution_policy`

## 9. 一句话结论

**`result` 负责 canonical 业务结果，`artifact_manifest` 负责稳定文件描述，`error_code` 负责 machine-readable 分类；三者必须解耦，不能再用一段松散文本同时承担状态、文件和错误语义。**
