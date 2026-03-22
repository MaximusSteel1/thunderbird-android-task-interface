# TaskMail Phase 5 `reply` / `/status` Android 实现规划（v0.1）

更新时间：2026-03-23

## 状态

- 本文承接：
  - `docs/taskmail/planning/android/taskmail-phase5-reply-status-direct-contract-prerequisites-v0.1.md`
  - `docs/taskmail/planning/android/taskmail-phase5-reply-status-pc-alignment-readout-v0.1.md`
  - `E:\projects\mail_based_task_manager\docs\plans\post_creation_session_action_contract_v1.md`
- 本文的目标不是提升 Layer 1 authority，而是把 shared planning-layer contract 翻译成 Android 侧可执行的实现规划。
- 本文当前只规划第一批：
  - `current-session plain reply`
  - `current-session /status`
## 2026-03-23 Batch D Live/Manual Closeout Update

2026-03-23 这轮 `Batch D` 已经完成第一组 formal-host live/manual closeout，当前阶段不再是“继续收样本”，而是“补 shared-artifact / strong-bind”。

- `thread_019 / Phase5 status closeout 20260323 A`：正式 `Tasks` detail 点击 `/status` 后，Android latest direct evidence 记录为 `actionType=Status`、`target=workspace_cb2404bf828c/thread_019`、`bootstrapStatus=hello_ack`、`outcome=MailFallbackSucceeded`、`switchGate=FallbackRequired`；PC mailbox 侧收敛到 canonical `[STATUS][S:thread_019] ...`，detail 返回重开后 evidence card 仍可恢复
- `thread_020 / Phase5 reply closeout 20260323 B`：发送 plain reply `PHASE5_REPLY_CLOSEOUT_20260323_B` 后，Android latest direct evidence 记录为 `actionType=Reply`、`target=workspace_cb2404bf828c/thread_020`、`bootstrapStatus=hello_ack`、`outcome=MailFallbackSucceeded`、`switchGate=FallbackRequired`；PC mailbox 侧先收到 reply ingress，再在 failed thread 上触发 fresh recovery run `20260323_020701_9913` 并收敛到 canonical `[DONE][S:thread_020] ...`
- `thread_020` 发送后瞬时出现过 `Unable to load session`，但 detail 重开与 formal-host desktop-launch cold start 后 evidence card 都能恢复，因此当前更像 post-send UI drift 候选，而不是 durable evidence failure
- 当前 direct path 仍然受 `FallbackRequired` gate 约束；live closeout 证明的是 guarded fallback semantics 与 durable evidence / persistence，不是 direct-default rollout
- Android retained `TaskMailSessionActionSendRecord` 现在已经足以稳定提供 `actionType + target_session_identity + outcome / switchGate / bootstrapStatus`
- 共享 closeout 仍未闭环：Android fallback 记录还没有 `requestId` / `transportMessageId`，PC 当前 post-creation fallback canonical artifacts 也还没有 `action_type` / `target_session_identity` / action-specific `ingress_message_id`

因此 `Batch D` 的下一个重点不再是继续收样本，而是补 shared-artifact / strong-bind gap。只有这个 gap 补齐后，才需要再回到同样窄的 live/manual 样本，确认 closeout bundle 能从 `last_summary` weak bind 升级为 stronger bind。

- 截至 2026-03-23，`Batch A`、`Batch B`、`Batch C` 已完成代码落地与 focused Gradle 验证；`Batch D` 的第一段 durable evidence / focused verification 也已落地；当前剩余主线已收敛到 live / mailbox closeout 与 shared-artifact closeout 读法。
- 本文不把以下能力提前拉进实现：
  - quick answer
  - multi-question `Answers:`
  - paused `/resume`
  - attachment continuation
  - targeted-session variant
  - cross-workspace switching

