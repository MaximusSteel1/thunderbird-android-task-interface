# TaskMail Phase 4 Rollback Trigger Note（Android 侧首轮 trigger baseline）

更新时间：2026-03-22

## 状态

- 本文是 Android 侧对 `phase4_rollback_trigger_note.md` 的首轮 trigger baseline。
- 当前仅覆盖 `new_task`。
- 本文冻结的是 trigger 形状与当前建议起点，并补入 Android 正式 flow 已具备的 send-side / UI / live-smoke
  证据来源；它仍不是最终实现状态。

## 当前 trigger 形状冻结

每条 rollback trigger 至少写清：

- `trigger_id`
- `covered_flow`
- `trigger_condition`
- `evidence_source`
- `action`
- `blocks_primary_path_switch`
- `status`
- `notes`

## Trigger 初稿

| trigger_id | covered_flow | trigger_condition | evidence_source | action | blocks_primary_path_switch | status | notes |
| --- | --- | --- | --- | --- | --- | --- | --- |
| new_task.bootstrap_failure | `new_task` | bootstrap 未到达可发送 direct packet 的前置状态 | Android bootstrap result、relay connect evidence、`TaskMailDirectSendEvidence.bootstrapStatus` | 当前发送回退到 mail；重复出现时进入 switch review | yes | `android_evidence_present` | 见 R1 |
| new_task.ack_classification_drift | `new_task` | `packet_ack` 分类与当前 authority / fallback matrix 不一致 | `packet_ack.accepted`、`error_code`、classification tests、shared parity checklist | 暂停扩大 direct-default；必要时回退到 mail-default | yes | `android_evidence_present` | 见 R2 |
| new_task.accepted_without_expected_outcome | `new_task` | direct accepted 后长期拿不到预期 thread / status mail outcome | parity checklist、mail outcome evidence、runtime state、latest send evidence | 记入 mismatch ledger；在证据闭环前阻止扩大 switch 范围 | yes | `android_boundary_confirmed` | 见 R3 |
| new_task.fallback_path_unavailable | `new_task` | direct 失败后 mail fallback 也不可用 | Android send result、mail send failure evidence、draft-preservation UI evidence | 保留 draft 并显示实际错误；阻止 primary-path switch | yes | `android_evidence_present` | 见 R4 |
| new_task.summary_outcome_drift | `new_task` | user-visible summary / terminal outcome 与 mail baseline 高风险漂移 | workspace/detail summary evidence、mail outcome evidence、shared parity checklist | 记入 mismatch ledger；必要时改回 mail-default | yes | `android_boundary_confirmed` | 见 R5 |

## 稳定消费顺序

- 第一步先看 parity checklist；没有对应 checklist row，就不直接判定 trigger。
- 第二步先确认该样本已经形成 checklist 里的 `daily_closeout_bundle`；没有同一 run 的 bundle，就不直接给 primary-path switch 下结论。
- 第三步只把 confirmed drift 升级到 mismatch ledger；`historical_gap` 这类留存缺口本身不单独触发 rollback。
- 第四步再按 trigger table 判定动作：`bootstrap_failure` / `fallback_path_unavailable` 主要看 send-side evidence，`accepted_without_expected_outcome` / `summary_outcome_drift` 主要看 checklist 与 PC canonical outcome。
- 对 `direct accepted` 行，same-run bind 顺序固定为：`request_id` -> `transportMessageId / ingress_message_id` -> `last_summary token` 弱回退。
- 若 `direct accepted` 行的 bind 还没闭环，只记 evidence gap，不直接判 `accepted_without_expected_outcome` 或 `summary_outcome_drift`；但该样本也不能作为 `direct-default` 的正向输入。
- 只有在 bound sample 上真的出现 expected outcome 缺失或 summary conflict，才升级到 mismatch / rollback review。
- 只要存在 open 的 `fallback_required` 或 `switch_blocker` 级别 mismatch，`new_task` 就不得进入 `direct-default` 评估。
- mail fallback 仍需保持可执行；当前 trigger note 只覆盖 `new_task`，不把 `reply` / `/status` 提前纳入本轮 switch gate。

## Android 侧 trigger 证据摘录

### R1 Bootstrap Failure

- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/domain/usecase/RunTaskMailDirectOrFallbackTest.kt`
  的 `execute should use mail fallback when bootstrap is unavailable` 已锁定：当 bootstrap 没到 `hello_ack` 时，
  Android 会产生 `TaskMailDirectOutcome.MailFallbackSucceeded`、`TaskMailDirectSwitchGate.FallbackRequired`，而不是假装
  direct 已成功。
- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/ui/newtask/TaskNewTaskViewModelTest.kt`
  的 `send should use mail fallback message when direct bootstrap is unavailable` 已锁定：
  - Android 会真的发出 canonical mail `new task`
  - 用户看到的是明确的 `mail fallback` 成功语义
  - `fallbackReason` 与 `bootstrapStatus` 会保留到 latest evidence
- 因此，这条 trigger 在 Android 侧已经不是抽象建议，而是已有 focused evidence 的实际 boundary。

