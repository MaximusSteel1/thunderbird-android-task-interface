# TaskMail Phase 4 双栈 Parity Checklist（Android 侧首轮 matrix readout）

更新时间：2026-03-22

## 状态

- 本文是 Android 侧对 `phase4_dual_stack_parity_checklist.md` 的首轮 matrix readout。
- 当前仅覆盖 `new_task`。
- 当前已回填 Android 正式 flow 上可直接证明的 send-side evidence、user-visible outcome、per-sender-account
  durable evidence，以及 2026-03-21 live smoke 的三场景 closeout。
- 这仍不等同于 Android / PC 双边 parity 已全部验收；它只是把 Android 侧现有证据正式映射进 shared
  checklist。
- 本文不授权把 `reply`、`/status` 或其他 flow 提前纳入 direct-default。

## 当前冻结口径

本 checklist 当前只按以下 authority 字段与结果语义对账：

- `packet_ack.accepted`
- `receipt_id`
- 可选 `transport_message_id`
  - 仅作为观察字段
  - Android 不得把它当作 v1 UI identity 依赖
- fallback-classified rejection
- hard rejection
- 是否产生预期的 thread / mail outcome
- 是否保持与当前 mail baseline 一致的 user-visible summary / terminal outcome

对 Android 侧来说，这些 shared evidence 还应结合当前 repository 内的：

- `TaskMailDirectSendEvidence`
- `TaskMailNewTaskSendRecordRepository`
- `TaskNewTaskViewModel.lastDirectSendEvidence`

一起阅读，但不把这些 Android 内部字段扩写成新的跨仓 authority。

## 检查表

| parity_item_id | covered_flow | scenario | expected_direct_evidence | expected_thread_mail_outcome | expected_user_visible_outcome | current_status | notes |
| --- | --- | --- | --- | --- | --- | --- | --- |
| new_task.direct_accepted | `new_task` | direct accepted | `packet_ack.accepted = true`，存在稳定 `receipt_id`，可选 `transport_message_id` 仅作观察 | 不发送 duplicate mail fallback；后续按当前 canonical mail contract 产生预期 thread / session / status mail | Android 仅显示 direct 发送成功，不提前伪造最终 session id；后续 summary 与 mail outcome 对齐 | `android_evidence_present` | 见 A1 |
| new_task.fallback_to_mail | `new_task` | fallback-classified direct failure | bootstrap 失败、transport 失败、`unsupported_action`、fallback-classified `packet_ack`，或其他 fallback-classified direct 结果 | 当前 mail `new task` 路径成功创建预期 thread / mail outcome | 用户看到的是 fallback 后的成功语义，而不是虚假的 direct accepted | `android_evidence_present` | 见 A2 |
| new_task.hard_rejection_stop | `new_task` | hard rejection local stop | `invalid_payload` / `validation_failed` / `unauthorized` 或等价 hard rejection 分类 | 不产生新的 fallback mail；不产生非预期 thread | draft 保留，Android 明确显示 direct-send error | `android_evidence_present` | 见 A3 |

## 首轮 Android 矩阵读数

| matrix_item | android_side_readout | notes |
| --- | --- | --- |
| `new_task.direct_accepted` | `pass` | focused automated coverage 已锁定 no-duplicate-mail、success message、`receiptId` durable persistence / rehydration；2026-03-21 live smoke `thread_083` 已证明 direct-bridge ingress 与 canonical mail outcome，2026-03-22 fresh direct sample `thread_095` 又补上了 relay re-provision 之后的同日 same-run direct parity |
| `new_task.fallback_to_mail` | `pass` | focused automated coverage 已锁定 bootstrap unavailable、capability rejection、fallback message 与 mail send；2026-03-21 live smoke `thread_084` 已证明 fallback-to-mail terminal outcome，2026-03-22 live sample `thread_093` 补上了 persisted-evidence review 与 formal-host recent-tasks cold-start 回看，稍后同日 `thread_094` 又补上了 retained Android workspace summary 与 PC canonical outcome 的同一 run 对齐 |
| `new_task.hard_rejection_stop` | `pass` | focused automated coverage 已锁定 hard rejection、draft retention、no implicit mail fallback；2026-03-21 live hard reject smoke B 已证明 `thread_085` 之后不再创建新的 adjacent-runtime thread |

## 同一 Run Summary Parity Readout