## 先读这些文档

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/TASKMAIL-MAIL-RULES.md`
- `docs/taskmail/planning/android/taskmail-phase5-new-task-guarded-rollout-observation-plan-v0.1.md`
- `docs/taskmail/planning/android/taskmail-phase5-reply-status-direct-contract-prerequisites-v0.1.md`
- `docs/taskmail/planning/android/taskmail-phase5-reply-status-pc-alignment-readout-v0.1.md`
- `E:\projects\mail_based_task_manager\docs\plans\post_creation_session_action_contract_v1.md`
- `feature/taskmail/api/src/main/kotlin/net/thunderbird/feature/taskmail/api/TaskMailRoute.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/model/TaskSessionKey.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/model/TaskSessionReplyContext.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/usecase/SendTaskMailReply.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/usecase/RunTaskMailDirectOrFallback.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/TaskSessionDetailViewModel.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/TaskMailModule.kt`

## 当前 shared contract 对 Android 的硬约束

`post_creation_session_action_contract_v1.md` 已把 Android 第一批需要遵守的边界冻结为：

- v1 scope 仅限 `current-session plain reply` 与 `current-session /status`
- v1 target boundary 固定为 `current-session only`
- 最小 target identity 固定为 canonical `workspace_id + session_id`
- `thread_id` 仅作 supporting identity，不能替代主 target key
- Android packet 只声明 canonical current session target 与 action intent
- server side 负责桥接进现有 canonical mail ingress
- accepted direct action 之后，最终 user-visible outcome 仍沿 canonical mail truth layer 收敛

这意味着 Android 的实现规划不能从“怎么拼 packet”开始，而必须先回答：

- 发送时能否稳定拿到 canonical `workspace_id + session_id`
- 当前 detail UI 能否在 v1 scope 外继续可靠地保持 mail path
- direct accepted 与最终 canonical mail outcome 之间的等待语义如何表达

## Android 当前实现现实

### 1. detail route / key 原先缺少 canonical `workspace_id`

本轮开始前，代码里：

- `TaskMailRoute.SessionDetail` 只携带 `sessionId` 与 `threadId`
- `TaskSessionKey` 也只包含 `sessionId` 与 `threadId`
- `TaskSessionDetailViewModel` 的 `LoadDetail` 入口按这组 key 装配 detail

这与 shared contract v1 的 target identity 要求不一致。那时 Android 端还没有一条稳定、显式、可测试的路径，把
canonical `workspace_id + session_id` 从导航入口贯通到 detail send path。

截至 2026-03-22 当前批次结束时，这个 blocker 已完成第一阶段收口：

- `TaskMailRoute.SessionDetail` 已显式承载 `workspaceId`
- `TaskSessionKey` 已扩展为 `workspaceId + sessionId + threadId`
- workspace 列表到 detail 导航已一并传递 canonical `workspaceId`
- session detail cache / snapshot / repository 已补上对 legacy 无 `workspaceId` key 的兼容读取
- 对应 route / workspace / detail / repository focused tests 已补齐并通过

因此，Phase 5 当前的首个真实 blocker 已不再是 “detail route 拿不到 canonical target identity”。同日后续实现已继续关闭：

- `Batch B`：`reply` / `/status` 的 session-action direct sender seam
- `Batch C`：detail `ViewModel` 的 guarded direct gating

当前真正剩余的是：

- `Batch D` 的后半段：把新 direct lane 的 closeout / live evidence 读法收口到最小可复用验证面

### 2. 当前 send path 仍是 mail-only

当前 detail send path 的现实是：

- `TaskSessionDetailViewModel` 的 `sendReply()` 仍全部走 `SendTaskMailReply`
- `sendStatusQuery()` 也仍全部走 `SendTaskMailReply.sendStatusQuery()`
- `SendTaskMailReply` 当前只负责 mail reply serialization 与 mail sender
- `TaskSessionReplyContext` 保存的是 mail reply 所需的 account / folder / message anchor 信息，不是 direct target identity

也就是说，现有 detail send stack 还没有一层专门承接 post-creation session-action direct sender。

### 3. 可复用的 send-side seam 已经存在

Android 当前并不是完全没有可复用基础：

- `RunTaskMailDirectOrFallback` 已经把 relay bootstrap、direct attempt、mail fallback、hard rejection 的总编排抽出来了
- 这条 seam 已被 `new_task` 使用，并且其“direct accepted / fallback / rejected”读法已在当前仓内稳定

因此，Phase 5 的 Android 实现规划不需要重新发明新的 bootstrap / fallback 编排，而应在这个 seam 上补足：

- session-action 级 direct sender
- session-action 级 result / classification 映射
- detail UI 的 gating 与 evidence 记录

## 第一个真实 blocker

当前最先要关闭的 blocker 不是 packet schema，而是 canonical target identity plumbing。

具体说，就是先把 canonical `workspace_id + session_id` 贯通到：

- `TaskMailRoute.SessionDetail`
- `TaskSessionKey`
- `TaskSessionDetailViewModel`
- detail send-path gating 所需的 state / request models

在这一步完成前，Android 不应：

- 用 `thread_id` 直接代替主 target identity
- 用 `repo_path + workdir` 去猜 v1 direct send target
- 依赖临时 direct observation 才回填 target，再反向推导 send path

原因很简单：shared contract 已经把 v1 direct action 的 target identity 冻结为 canonical `workspace_id + session_id`，
Android 侧不应在实现起点就偏离这条约束。

## 推荐分批

### Batch A：canonical target identity plumbing

目标：把 `workspace_id + session_id` 变成 detail route 与 send gate 的稳定输入，而不是运行期猜测结果。

本批应完成：

- 扩展 `TaskMailRoute.SessionDetail`，显式承载 canonical `workspaceId`
- 扩展 `TaskSessionKey`，与 route 保持一致
- 从 workspace 列表到 detail 导航时，把 canonical `workspaceId` 一并带入
- 补齐 route / navigation / deep-link 相关测试
- 明确 detail reopen / process death 后，`workspaceId` 的恢复路径仍保持稳定

本批 closeout 条件：

- Android 侧可以在不依赖 mail reply context、也不依赖 direct observation 回填的前提下，稳定拿到
  canonical `workspace_id + session_id`

本批当前结果（2026-03-22）：

- 已完成代码落地
- 已补齐 focused route / ViewModel / repository / cache tests
- 已通过：
  - `.\gradlew.bat :feature:taskmail:internal:detekt`
  - `.\gradlew.bat :feature:taskmail:internal:lintDebug`
  - focused `testDebugUnitTest`（见 handoff）
- 额外说明：
  - 全量 `:feature:taskmail:internal:testDebugUnitTest` 仍受既有环境依赖影响，`TaskMailValidationRunner`
    会读取相邻 PC 仓缺失的 `scripts/test_fetch_latest_100.json`
  - 该阻塞不属于本批代码语义错误，也不应为此改动相邻 PC 仓

### Batch B：post-creation session-action direct sender

目标：引入第一批 direct session-action sender，但不改写现有 mail reply serialization 规则。

本批应完成：

- 新增 session-action direct sender / use case
- 明确它与 `SendTaskMailReply` 的职责边界：
  - `SendTaskMailReply` 继续负责 canonical mail path
  - 新 sender 负责 shared contract v1 packet 组装与 direct attempt
- 复用 `RunTaskMailDirectOrFallback`
- 把 shared contract 的 `fallback_required` / `hard_stop` / `switch_blocker`
  映射到 Android 侧可消费的结果层

本批 closeout 条件：

- Android 侧已有可测试的 direct sender seam，且不会把 `reply` mail semantics 与 direct session-action packet 语义混写

本批当前结果（2026-03-22）：

- 已完成代码落地
- `RelayTaskMailDirectSessionActionSender` 已按 shared
  `post_creation_session_action_contract_v1` 覆盖 `current-session plain reply` 与 `current-session /status`
- `SendTaskMailDirectSessionAction` 已接入 DI
- relay `packet_ack` / server-error 到 `FallbackToMail` / `Rejected` 的 sender-level 分类已补齐 focused tests

### Batch C：detail ViewModel gating

目标：只把 v1 scope 内、且 target identity 完整的发送操作接到 direct seam，其余全部继续留在 mail path。

本批应完成：

- `TaskSessionDetailViewModel` 只对以下场景尝试 direct lane：
  - current-session plain reply
  - current-session `/status`
- 下列场景继续保持 mail path：
  - quick answer
  - multi-question `Answers:`
  - paused `/resume`
  - attachment continuation
  - 任意 targeted-session 读法
- direct accepted 只表示“已进入 direct lane”，不能被 UI 误写成最终 task outcome
- final outcome 仍由 canonical mail truth layer 驱动 detail 刷新与用户可见状态

本批 closeout 条件：

- direct lane 的启用条件、禁用条件、fallback / hard-stop 行为都在 ViewModel 层可 review、可测试

本批当前结果（2026-03-22）：

- 已完成代码落地
- `TaskSessionDetailViewModel` 现已只在以下条件满足时尝试 guarded direct lane：
  - canonical `workspace_id + session_id` 可从当前 detail route 获得
  - 动作属于 current-session plain reply 或 current-session `/status`
- 下列行为继续保持 mail path：
  - quick answer
  - multi-question `Answers:`
  - paused `/resume`
  - attachment-bearing continuation
- direct accepted 仍只表示“已进入 direct lane”，成功文案也明确保留 canonical mail truth 的后续收敛语义

### Batch D：evidence / verification / closeout

目标：在代码落地后，按 shared contract 的 closeout anchors 补齐最小证据面。

本批应完成：

- 为 post-creation session-action 新增 durable latest-send record 与 detail review surface，至少带出：
  - `action_type`
  - `target_session_identity`
  - `outcome`
  - `switch_gate`
  - `bootstrap_status`
  - optional `request_id` / `receipt_id` / `transport_message_id`
  - optional `fallback_reason` / `error_message`
- focused unit tests：
  - route / key / ViewModel target identity gating
  - plain reply 与 `/status` 的 direct-eligible / direct-ineligible 分流
  - fallback / hard-stop / switch-blocker 映射
  - accepted direct result 与最终 mail outcome 分离读法
- 如涉及 host / navigation 改动，再补对应 assemble coverage
- 后续 live closeout 应对齐记录：
  - `action_type`
  - `target_session_identity`
  - `request_id`
  - `ingress_message_id`
  - `terminal_mail_subject`
  - `last_summary`
  - same-run bind readout

本批 closeout 条件：

- Android 侧已具备最小自动化验证与 closeout 字段规划，不会把 direct `reply` / `/status` 做成只有 demo 没有证据的能力

本批当前结果（2026-03-23）：

- 已完成 `TaskMailSessionActionSendRecord`、`TaskMailSessionActionSendRecordRepository` 与 file-backed JSON codec / repository
- 最新记录当前按 canonical `workspace_id + session_id` 持久化；`thread_id` 仅作 supporting identity，不参与 latest-record 主键选择
- `TaskSessionDetailViewModel` 现已在 guarded plain reply 与 guarded `/status` 尝试后保存 latest direct session-action evidence，并在 detail 载入时重建该记录
- `TaskSessionDetailContent` 现已新增 latest direct evidence review surface，用于回看 `outcome`、`switch_gate`、`bootstrap_status`、optional `request_id` / `receipt_id` / `transport_message_id`、以及 optional `fallback_reason` / `error_message`
- 已通过本轮 focused 验证：
  - `.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest --tests "net.thunderbird.feature.taskmail.internal.data.cache.FileBackedTaskMailSessionActionSendRecordRepositoryTest" --tests "net.thunderbird.feature.taskmail.internal.ui.detail.TaskSessionDetailViewModelTest" --tests "net.thunderbird.feature.taskmail.internal.ui.detail.TaskSessionDetailScreenKtTest"`
  - `.\gradlew.bat :feature:taskmail:internal:detekt :feature:taskmail:internal:lintDebug`
- 当前仍未收口：
  - live mailbox / device closeout
  - latest direct session-action evidence 与 shared closeout artifacts 的 same-run bind
  - accepted direct `reply` 与 accepted direct `/status` 的 live canonical outcome 收敛证明

## 当前非目标

在上述分批完成前，Android 侧继续明确不做：

- 不把 quick answer / `Answers:` / `/resume` / attachment continuation 并进第一批
- 不把 targeted-session variant 或 cross-workspace switching 偷带进 v1
- 不把 ack 直接当成最终 status / reply result
- 不改写 Layer 1 mail-first current behavior
- 不把 shared planning-layer contract 误写成 current protocol authority

## 本文结论

截至 2026-03-22 当前批次结束时，Phase 5 `reply` / `/status` 的 Android 下一步已经不再是“继续要协议答案”，而是：

1. 接受 shared contract 已冻结的 v1 边界。
2. 将 canonical `workspace_id + session_id` 的 detail-route blocker 视为已完成的前置批次。
3. 将 `Batch B` 与 `Batch C` 视为已完成的 guarded implementation slices：
   - sender seam 已落地
   - detail gating 已落地
4. 下一步继续 `Batch D` 的后半段：围绕 `current-session plain reply` 与 `current-session /status` 做 live / mailbox closeout，并把 latest direct session-action evidence 与 shared closeout artifacts 的记录方式对齐。
