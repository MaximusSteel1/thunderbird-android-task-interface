# TaskMail VPS-First 控制面全量字段冻结（v0.1）

更新时间：2026-03-25  
状态：平台层 freeze doc；用于冻结 Android 与 PC/VPS 并行开发所依赖的控制面字段形状，不替代 current-truth 文档

> 说明：
>
> 本文不是当前实现事实说明。
>
> 本文回答的是：在 `VPS-first 多 PC 控制面` 主线下，哪些对象、字段、枚举和 payload 形状现在已经足够冻结成一套共享开发基线。
>
> 当前实现事实仍以：
>
> - `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
> - `docs/TASKMAIL-MAIL-RULES.md`
> - `E:\projects\mail_based_task_manager\docs\current\mail_protocol.md`
>
> 为准。

## 1. 文档角色

本文只回答一个问题：

**现在是否已经有一套足够完整的控制面字段冻结，可以让 Android 与 PC/VPS 按同一形状并行开发。**

本文的角色是：

- 作为当前 `VPS-first` 主线的全量字段冻结入口
- 作为各 companion docs 的统一汇总读法
- 作为后续跨仓并行开发的共享 baseline

它不替代：

- authority
- current behavior truth
- 分阶段实施计划

## 2. 当前结论

从 2026-03-25 起，`VPS-first 多 PC 控制面` 的全量字段形状可以按本文冻结。

这意味着：

1. Android 可以按这套字段建设 DTO、domain model、view state、page API contract。
2. PC/VPS 可以按这套字段建设 dispatch、handler、持久化、serializer、replay。
3. 双方后续不需要先等“再补一轮字段讨论”才能动工。

这里的“冻结”指：

- 字段命名
- 枚举空间
- payload 形状
- 对象边界

这里的“冻结”不指：

- 当前代码已经实现完毕
- 第一阶段就要把所有 deferred 动作都做完
- 后续完全不允许扩展可选字段

## 3. 冻结范围

当前冻结范围包括：

- `pc`
- `workspace`
- `session`
- `run`
- `execution_policy`
- `command`
- `event`
- `output_chunk`
- `result`
- `artifact`
- `download_ref`
- `error_code / error_message / error_type`

## 4. 对象冻结入口表

| 对象 / 主题 | 当前冻结入口 |
| --- | --- |
| `pc / workspace / session / run` | `taskmail-multi-pc-control-plane-v0.1.md` |
| `execution_policy` | `taskmail-execution-policy-appendix-v0.1.md` |
| legacy mail -> control plane 映射 | `taskmail-legacy-mail-to-control-plane-mapping-v0.1.md` |
| `command / event` payload | `taskmail-command-event-payload-appendix-v0.1.md` |
| `result / artifact / error_code` | `taskmail-result-artifact-errorcode-appendix-v0.1.md` |
| envelope / replay / fencing / 消息类型 | `taskmail-pc-vps-control-protocol-v0.1.md` |

## 5. 当前冻结的值空间

### 5.1 `execution_policy`

当前冻结：

- `backend = codex | opencode`
- `profile = 稳定档位标签`
- `permission = default | highest`
- 可选 `backend_transport`
- `resolved_model` 只作执行侧回报

### 5.2 `command_type`

当前已冻结为第一批或已给出 canonical 去向的动作包括：

- `new_task`
- `reply`
- `status`
- `pause`
- `resume`
- `kill`
- `sync_project_folders`
- `list_sessions`
- `rerun`
- `end`
- `host_control_restart`

其中：

- 第一批主协议骨架当前只强依赖 `new_task / reply / status / pause / resume / kill`
- 其余动作虽然仍有 `deferred` 或 `compatibility-only` 边界，但 canonical 命名与 payload 去向已经冻结

### 5.3 `event_type`

当前冻结：

- `queued`
- `accepted`
- `running`
- `awaiting_user_input`
- `paused`
- `done`
- `failed`
- `killed`

### 5.4 `final_status`

当前冻结：

- `accepted`
- `running`
- `awaiting_user_input`
- `paused`
- `done`
- `failed`
- `killed`

### 5.5 `result.structured_payload.kind`

当前冻结：

- `task_outcome`
- `session_status_snapshot`
- `question_set`
- `sync_project_folders`

### 5.6 `download_ref.kind`

当前冻结：

- `vps_file`
- `external_url`
- `inline_data`

## 6. 当前冻结的 payload 读法

### 6.1 `command.payload`

当前已经冻结了以下 payload 读法：

- `new_task`
  - `repo_path`
  - `workdir`
  - `task_text`
  - `mode`
  - `timeout_seconds`
  - `acceptance`
  - `input_artifacts[]`
- `reply`
  - `reply_text`
  - `answers`
  - `timeout_seconds`
  - `mode`
  - `acceptance`
  - `input_artifacts[]`
- `status`
  - `include_question_set`
  - `include_latest_reply_text`
  - `include_artifacts`
- `pause`
  - `reason`
- `resume`
  - `reply_text`
  - `answers`
  - `timeout_seconds`
  - `mode`
  - `acceptance`
  - `input_artifacts[]`
- `kill`
  - `reason`
  - `target`
- `sync_project_folders`
  - `roots_scope[]`

### 6.2 `event.payload`

当前已经冻结了以下 payload 读法：

- `queued`
  - `queue_position`
  - `queue_name`
- `accepted`
  - `effective_execution`
  - `accepted_at`
- `running`
  - `effective_execution`
  - `backend_session_id`
  - `step_summary`
- `awaiting_user_input`
  - `question_set`
  - `last_summary`
  - `effective_execution`
- `paused`
  - `paused_from_status`
  - `reason`
  - `question_set`
- `done`
  - `summary`
  - `result_preview`
- `failed`
  - `error_code`
  - `error_type`
  - `error_message`
  - `summary`
- `killed`
  - `reason`
  - `killed_by`

## 7. 当前冻结的共用形状

当前已经冻结了以下共用结构：

- `question_set`
  - `question_set_id`
  - `questions[]`
  - question 内至少有：
    - `question_id`
    - `question_type`
    - `required`
    - `question_text`
    - `choices`
    - `choice_labels`
- `input_artifacts[]`
  - `artifact_id`
  - `name`
  - `content_type`
  - `size`
  - `download_ref`
- `artifact_manifest.artifacts[]`
  - `artifact_id`
  - `name`
  - `kind`
  - `role`
  - `content_type`
  - `size`
  - `sha256`
  - `download_ref`
  - 可选 `inline_preview`
  - 可选 `caption`

## 8. 并行开发规则

从现在开始，Android 与 PC/VPS 并行开发时应遵守：

1. Android 不再自定义另一套字段名。
2. PC/VPS 不再在实现里临时发明“只在服务端存在”的平行 payload 形状。
3. 若字段已经在 freeze doc 中出现，双方都应优先使用该 canonical 名称。
4. 若确需新增字段：
   - 优先新增为可选字段
   - 同一变更里同步更新对应 appendix
   - 若字段影响跨端互通，再同步更新本文
5. 若要删除、重命名或改变枚举含义，必须显式更新本文，不允许在实现中先改再补文档。

## 9. 当前不再视为主要卡点的事项

当前主线下，shared relay / transport connectivity 已不再是字段冻结的主要卡点。

因此当前读法应改为：

- transport 通路已具备继续推进主线的基本条件
- 下一阶段的主要工作转向控制面 handler、storage、page integration 与状态机落地
- 字段形状不应继续被读成“还没法冻结，所以双方先别开发”

## 10. 当前仍未被这份 freeze 解决的事项

这份 freeze 仍然不直接解决：

- 当前代码是否已经实现这些对象
- 多用户 / principal / ACL 细化
- 运行中的 cross-PC hot migration
- Android 最终 UI 交互细节
- 哪些 deferred 动作会进入 Phase 1，哪些会进入更后阶段

但这些问题已经不构成“双方无法并行开发字段层”的阻塞。

## 11. 一句话结论

**截至 2026-03-25，`VPS-first 多 PC 控制面` 的对象、枚举、payload 与错误面已经足够冻结成一套共享开发基线；Android 与 PC/VPS 可以按这套字段形状并行开发，而不必再等待新的字段讨论。**