| sample_thread | scenario | android_live_readout | pc_canonical_mail_outcome | reconciliation_status | notes |
| --- | --- | --- | --- | --- | --- |
| `thread_083` | direct accepted | `_tmp_device/taskmail_phase2_negative_after_tap.xml` 保留了 `Phase2 direct smoke B / Done / Codex / PHASE2_DIRECT_SMOKE_20260321_B` | `thread_083/thread_state.json.last_summary = PHASE2_DIRECT_SMOKE_20260321_B`，`mail/raw_004.json` 为 `[DONE][S:thread_083]` 且 `Reply:` 同 token | `pass` | `mail/raw_001.json` 首封 ingress 同时带 `X-TaskMail-Direct: 1`，说明这行既闭环了 direct ingress，也闭环了同一 run summary parity |
| `thread_095` | direct accepted after relay re-provision | 设备最新 send record 落成 `bootstrapStatus = hello_ack` / `DirectAccepted` / `KeepDirectDefault`，并保留 `receiptId = relay-receipt:android-taskmail:new-task:req_f8f52bd45be6445185c0553b4b248fb0:03bed494` 与 `transportMessageId = <177416864698.472093.13590235740865598638@mail-runner.local>`；formal-host 用户可见发送提示手工确认为 `[Relay]` | `runs/20260322_163746_d160/canonical_summary.json` 记录 `ingress_type = direct_bridge`、`request_id = req_f8f52bd45be6445185c0553b4b248fb0`、`ingress_message_id = <177416864698.472093.13590235740865598638@mail-runner.local>`、`last_summary = PHASE4_DIRECT_ACCEPT_20260322_C`，`mail/raw_004.json` 为 `[DONE][S:thread_095]` 且 `Reply:` 同 token | `pass` | 这行是 relay 重新配回正式参数之后的 fresh direct-accepted 样本，也是在 Android shared-artifact 手动消费里第一次优先使用 PC `canonical_summary.json` 而不是只靠 `raw_001` / `raw_004` 人工拼接 |
| `thread_097` | direct accepted post-freeze closeout rerun | 设备 fresh latest send record 落成 `bootstrapStatus = hello_ack` / `DirectAccepted` / `KeepDirectDefault`，并保留 `receiptId = relay-receipt:android-taskmail:new-task:req_c8c28c6db7394e3bbde73935461b38c9:232ada8a` 与 `transportMessageId = <177417609933.472093.14011859041567057941@mail-runner.local>`；同次拉取的 `taskmail_session_details.json` 闭环 `Phase 5 fresh closeout 20260322 E / Done / Codex / PHASE5_FRESH_CLOSEOUT_20260322_E`，但该 fresh live record 仍未带出 `requestId` | `runs/20260322_184156_afc8/canonical_summary.json` 记录 `ingress_type = direct_bridge`、`request_id = req_c8c28c6db7394e3bbde73935461b38c9`、`ingress_message_id = <177417609933.472093.14011859041567057941@mail-runner.local>`、`last_summary = PHASE5_FRESH_CLOSEOUT_20260322_E`、`terminal_mail_subject = [DONE][S:thread_097] Phase 5 fresh closeout 20260322 E`；同 run `taskmail_daily_closeout_bundle.json` 按 `terminal_mail_message_id` 定位 terminal mail | `pass` | 这行关闭了 freeze 之后的第一条 fresh `daily_closeout_bundle` 复用，并把 same-run bind 提升到 `transport_message_id` 强绑定；但 helper note `android_request_id_missing` 也说明它还不能单独关闭 `request_id` 首键的 switch-review gate |
| `thread_098` | direct accepted request-id-first closeout rerun | 安装当前 formal-host build 后，设备 fresh latest send record 落成 `bootstrapStatus = hello_ack` / `DirectAccepted` / `KeepDirectDefault`，并保留 `requestId = req_2a1e0790bdd54194bdce17e96c378e5e`、`receiptId = relay-receipt:android-taskmail:new-task:req_2a1e0790bdd54194bdce17e96c378e5e:ddf5624a`、`transportMessageId = <177417776437.472093.5957744132826889915@mail-runner.local>` | `runs/20260322_190954_7e1f/canonical_summary.json` 记录 `ingress_type = direct_bridge`、`request_id = req_2a1e0790bdd54194bdce17e96c378e5e`、`ingress_message_id = <177417776437.472093.5957744132826889915@mail-runner.local>`、`last_summary = PHASE5_FRESH_CLOSEOUT_20260322_F`、`terminal_mail_subject = [DONE][S:thread_098] Phase 5 fresh closeout 20260322 F`；同 run `taskmail_daily_closeout_bundle.json` 记录 `same_run_bind.effective_bind_level = request_id`、`matched_fields = request_id + transport_message_id + last_summary`、notes 为空 | `pass` | 这行关闭了 `request_id` 首键 bind 的 fresh formal-host gate；它不等于立即授权切换 direct-default，但它使 `new_task` 进入 guarded direct-default review 成为合理下一步 |
| `thread_093` | bootstrap-unavailable fallback | `_tmp_device/current_taskmail_workspace2.xml` 保留了 `Phase 4 live review 20260322 A / Done / Codex / PHASE4_LIVE_REVIEW_20260322_A`；`_tmp_device/taskmail_reopen_scrolled.xml` 还保留了 `Mail fallback succeeded / Fallback required / Not configured` | `thread_093/thread_state.json.last_summary = PHASE4_LIVE_REVIEW_20260322_A`，`mail/raw_004.json` 为 `[DONE][S:thread_093]` 且 `Reply:` 同 token | `pass` | 这行同时闭环了 mail-fallback user-visible 语义、persisted latest-evidence review，以及 same-run workspace summary parity |
| `thread_094` | fallback_to_mail replacement sample | `_tmp_device/phase4_fallback_after_refresh.xml` 保留了 `Phase 4 fallback parity 20260322 B / Done / Codex / PHASE4_FALLBACK_PARITY_20260322_B`；设备最新 send record 仍是 `bootstrapStatus = not_configured` / `MailFallbackSucceeded` / `FallbackRequired` | `thread_094/thread_state.json.last_summary = PHASE4_FALLBACK_PARITY_20260322_B`，`mail/raw_004.json` 为 `[DONE][S:thread_094]` 且 `Reply:` 同 token | `pass` | 这行补齐了 fallback-row retained Android terminal-summary sample，也把 `thread_084` 降成历史 retained-artifact 缺口，而不是当前 blocker |
| `thread_084` | fallback_to_mail historical artifact gap | 当前仅保留到 `_tmp_device/taskmail_phase2_hardreject_prep.xml`：可见 `Phase2 fallback smoke A / Unknown / Codex /` 原始 prompt text；Android send-side fallback success 语义已有 test + live evidence，但没有 retained Android terminal-summary sample | `thread_084/thread_state.json.last_summary = PHASE2_FALLBACK_SMOKE_20260321`，`mail/raw_004.json` 为 `[DONE][S:thread_084]` 且 `Reply:` 同 token | `historical_gap` | 这行现在应读作历史 retained-artifact 缺口；2026-03-22 的 `thread_094` 已提供同等级 fallback-row same-run parity replacement sample，因此它不再构成当前 blocker |

