# TaskMail Next Session Handoff 2026-03-26

## 当前判断

- `2026-03-26` 重新核对 Android 与 PC 仓后，可以明确：`VPS-first 多 PC` live 主线当前是 `ws://124.223.41.153:8787/pc-control` 这一套 `pc-control` 协议，不是旧 Android compatibility `/control`。
- Android 仓这次先补的是本地主线构造层：`TaskMailNewTaskDraft -> canonical control-plane new_task command`。
- Android 当前还**没有**把这个 command 真正发到 live `pc-control` 主线入口；现有 direct send 仍停在旧 `/relay` phase2 packet。

## Read First

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

- 新增 `TaskMailNewTaskControlPlaneMapper`：
  - `TaskMailNewTaskDraft.toControlPlaneNewTaskCommand(...)`
  - `TaskMailNewTaskDraft.toControlPlaneNewTaskDispatchMessage(...)`
- 映射规则现在会把：
  - `pc_id / workspace_id / execution_policy`
  - `repo_path / workdir / task_text / mode / timeout_seconds / acceptance / source`
  - 收口到 canonical control-plane 形状。
- 明确要求 `pcId + workspaceId` 不能缺失；这条约束目前先落在 mapper 层，而不是 UI 校验层。
- 补了对应单测，确认 command / dispatch message 形状与现有 control-plane codec 没打架。

## 下一步

1. 先决定 Android 第一轮 live 注入到底走哪条入口：
   - 若只是 operator/debug 联调，优先评估临时接 `POST /debug/pc-control/dispatch`
   - 若要做真正 Android-facing 主线入口，先别把旧 `/control` compatibility shell 硬扩成主协议
2. 把 `TaskNewTask` 页面校验补到主线要求：
   - `pc_id` 必选
   - `workspace_id` 必选
3. 再把 transport sender 接到确定的主线入口，并做第一轮窄联调：
   - `new_task command -> command_ack -> event -> result`

## 验证

- 已运行：
  - `.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest --tests "*TaskMailNewTaskControlPlaneMapperTest" --tests "*ControlPlaneProtocolJsonCodecTest" --console=plain`
- 未运行：
  - 真实 Android / VPS / PC live 握手
  - `TaskNewTaskViewModel` 主线必选校验改造
