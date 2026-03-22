# TaskMail Next Session Handoff - 2026-03-23 - VPS Retest

## 当前状态

- Phase 5 `reply` / `/status` 的设备侧 durable evidence 已经闭环：
  - 旧样本 `thread_019` 的 `/status` 与 `thread_020` 的 plain reply 都已验证 detail reload
  - `thread_020` 还额外验证了 formal-host 桌面冷启动后 evidence card 仍可恢复
- 本地 PC 侧 `@openai/codex-sdk` 依赖已经恢复，`scripts/codex_sdk_sidecar/node_modules/@openai/codex-sdk` 存在，`node -e "import('@openai/codex-sdk')"` 已通过
- 相邻 PC 仓本地代码已经包含新的 post-creation direct server path：
  - `mail_runner/relay_server/post_creation_actions.py`
  - schema 为 `post-creation-session-action-contract-v1`

## 本轮最新结论

- VPS 升级后重新补测 fresh `/status` 样本 `thread_021`
- 结果不再是旧的 `FallbackRequired`
- Android latest direct evidence 现在是：
  - `actionType = Status`
  - `target = workspace_cb2404bf828c / thread_021`
  - `bootstrapStatus = hello_ack`
  - `outcome = DirectRejected`
  - `switchGate = SwitchBlocker`
  - `errorMessage = could not resolve a session for the requested workspace/session locator`

这说明：

- 新的 post-creation direct VPS 路径已经生效
- 当前真正 blocker 已变成 server-side current-session locator 解析失败

## 当前最可能根因

- VPS 上 relay server 的 `MAIL_RUNNER_TASK_ROOT` 很可能指错了
- 正确参考值应指向真正包含 `thread_*` 与 `_scheduler` 的 task root：
  - `E:\projects\mail_based_task_manager\_tmp_live_mail_runner\tasks`
- 不应只指到 runtime root：
  - `E:\projects\mail_based_task_manager\_tmp_live_mail_runner`
- 本地已有明确索引存在：
  - `E:\projects\mail_based_task_manager\_tmp_live_mail_runner\tasks\_scheduler\workspaces\workspace_cb2404bf828c\sessions\thread_021.json`

## 样本说明

- `thread_021` 是 `/status` 样本，但标题误写成了 `Phase5 reply direct accept 20260323 D`
- `thread_022` 是 plain reply 样本，标题也是 `Phase5 reply direct accept 20260323 D`
- 因为 `reply` 与 `/status` 共用 `_resolve_current_session_thread_state(...)`，在 VPS task-root 修正前不要继续消耗 `thread_022`

## 下一步

1. 让 VPS 侧检查并修正 `MAIL_RUNNER_TASK_ROOT`
2. 重启 relay server
3. 修好后先重试 `thread_021` 的 `/status`
4. 如果 `thread_021` 不再报 session locator 解析失败，再继续 `thread_022` plain reply
5. 补测时重点看：
   - Android 是否从 `DirectRejected` 变成 `DirectAccepted`
   - Android record 是否带 `requestId` / `receiptId` / `transportMessageId`
   - PC 侧是否生成 `session_action_closeout.json`
   - closeout bundle 是否能从 weak `last_summary` bind 升级

## 本次变更

- Android 行为代码：无
- Android 文档：已补 handoff 与 authority/ledger 状态
- 设备验证：有
- 新结论：VPS 已进入新 direct path，但 server-side session locator 仍未闭环