注：上表中的 `mail/raw_004.json` 是这些已留存 sample 当次实际落盘的 artifact 路径，不是 cross-repo parity 的稳定文件编号合同。

## 稳定消费顺序

- 每次对账先冻结一个 `sample_thread` 与同一 sender-account scope，不混读跨 run 证据。
- 先读 Android send-side evidence：`TaskMailDirectSendEvidence`、latest send record、latest-evidence card，用来确认 `outcome`、`switchGate`、`receiptId`、`fallbackReason` 等发送语义。
- 再读 Android terminal summary：优先保留 formal-host workspace dump 或同等级截图，至少确认 `title`、`status`、`backend`、`last_summary token`。
- 再读 PC canonical outcome：若同 run 已存在 `runs/<task_id>/canonical_summary.json`，优先先读它的 `ingress_type`、`request_id`、`ingress_message_id`、`last_summary`、`terminal_mail_message_id` / `terminal_mail_subject`；没有该工件时，再至少核对 `thread_state.json.last_summary` 与按 terminal status mail 类型定位到的 `mail/raw_*.json`；如果是 direct accepted 行，再补看 ingress `mail/raw_*.json` 是否带 `X-TaskMail-Direct: 1`。
- 日常 `daily_closeout_bundle` 至少要同时冻结：
  - `sample_thread` 或 `runs/<task_id>`
  - sender-account scope
  - Android latest send evidence
  - Android terminal-summary artifact
  - PC canonical outcome artifact
- 对 `direct accepted` 行，same-run bind 顺序固定为：
  - 先 `request_id`
  - 再 `transportMessageId <-> ingress_message_id`
  - 最后才把 `last_summary token` 当作较弱回退
