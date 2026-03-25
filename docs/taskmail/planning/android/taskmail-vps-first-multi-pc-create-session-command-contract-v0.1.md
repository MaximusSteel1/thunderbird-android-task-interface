# TaskMail VPS-First 多 PC CreateSessionCommand Contract（v0.1）

更新时间：2026-03-26

## 状态

本文是 `VPS-first 多 PC` 主线下，Android-facing `CreateSessionCommand` 的 first-pass contract。

它负责冻结以下内容：

- Android-facing `CreateSessionCommand` 的最小请求语义
- `submit ack + session binding` 的最小返回语义
- 第一版稳定错误家族
- Android-facing contract 与内部 `pc-control` 的最小映射关系

它不替代以下文档：

- 主线方向 authority：`docs/taskmail/planning/android/taskmail-vps-first-multi-pc-authority-v0.1.md`
- Android-facing facade authority：`docs/taskmail/planning/android/taskmail-vps-first-multi-pc-android-facing-facade-authority-v0.1.md`
- 页面级 API 需求：`docs/taskmail/planning/android/taskmail-vps-first-multi-pc-page-api-requirements-v0.1.md`
- `PC <-> VPS` 控制面协议：`docs/taskmail/planning/platform/taskmail-pc-vps-control-protocol-v0.1.md`
- `command/event` payload 附录：`docs/taskmail/planning/platform/taskmail-command-event-payload-appendix-v0.1.md`
- `execution_policy` 附录：`docs/taskmail/planning/platform/taskmail-execution-policy-appendix-v0.1.md`
- repo-side 同步要求：`E:\projects\mail_based_task_manager\docs\plans\android_facing_create_session_facade_requirements_v0.1.md`

这些文档分别回答“主线方向是什么”“页面要什么”“内部协议怎么走”。
本文回答的是：

**Android-facing 第一条正式写入口，到底应如何提交和返回。**

## 一句话结论

Android-facing 第一条正式写入口固定为：

- `CreateSessionCommand`

它的 contract 读法固定为：

- 请求层说页面语义
- 返回层说 `submit ack + session binding`
- VPS 内部继续尽量复用 `command_dispatch(new_task) -> command_ack`

因此，这份 contract 不是在另起一套重协议，而是在补 Android 页面真正缺的那一层业务边界。

## 范围

本文只冻结以下内容：

1. 一个写动作：`CreateSessionCommand`
2. 一个提交返回窗口：`submit ack + session binding`
3. 一组稳定拒绝码
4. 该动作到内部 `command_dispatch(new_task)` 的映射

本文暂不冻结：

- 最终 endpoint URL
- 一定使用 REST / WebSocket / SSE 中的哪一种
- 完整读侧 `session snapshot / timeline / replay`
- `reply / status / pause / resume / kill` 的 Android-facing contract
- Android-facing app auth 的最终 credential 方案
- 客户端自带幂等 key 或 client-generated `command_id`

## 设计原则

第一版 `CreateSessionCommand` contract 默认遵守以下原则：

1. `pc_id + workspace_id` 是主路由键。
2. `prompt` 是 Android-facing 输入语义；内部可映射到 `task_text`。
3. `execution_policy` 继续沿用主线字段，不再另造平行字段族。
4. `repo_path / workdir` 若存在，只读作审计镜像或兼容镜像，不替代主路由键。
5. Android-facing 页面不直接接触 `hello/hello_ack`、`connection_epoch`、schema negotiation。
6. 若内部 `command_ack` 尚不包含 `session_id`，则由 facade 补出 `session binding`，而不是把缺口留给客户端。

## 1. 请求 Contract

### 1.1 语义动作名

Android-facing 主线对外统一使用：

- `CreateSessionCommand`

它的语义是：

- 选择某个 `pc_id + workspace_id`
- 创建一个 fresh `session`
- 触发首轮 `new_task` 执行

### 1.2 最小输入

第一版最小业务输入固定为：

- `pc_id`
- `workspace_id`
- `prompt`
- `execution_policy`

其中：

- `pc_id`：目标执行节点
- `workspace_id`：目标工作目录 identity
- `prompt`：用户任务正文
- `execution_policy`：`backend / profile / permission / backend_transport`

### 1.3 可选输入

第一版允许以下可选输入：

