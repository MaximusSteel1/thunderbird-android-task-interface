# TaskMail Next Session Handoff - 2026-03-23 - Thread 105 Reply Status Smoke Resume

## 当前结论

- 这轮没有新增 Android 生产代码，也没有新增 Gradle 验证；本次只推进了 Phase 5 formal-host live/manual smoke 的运行态收口准备。
- 旧样本 `thread_019` 不适合继续直接复用，因此已改为在当前安装包上新建 fresh formal-host 样本。
- 当前可继续复用的 fresh 样本已经固定为 `thread_105 / Phase 5 fresh reply-status closeout 20260323 E`。
- 两小时后继续时，不要再新建种子线程，也不要重跑 `thread_019` / `thread_020`；直接在 `thread_105` 上做 `/status` + plain reply` 的窄烟测即可。

## Read First

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/TASKMAIL-DEBUG-VALIDATION.md`
- `docs/TASKMAIL-MAIL-RULES.md`
- `docs/taskmail/archive/android/handoff/taskmail-next-session-handoff-2026-03-23-session-action-fallback-requestid.md`
- `docs/taskmail/archive/android/handoff/taskmail-next-session-handoff-2026-03-23-vps-retest.md`

## 本次已确认的事实

- Android 当前安装包上的 relay / bot mailbox 配置已经恢复并落盘。
- Android formal-host `New task` 新发的 fresh 样本标题为 `Phase 5 fresh reply-status closeout 20260323 E`，任务正文种子为 `PHASE5_SEED_20260323_E`。
- 该次 Android `new_task` retained evidence 已确认是：
  - `bootstrapStatus = hello_ack`
  - `outcome = DirectAccepted`
  - `switchGate = KeepDirectDefault`
  - `requestId = req_93ba61b3c33b4d30bd9cace577947928`
  - `transportMessageId = <177422805814.922549.10522435308081752274@mail-runner.local>`
- 最初本地 PC 没有观测到任何新 ingress / raw mail / thread，不是因为 Android 发送失败，而是因为本地 `mail_runner.host` 已经挂住。
- 通过托管脚本重启后，本地 PC 第一轮轮询立刻补拉遗漏 ingress，并生成：
  - `thread_105`
  - `workspace_id = workspace_cb2404bf828c`
  - `session_name = Phase 5 fresh reply-status closeout 20260323 E`
  - `last_summary = PHASE5_SEED_20260323_E`
- 本地 PC 现在已经落齐 `thread_105` 的 `raw_001` ingress、`[ACCEPTED]` / `[RUNNING]` / `[DONE]`、`canonical_summary.json`、`processed_messages.json` 命中和 scheduler session 索引。
- Android 侧随后也已经能在正式 `Tasks` 列表里看到 `thread_105`。

## 关键 pitfall

- 症状：
  - Android retained evidence 显示 `DirectAccepted`，但本地 PC 查不到任何新 ingress / raw mail / thread。
- 触发条件：
  - 本地 `mail_runner.host` 进程仍存活，但 `loop.stderr.log` 长时间不再推进，`host_state.json` 仍显示 `running`。
- 当前确认：
  - 这次本地 host 在 `2026-03-23 08:06` 后就不再正常轮询；Android fresh send 发生在 `2026-03-23 09:07:38`，因此这台 host 当时不是可信观测点。
- 规避方法：
  - 如果后续再出现“Android accepted 但本地 PC 无 ingress”的读数，先检查 `E:\projects\mail_based_task_manager\_tmp_live_mail_runner\loop.stderr.log` 是否仍在推进。
  - 若日志停滞，先用托管脚本安全重启，再继续判读，不要先让 Android 重发样本。

## 本次实际执行的恢复动作

- 使用仓库托管脚本，而不是手工杀进程：
  - `E:\projects\mail_based_task_manager\scripts\manage_mail_runner.ps1 restart -ConfigPath E:\projects\mail_based_task_manager\_tmp_live_mail_runner\mail_config.loop_30s.yaml -RuntimeDir E:\projects\mail_based_task_manager\_tmp_live_mail_runner -NoPopup`
- 重启后的 host 状态：
  - 新 pid 为 `66268`
  - `2026-03-23 09:18:42` 首轮轮询日志为 `fetched=3 processed=3`
- 这一轮补拉直接生成 `thread_105`，说明先前遗漏的是 host 轮询停滞，不是 Android direct send 失败。

## 两小时后继续时的最短路径

1. 先确认本地 `mail_runner.host` 仍在正常轮询。
   - 看 `E:\projects\mail_based_task_manager\_tmp_live_mail_runner\loop.stderr.log` 是否仍有新时间戳。
2. 从桌面图标冷启动 Thunderbird，进入正式 `Tasks`，打开 `thread_105`。
3. 先在 `thread_105` 上点击一次 `/status`。
4. 然后回到同一条 `thread_105`，发送一条 plain reply。
5. plain reply 建议继续使用新的唯一 token，避免和旧样本串线。
6. 每做完一步，都立刻回收 Android `taskmail_session_action_send_records.json`，并在本地 PC 用 `thread_105` 生成 closeout bundle。

## 继续烟测时要看什么

- Android latest session-action evidence 是否写入 `thread_105`：
  - `/status` 应落 `actionType = Status`
  - plain reply 应落 `actionType = Reply`
- fallback / rejected 时是否继续保留：
  - `requestId`
  - 可用时的 `receiptId`
  - 可用时的 `transportMessageId`
- PC closeout bundle 是否能围绕 `thread_105` 稳定解释：
  - Android retained evidence
  - same-run bind
  - canonical mail outcome
- 若再次出现“accepted 但 PC 无 ingress”，优先先判定 host 是否再次停滞，不要先怀疑 Android。

## 当前未完成项

- 尚未在 `thread_105` 上实际执行 `/status`
- 尚未在 `thread_105` 上实际执行 plain reply
- 尚未生成 `thread_105` 的 session-action closeout bundle
- 尚未把这轮运行态 pitfall 回写到 authority / ledger；当前仅在本 handoff 中保留

## 本次变更

- Android 代码：无
- Android 测试：无
- 仓库文档：新增本 handoff
- 设备验证：有，但只到 `thread_105` 可见与续跑前准备完成
