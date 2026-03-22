# TaskMail Next Session Handoff - 2026-03-23 - Phase 5 Batch D Durable Evidence

## 当前决策

- `reply` / `/status` 的 shared planning-layer contract 仍只停留在 planning/shared 边界：
  - `E:\projects\mail_based_task_manager\docs\plans\post_creation_session_action_contract_v1.md`
- Android 侧当前已完成 `Batch A` / `Batch B` / `Batch C`，并在 2026-03-23 完成了 `Batch D` 的第一段 durable evidence。
- 当前第一批 scope 仍固定为：
  - `current-session plain reply`
  - `current-session /status`
- 当前读法仍是 guarded direct slice：
  - 不是 current protocol
  - 不是 direct-default
  - 不是 live rollout

## 2026-03-23 Live/Manual Closeout Update

2026-03-23 这轮 `Batch D` closeout 已经完成第一组 formal-host live/manual 收口，不再是“只差设备验证”的状态。

- 设备前置处理：旧 `net.thunderbird.android.debug` 因签名漂移必须卸载重装；relay 通过 `app://taskmail/debug/relay` 手动恢复 token 后，`Healthz` / `Connect` 都成功
- `thread_019 / Phase5 status closeout 20260323 A`：正式 `Tasks` detail 点击 `/status` 后，Android latest direct evidence 记录为 `actionType=Status`、`target=workspace_cb2404bf828c/thread_019`、`bootstrapStatus=hello_ack`、`outcome=MailFallbackSucceeded`、`switchGate=FallbackRequired`；PC mailbox 侧留下 user ingress `raw_005.json` 并收敛到 canonical `[STATUS][S:thread_019] ...` `raw_006.json`
- `thread_019` 返回重开 detail 后，`Latest direct result` 卡仍能恢复为 `Status query / Mail fallback succeeded / Fallback required / Hello ack`
- `thread_020 / Phase5 reply closeout 20260323 B`：发送 plain reply `PHASE5_REPLY_CLOSEOUT_20260323_B` 后，Android latest direct evidence 记录为 `actionType=Reply`、`target=workspace_cb2404bf828c/thread_020`、`bootstrapStatus=hello_ack`、`outcome=MailFallbackSucceeded`、`switchGate=FallbackRequired`；PC mailbox 侧先留下 `raw_005.json` ingress，随后因原线程 `FAILED` 触发 fresh recovery run `20260323_020701_9913`，最终收敛到 canonical `[DONE][S:thread_020] ...` `raw_008.json`
- `thread_020` detail 返回重开后 evidence 卡恢复；再按 formal-host 要求从桌面图标冷启动 Thunderbird 并经 `Tasks` 回到 detail，evidence 卡仍能恢复为 `Plain reply / Mail fallback succeeded / Fallback required / Hello ack`
- 相邻 PC 仓本轮已补 `mail_runner/taskmail_closeout.py` 对 Android `session_action` 记录的 target-based 选取与 `action_type` / `target_session_identity` 透传；`pytest tests/test_taskmail_closeout.py` 已通过

当前结论：

- 设备侧 durable evidence / persistence 已关
- shared-artifact / strong-bind 仍未关
- 当前 live bundle 只能做到 `last_summary` weak bind，因为 Android fallback `session_action` 记录还没有 `requestId` / `transportMessageId`，PC 当前 post-creation fallback canonical artifacts 也还没有 `action_type` / `target_session_identity` / action-specific ingress anchors

## Narrow Pitfall

- `thread_020` 在 plain reply 发送后曾瞬时显示 `Unable to load session`
- 触发条件：发送后立即停留在 detail 页面，尚未返回/重开
- 当前最佳理解：更像 post-send UI drift，而不是 latest direct evidence 持久性丢失，因为 detail 重开与 formal-host desktop-launch cold start 后 evidence 卡都能恢复
- 规避/验证：把它当成后续窄 UI slice 候选；不要让它重新打开 `Batch D` 的 persistence 结论

## Next Exact Step

- 不要在行为未变化的情况下重复跑 `thread_019` / `thread_020`
- 下一个真正的 closeout blocker 是 strong-bind：
  - Android fallback `TaskMailSessionActionSendRecord` 需要补 `requestId` 和/或 `transportMessageId`
  - PC post-creation fallback closeout 需要在 canonical artifacts 中保留 `action_type`、`target_session_identity`、action-specific `ingress_message_id`、`terminal_mail_subject`
- 只有把 shared-artifact strong-bind 补齐后，才需要回到 live bundle 再跑同一对窄样本，确认 `last_summary` weak bind 能升级
- `thread_020` 的 `Unable to load session` 应放在 strong-bind 之后再判断是否需要单独开 UI 修复 slice

## 2026-03-23 VPS Retest Pitfall

- 在 VPS 升级后重新补测 `thread_021` current-session `/status`，Android latest direct evidence 不再是旧的 `FallbackRequired`，而是：
  - `outcome = DirectRejected`
  - `switchGate = SwitchBlocker`
  - `errorMessage = could not resolve a session for the requested workspace/session locator`