- `mode`
- `timeout_seconds`
- `acceptance[]`
- `repo_path`
- `workdir`
- `source`

固定读法：

- `mode`：例如 `modify / analysis_only`
- `timeout_seconds`：本轮执行超时
- `acceptance[]`：验收标准
- `repo_path / workdir`：仅作审计镜像或兼容镜像，不作为主路由依据
- `source`：默认建议由 Android 写成 `android`

### 1.4 请求对象建议形状

本文不冻结最终 endpoint，但冻结第一版建议对象形状如下：

```json
{
  "pc_id": "pc_home",
  "workspace_id": "workspace_android_task_manager",
  "prompt": "审查第一次 VPS-first handshake 主阻塞项。",
  "execution_policy": {
    "backend": "codex",
    "profile": "default",
    "permission": "highest",
    "backend_transport": "sdk"
  },
  "mode": "analysis_only",
  "timeout_seconds": 5400,
  "acceptance": [
    "List only the mainline blockers.",
    "Do not widen scope."
  ],
  "repo_path": "E:/projects/android_task_manager",
  "workdir": "feature/taskmail/internal",
  "source": "android"
}
```

### 1.5 固定验证规则

第一版固定以下验证规则：

1. `pc_id` 必填。
2. `workspace_id` 必填。
3. `prompt` 必填，且不能是空字符串。
4. `execution_policy.backend` 对 `CreateSessionCommand` 必填。
5. `repo_path / workdir` 缺失不能阻止提交，只要 `workspace_id` 已明确。
6. Android-facing 第一版不要求请求中自带 `command_id`。

最后一条的含义是：

- 第一版由 facade 负责生成或确认 `command_id`
- 若后续要引入客户端幂等 key，再单独冻结 companion contract

## 2. 返回 Contract

### 2.1 返回窗口的固定语义

从本文生效后，`CreateSessionCommand` 的 Android-facing 返回统一按下面两层理解：

1. `submit ack`
2. `session binding`

其中：

- `submit ack` 回答“这次提交有没有被接收”
- `session binding` 回答“这次提交最终落到了哪个 `session_id` 上”

### 2.2 `submit ack`

`submit ack` 最少应包含：

- `command_id`
- `ack_status`
- `queue_position`
- `reason`
- `error_code`

建议 `ack_status` 固定为：

- `accepted`
- `accepted_but_queued`
- `rejected`

说明：

- `queue_position` 只在 `accepted_but_queued` 时有意义
- `reason` 是可展示说明
- `error_code` 是稳定程序化拒绝原因

### 2.3 `session binding`

`session binding` 最少应包含：

- `session_id`
- `pc_id`
- `workspace_id`

固定规则：

1. 当 `ack_status = rejected` 时，不应返回 `session binding`。
2. 当 `ack_status = accepted | accepted_but_queued` 时，Android-facing 返回窗口必须给出 `session binding`。
3. `session binding` 是页面跳转和 follow-up 的正式主锚点，不再退回 `thread_id` 主锚点。

### 2.4 推荐返回形状：组合回包

若最终 transport 更适合 request-response，推荐把返回表现成一个组合对象：

```json
{
  "command_id": "cmd_01",
  "submit_ack": {
    "ack_status": "accepted",
    "queue_position": null,
    "reason": null,
    "error_code": null
  },
  "session_binding": {
    "session_id": "sess_01",
    "pc_id": "pc_home",
    "workspace_id": "workspace_android_task_manager"
  }
}
```

### 2.5 允许返回形状：分段回包

若最终 transport 更适合流式/双向通道，第一版也允许把它表现成两个连续对象：

1. `CreateSessionSubmitAck`
2. `CreateSessionSessionBinding`

等价语义是：

- 先回 `submit ack`
- 再回 `session binding`

但固定约束仍然不变：

- 若 `ack_status = rejected`，则后续不应再出现 `session binding`
- 若 `ack_status = accepted | accepted_but_queued`，则必须在同一提交窗口内补出 `session binding`
- Android 页面层不应把“只有 ack、没有 binding”的中间态误当成完整成功提交

### 2.6 队列态示例

