# TaskMail Next Session Handoff 2026-03-26

## 当前判断

- `2026-03-26` 重新核对 Android 与 PC 仓后，可以明确：`VPS-first 多 PC` live 主线当前是 `ws://124.223.41.153:8787/pc-control` 这一套 `pc-control` 协议，不是旧 Android compatibility `/control`。
- Android 当前 `new_task` 第一轮主线 cutover 已不再走旧 `/relay` phase2 packet；它现在走 Android-facing `POST /v1/android/create-session` facade。
- Android 侧仍然**没有**直接接入 live `pc-control` websocket；当前是 facade cutover，不是 raw protocol cutover。

## Read First

- `docs/taskmail/planning/android/taskmail-vps-first-multi-pc-android-facing-facade-authority-v0.1.md`
- `docs/taskmail/planning/android/taskmail-vps-first-multi-pc-create-session-command-contract-v0.1.md`
- `docs/taskmail/planning/android/taskmail-vps-first-multi-pc-pc-handshake-readiness-checklist-v0.1.md`
- `docs/taskmail/planning/platform/taskmail-pc-vps-control-protocol-v0.1.md`
- `docs/taskmail/planning/platform/taskmail-command-event-payload-appendix-v0.1.md`
- `docs/taskmail/planning/platform/taskmail-execution-policy-appendix-v0.1.md`
- `E:\projects\mail_based_task_manager\docs\plans\vps_first_multi_pc_control_plane_mainline_v0.1.md`
- `E:\projects\mail_based_task_manager\docs\plans\vps_first_multi_pc_phase1_execution_plan_v0.1.md`
- `E:\projects\mail_based_task_manager\docs\current\README.md`
- `E:\projects\mail_based_task_manager\mail_runner\relay_server\pc_control_protocol.py`
- `E:\projects\mail_based_task_manager\mail_runner\pc_control_operator_dispatch.py`

## 本次会话做了什么

- 保留并复用了 `TaskMailNewTaskDraft -> canonical control-plane` mapper，作为 facade 请求的 canonical payload 归一层。
- 新增 Android-facing `create-session` HTTP client：
  - `POST /v1/android/create-session`
  - 使用独立 `android_app_token`
  - 把 `submit_ack + session_binding` 映射回 `TaskMailCreateSessionResult`
- `TaskMailModule` 中 `TaskMailCreateSessionClient` 已切到 facade client，不再默认绑定旧 relay compatibility sender。
- `TaskNewTask` 成功后若拿到 `session_binding`，会直接导航到 `session detail`；没有 binding 时才回退到仅 toast + back。
- `TaskMail relay debug` 现在可保存 `android_app_token`；保存配置不再强制要求必须先填 relay transport token。
- 已补 focused unit tests，覆盖 config、client、ViewModel、navigation/screen 接线。

## 下一步

1. 先做一轮 fresh device/VPS smoke：
   - 配 `android_app_token`
   - 验 `create-session -> submit_ack -> session_binding -> detail` 真机闭环
2. 继续 Batch A/B：
   - 把 SessionDetail 对 control-plane session/result 的承接补齐
   - 把“提交后立即可见”的体验从 mail refresh 依赖转成 facade/session binding 主线
3. 再推进 `reply` / `/status` 主写路径 cutover，别回头继续扩旧 `/relay` packet 主线

## 验证

- 已运行：
  - `.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest --tests "*DefaultTaskTransportConfigRepositoryTest" --tests "*OkHttpTaskMailCreateSessionFacadeClientTest" --tests "*TaskNewTaskViewModelTest" --tests "*TaskNewTaskScreenKtTest" --tests "*TaskMailRelayDebugViewModelTest" --console=plain`
- 未运行：
  - 真实 Android / VPS / PC live create-session smoke
  - SessionDetail / reply-status 的后续 Batch B/C 改造
