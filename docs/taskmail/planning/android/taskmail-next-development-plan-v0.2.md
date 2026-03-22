# TaskMail Android 下一阶段开发计划（v0.2）

更新时间：2026-03-22

## 状态

- 本文是当前 Android TaskMail 的近程集成开发计划。
- 本文承接 `docs/taskmail/planning/android/taskmail-next-session-handoff-2026-03-22-phase4-dual-stack-start.md`。
- 本文把 Phase 4 的 `dual-stack parity + primary-path switch` 目标拆成可执行的 Android 侧批次。
- 2026-03-22 当前读法已进一步收紧：Phase 4 应先收口为 `new_task parity baseline`，Phase 5 的第一刀见
  `docs/taskmail/planning/android/taskmail-phase4-closeout-and-phase5-narrow-plan-v0.1.md`。
- 当前已额外产出 `docs/taskmail/planning/android/taskmail-phase5-new-task-switch-review-v0.1.md`；最新读法已从
  `not_ready_keep_mail_default` 推进到 `ready_for_direct_default_review`，但这仍不等于立即切换 `direct-default`。
- 当前已额外产出 `docs/taskmail/planning/android/taskmail-phase5-new-task-guarded-rollout-observation-plan-v0.1.md`；
  这份 note 继续把 Phase 5 推进到 guarded rollout / observation 边界，并显式写出 Android 侧后续 closeout
  discipline 与需要 PC 侧配合提供的 artifact / contract 要求。
- 当前已额外产出 `docs/taskmail/planning/android/taskmail-phase5-reply-status-direct-contract-prerequisites-v0.1.md`；
  这份 note 不把 `reply` / `/status` 提前拉进实现，而是先把进入 direct contract freeze 之前必须关闭的
  current-protocol 前提、范围裁剪与 PC 配合项显式写清。
- 当前已额外产出 `docs/taskmail/planning/android/taskmail-phase5-reply-status-pc-coordination-checklist-v0.1.md`；
  这份短版 checklist 把 Android 侧希望相邻 PC 侧先冻结到 `docs/current/*` 的问题压缩成可直接 review 的输入，
  仍不等于已经授权 Android 进入 direct `reply` / `/status` 实现。
- 当前已额外产出 `docs/taskmail/planning/android/taskmail-phase5-reply-status-pc-message-template-v0.1.md`；
  这份模板把 checklist 进一步压缩成可直接发给相邻 PC 侧的沟通文本，方便后续协作，但仍不替代 protocol freeze 本身。
- 相邻 PC 侧现已不仅回给 repo-side reading，还已在
  `E:\projects\mail_based_task_manager\docs\plans\post_creation_session_action_contract_v1.md`
  落地 shared planning-layer contract；当前 Android 仓已把这轮对齐与 shared contract 收口到
  `docs/taskmail/planning/android/taskmail-phase5-reply-status-pc-alignment-readout-v0.1.md`。
- 当前因此不再是“继续等待 shared contract”，而是“基于 shared contract 进入 Android 实现规划”；对应 note 为
  `docs/taskmail/planning/android/taskmail-phase5-reply-status-android-implementation-plan-v0.1.md`。同日后续代码
  已继续关闭 `Batch A` / `Batch B` / `Batch C`：
  - detail route / `TaskSessionKey` 已补 canonical `workspace_id + session_id`
  - session-action direct sender / result mapping 已落地
  - `TaskSessionDetailViewModel` 已对 `current-session plain reply` 与 `current-session /status` 接入 guarded
    direct lane
  - `TaskMailSessionActionSendRecord` durable evidence / latest-review surface 已落地
  当前剩余主线已收敛到 `Batch D` 的后半段：live / mailbox closeout 与 shared-artifact closeout 读法。
- 本文不替代以下当前 authority：
  - `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
  - `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
  - `docs/TASKMAIL-MAIL-RULES.md`

## 目的

本文回答的问题是：

> 在 Phase 3 已冻结、mail fallback 仍必须保留的前提下，Android 接下来应如何推进 Phase 4，才能把 direct path 做成“有可比对 parity、可回退、可切主路”的正式能力，而不是把少数已闭环 flow 误写成全量切换？