```json
{
  "command_id": "cmd_02",
  "submit_ack": {
    "ack_status": "accepted_but_queued",
    "queue_position": 2,
    "reason": "pc queue is busy",
    "error_code": null
  },
  "session_binding": {
    "session_id": "sess_02",
    "pc_id": "pc_home",
    "workspace_id": "workspace_android_task_manager"
  }
}
```

### 2.7 拒绝示例

```json
{
  "command_id": "cmd_03",
  "submit_ack": {
    "ack_status": "rejected",
    "queue_position": null,
    "reason": "workspace is not available on the target pc",
    "error_code": "workspace_unavailable"
  },
  "session_binding": null
}
```

## 3. 第一版稳定拒绝码

第一版固定以下稳定拒绝码：

- `unsupported_backend`
- `unsupported_profile`
- `unsupported_permission`
- `profile_model_unresolved`
- `workspace_unavailable`
- `pc_offline`

补充读法：

- 前 4 个优先对应 `execution_policy` 校验或解析失败
- `workspace_unavailable` 表示目标 `workspace_id` 当前不可用、不可见或已失效
- `pc_offline` 表示目标 `pc_id` 当前不在线或当前不可投递

第一版暂不要求额外细分：

- `unsupported_backend_transport`
- 更细粒度 admission / auth / quota error family

若后续确有需要，再单独扩错误码 companion 文档。

## 4. 与内部 `pc-control` 的映射

### 4.1 写入映射

Android-facing `CreateSessionCommand` 到内部控制面的默认映射读法如下：

- `pc_id` -> `command_dispatch.pc_id`
- `workspace_id` -> `command_dispatch.payload.workspace_id`
- `prompt` -> `command_dispatch.payload.payload.task_text`
- `execution_policy` -> `command_dispatch.payload.execution_policy`
- `mode` -> `command_dispatch.payload.payload.mode`
- `timeout_seconds` -> `command_dispatch.payload.payload.timeout_seconds`
- `acceptance[]` -> `command_dispatch.payload.payload.acceptance`
- `repo_path` -> `command_dispatch.payload.payload.repo_path`
- `workdir` -> `command_dispatch.payload.payload.workdir`
- `source` -> `command_dispatch.payload.payload.source`

固定规则：

- 内部 `command_type` 固定为 `new_task`
- 内部 `command_dispatch.payload.session_id` 对 fresh session 应保持 `null`

### 4.2 返回映射

内部 `command_ack` 与 Android-facing `submit ack` 的默认映射读法如下：

- `command_ack.payload.command_id` -> `command_id`
- `command_ack.payload.ack_status` -> `submit_ack.ack_status`
- `command_ack.payload.queue_position` -> `submit_ack.queue_position`
- `command_ack.payload.reason` -> `submit_ack.reason`
- `command_ack.payload.error_code` -> `submit_ack.error_code`

### 4.3 `session binding` 的补齐责任

`session binding` 不要求由内部 `command_ack` 直接提供。

第一版默认责任边界固定为：

- 若内部 runtime 已能同步拿到 `session_id`，facade 直接投影即可
- 若内部 `command_ack` 尚无 `session_id`，则由 facade 通过 runtime / session store / binding resolver 补齐

无论内部实现怎么拿到这个值，Android-facing 返回窗口都必须维持本文的 `submit ack + session binding` 语义。

## 5. Android 侧实现约束

Android 侧接这份 contract 时，默认应遵守以下约束：

1. 页面或 ViewModel 不直接构造原始 `command_dispatch` envelope。
2. 页面不自己维护 `connection_epoch`。
3. 页面成功导航到 Session 时，必须已经拿到 `session_id`。
4. `repo_path / workdir` 不能再被 UI 当作主路由身份。
5. 当前页若收到 `accepted_but_queued`，应按“已建 session，但暂未开始执行”处理，而不是当作失败。

## 6. 非目标

本文第一版明确不做：

- `reply / status` contract
- Session snapshot / timeline / replay contract
- artifact 下载 contract
- Android-facing app auth credential 细节
- client-generated `command_id`
- `thread_id` 兼容策略细节

这些都应在后续 companion 文档中继续冻结，而不是在这份 first-pass 里一次塞满。

## 一句话结论

**`CreateSessionCommand` 的 first-pass contract 只做一件事：让 Android 能稳定提交 `new_task`，并稳定拿到 `command_id + submit_ack + session binding`，其余复杂度继续留在薄 facade 和内部控制面。**
