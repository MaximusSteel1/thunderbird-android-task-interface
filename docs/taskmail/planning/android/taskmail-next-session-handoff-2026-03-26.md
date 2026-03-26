# TaskMail Next Session Handoff 2026-03-26

## 当前判断

- `2026-03-26` 重新核对 Android 与 PC 仓后，可以明确：`VPS-first 多 PC` live 主线当前是 `ws://124.223.41.153:8787/pc-control` 这一套 `pc-control` 协议，不是旧 Android compatibility `/control`。
- Android 当前 `new_task` 第一轮主线 cutover 已不再走旧 `/relay` phase2 packet；它现在走 Android-facing `POST /v1/android/create-session` facade。
- Android 侧仍然**没有**直接接入 live `pc-control` websocket；当前是 facade cutover，不是 raw protocol cutover。
- 本次 Batch B 已把 detail 里的 current-session `reply` / `/status` 主写路径切到 shared `/control` compatibility lane；它仍不是 live `pc-control` cutover。
- 本次 Batch C 已完成第一轮 public shell / legacy cleanup，并已补 focused regression。
- 本次 Batch D 第一轮已经把 `workspace/detail` 的 user-visible result 推进到 `VPS-native projection cache when available`：已进入 projection 的 session 不再默认回退成 mail truth。

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
- `TaskSessionDetail` 的 current-session `plain reply` / `/status` 主写路径已从旧 `/relay` packet 切到 shared `/control command(status|reply)`。
- `RunTaskMailDirectDispatch` 现在会先做 `/control hello_ack` capability bootstrap，再发送 session action command。
- detail 发送成功后会先合并 `command_ack/result(session_action_result)` control-plane snapshot，再继续本地 detail refresh。
- `workspace/session` 公共 route-key 继续收口到 `workspace_id + session_id`；`thread_id` 只保留在 cache / direct-subscribe 边界。
- workspace/home 现在去掉 fake `PC summaries` placeholder，只保留 `session-first + Routed workspaces` public 读法。
- `New task` 与 detail public 页面不再渲染 relay-era direct evidence 卡；durable evidence 暂退到 internal/debug guardrail。
- 旧 relay-era runtime seam 已删：
  - `RunTaskMailDirectOrFallback`
  - 旧 `new_task` relay compatibility sender / client
  - 旧 session-action relay sender
- `Project list` 等待态里的 `mail retry` 现已明确标成 compatibility fallback，不再按主流程口吻暴露。
- `TaskSessionDetail` 本地缓存现已持久化 `projectionSyncState`；codec/store 均已升级到可保存 `lastSequence` / `lastEventId` / `lastResultId` / `subscriptionStatus`。
- `create-session` accepted + `session_binding` 现在会先 seed 一个 provisional `Queued` session detail 到共享 cache，保证 workspace/detail 不用先等 mail 才能看到 session。
- direct detail observation 不再要求先拿到 `thread_id`；只要有 `workspace_id + session_id` 就可启动 direct subscribe，并把 projection 持久化回 detail store。
- workspace 现在会监听 detail store 变化；detail direct update、provisional create-session binding、mail compatibility repair 都会推动 workspace reload。
- `SyncTaskMailCache` 现在不会再用 mail rebuild 覆盖 `VPS-native` detail；mail 当前只做 compatibility repair merge。
- 本次已补 focused regression，覆盖：
  - provisional detail seeding
  - no-thread-id direct subscribe
  - workspace/detail 命中 VPS cache 后跳过默认 mail sync
  - mail compatibility repair 不再覆盖 VPS-native detail

## 下一步

1. 先做一轮 fresh device/VPS smoke：
   - 配 `android_app_token`
   - 验 `create-session -> submit_ack -> session_binding -> provisional session -> detail` 真机闭环
   - 验 workspace 在 session binding 后是否立即可见，不再等 canonical mail
   - 验 detail 内 `reply` / `/status` 的 `/control` continuity 与第一跳 UI 更新
2. 如果设备 smoke 暴露缺口，优先继续补 Batch D 的 continuity 细节：
   - workspace-level continuity / reconnect
   - direct gap repair 的真实设备读法
   - projection cache 与 mail compatibility repair 的边界
3. 设备 smoke 稳住后，再决定是否继续删 send-record internal guardrail，并准备从 shared `/control` compatibility lane 向 live `pc-control` 主线承接

## 验证

- 已运行：
  - `.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest --tests "*DefaultTaskTransportConfigRepositoryTest" --tests "*OkHttpTaskMailCreateSessionFacadeClientTest" --tests "*TaskNewTaskViewModelTest" --tests "*TaskNewTaskScreenKtTest" --tests "*TaskMailRelayDebugViewModelTest" --console=plain`
  - `.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest --tests "*RelayControlTaskMailSessionActionSenderTest" --tests "*RunTaskMailDirectDispatchTest" --tests "*TaskSessionDetailViewModelTest" --tests "*ObserveTaskMailDirectSessionDetailTest" --console=plain`
  - `.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest --tests "*TaskWorkspaceViewModelTest" --tests "*GetTaskSessionDetailTest" --tests "*FileBackedTaskSessionDetailRepositoryTest" --tests "*SyncTaskMailCacheTest" --tests "*DefaultTaskMailRepositoryTest" --console=plain`
  - `.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest --tests "*TaskNewTaskViewModelTest" --tests "*TaskWorkspaceViewModelTest" --tests "*TaskSessionDetailViewModelTest" --tests "*ObserveTaskMailDirectSessionDetailTest" --tests "*SyncTaskMailCacheTest" --console=plain`
- 未运行：
  - 真实 Android / VPS / PC live create-session smoke
  - 真实 Android / VPS / PC live `reply/status` `/control` smoke
  - Batch D 当前 `VPS-native cache/projection` 的真实设备 smoke