## 先读这些文档

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/TASKMAIL-MAIL-RULES.md`
- `docs/TASKMAIL-DEBUG-VALIDATION.md`
- `docs/taskmail/planning/android/taskmail-android-public-plaintext-direct-connect-plan-v0.1.md`
- `docs/taskmail/planning/android/taskmail-phase4-dual-stack-boundary-freeze-v0.1.md`
- `docs/taskmail/planning/android/taskmail-phase2-direct-outbound-contract-v0.1.md`
- `docs/taskmail/planning/android/taskmail-phase5-new-task-guarded-rollout-observation-plan-v0.1.md`
- `docs/taskmail/planning/android/taskmail-phase5-reply-status-direct-contract-prerequisites-v0.1.md`
- `docs/taskmail/planning/android/taskmail-phase5-reply-status-pc-coordination-checklist-v0.1.md`
- `docs/taskmail/planning/android/taskmail-phase5-reply-status-pc-message-template-v0.1.md`
- `docs/taskmail/planning/android/taskmail-phase5-reply-status-pc-alignment-readout-v0.1.md`
- `docs/taskmail/planning/android/taskmail-phase5-reply-status-android-implementation-plan-v0.1.md`
- `docs/taskmail/planning/android/taskmail-next-session-handoff-2026-03-21-reply-direct-send-seam.md`
- `docs/taskmail/planning/android/taskmail-next-session-handoff-2026-03-22-phase4-dual-stack-start.md`
- `E:\projects\mail_based_task_manager\docs/current/mail_protocol.md`
- `E:\projects\mail_based_task_manager\docs/current/android_reply_method_rules.md`
- `E:\projects\mail_based_task_manager\docs\plans\post_creation_session_action_contract_v1.md`

## 当前规划基线

截至 2026-03-22，Phase 4 的起点应理解为以下事实组合，而不是单一一句“direct 已经完成”：

- Phase 3 已按当前边界冻结。
  - `detail` 与 `workspace` 的 durable-mail 驱动刷新 closeout 已经补齐到当前可接受边界。
  - duplicate pending question / duplicate answer line 目前更应视为上游 source `[QUESTION]` 输入问题，而不是 Android Phase 3 blocker。
- `new task` 是当前唯一已经在 Android 正式 flow 上完成 live direct closeout 的 direct business slice。
  - 已有 live evidence 覆盖 direct accepted。
  - 已有 live evidence 覆盖 fallback-classified 失败回退到 mail。
  - 已有 live evidence 覆盖 hard rejection 本地停止并保留 draft。
- 后续状态、结果、附件与最终用户可见产物当前仍以 mail 为 truth layer。
- `reply`、`/status` 以及更广的 post-creation control action 当前仍是 canonical mail path。
- Phase 3 direct inbound sidecar 的 authority 仍是“read-side freshness 增强”，不是“允许 direct reply / direct command”的放宽。

因此，Phase 4 当前最稳妥的解释不是“把所有 TaskMail flow 改成纯 direct”，而是：

- 先对已经闭环的 covered flows 做 dual-stack parity；
- 再定义 mismatch triage 与 rollback trigger；
- 最后只对 parity 已经足够的 flow 做 primary-path switch。

## Phase 4 总目标

Phase 4 的目标应固定为：

1. 对 covered flows 建立 direct 与 mail 的可比较 parity 语义。
2. 在 Android 侧保留可信的 mail fallback，而不是只留下名义上的兜底代码。
3. 让 direct 成为 covered flows 的实际默认路径，但仅限于证据已经闭环的 flow。
4. 为未来可能的 `reply` / `/status` direct 化保留进入条件，而不是提前越过跨仓协议冻结边界。

## 当前 covered flow 决策

### 本阶段纳入 covered flows

当前 Phase 4 应只把以下 flow 视为 covered：

- 正式 TaskMail host 中的 `new task`

### 本阶段暂不纳入 covered flows

以下 flow 仍不应在当前 Phase 4 计划里直接进入实现：

- plain continuation `reply`
- single-question direct answer
- multi-question `Answers:` direct answer
- paused `/resume` direct answer
- `/status`
- `/pause`
- `/end`
- attachment reply over direct transport
- 更广的 workspace / session read-side direct 切换

原因不是这些 flow 永远不做，而是它们当前仍缺：

- 跨仓 direct contract freeze
- 与 mail authority 一致的 reply / command 语义镜像
- live parity 与 rollback 方案

## Phase 4 共享工件冻结

当前 Android 侧与 PC 侧对 Phase 4 的收口工件，先冻结为以下三份共享文档：

1. `phase4_dual_stack_parity_checklist.md`
2. `phase4_mismatch_ledger.md`
3. `phase4_rollback_trigger_note.md`

在 Android 仓库中的当前初稿路径分别是：

- `docs/taskmail/planning/android/phase4_dual_stack_parity_checklist.md`
- `docs/taskmail/planning/android/phase4_mismatch_ledger.md`
- `docs/taskmail/planning/android/phase4_rollback_trigger_note.md`

这三份工件的角色分别冻结为：

- parity checklist：逐项定义 covered flow 的对账面与达标证据
- mismatch ledger：记录当前已知差异、严重度、归属、状态与 rollback 影响
- rollback trigger note：把“只记证据 / 回退到 mail / 阻塞 primary-path switch”的触发规则写成显式说明

本文后续的 parity / mismatch / rollback 讨论，都以这三份共享工件为收口形状，而不是临时自由扩写。

## 分批执行计划

### Slice A：covered flow freeze 与 parity matrix

目标：先把 `new task` 的 parity 定义清楚，不急于扩 scope。

本批应完成：

- 冻结当前 covered flow 清单，仅包含 `new task`。
- 为 `new task` 建立 parity matrix，至少覆盖三类结果：
  - direct accepted
  - fallback-classified direct failure -> mail fallback
  - hard direct rejection -> local stop
- 对每类结果明确以下对齐项：
  - Android 本地用户可见提示
  - draft 是否保留
  - 是否允许继续走 mail
  - 后续 mail thread / session outcome 预期
  - workspace / detail 最终 summary 预期
  - 明确证据点，例如 `packet_ack.accepted`、`receipt_id`、可选 `transport_message_id`（仅作观察字段，不作为 v1 UI identity）、fallback / hard rejection 分类，以及是否产生预期的 thread / mail outcome
- 本批的共享工件输出为：
  - `phase4_dual_stack_parity_checklist.md` 首版

本批 closeout 条件：

- 团队可以明确回答“什么叫 `new task` parity 已达标，什么叫只是 direct 能发出去但还不能切主路”。

### Slice B：mismatch triage 与 rollback trigger 冻结

目标：先定义“何时记日志，何时回退，何时本地失败”，避免 direct 默认化后出现模糊语义。

本批应完成：

- 为 covered flow 定义三档结果处理：
  - `observe-only mismatch`
    - 记录诊断证据，但不立即改路由。
  - `fallback-required mismatch`
    - 当次或后续发送切回 mail path。
  - `hard-stop mismatch`
    - 不得静默回退，必须保留 draft 并向用户显示失败。
- 当前建议的归类起点：
  - bootstrap unavailable / transport unavailable / capability unsupported：`fallback-required`
  - direct send throw / relay temporary transport failure：`fallback-required`
  - `invalid_payload` / `validation_failed` / `unauthorized` 及同类 hard rejection：`hard-stop`
  - direct accepted 之后若长期拿不到与 covered flow 预期一致的后续 mail/session evidence：先记为 parity gap，不在证据闭环前扩大 switch 范围
- 冻结最小回退触发条件：
  - 当前发送尝试立即回退
  - 某 covered flow 暂时改回 mail-default
  - 明确哪些情形只影响单次发送，哪些情形应影响该 flow 的后续默认路由
- 本批的共享工件输出为：
  - `phase4_mismatch_ledger.md` 首版
  - `phase4_rollback_trigger_note.md` 首版

本批 closeout 条件：

- direct 与 mail 的分流不再依赖“看到异常时临时猜测”，而是有 reviewable 的分类规则。

### Slice C：`new task` primary-path switch

目标：在 `new task` 上把 direct 从“已验证可用的候选路径”切到“正式默认路径”，同时保留真实 rollback。

本批应完成：

- 在正式 `new task` flow 上明确 direct-default 规则。
- 保留 mail fallback。
- 保留 hard rejection 本地停止与 draft retention。
- 让调试与日志能区分：
  - direct accepted
  - fallback to mail
  - hard rejection
- 如需开关，优先采用“按 covered flow 控制”的粒度，而不是一次性放大全量 TaskMail transport 范围。

本批 closeout 条件：

- `new task` 默认走 direct，但 mail fallback 仍有可执行验证，不是死代码。

### Slice D：为下一批 direct control flows 准备进入条件

目标：不直接实现 `reply` / `/status`，而是把它们进入下一批的前提列清楚。

后续 flow 想进入 covered scope，至少应先满足：

- PC-side current docs 已冻结对应 direct contract，而不是只停留在 planning。
- Android 与 PC 对以下语义已有一一对应：
  - plain continuation `reply`
  - single-question answer
  - multi-question `Answers:`
  - paused `/resume`
  - attachment-only continuation
  - `/status`
- 已明确这些 flow 的 fallback 与 hard-stop 规则。
- 已明确 direct path 不会破坏当前 mail authority 中的 subject identity、reply anchor、`bot mailbox` 路由与 structured answer 语义。

在这些条件未满足前，Phase 4 不应把 `reply` 或 `/status` 直接纳入实现承诺。

## 实施 guardrails

Phase 4 期间必须持续遵守以下边界：

- 不移除 mail path。
- 不把 current-status / validation-ledger / mail-rules 的 authority 让位给 planning 文档。
- 不把 direct inbound sidecar 误写成“direct transport 已可替代 mail truth layer”。
- 不修改 `reply` 相关协议语义来迁就尚未冻结的 direct contract。
- 不因为追求 direct-default 就弱化 hard rejection 的本地失败可见性。
- 调试日志仍需遵守隐私约束，不记录邮件正文、邮箱地址、凭据或 token。

## 推荐验证顺序

### 自动化验证

每个实际实现批次至少先跑：

- `.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest`
- `.\gradlew.bat :feature:taskmail:internal:detekt`
- `.\gradlew.bat :feature:taskmail:internal:lintDebug`

如改到正式 host / navigation / app wiring，再补：

- `.\gradlew.bat :app-thunderbird:assembleDebug`
- `.\gradlew.bat :app-k9mail:assembleDebug`

### 手工 / live 验证

在 `docs/TASKMAIL-DEBUG-VALIDATION.md` 的设备路径基础上，Phase 4 至少应补齐：

- `new task` direct accepted
- `new task` fallback-classified direct failure -> mail fallback
- `new task` hard rejection -> draft retention + local error
- direct accepted 后，不依赖手动 `pull-to-refresh` 的后续 mail/session 可见结果
- workspace / detail summary 与最终结果 token 的一致性
- rollback trigger 被触发时，下一次发送是否按预期走 mail-default 或保留 hard-stop

## 文档 closeout 要求

每完成一个实际实现批次，都应同步更新：

1. `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
2. `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
3. 最新 handoff note

如果批次只是 planning / freeze，而没有代码与验证，也应在 handoff 中明确写清：

- 当前 covered flow 边界
- 尚未冻结的 direct contract
- 下一批的唯一主线

## 当前建议的执行顺序

如果下一轮是工程实现而不是继续做纯文档，推荐顺序为：

1. 先把当前 Phase 4 读成 `new_task parity baseline` 收口，而不是继续扩旧样本范围。
2. `thread_097` 已经用冻结后的 shared-artifact 流程完成第一条 post-freeze fresh closeout，`thread_098` 又在当前 formal-host build 上闭环了 `request_id` 首键 bind；下一轮不要回到 old-style 人工拼接，也不需要默认再先追同类样本。
3. 当前 `new_task` 已具备进入 guarded direct-default review 的 evidence 条件，对应 decision note 与 rollout / activation note 现已落地；同日后续 authority / validation 也已把更窄问题收口为 `no_additional_new_task_activation_config_needed`，因此下一步不再是补 bind blocker、补这两份 note，或再单独新增 `new_task` flow-scoped activation / config change。
4. 如果后续 fresh sample 再次退回到 `transportMessageId` / `ingress_message_id` 才能闭环，或 helper 重新出现 `android_request_id_missing`，再把结论回退到 `not_ready_keep_mail_default`。
5. `reply` / `/status` 的 shared post-creation session-action contract 现已在相邻 PC 仓 planning/shared 层落地；
   当前下一步不再是继续追问 contract ownership / scope，而是进入 Android 实现规划，对应主文档为
   `taskmail-phase5-reply-status-android-implementation-plan-v0.1.md`。
6. 同日后续实现已继续把这条线推进到 guarded code slice：
   - canonical target identity plumbing 已闭环
   - `RelayTaskMailDirectSessionActionSender` 已按 shared contract 落地
   - `TaskSessionDetailViewModel` 现已只对 `current-session plain reply` 与 `current-session /status` 尝试 guarded
     direct lane
   - latest direct session-action evidence 持久化、重建与 review surface 已落地
   当前下一步不再是写 sender / gating，而是继续 Batch D 的 live / mailbox closeout，并把记录字段对齐到 shared
   closeout artifacts。

这比“同时推进 `new task`、`reply`、`/status` 的 direct 化”更稳，因为它尊重了当前 repository 已闭环的唯一 direct business slice，也避免把未冻结的 post-creation control actions 过早拉进实现范围。

## 当前未决问题

截至本文更新时，仍需显式保留的开放问题包括：

- latest direct session-action evidence 已能在 Android 本地持久化与重建，但它尚未进入 shared-artifact closeout；
  live closeout 时应如何把 `action_type`、`target_session_identity`、`request_id`、`ingress_message_id`、
  `terminal_mail_subject`、`last_summary` 与 same-run bind readout 对齐到 shared contract，仍待后续实跑收口。
- accepted direct `reply` 与 accepted direct `/status` 目前只有 focused automated evidence；二者如何在 live mailbox
  上稳定收敛到 canonical mail outcome，仍需后续 closeout。
- latest direct evidence review surface 现已具备仓内 UI / ViewModel / repository 证据，但它在真实设备上的 screen reload /
  recent-tasks cold start 保持可 review 仍待后续手工验证。
- 更宽的 `:feature:taskmail:internal:testDebugUnitTest` 仍会被 `TaskMailValidationRunner` 对相邻 PC 仓
  `scripts/test_fetch_latest_100.json` 的本地依赖阻塞；当前不应把该失败误读为本轮 Phase 5 guarded slice 回归。

## 当前结论

2026-03-22 之后的 Android TaskMail 下一阶段主线应明确为：

- 不是继续把 Phase 3 少量尾项当 blocker；
- 也不是立刻把全部 TaskMail flow 改成纯 direct；
- 而是先把 Phase 4 收口成 `new_task parity baseline`，再进入 Phase 5 的窄范围 hardening：沿用已在 `thread_097`
  / `thread_098` 上跑通的 shared-artifact closeout 流程，把 `new_task` 的 guarded direct-default review 结论、
  decision note 与 rollout / activation 边界显式写清楚；在没有新的 evidence 回退前，不继续为同类 bind blocker
  反复补样本，也不提前扩大到 `reply` / `/status`；当前 Android 侧的更近一步已经明确为保持
  `no_additional_new_task_activation_config_needed` 这个 verdict 同步在 authority / handoff 中，而不是再造新的
  activation/config 实现；而 `reply` / `/status` 这条线也已经从 prerequisites / PC coordination 推进到 shared
  planning-layer contract 已落地、Android 可进入实现规划的阶段：第一批 scope 固定为
  `current-session plain reply` + `current-session /status`；canonical `workspace_id + session_id`、guarded direct
  sender / gating、以及 latest direct session-action evidence 首段已经落地，当前下一步是保持 Layer 1 mail truth
  layer 不变，继续补 live / mailbox closeout 与 shared-artifact evidence 读法，而不是再扩写 sender scope。