- 若同 run 已有 `canonical_summary.json`，`mail/raw_001.json` 只再用来补看 `X-TaskMail-Direct: 1` 与 ingress headers，不再作为 primary join source。
- `fallback_to_mail` 行若已有 `canonical_summary.json` 也优先消费它；只有缺该工件时，才回退到 `thread_state.json` 加上按 terminal status mail 类型定位到的 `mail/raw_*.json`，而不是写死 `raw_004.json`。
- 三边读数一致时记 `pass`；PC canonical outcome 已闭环但 Android terminal-summary 样本未留存时记 `historical_gap`；只有同一 run 的 machine-readable evidence 明确互相冲突时，才升级为 mismatch candidate。
- 若 `direct accepted` 行只有 `last_summary token` 弱对齐、但 `request_id` / `message_id` bind 未闭环，则该样本只留在 checklist 备注区作为 open binding gap，不直接升 mismatch，也不能当作 `direct-default` switch review 的正向输入。
- 若 helper 已给出 strong bind，但 note 含 `android_request_id_missing`，则该 sample 可作为 `daily_closeout_bundle` 工作流已能复用的证据；但它仍不能单独关闭 `request_id` 首键的 switch-review gate，下一条 fresh sample 仍需让 Android latest send evidence 自身带出 `requestId`。
- 若 helper 给出 `same_run_bind.effective_bind_level = request_id` 且 notes 为空，则该 sample 可进入 `new_task` 的 guarded direct-default review 输入；它仍不自动等于“现在立刻切换 direct-default”。
- checklist 的职责是先把样本读数写清楚；在 checklist 还没写明 sample、evidence、status 之前，不直接跳到 rollback 或 switch 结论。

## Android 侧首轮证据摘录

### A1 Direct Accepted

- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/domain/usecase/RunTaskMailDirectOrFallbackTest.kt`
  的 `execute should return direct accepted after successful bootstrap` 已锁定 Android 在 `hello_ack` 后会把结果归一成
  `TaskMailDirectOutcome.DirectAccepted` 和 `TaskMailDirectSwitchGate.KeepDirectDefault`。
- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/ui/newtask/TaskNewTaskViewModelTest.kt`
  的 `send should prefer direct path after successful bootstrap` 已锁定：
  - 不发送 duplicate mail fallback
  - 用户看到 `Task request sent. It will appear after the first TaskMail status mail arrives.`
  - `lastDirectSendEvidence` 与 latest send record 都会保留稳定 `receiptId`
- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/data/cache/FileBackedTaskMailNewTaskSendRecordRepositoryTest.kt`
  已锁定 latest record 会按 `senderAccountId` 落盘并重新装载。
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/newtask/TaskNewTaskDirectEvidenceCard.kt`
  与
  `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/ui/newtask/TaskNewTaskScreenKtTest.kt`
  现在补上了 formal `New task` 页面里的 latest-evidence review surface，已锁定：
  - `Direct accepted` 时可 review `receiptId` / `transportMessageId`
  - `Direct rejected` 时可 review fallback reason / surfaced error
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md` 中 2026-03-21 `Phase 2 Live Direct-Smoke Closure` 已记录 live
  `thread_083`：
  - 设备 logcat 记录 `Sending relay packet ...` 与 `Received relay packet ack ...`
  - 相邻 PC runtime 首封 ingress mail 带 `X-TaskMail-Direct: 1`
  - 最终 canonical mail outcome 仍按当前 `[ACCEPTED] / [RUNNING] / [DONE]` 路径闭环
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md` 中稍后的 2026-03-22 relay re-provision / direct closeout 又补上了
  `thread_095`：
  - debug relay screen 先闭环 `status=ok` 与 `connection=connected`
  - formal-host fresh `new task` 用户可见提示手工确认为 `[Relay]`
  - Android latest send record 直接落成 `hello_ack` / `DirectAccepted` / `KeepDirectDefault`
  - PC `canonical_summary.json` 已把 `ingress_type = direct_bridge`、`request_id`、`ingress_message_id`、终态 mail subject 收敛成同 run 摘要
- 因此，当前缺的已经不再是“仓库里没有 review surface”或“重进页面后看不到 persisted evidence”；这些边界已在
  2026-03-22 的 formal-host live sample 里补上。direct accepted 这行现在不仅有 `thread_083` 的 earlier positive
  sample，也有 `thread_095` 这条 relay re-provision 后的 fresh same-day 样本。当前真正还没收口的是 shared
  artifacts 的稳定 operational consumption，以及 Android 尚未把同一 run 的后续 canonical mail outcome 自动绑定回
  latest direct evidence。

### A2 Fallback To Mail

- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/domain/usecase/RunTaskMailDirectOrFallbackTest.kt`
  已锁定：
  - bootstrap 不可用时会走 `MailFallbackSucceeded`
  - direct send 抛错且 mail 失败时会得到 `MailFallbackFailed`
- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/data/relay/RelayTaskMailDirectNewTaskSenderTest.kt`
  已锁定：
  - capability rejection `unsupported_action` 会归到 `FallbackToMail`
  - `packet_ack.accepted = false` 且非 hard-rejection 的能力类拒绝仍保留在 mail fallback 路径
- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/ui/newtask/TaskNewTaskViewModelTest.kt`
  的以下用例已锁定 Android user-visible 语义：
  - `send should use mail fallback message when direct bootstrap is unavailable`
  - `send should fallback to mail when direct send is temporarily unavailable`
  两者都要求用户看到 `Task request sent over mail fallback. It will appear after the first TaskMail status mail arrives.`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md` 中 2026-03-21 `Phase 2 Live Negative-Path Closure` 已记录
  `thread_084`，证明 fallback-classified direct failure 会在 live 设备上重新走 mail `new task` 并完成预期终态。
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md` 中 2026-03-22 `Phase 4 New Task Live Persisted-Evidence / Mail-Fallback Closeout`
  又补上一条 formal-host live sample：
  - 用户可见结果是 `[Mail fallback]`
  - app-private send record 落成 `bootstrapStatus = not_configured` /
    `TaskMailDirectOutcome.MailFallbackSucceeded` /
    `TaskMailDirectSwitchGate.FallbackRequired`
  - 同一条 evidence 在 screen reload 与 recent-tasks cold start 后仍可在 formal `New task` 页面 review
  - mailbox-side canonical outcome 继续按当前 mail baseline 在 `thread_093` 收口到 `[DONE]`
- 当前 fallback 行的 same-run summary parity 读数应拆开看：
  - `thread_093` 已闭环：Android workspace summary 与 PC `last_summary` 同为 `PHASE4_LIVE_REVIEW_20260322_A`
  - `thread_094` 也已闭环：Android workspace dump `_tmp_device/phase4_fallback_after_refresh.xml` 显示
    `Phase 4 fallback parity 20260322 B / Done / Codex / PHASE4_FALLBACK_PARITY_20260322_B`，PC
    `thread_094/thread_state.json.last_summary` 与 `mail/raw_004.json` 也都闭环到同一 token
  - `thread_084` 现在应读作历史 retained-artifact 缺口：它仍然能证明 earlier fallback live reroute，但已经被
    `thread_094` 替代，不再构成当前 same-run parity blocker，也不足以升级成 confirmed mismatch
- Android 当前已能把这类场景落成 `TaskMailDirectOutcome.MailFallbackSucceeded` /
  `TaskMailDirectSwitchGate.FallbackRequired`，因此 shared checklist 不再需要用“direct failed somehow”这类模糊表述。

### A3 Hard Rejection Stop

- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/data/relay/protocol/RelayProtocolJsonCodecTest.kt`
  已锁定 `packet_ack` 可解出可选 `error_code`。
- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/data/relay/RelayTaskMailDirectNewTaskSenderTest.kt`
  已锁定：
  - `invalid_payload` 会作为 hard rejection 返回
  - `packet_ack.error_code = invalid_payload` 会作为 hard rejection 返回
  - `validation_failed: ...` 这种 hard-code 前缀也会作为 hard rejection 返回
- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/ui/newtask/TaskNewTaskViewModelTest.kt`
  的 `direct hard rejection should keep draft and skip mail fallback` 已锁定：
  - Android 保留 draft
  - `sendError` 明确显示 direct-send error
  - 不发送 mail fallback
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md` 中 2026-03-21 `Phase 2 Live Negative-Path Closure` 已记录
  live hard reject smoke B：
  - 设备 logcat 显示 `Relay packet ack rejected ... code=invalid_payload`
  - `thread_085` 之后不再出现新的 adjacent-runtime thread
  - 设备停留在 `New task`，保留草稿并显示 `TaskMail send failed`
- 因此，当前 Android 侧已可把 hard rejection 明确读成 `no implicit mail fallback` 的 `switch_blocker` 类边界，而不是
  “失败后也许还能偷偷走 mail”。

## 备注

- `current_status` 暂使用：
  - `android_evidence_present`：Android 仓库已有 focused test、UI 断言或 live smoke 证据，可进入 cross-repo parity 对账。
- 如果后续要把新的 flow 纳入 parity checklist，先更新 Phase 4 freeze note 或新增 contract freeze。
- 这份 checklist 的用途是对账，不是重写 protocol authority。
