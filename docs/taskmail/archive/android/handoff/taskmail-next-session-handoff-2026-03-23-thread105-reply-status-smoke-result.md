# TaskMail Next Session Handoff - 2026-03-23 - Thread 105 Reply/Status Smoke Result

## 当前结论

- `thread_105` 的 formal-host `/status` 与 plain reply 已完成一轮失败样本和一轮修复后复测。
- 初始失败的共同根因已确认：VPS relay 可见的 `MAIL_RUNNER_TASK_ROOT` 是旧快照，缺少 `workspace_cb2404bf828c / thread_105` 的 session/thread 状态文件，导致 server-side current-session locator 在 direct path 上直接拒绝。
- 修复方式不是改 Android，也不是改本地 PC mail runner，而是把本地 live task store 同步到 VPS relay 可见的 task root。
- 同步后复测结果通过：
  - `/status`：`DirectAccepted`，PC 收到 direct ingress 与 `[STATUS]` terminal mail。
  - plain reply：`DirectAccepted`，PC 收到 direct ingress、`[ACCEPTED]`、`[RUNNING]`、`[DONE]`，线程重新跑完并以 reply 文本收口。

## Read First

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/TASKMAIL-DEBUG-VALIDATION.md`
- `docs/TASKMAIL-MAIL-RULES.md`
- `docs/taskmail/archive/android/handoff/taskmail-next-session-handoff-2026-03-23-thread105-reply-status-smoke-resume.md`
- `docs/taskmail/archive/android/handoff/taskmail-next-session-handoff-2026-03-23-vps-retest.md`
- PC-side 参考仓库：`E:\projects\mail_based_task_manager`

## 初始失败样本

### 1. `/status`

- Android retained evidence:
  - `actionType = Status`
  - `target = workspace_cb2404bf828c / thread_105`
  - `bootstrapStatus = hello_ack`
  - `outcome = DirectRejected`
  - `switchGate = SwitchBlocker`
  - `requestId = req_ce219cb7f88e405d8a2173ba78b5d79a`
  - `errorMessage = could not resolve a session for the requested workspace/session locator`
- Android UI dump 与详情页一致显示：
  - `Latest direct result -> Status query -> Direct rejected`
  - 相同 `requestId`
  - 相同 locator 错误
- PC 侧没有任何新 ingress / raw mail / `session_action_closeout.json`

### 2. plain reply

- 用户输入正文：
  - `THREAD105_PLAIN_REPLY_20260323_F`
- Android retained evidence:
  - `actionType = Reply`
  - `target = workspace_cb2404bf828c / thread_105`
  - `bootstrapStatus = hello_ack`
  - `outcome = DirectRejected`
  - `switchGate = SwitchBlocker`
  - `requestId = req_928aa0255a624435a40bb270aaa8c9ae`
  - `errorMessage = could not resolve a session for the requested workspace/session locator`
- Android UI 截图与 dump 一致显示：
  - inline banner `TaskMail reply failed`
  - 相同 locator 错误
- PC 侧同样没有任何新 ingress / raw mail / `session_action_closeout.json`

## 根因与修复

### 确认根因

- 远端 `/etc/mail-runner-relay.env` 已经正确配置：
  - `MAIL_RUNNER_TASK_ROOT=/opt/mail_runner_relay/shared/task_root`
- 但该远端 `task_root` 是旧快照：
  - 存在 `workspace_cb2404bf828c`
  - `sessions/` 仅同步到较早线程
  - 缺少 `thread_105.json`
  - 缺少 `thread_105/thread_state.json`
- 因此 relay 的 post-creation resolver 无法将 `workspace_cb2404bf828c / thread_105` 解析成当前 session target。

### 已执行修复

- 在 `E:\projects\mail_based_task_manager` 执行：

```powershell
.\.venv\Scripts\python.exe .\scripts\sync_relay_task_root.py `
  --host 124.223.41.153 `
  --user ubuntu `
  --key-path .\work_bot.pem `
  --local-task-root E:\projects\mail_based_task_manager\_tmp_live_mail_runner\tasks `
  --remote-task-root /opt/mail_runner_relay/shared/task_root
```

- 本次 one-shot sync 成功：
  - `sync_id = 20260323_124459`
  - `file_count = 7233`
- 同步后远端已可见：
  - `/opt/mail_runner_relay/shared/task_root/_scheduler/workspaces/workspace_cb2404bf828c/sessions/thread_105.json`
  - `/opt/mail_runner_relay/shared/task_root/thread_105/thread_state.json`

## 修复后复测结果

### 1. `/status` 复测通过

- Android retained evidence:
  - `actionType = Status`
  - `outcome = DirectAccepted`
  - `switchGate = KeepDirectDefault`
  - `bootstrapStatus = hello_ack`
  - `requestId = req_e629ec4b1b2d4083b2dc689f0d306af2`
  - `receiptId = relay-receipt:android-taskmail:session-action:req_e629ec4b1b2d4083b2dc689f0d306af2:e189f201`
  - `transportMessageId = <177424125431.922549.6037538899818063054@mail-runner.local>`
- Android UI dump 显示：
  - `Latest direct result -> Status query -> Direct accepted`
  - `Keep direct default`
  - `Hello ack`
- PC 侧证据：
  - `mail/raw_005.json`
    - direct ingress
    - `X-TaskMail-Action-Type: status`
    - `X-TaskMail-Relay-Request-Id: req_e629ec4b1b2d4083b2dc689f0d306af2`
  - `mail/raw_006.json`
    - `[STATUS][S:thread_105] ...`
    - summary 为 `This session is not currently running. Current thread status: done.`