### R2 Ack Classification Drift

- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/data/relay/protocol/RelayProtocolJsonCodecTest.kt`
  已锁定 `packet_ack` 的 `error_code` / `error_message` 解码。
- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/data/relay/RelayTaskMailDirectNewTaskSenderTest.kt`
  已锁定：
  - `unsupported_action` -> `FallbackToMail`
  - `invalid_payload` -> `Rejected`
  - `packet_ack.error_code = invalid_payload` -> `Rejected`
  - `validation_failed: ...` -> `Rejected`
  - 非 hard-rejection 的 ack capability rejection 仍留在 mail fallback 路径
- 因此，只要 Android 实测观察到的 `packet_ack` 分类和当前 shared matrix 不一致，就不应继续扩大
  `direct-default`，而应先把差异升级为 parity / mismatch 证据。

### R3 Accepted Without Expected Outcome

- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/ui/newtask/TaskNewTaskViewModelTest.kt`
  的 `send should prefer direct path after successful bootstrap` 已锁定：
  - direct accepted 时不发 duplicate mail fallback
  - success message 不伪造最终 session id
  - `receiptId` 会进入 latest durable evidence
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md` 中 2026-03-21 `Phase 2 Live Direct-Smoke Closure` 已记录
  `thread_083`：live Android formal flow 得到 `packet_ack` 后，PC 侧首封 ingress mail 带 direct-bridge marker，随后按
  canonical mail 路径到 `DONE`。
- 因此，如果未来出现 `DirectAccepted` 但长期拿不到预期 thread / status mail outcome，不应把它当成“偶发噪音”，而应
  直接阻止 primary-path switch 扩大。
- 当前这条 trigger 仍有一个 open tail：Android 还没有把同一 run 的后续 mail outcome 自动绑定回 latest direct
  evidence，所以最终判断仍需要 shared parity checklist 与 live/manual 证据一起读。

### R4 Fallback Path Unavailable

- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/domain/usecase/RunTaskMailDirectOrFallbackTest.kt`
  的 `execute should fall back to mail when direct send throws` 已锁定：当 direct send 失败且 mail fallback 也失败时，
  Android 会得到 `TaskMailDirectOutcome.MailFallbackFailed` 与真实错误信息。
- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/ui/newtask/TaskNewTaskViewModelTest.kt`
  的 `send failure should preserve draft and advanced state` 与
  `send failure should surface bot mailbox configuration error without clearing draft` 已锁定：
  - draft 与 advanced state 会保留
  - Android 不会把 fallback failure 伪装成成功
- 这意味着 Android 侧已经具备“direct 失败后 mail 也不可用时要保留 draft 并阻止 switch”的最小证据。

### R5 Summary Outcome Drift

- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md` 中当前已闭环的 live 样本显示：
  - accepted direct ingress -> `thread_083`
  - fallback-to-mail live reroute from relay rejection -> `thread_084`
  - bootstrap-unavailable fallback + persisted-evidence review -> `thread_093`
  - fallback-row same-run parity replacement sample -> `thread_094`
  - hard rejection -> `thread_085` 后不再产生新 thread
- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/ui/newtask/TaskNewTaskViewModelTest.kt`
  已锁定三类 Android user-visible send 语义：
  - direct accepted success message
  - mail fallback success message
  - hard rejection explicit error
- 当前 same-run summary parity 的读数已经不再缺 fallback 正向样本：
  - `thread_083` 已有 Android `Done + PHASE2_DIRECT_SMOKE_20260321_B` 与 PC-side `last_summary` 对齐
  - `thread_093` 已有 Android `Done + PHASE4_LIVE_REVIEW_20260322_A` 与 PC-side `last_summary` 对齐
  - `thread_094` 已有 Android `Done + PHASE4_FALLBACK_PARITY_20260322_B` 与 PC-side `last_summary` 对齐
- 但 Android 当前还没有把同一 run 的后续 workspace/detail summary 自动写回 shared Phase 4 工件，而且
  `thread_084` 当前只保留到更早的 `Unknown + prompt` Android artifact；这条现在应读作历史 retained-artifact gap，
  已被 `thread_094` 替代，而不是当前 blocker 或 confirmed mismatch。
- 因此 summary drift 仍然需要 Android / PC 双边证据一起判定；在 latest-evidence 与 canonical mail outcome 建立更稳定
  的自动绑定前，不应扩大 covered-flow switch。

## 备注

- `status` 暂使用：
  - `android_evidence_present`：Android 仓库已有 focused test、UI 或 live evidence，可直接支撑 trigger 判断。
  - `android_boundary_confirmed`：Android 侧已冻结触发边界，但最终 same-run parity 判定仍依赖 shared checklist
    与 PC-side mail outcome 证据。
- 当前 trigger note 的重点是把“什么时候只记账、什么时候回退、什么时候阻塞 switch”写成显式规则。
- 如果后续要把 `reply` 或 `/status` 纳入 trigger note，必须先有新的 contract freeze。
