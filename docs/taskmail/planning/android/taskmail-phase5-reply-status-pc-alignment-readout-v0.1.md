# TaskMail Phase 5 `reply` / `/status` PC Alignment Readout（v0.1）

更新时间：2026-03-22

## 状态

- 本文承接：
  - `docs/taskmail/planning/android/taskmail-phase5-reply-status-direct-contract-prerequisites-v0.1.md`
  - `docs/taskmail/planning/android/taskmail-phase5-reply-status-pc-coordination-checklist-v0.1.md`
  - `docs/taskmail/planning/android/taskmail-phase5-reply-status-pc-message-template-v0.1.md`
- 本文记录两层收敛：
  - 相邻 PC 侧对 Android 提出的 repo-side reading 已给出正式对齐
  - `E:\projects\mail_based_task_manager\docs\plans\post_creation_session_action_contract_v1.md`
    已作为 shared planning-layer contract 落地
- 本文不把这些口径误写成 Layer 1 current-protocol authority。
- 本文的结论也不是“可以直接切 direct reply / direct /status”，而是：
  - 缺口已经不再是 shared contract 不存在
  - Android 可以进入实现规划
  - 但 current protocol 与 mail truth layer 仍不变

## 先读这些文档

- `docs/TASKMAIL-MAIL-RULES.md`
- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/taskmail/planning/android/taskmail-phase5-reply-status-direct-contract-prerequisites-v0.1.md`
- `docs/taskmail/planning/android/taskmail-phase5-reply-status-pc-coordination-checklist-v0.1.md`
- `docs/taskmail/planning/android/taskmail-phase5-reply-status-pc-message-template-v0.1.md`
- `E:\projects\mail_based_task_manager\docs\current\mail_protocol.md`
- `E:\projects\mail_based_task_manager\docs\current\android_runner_communication_contract.md`
- `E:\projects\mail_based_task_manager\docs\plans\phase2_direct_outbound_contract_v1.md`
- `E:\projects\mail_based_task_manager\docs\plans\phase3_direct_inbound_wire_v1.md`
- `E:\projects\mail_based_task_manager\docs\plans\post_creation_session_action_contract_v1.md`

## 已在 shared planning-layer 收敛的点

### 1. 当前事实层

相邻 PC 侧确认以下读法与 Android 当前 authority 一致：

- Layer 1 当前仍是 mail-first control plane
- 当前 direct 例外仍只限：
  - direct `new_task`
  - active-session-detail read-side sidecar
- direct `reply` / direct `/status` 当前都还不属于 current protocol
- current canonical truth layer 仍是 mail truth layer
- current same-run closeout / parity 锚点继续包括：
  - `request_id`
  - `ingress_message_id`
  - `packet_id`
  - `last_summary`
  - `terminal_mail_message_id`
  - `terminal_mail_subject`
  - same-run bind readout 仍沿用
    `request_id -> transport_message_id <-> ingress_message_id -> last_summary`

### 2. v1 scope / ownership / target boundary

相邻 PC 侧已把以下点冻结进 shared planning-layer contract：

- 第一批 scope 只包含：
  - `current-session plain reply`
  - `current-session /status`
- ownership 不扩写 `phase2-direct-outbound-contract-v1`
- 另起 `post-creation session-action contract`
- v1 target boundary 固定为 `current-session only`
- 第一批不引入：
  - targeted-session variant
  - cross-workspace switching
  - quick answer
  - multi-question `Answers:`
  - paused `/resume`
  - attachment continuation

### 3. identity / bridge / canonical semantics

shared contract 已明确：

- v1 最小 target identity 为：
  - `workspace_id + session_id`
  - `thread_id` 仅作 supporting identity
- direct `reply` 不要求 Android 构造 mail `In-Reply-To` / `References` 等价物
- Android packet 只声明 canonical current session target 与当前 action intent
- server side 负责桥接进现有 canonical mail ingress
- direct `reply` accepted 后，后续 run / question / terminal outcome 仍沿 canonical mail truth layer 产出
- direct `/status` accepted 后，仍产出 canonical `[STATUS]` mail
- subject / state capsule / terminal semantics 与 mail path 保持一致

### 4. machine-readable classification / closeout artifacts

shared contract 已至少冻结：

- `fallback_required`
- `hard_stop`
- `switch_blocker`

shared contract 也已明确 live closeout 至少应规划保留：

- `action_type`
- `target_session_identity`
- `request_id`
- `ingress_message_id`
- `terminal_mail_subject`
- `last_summary`
- same-run bind readout

## shared contract 已解决什么，没解决什么

### 已解决的 gap

当前最大的跨仓 gap 已不再是“有没有 shared contract”。这一步已经被
`post_creation_session_action_contract_v1.md` 关闭。

因此，Android 侧现在不需要再继续向 PC 侧追问这些问题：

- 第一批 scope 到底是不是 `current-session plain reply + current-session /status`
- contract ownership 到底是不是另起一份 session-action contract
- v1 target boundary 是否允许 targeted-session variant
- direct `reply` / `/status` accepted 后是否仍沿 canonical mail truth layer 收敛

### 仍未解决的 gap

但 shared planning-layer contract 的出现，并不等于以下内容已成立：

- direct `reply` / direct `/status` 已进入 `docs/current/*`
- Android 已可以不经实现规划直接接入 detail direct send path
- Layer 1 mail-first 行为已经被改写

当前真正剩下的 gap 是：

- Android 侧如何把 shared contract 翻译成可实现的 route / key / sender / ViewModel 变更
- 实现后如何拿到 closeout evidence，再决定何时回写 `docs/current/*`

## Android 侧当前读法

基于这轮对齐与 shared contract，Android 侧当前最稳妥的读法是：

1. prerequisites note 里的关键协议问题已经完成跨仓收口。
2. 当前不再需要继续扩 PC coordination 文档链。
3. 下一份 Android 主文档不该继续停留在“要不要起 contract”，而应转向实现规划。
4. 这仍不授权直接把 `TaskSessionDetailViewModel` 接到 direct seam，更不授权把 Layer 1 行为误写成已经切换。

## Android 侧第一个真实 blocker

shared contract v1 要求 direct session-action 以 canonical `workspace_id + session_id` 作为最小 target identity。

但 Android 当前实现里：

- `feature/taskmail/api/src/main/kotlin/net/thunderbird/feature/taskmail/api/TaskMailRoute.kt`
  中的 `TaskMailRoute.SessionDetail` 只携带 `sessionId + threadId`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/model/TaskSessionKey.kt`
  也只保留 `sessionId + threadId`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/TaskSessionDetailViewModel.kt`
  的 `LoadDetail` 入口仍按这组 key 装配 detail

因此，Android 当前真正的首个实现 blocker 不是 packet schema，而是：

- 先把 canonical `workspace_id + session_id` 稳定贯通到 detail route / `TaskSessionKey` / send-path gate

在这个 blocker 关闭前，Android 不应直接走 shared contract v1 的 send path，也不应靠 `thread_id`、
`repo_path + workdir` 或临时 direct observation 结果去猜 direct action target。

## Android 侧继续保持的非目标

相邻 PC 侧已同意 Android 当前继续坚持这些非目标：

- 不把 `TaskSessionDetailViewModel` 直接接到 direct seam
- 不猜 direct `reply` / `/status` packet schema 之外的 hidden bridge behavior
- 不把 quick answer / `Answers:` / `/resume` / attachment continuation 并进第一批
- 不把 shared planning-layer contract 误写成 current protocol

## 当前结论

这轮 PC 侧正式回复与 shared contract 的落地，把 Phase 5 文档工程从“等待对齐 / 等待 contract”推进到了
“跨仓 contract 已有，Android 应进入实现规划”。

因此，当前最合理的下一步不再是继续扩 PC coordination 文档，而是：

- 基于 `post_creation_session_action_contract_v1.md` 起 Android 实现规划
- 先关闭 canonical `workspace_id + session_id` 的 detail-route blocker
- 在不改写 Layer 1 mail truth layer 的前提下，再规划 `current-session plain reply` 与
  `current-session /status` 的受控 direct send
