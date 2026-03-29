# TaskMail VPS-First 多 PC Session Action Android 硬切统一执行方案（v0.1）

更新时间：2026-03-28

## 状态

本文覆盖同路径下 2026-03-28 之前的 session-action 方案文档。

从本文生效后，Android 仓对 post-creation session action 的执行口径固定为：

- `session-action` 是正式 owner seam，不是 detail 页的局部发送补丁
- `session-action` 首发就要承接所有用户可见的 post-creation session 操作
- 旧 mail `/control` lane 不再作为主线兼容通道设计，只保留回滚预案价值

本文不替代以下文档：

- 当前实现真相：`docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- 当前验证摘要：`docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- Android 协议 authority：`docs/TASKMAIL-MAIL-RULES.md`
- Android 仓 authority：`docs/taskmail/planning/android/taskmail-vps-first-multi-pc-authority-v0.1.md`
- PC / VPS companion plan：`E:\projects\mail_based_task_manager\docs\plans\android_facing_session_action_facade_requirements_v0.1.md`

本文回答的问题只有一个：

**Android 为了直接落地“长期架构最纯、协议最统一”的方案，应把 post-creation `session-action` 收敛成什么样，并按什么门槛硬切。**

## 一句话结论

Android 长期最优方案不是继续修旧 `/control` 门禁，也不是长期保留 “一部分动作走 facade、一部分动作走 mail” 的分裂状态，而是：

- `create-session`
- `session-action`
- `session-snapshot`

三条生命周期 seam 继续分职，但在 owner 模型上完全统一：

- 同一 admission：`android_app_token`
- 同一 submit 语义：`submit_ack`
- 同一 owner submit id：`command_id`
- 同一 target identity：`target_session_identity`
- 同一读回真相层：`VPS-native projection / session-snapshot`

其中所有用户可见的 post-creation session 操作，一次性统一进入 `session-action`。

## 1. 固定方向

从本文生效后，Android 对 session 生命周期的长期读法固定为：

1. `create-session` 负责创建 canonical session
2. `session-action` 负责对既有 session 发起 canonical action
3. `session-snapshot` 与 projection 负责返回 action 之后的 canonical session state

固定规则：

1. 不把创建、操作、读回混成一个万能接口。
2. 不允许 post-creation action 长期分裂成 facade、mail、`/control` 多套 owner 模型。
3. detail 页面不再按动作类型决定“走哪条历史通道”，而是统一走 `session-action`。

## 2. v0.1 首发 scope 冻结

`session-action` 的首发 scope 固定为所有用户可见的 post-creation session 操作：

- `reply`
- `status`
- `answers`
- `pause`
- `resume`
- `kill`
- `end`
- `attachment_continuation`

固定规则：

1. 不再以“先 `reply/status`，其他动作继续走 mail”作为主线完成态。
2. 内部可以分切片开发，但正式硬切门槛是以上动作都进入同一家 `session-action` owner seam。
3. 若其中任一用户可见动作仍需依赖旧 mail `/control` 才能正常使用，则不应宣布主线切换完成。

## 3. Android-facing 写路径必须怎样工作

Android detail 对任一 `session-action` 提交，固定按下面顺序理解：

1. 从当前 detail state 读取 canonical `session_id`
2. 组装 `request_id + action + target`
3. 调用 Android-facing `session-action facade`
4. 拿到 `command_id + submit_ack + target_session_identity`
5. 在本地 detail store 写入 `pending submission`
6. 后续继续通过 projection / snapshot 观察该 action 的状态推进

固定规则：

1. `submit_ack` 只回答“请求是否被接收”，不是最终结果。
2. UI 不应继续等待 canonical mail 才确认 action 是否生效。
3. Android 不应再把 `/control` transport-level 失败暴露成主错误。

## 4. 成功回包与 continuity 必须怎样读

### 4.1 成功回包的最小 must-have

Android 对 post-creation `session-action` 的成功回包要求固定为：

1. 必须有 `command_id`
2. 必须有 `submit_ack`
3. 必须有完整最小 `target_session_identity`

其中成功回包的最小 `target_session_identity` 必须包含：

- `pc_id`
- `workspace_id`
- `session_id`

补充固定规则：

1. `thread_id` 在 Android `v0.1` 只作 optional supporting identity / diagnostics，不是 hard requirement。
2. Android 不接受“success + partial identity，剩余字段再靠 projection / snapshot 补齐”作为标准成功路径。
3. 若 repo-side 在 submit 窗口内拿不出完整最小 identity，正确结果应是 `rejected`，而不是 success + partial identity。

### 4.2 `status` 的 ack 语义

Android 对 `status` 的 `v0.1` must-have 结论固定为：

1. Android 不要求 `status` 必须支持 `accepted_but_queued`。
2. repo-side 若把 `status` 冻结成只有 `accepted | rejected`，Android 可接受。
3. 若 repo-side 保留 `accepted_but_queued`，Android 也可接受。
4. 但 `status.accepted_but_queued` 的状态机语义必须与 `accepted` 等价。
5. 它最多只允许影响提示文案，不应创建单独的 continuity 或 pending submit 分支。

### 4.3 `command_id` 主锚点

Android 对 same-run continuity 的主锚点固定为：

1. `command_id` 是 authoritative 主锚点。
2. `request_id + session_id` 只作 supporting key。
3. `request_id` 的职责是：
   - client-side idempotency key
   - retry dedupe key
   - supporting continuity key
4. projection / snapshot 一旦能回收到 `command_id`，Android 就应优先按 `command_id` 清理 pending submission。