- 这说明新的 post-creation direct server path 已经生效，但 server-side current-session target resolution 仍未闭环
- 同时，本地 PC runtime 明确存在对应 session index：
  - `E:\projects\mail_based_task_manager\_tmp_live_mail_runner\tasks\_scheduler\workspaces\workspace_cb2404bf828c\sessions\thread_021.json`
- 当前最佳理解：relay server 的 `MAIL_RUNNER_TASK_ROOT` 很可能指到了 runtime root 或其他错误目录，而不是包含 `thread_*` 与 `_scheduler` 的真正 task root
  - 本地正确参考值是：`E:\projects\mail_based_task_manager\_tmp_live_mail_runner\tasks`
  - 不应指到：`E:\projects\mail_based_task_manager\_tmp_live_mail_runner`
- 在这个 pitfall 修正前，不要继续消耗 `thread_022` 做 plain reply closeout；`/status` 与 plain reply 两条 bridge 在 `mail_runner/relay_server/post_creation_actions.py` 里共用 `_resolve_current_session_thread_state(...)`，大概率会撞同一类 session-locator 失败

## Read First

- `docs/taskmail/planning/android/taskmail-phase5-reply-status-android-implementation-plan-v0.1.md`
- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/TASKMAIL-MAIL-RULES.md`
- `E:\projects\mail_based_task_manager\docs\plans\post_creation_session_action_contract_v1.md`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/model/TaskMailSessionActionSendRecord.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/data/cache/FileBackedTaskMailSessionActionSendRecordRepository.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/TaskSessionDetailViewModel.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/TaskSessionDetailDirectEvidenceCard.kt`

## 本次代码与验证

- Android 生产代码改动：有
- Android 测试代码改动：有
- planning / status / ledger 文档改动：有
- 新的 Gradle 验证：有
- 新的设备验证：无

## 本轮收口了什么

- `TaskMailSessionActionSendRecord`、`TaskMailSessionActionSendRecordRepository` 与 file-backed JSON codec / repository 已落地。
- latest session-action record 当前按 canonical `workspace_id + session_id` 选取；`thread_id` 仅作 supporting identity。
- `TaskSessionDetailViewModel` 现已在 guarded plain reply 与 guarded `/status` 尝试后保存 latest direct evidence，并在 detail 载入时重建该记录。
- `TaskSessionDetailContent` 现已新增 latest direct evidence review surface，可回看：
  - `outcome`
  - `switchGate`
  - `bootstrapStatus`
  - optional `requestId` / `receiptId` / `transportMessageId`
  - optional `fallbackReason` / `errorMessage`
- 本轮 focused 验证已通过：
  - `.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest --tests "net.thunderbird.feature.taskmail.internal.data.cache.FileBackedTaskMailSessionActionSendRecordRepositoryTest" --tests "net.thunderbird.feature.taskmail.internal.ui.detail.TaskSessionDetailViewModelTest" --tests "net.thunderbird.feature.taskmail.internal.ui.detail.TaskSessionDetailScreenKtTest"`
  - `.\gradlew.bat :feature:taskmail:internal:detekt :feature:taskmail:internal:lintDebug`

## 仍未收口

- 真实设备上的 screen reload / recent-tasks cold start 之后，latest direct evidence card 是否仍稳定可 review。
- accepted direct `reply` 是否稳定收敛到 canonical mail reply outcome。
- accepted direct `/status` 是否稳定生成 canonical `[STATUS]` mail。
- latest session-action send record 如何在 live closeout 中对齐：
  - `action_type`
  - `target_session_identity`
  - `request_id`
  - `ingress_message_id`
  - `terminal_mail_subject`
  - `last_summary`
  - same-run bind readout

## 环境阻塞

- 更宽的 `:feature:taskmail:internal:testDebugUnitTest` 仍会被 `TaskMailValidationRunner` 对相邻 PC 仓
  `scripts/test_fetch_latest_100.json` 的本地依赖阻塞。
- 这不是本轮 Phase 5 guarded slice 的语义回归，不应为此改动相邻 PC 仓。

## 下一步最合理顺序

1. 先做一轮很窄的 live/manual closeout，只跑两类样本：
   - `current-session plain reply`
   - `current-session /status`
2. 对每条样本收集并回写 shared closeout anchors：
   - `action_type`
   - `target_session_identity`
   - `request_id`
   - `ingress_message_id`
   - `terminal_mail_subject`
   - `last_summary`
   - same-run bind readout
3. 设备上补看 latest direct evidence card：
   - 实际 screen reload 后是否还在
   - recent-tasks cold start 后是否还能重建
4. 然后再回写 authority / ledger / implementation plan。

## 当前明确不做

- 不把 quick answer / `Answers:` / `/resume` / attachment continuation 并进第一批
- 不把 targeted-session variant 或 cross-workspace switching 拉进 v1
- 不把 ack 当成最终 status / reply result
- 不把 shared planning-layer contract 误写成 current protocol
