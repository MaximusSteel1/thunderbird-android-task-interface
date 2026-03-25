# TaskMail Next Session Handoff - 2026-03-24 - Reply/Status Closeout Paths

## 当前决定

- `reply` / `/status` 这条主线当前不需要继续扩 direct scope。
- Android 本地 `session_action` evidence 链路已能保留 `requestId` / `receiptId` / `transportMessageId`，这轮新增的是一个更窄的 closeout 辅助面：在 `TaskMail relay debug` 页面直接暴露本地 send-record 文件路径。
- authority / ledger 这轮先不改写为“PC fallback artifact 缺字段已关闭”，因为当前只确认了相邻 PC 仓本机代码现状，还没有再做 fresh live closeout 回写。

## 这轮做了什么

- `TaskMail relay debug` 现在会显示两条本地 evidence 文件路径：
  - `taskmail_session_action_send_records.json`
  - `taskmail_new_task_send_records.json`
- 入口仍是 `app://taskmail/debug/relay`。
- 这两个路径都位于 `androidContext().filesDir/taskmail/` 下，便于下一轮同机 closeout 时直接定位 Android send records。
- 同一页面现在还提供 `Share session-action send records` 按钮，可直接把 `taskmail_session_action_send_records.json` 通过系统分享面板导出给 PC 侧 closeout 使用。

## 为什么做这一步

- 下一轮 `reply` / `/status` closeout 的真正阻塞点已经收缩为：
  - fresh same-run live bundle
  - relay-visible `task_root` 前置条件
  - Android send records 与 PC closeout bundle 的同轮对账
- 当前仓内之前没有现成入口暴露 `session_action` send-record 文件路径，手工定位成本高，而且容易拿错文件。

## 相邻 PC 仓本机现状

截至本次会话，本机相邻仓 `E:\projects\mail_based_task_manager` 已可读到：

- `mail_runner/session_action_closeout.py`
  - 已保留 `action_type`
  - 已保留 `target_session_identity`
  - 已保留 `ingress_message_id`
- `mail_runner/taskmail_closeout.py`
  - 已能优先读取 `session_action_closeout.json`
  - 已能读取 Android `taskmail_session_action_send_records.json`
  - 已能输出 `same_run_bind`
- `tests/test_taskmail_closeout.py`
  - 已覆盖 direct reply / status closeout 与 Android send-record bind 场景

这说明“PC 侧完全没有这些字段实现”已不再是本机代码现状；真正还没关的是 fresh live closeout。

## 当前验证

- 已运行：
  - `.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest --tests "*TaskMailRelayDebugViewModelTest" --console=plain`
- 运行前需要本地环境：
  - `JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot`
  - `GRADLE_USER_HOME=E:\projects\android_task_manager\.gradle-user-home`
  - `ANDROID_USER_HOME=E:\projects\android_task_manager\.android-user-home`
- 本轮未运行：
  - detekt / lint
  - 真机 `reply` / `/status` fresh rerun

## 下一步最小顺序

1. 先确认 relay-visible `task_root` 仍指向真正的 `..._tmp_live_mail_runner\tasks`，不要指到 runtime root。
2. 真机只跑两类窄样本：
   - `current-session /status`
   - `current-session plain reply`
3. 从 Android debug 页面记录 `taskmail_session_action_send_records.json` 路径，并拉取该文件给 PC closeout bundle 使用。
4. 在 PC 侧生成或复读：
   - `session_action_closeout.json`
   - `taskmail_daily_closeout_bundle.json`
5. 再决定是否回写：
   - `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
   - `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`

## 相关文件

- `feature/taskmail/internal/src/debug/kotlin/net/thunderbird/feature/taskmail/internal/debug/TaskMailRelayDebugScreen.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/relaydebug/TaskMailRelayDebugViewModel.kt`
- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/ui/relaydebug/TaskMailRelayDebugViewModelTest.kt`