## 5. 本地缓存必须补什么

长期 owner line 的关键，不是“能发出去”，而是“发出去之后 detail 如何稳态承接”。

Android 必须在 `TaskSessionDetail` 本地缓存里补统一的 `pending submission` 元数据。

建议最小字段：

- `commandId`
- `requestId`
- `actionType`
- `submittedAt`
- `ackStatus`
- `targetSessionIdentity`

这层元数据的职责固定为：

1. 提交成功后立即给 detail 稳定可见状态
2. 支撑进程重启后的 same-run continuity
3. 为 projection / snapshot 到达后清理 pending submission 提供主锚点

固定清理规则：

1. 优先用 projection / snapshot 中可见的同一 `command_id` 清理 pending submission。
2. `request_id + session_id` 只能作过渡期辅助对照，不应长期取代 `command_id`。

## 6. Android 领域模型与 UI 必须怎样统一

### 6.1 领域模型

Android 后续实现不应继续把 owner line 建在旧 `Direct` / `/control` 命名之上。

建议固定收敛方向：

- `TaskMailDirectSessionAction*` -> 中性 `TaskMailSessionAction*`
- `Direct dispatch` 文案 -> `Session action` / `VPS` 语义
- 旧 `RelayControl*` 类型降成回滚预案或诊断资产

固定规则：

1. 新增状态、缓存和测试，应优先使用 `session-action` 语义。
2. 不再继续扩大 `direct` 词汇覆盖面。

### 6.2 统一 UI 行为

所有用户可见 action 的 UI 行为都应按同一 owner 模型理解：

1. 点击动作按钮
2. 发起 `session-action`
3. 收到 `submit_ack`
4. detail 展示 pending submission
5. projection / snapshot 推进到后续状态
6. 按同一 `command_id` 清理 pending submission

### 6.3 Reply / Status / 其他动作

具体动作可以有各自的表单差异，但不应再有不同 owner 模型：

1. `reply` 与 `answers` 有输入负载，`accepted` 后按规则清空草稿。
2. `status`、`pause`、`resume`、`kill`、`end` 可以没有草稿，但仍走同一 submit / pending / continuity 语义。
3. `attachment_continuation` 也应走同一 `session-action` family，不再靠 mail path 承担主线语义。

## 7. 硬切执行规则

内部实现可以分切片，但主线 acceptance 固定按硬切读法判断。

### 7.1 允许的内部切片

允许 Android 在实现过程中按以下顺序推进：

1. 收敛 `session-action` 领域命名与 facade 注入
2. 补统一 `pending submission` 与 `command_id` continuity
3. 把全部用户可见动作切到 `session-action`
4. 联合 PC / VPS 做端到端 hard cut 验证

### 7.2 不允许的主线完成态

下面这些状态都不应被读成主线完成：

1. 只有 `reply/status` 进入 `session-action`，其他动作还在 mail。
2. detail 仍需按动作类型决定走 facade 还是旧通道。
3. 本地 detail store 还没有统一 `pending submission`。
4. UI 仍暴露旧 `direct/control` 时代的主路径文案或错误语义。

### 7.3 旧通道的定位

旧 mail `/control` lane 只保留：

- 回滚预案
- 内部诊断
- 历史证据参考

固定规则：

1. 它不再写进主线 owner 文档的正向行为定义。
2. 它不再作为 Android UI、状态机或错误文案的组成部分。
3. 它不再成为“先保留一半、后面再慢慢换”的长期设计借口。

## 8. 明确不该做的事

这条线里，Android 不应继续做下面这些事：

1. 不重新把 `/control hello_ack` 能力探测抬回主流程门禁。
2. 不把 mail fallback 继续设计成 post-creation action 的默认 owner closeout。
3. 不把 `reply/status` 特判成长期例外，让其他动作继续走另一套模型。
4. 不把 `request_id` 升格成长期唯一 continuity anchor。
5. 不把新 owner 逻辑继续包在旧 `Direct` 命名和旧 compatibility 文案里。

## 9. 验收门槛

Android 做完这一轮硬切后，至少应拿出下面 4 类证据。

### 9.1 unit test

- 全部 action family 的请求组装
- `request_id` 复用与 retry 语义
- `reply` 的 `accepted | accepted_but_queued | rejected` ViewModel 状态迁移
- `status` 的 `accepted | rejected` ViewModel 状态迁移；若 repo-side 保留 `accepted_but_queued`，应验证其与 `accepted` 等价
- 全部 action family 的 `pending submission` 写入与按 `command_id` 清理

### 9.2 repository / parser test

- `android_app_token` 缺失时的错误映射
- submit 返回解析
- success path 必带完整最小 `target_session_identity`
- 稳定错误码映射

### 9.3 manual smoke

至少要有覆盖全部用户可见动作家族的 focused live 样本，证明：

1. detail 页发起 `session-action`
2. Android 拿到 `command_id + submit_ack + target_session_identity`
3. 本地 detail 写入 pending submission
4. projection / snapshot 后续更新
5. 按同一 `command_id` 清理 pending submission

### 9.4 不回归现有主线

Android 还必须证明：

- `create-session` 主路径不回归
- `workspace/detail` 的 `VPS-native projection` 主读链不回归
- 当前实现真相文档在真正硬切完成前不被误写成“已经全部完成切换”

## 一句话结论

**Android 侧长期最优执行方案，是让 `session-action` 一次性承接所有用户可见的 post-creation session 操作，并与 `create-session / session-snapshot` 组成统一的 facade family；旧 mail `/control` 只保留回滚预案价值，不再参与主线 owner 设计。**