- 远端 relay-visible task root 已写出：
  - `thread_105/session_actions/req_e629ec4b1b2d4083b2dc689f0d306af2/session_action_closeout.json`

### 2. plain reply 复测通过

- 用户输入正文：
  - `THREAD105_PLAIN_REPLY_20260323_G`
- Android retained evidence:
  - `actionType = Reply`
  - `outcome = DirectAccepted`
  - `switchGate = KeepDirectDefault`
  - `bootstrapStatus = hello_ack`
  - `requestId = req_6319e94ee2744e4f9b2b2608a4160665`
  - `receiptId = relay-receipt:android-taskmail:session-action:req_6319e94ee2744e4f9b2b2608a4160665:5a479a16`
  - `transportMessageId = <177424159214.922549.13991150858002992506@mail-runner.local>`
- Android UI dump / screenshot 显示：
  - `Latest direct result -> Plain reply -> Direct accepted`
  - `Keep direct default`
  - `Hello ack`
  - 相同 `requestId` / `receiptId` / `transportMessageId`
- PC 侧证据：
  - `mail/raw_007.json`
    - direct ingress
    - `X-TaskMail-Action-Type: reply`
    - `X-TaskMail-Relay-Request-Id: req_6319e94ee2744e4f9b2b2608a4160665`
    - body 为 `THREAD105_PLAIN_REPLY_20260323_G`
  - `mail/raw_008.json`
    - `[ACCEPTED][S:thread_105] ...`
  - `mail/raw_009.json`
    - `[RUNNING][S:thread_105] ...`
  - `mail/raw_010.json`
    - `[DONE][S:thread_105] ...`
    - `Reply:` 段落回显 `THREAD105_PLAIN_REPLY_20260323_G`
- 本地 run 结果：
  - `runs/20260323_125319_1ab0/result.json -> status = success`
  - `thread_state.json -> status = done`
  - `thread_state.json -> last_summary = THREAD105_PLAIN_REPLY_20260323_G`

## 新发现的两个 pitfall

### 1. Windows PowerShell 直重定向会把 Android record 快照写成 UTF-16 LE

- 症状：
  - `adb exec-out ... > taskmail_session_action_send_records.json` 后，bundle 脚本报 UTF-8 decode 错误。
- 触发条件：
  - 在 PowerShell 中直接用 `>` 保存 `adb exec-out` 输出。
- 确认原因：
  - PowerShell 会把文本重定向结果写成 UTF-16 LE。
- 规避方式：
  - 若该文件要喂给 `build_taskmail_closeout_bundle.py`，先转成无 BOM UTF-8。

### 2. `/status` closeout 与 reply closeout 的落点不对称

- `/status` 这次的 `session_action_closeout.json` 出现在 VPS relay-visible task root。
- plain reply 这次没有在本地 `thread_105/session_actions/` 生成新的 closeout 文件，但本地 `runs/20260323_125319_1ab0/canonical_summary.json` 已包含：
  - `request_id`
  - `packet_id`
  - `action_type = reply`
  - `terminal_mail_message_id`
  - `terminal_mail_subject`
- 结果是：
  - plain reply 可以直接用本地 `canonical_summary.json + raw_007..raw_010 + Android record` 组成 strong-bind closeout bundle。
  - `/status` 若要用 bundle 形式留档，当前仍更依赖远端 `session_action_closeout.json`。

## 本次保留的关键 artifact

- Android retained evidence:
  - `_tmp_device/thread105_status_taskmail_session_action_send_records.json`
  - `_tmp_device/thread105_reply_taskmail_session_action_send_records.json`
  - `_tmp_device/thread105_status_rerun_taskmail_session_action_send_records.json`
  - `_tmp_device/thread105_reply_rerun_taskmail_session_action_send_records.json`
  - `_tmp_device/thread105_reply_rerun_taskmail_session_action_send_records.utf8nobom.json`
- Android UI artifacts:
  - `_tmp_device/thread105_reply_failure.png`
  - `_tmp_device/thread105_reply_failure.xml`
  - `_tmp_device/thread105_status_rerun.xml`
  - `_tmp_device/thread105_reply_rerun.xml`
  - `_tmp_device/thread105_reply_rerun.png`
- Closeout artifacts:
  - `_tmp_device/thread105_status_closeout_bundle.json`
  - `_tmp_device/thread105_reply_closeout_bundle.json`
  - `_tmp_device/thread105_status_rerun_session_action_closeout.json`
  - `_tmp_device/thread105_reply_rerun_closeout_bundle.json`

## 下一步

1. 若后续还要做 formal-host direct smoke，先保证 VPS relay-visible `task_root` 与本地 live task store 持续同步。
2. 若 smoke 时间较长，优先使用：

```powershell
.\.venv\Scripts\python.exe .\scripts\sync_relay_task_root.py `
  --host 124.223.41.153 `
  --user ubuntu `
  --key-path .\work_bot.pem `
  --local-task-root E:\projects\mail_based_task_manager\_tmp_live_mail_runner\tasks `
  --remote-task-root /opt/mail_runner_relay/shared/task_root `
  --repeat-seconds 2
```

3. 若要从根上降低这类 locator 脆弱性，下一步代码硬化应在 PC-side relay：
  - 让 post-creation resolver 在 `session_state` 缺失时，参考 `thread_state` 做 fallback 解析。

## 本次变更

- Android 代码：无
- Android 测试：无
- 仓库文档：更新本 handoff
- 设备验证：有
