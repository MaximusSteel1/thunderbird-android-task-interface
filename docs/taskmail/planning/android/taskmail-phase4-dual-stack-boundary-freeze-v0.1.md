# TaskMail Android Phase 4 边界冻结（v0.1）

更新时间：2026-03-22

## 状态

- 本文冻结的是 Android 侧当前 Phase 4 的边界解释与进入条件。
- 本文不声称 Android 已经完成 Phase 4 实现或验证。
- 本文也不冻结新的跨仓 direct `reply` / direct `/status` contract。

## 相关文档

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/TASKMAIL-MAIL-RULES.md`
- `docs/taskmail/planning/android/taskmail-next-development-plan-v0.2.md`
- `docs/taskmail/planning/android/taskmail-next-session-handoff-2026-03-22-phase4-dual-stack-start.md`
- `docs/taskmail/planning/android/taskmail-phase2-direct-outbound-contract-v0.1.md`
- `E:\projects\mail_based_task_manager\docs/current/mail_protocol.md`
- `E:\projects\mail_based_task_manager\docs/current/android_reply_method_rules.md`

## 冻结目的

本文冻结的是这样一个更窄、更可执行的问题：

> 在 Phase 3 已冻结、mail fallback 仍必须保留的前提下，Android 当前到底把 Phase 4 的哪些内容视为已进入冻结范围，哪些内容仍必须留在后续 contract freeze 之后？

## 冻结结论

当前 Android 侧对 Phase 4 的冻结结论是：

1. Phase 4 的含义是 `dual-stack parity + primary-path switch`，不是“把全部 TaskMail flow 立即改成纯 direct”。
2. mail fallback 继续保留，且必须被视为真实运行路径，而不是名义上的兜底代码。
3. 当前唯一进入 covered-flow 冻结范围的正式 direct business flow 是 `new task`。
4. `new task` 的 parity 讨论必须同时覆盖：
   - direct accepted
   - fallback-classified direct failure -> mail fallback
   - hard direct rejection -> local stop
5. 后续状态、结果、附件与最终用户可见产物当前仍以 mail 为 truth layer。
6. 当前 direct inbound sidecar 仍只冻结为 read-side freshness 增强，不能据此推导出 direct `reply` 或 direct command 已解冻。
7. `reply`、`/status` 以及更广的 post-creation control action 目前仍不在本 freeze note 的冻结范围内。

## 冻结的 covered-flow 边界

### 当前纳入冻结范围

当前仅冻结以下 covered flow：

- 正式 TaskMail host 中的 `new task`

这意味着 Android 当前可以正式围绕 `new task` 冻结：

- direct 与 mail 的 parity 比较口径
- mismatch triage
- rollback trigger
- primary-path switch 的启用边界

### 当前不纳入冻结范围

以下内容当前明确不在本 freeze note 中冻结：

- plain continuation `reply`
- single-question direct answer
- multi-question `Answers:` direct answer
- paused `/resume` direct answer
- `/status`
- `/pause`
- `/end`
- attachment reply over direct transport
- 更广的 workspace / session read-side direct 切换

它们当前之所以不冻结，不是因为永远不做，而是因为 Android 侧还缺少至少一项前置条件：

- 跨仓 current direct contract 仍未冻结
- 与 mail authority 对齐的 send-side 语义镜像尚未定稿
- live parity / fallback / hard-stop 规则尚未闭环

## 冻结的共享工件

Android 侧当前把 Phase 4 需要与 PC 侧同步收口的共享工件，明确冻结为以下三份：

1. `phase4_dual_stack_parity_checklist.md`
2. `phase4_mismatch_ledger.md`
3. `phase4_rollback_trigger_note.md`

在当前 Android 仓库中，对应的初稿路径是：

- `docs/taskmail/planning/android/phase4_dual_stack_parity_checklist.md`
- `docs/taskmail/planning/android/phase4_mismatch_ledger.md`
- `docs/taskmail/planning/android/phase4_rollback_trigger_note.md`

这三份工件的形状当前冻结为：

- parity checklist：逐项列出 covered flow 的 parity 字段、预期证据、预期 thread / mail outcome、user-visible outcome 与当前状态
- mismatch ledger：固定记录 `mismatch_id`、`covered_flow`、`scenario`、`observed_behavior`、`expected_behavior`、`severity`、`owner_repo`、`current_status`、`evidence`、`rollback_impact`
- rollback trigger note：每条 trigger 至少写清触发条件、证据来源、自动或人工动作、是否阻塞 primary-path switch

除非后续再出新的 freeze 或 update note，Android 侧与 PC 侧都应以这三份工件作为 Phase 4 的共同收口形状。

## 冻结的结果分类

对当前唯一 covered flow `new task`，Android 侧先冻结以下三类结果分类：

### 1. direct accepted

冻结语义：

- direct path 被视为本次发送的主路径
- Android 不再重复发出同一份 mail `new task`
- 后续状态与结果仍等待当前 mail truth layer 回流
- parity 证据应优先使用当前已有 authority 的明确字段与结果语义，例如：
  - `packet_ack.accepted = true`
  - 稳定 `receipt_id`
  - 可选 `transport_message_id`，但 Android 不得把它当作 v1 UI identity 依赖
  - 是否产生预期的 thread / mail outcome

### 2. fallback-required mismatch

冻结语义：

- 本次 direct 尝试不应继续强撑为成功
- Android 应改走当前 mail path
- 该类结果允许本次或后续对该 covered flow 暂时回退为 mail-default

当前建议先归入此类的起点包括：

- bootstrap unavailable
- transport unavailable
- relay temporary failure
- capability unsupported
- direct send throw 且不属于 hard rejection
- `packet_ack` 分类落到 fallback-classified rejection，且 mail fallback 仍可用

### 3. hard-stop mismatch

冻结语义：

- 不允许静默回退到 mail
- 必须保留 draft
- 必须向用户显示 direct 失败

当前建议先归入此类的起点包括：

- `invalid_payload`
- `validation_failed`
- `unauthorized`
- 其他已被明确定义为“不能自动降级成 mail fallback”的 direct rejection

## 冻结的 rollback 边界

Phase 4 当前冻结的 rollback 边界是：

- rollback 是“按 covered flow 处理”的，不是“一次性改写全部 TaskMail flow 的 transport 决策”
- 对 `new task` 观察到 parity gap 时，优先先影响 `new task` 的默认路由，不扩大到 `reply` 或其他尚未冻结的 flow
- direct accepted 之后如果长期拿不到与当前 covered flow 预期一致的后续 mail / session evidence，应先视为 parity gap，阻止扩大 switch 范围，而不是直接据此宣称全量 direct 失败
- 如果共享工件中的 evidence 口径与 PC-side authority 发生漂移，应先收紧工件与 freeze note，再讨论实现推进

## 冻结的 primary-path switch 前提

Android 侧当前冻结的 `new task` primary-path switch 前提是：

1. `new task` 的三条结果路径都有 reviewable 的 parity 口径：
   - direct accepted
   - fallback to mail
   - hard rejection local stop
2. 对应的 automated validation 已覆盖 send-side 关键分支。
3. 对应的 live/manual evidence 已证明：
   - accepted path 不会重复发 mail
   - fallback path 能真正回到 mail
   - hard rejection 会保留 draft 并显示错误
4. 后续 workspace / detail 的 mail-driven 可见结果不会把用户带回明显错误的旧状态。
5. mail fallback 仍保留可执行验证，而不是只剩代码路径。

在这些前提之外，本 freeze note 不授权扩大 direct-default 的覆盖范围。

## 当前未冻结事项

截至本文，以下事项仍必须留在后续 freeze 或 contract note 中处理：

- direct `reply` contract 到底是扩展 `phase2-direct-outbound-contract-v1` 还是另起 session-action contract
- `reply` direct 化时，single-question、multi-question `Answers:`、paused `/resume`、attachment-only continuation 是否共用同一 transport envelope
- direct `/status` 的 wire contract、fallback 规则与 user-visible 语义
- 更广 read-side direct path 是否只做 freshness sidecar，还是会进入更高优先级的 UI truth 判断

## 当前执行判断

在 Android 侧，当前最合理的执行判断应固定为：

- 先围绕 `new task` 完成 parity matrix、rollback trigger 与 switch 边界；
- 不把 `reply` 或 `/status` 提前偷换进 covered-flow 实现；
- 如果未来要把新的 flow 纳入 covered 范围，先补一份新的 contract freeze 或 boundary update，而不是直接编码。

## 本冻结说明的作用边界

本文的作用是把当前 Android 侧对 Phase 4 的理解冻结成一个可引用、可 review 的边界说明。

它的用途不是：

- 覆盖 `CURRENT-STATUS` 对仓库实现现状的描述
- 覆盖 `VALIDATION-LEDGER` 对已验证证据的描述
- 覆盖 `MAIL-RULES` 或 PC-side current docs 对协议 authority 的描述

如果后续实现或跨仓 contract 有变化，应先更新对应 authority，再更新本文。
