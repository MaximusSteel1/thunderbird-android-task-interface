# TaskMail Android 当前状态

本文是 Android 仓库内 TaskMail 当前实现读法的事实源。

如果本文件与旧 planning、handoff 或历史 phase 文档冲突，应以本文件为当前基线，再按需要回查 archive。

## 日期

- 最后更新：2026-03-29

## 文档维护约定

- 本文件只回答“Android TaskMail 当前已经具备什么能力，以及这些能力应如何读”
- 协议 authority 仍由 `docs/TASKMAIL-MAIL-RULES.md` 承担
- 当前验证摘要仍由 `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md` 承担
- 详细历史过程已迁到 `docs/taskmail/archive/`

## 本文回答的问题

> Android TaskMail 现在到底已经做到哪一步，哪些边界已经固定，哪些主线仍在推进？

## 当前基线

截至 2026-03-26，Android TaskMail 不再是 debug-only 原型。

当前更准确的读法是：

- 真实 TaskMail 邮件已接入本地 mail store，并继续作为 legacy/compatibility data source 参与 `workspace -> session -> detail`
- formal host 已存在，`Tasks` 入口、launcher 路径和正式宿主链路都已落地
- `New task`、`Project list`、workspace、detail 都是正式 TaskMail 宿主内的真实表面，而不是只留在 debug activity 的实验路径
- `new_task` 当前主写路径已切到 Android-facing `POST /v1/android/create-session` facade，而不是旧 `/relay` phase2 packet
- Android 还没有直接接入 live `pc-control` websocket；当前只是先完成 Android-facing facade cutover
- 对已经进入 `VPS-native session detail projection` 的会话，workspace/detail 当前优先读取本地 projection cache；mail 在这些会话上已降为 compatibility repair
- 对尚未进入 `VPS-native projection` 的 legacy / mail-only 会话，mail 仍然是当前 user-visible outcome 来源
- `TaskMail relay debug` 当前还承载一组 transport readiness / observability debug-only 入口，但这不等于业务 cutover 已完成

## 当前已实现的主要能力

### 1. 读路径与投影

- TaskMail 真实邮件读取、聚合和协议感知解析已经存在
- `workspace_id + session_id` 是当前 TaskMail 会话主键读法
- `thread_id` 现在只保留在 cache / direct-subscribe 之类的内部兼容边界，不再作为公开 route/key 的主匹配键
- `TaskSessionDetail` 本地缓存现在会持久化 `projectionSyncState`，包括 `lastSequence` / `lastEventId` / `lastResultId` / `subscriptionStatus`
- `create-session` accepted + `session_binding` 现在会先 seed 一个 provisional `Queued` session detail 到本地 cache，再进入 detail
- direct session projection 现在可在只有 `workspace_id + session_id`、还没有 `thread_id` 时启动订阅，并把结果持久化回共享 detail store
- workspace/detail 只要命中 `VPS-native` detail cache，就会优先渲染该 cache，并跳过默认 mail sync
- `[SYNC]` bootstrap 邮件仍保持在 TaskMail session/detail 投影之外
- rich-text detail、attachment timeline、refresh/live-update、draft/attachment 保持都已在仓库内落地

### 2. `new_task`

- formal-host `New task` 页面与 sender-account 解析已经存在
- 当前 `new_task` 主写路径是 Android-facing `create-session` facade：
  - `POST /v1/android/create-session`
  - 请求主键是 `pc_id + workspace_id`
  - 认证令牌是独立 `android_app_token`
- accepted submit 若返回 `session_binding`，Android 会直接打开对应 `session detail`
- hard rejection 仍会显式停止，不会静默回退
- 当前这条主线不应再读成“旧 `/relay` packet 的 observation 阶段”，而应读成“Batch A facade cutover 已落地，后续继续承接 SessionDetail / route-key / reply-status”
- relay-era `latest direct result` durable evidence 仍保留为 internal/debug guardrail，但已经退出 `New task` public surface

### 3. `session-action`

- formal-host detail 的当前主写路径已经切到 Android-facing `POST /v1/android/session-action` facade
- 当前 detail public surface 已统一走同一家 `session-action` family：
  - plain reply
  - quick answer
  - structured `Answers`
  - `attachment_continuation`
  - `/status`
  - `/kill`
  - `/end`
- 当前 submit 成功后，Android 不再等 canonical mail 才承接第一跳状态，而是会先：
  - 持久化 latest session-action evidence
  - 在本地 detail cache 写入 `pendingSubmissions`
  - 继续用 `session_snapshot.latest_session_action.command_id` 做 same-run continuity 清理
- `session-action` 写路径当前按 `session_id` 为主锚点；缺 canonical `workspace_id` 时，只要已有 canonical `session_id`，仍可提交
- Android 读侧当前仍保持 `workspace-aware`：
  - 本地 detail key / navigation / history locator 还没有整体切到 `session_id only`
  - submit 回包里的 canonical `workspace_id` 仍会继续回写到本地 identity
- latest session-action evidence 仍会按 canonical target 持久化，但已经退出 detail public surface，只保留 internal/debug guardrail 价值
- `paused` session 当前不会在 detail 页自动 prepend `/resume`：
  - 当前 public UI 会把 plain-text reply 标记为 unavailable
  - `pause` / `resume` action family 已进入 Android 领域模型与 facade sender 范围
  - 但 detail public control 还没有单独暴露 dedicated `pause` / `resume` 按钮
- 这条主线当前正确读法不再是“guarded direct lane”，而是“current-session `session-action` owner seam 已切通，后续继续围绕 projection / snapshot continuity 与 live smoke 收口”

### 3.5. workspace / workbench public shell

- formal-host 首页当前按 `session-first` 读法组织：`Needs attention`、`Active sessions`、`Recent sessions` 仍是主入口
- fake `PC summaries` placeholder 已退出 public home；当前只保留 `Routed workspaces` 作为 route context
- workspace 卡片现在直接展示 `Workspace ID` route anchor，不再用 “PC inventory not wired yet” 这类占位文案解释首页
- relay-era direct evidence 卡已经退出 `New task` 与 detail 的 public 页面
- 旧 relay-era runtime seam 已退出运行时主绑定：
  - `RunTaskMailDirectOrFallback`
  - 旧 `new_task` relay compatibility sender / client
  - 旧 session-action relay sender
- 当前 project-sync 的 mail retry 仍保留，但已按 compatibility fallback 读取，而不是产品主链真相

### 3.6. VPS-native cache / projection

- Batch D 第一轮已经把 `workspace/detail` 主读链推进到 `VPS-native projection cache when available`
- `FileBackedTaskSessionDetailRepository` 现在会持久化 projection state，并在 upsert 后对 workspace 发出 store change
- detail 收到 direct projection 后，不再只做内存 overlay；它会把 projection 映射成 `TaskSessionDetail` 并写回共享 detail store
- workspace 现在会监听 detail store 变化；detail direct update、provisional create-session binding、mail compatibility repair 都会推动 workspace reload
- `SyncTaskMailCache` 现在不会再用 mail rebuild 覆盖 `VPS-native` detail：
  - status / summary / pending questions / timeline 主读法继续保留 VPS projection
  - mail 当前只补 compatibility repair，例如 reply context、mail timeline 合流、control-plane fallback merge
- 当前正确读法不是“mail 完全退出 Android 侧”，而是“只要 session 已进入 VPS-native cache，workspace/detail 就不再默认把 mail 当主真相层”

### 4. `[SYNC] Project list`

- `Project list` 路由、最新 `[SYNC] Project Folder List` 解析与渲染、`Use this repo` 回填已经存在
- formal-host smoke 已证明：
  - `Project list` 可读
  - `Use this repo` 可回填到 `New task`
  - `[SYNC]` 不进入 TaskMail workspace/session/detail 投影
- 当前 `[SYNC]` 请求主线已经推进到 direct-first，但仍保持 canonical mail reply 作为唯一结果面
- 当前 direct `[SYNC]` 读法是 `single-account available`：
  - relay `packet_ack` 已恢复到亚秒级
  - 页面会区分“请求失败”和“正在等待新 `[SYNC]` reply”
  - 当前实现包含有限 follow-up refresh
- 当前 debug-host 还保留了一个面向 `[SYNC]` 联调的 file-backed trace 开关：
  - 入口在 `TaskMail relay debug`
  - 落盘文件是 `project-sync-debug.log`
  - 默认关闭，只应在 focused device repro 时临时开启，抓完后再关闭
- 这条主线当前最大的未闭环项不是 Android 早超时，而是上游 `[SYNC] Project Folder List` 回流耗时仍可能达到分钟级

### 5. transport readiness / observability

- `TaskMail relay debug` 当前已具备 debug-only `transport_probe` 与 `/v1/files` 单样本入口
- `transport_probe` 当前的正确读法是 transport observability harness，而不是 `new_task` / `reply` / `[SYNC]` 的统一控制面 cutover
- 真机联调已正向证明：
  - `/control transport_probe` 可拿到同一 `probe_id` 的 `command_ack -> event* -> result`
  - accepted 后对同一 `packet_id/request_id` replay 会稳定复用同一组 `receipt_id` / `event_id` / `result_id`
  - `/v1/files` debug-only 单样本可走通 `POST /v1/files -> GET metadata -> GET content -> sha256`
- 当前 live runtime 对 `/v1/files` metadata 的 `kind` 仍只接受 `image | file`
- 因此当前实现里，大文本或 JSON sidecar 若要走 live `/v1/files`，仍应先按 `kind=file` + 正确 `mime_type` 读取，而不是把 planning 文档里的 `kind=text/json` 直接当成 current behavior

### 6. 其余已稳定能力

- runtime TaskMail bot-mailbox settings 已存在，并且改动对发送路径即时可见
- plain reply、attachment continuation、single-question quick answer、多问题 `Answers:` 模板、`/status`、`/kill`、`/end` 在 detail 当前都已按 `session-action` 主路径读取
- `paused` / `resume` 当前还没有 detail public control，但这不应再被误写成“reply/status 仍走 mail path”
- drawer `Tasks` 入口、launcher handoff、workspace/detail refresh、attachment open/save 都已存在

## 当前不能误写的边界

- mail 不再是所有 session 的统一 canonical truth：
  - 已进入 `VPS-native projection` 的 session，workspace/detail 以本地 projection cache 为主
  - legacy / mail-only session 仍以 mail 为唯一结果源
- `[SYNC]` 仍然是 bootstrap discovery 行为，不创建 task/thread/session，也不进入 session 投影
- detail 当前已切通的是 `session-action` current-session write seam，不应再误写成 `/control status|reply` compatibility lane
- Android 当前虽然已按 `session_id` 主锚点提交 `session-action`，但本地 detail/store/navigation/history 仍不是 `session_id only` 模型
- `pause` / `resume` 虽已进入 action family 和 sender contract，但 detail public UI 还没有 dedicated control；不能误写成“已在页面上完整可用”
- `new_task` 当前不是“回头扩旧 `/relay` submit”的阶段，而是 `create-session facade -> session binding -> routed workspace` 主线承接阶段
- `[SYNC]` 当前不是“多账号都已严格支持”的阶段；当前只应按 single-account available 读
- `transport_probe` 与 `/v1/files` debug harness 当前只应按 readiness / observability 基础设施读取，不能误写成 TaskMail 业务主链已经完成 `/control` cutover

## 当前验证读法

截至 2026-03-29，应这样理解验证状态：

- 读路径、projection、reply 基础语义、attachments、refresh、`session-action` durable evidence、`Project list` 渲染与 repo prefill 都已有 focused automated coverage
- `new_task` facade create-session client、config、ViewModel、navigation 与 screen 接线已有 focused automated coverage
- detail 当前 `session-action` facade sender、`pending submission`、`session_id` 主锚点写路径、history-snapshot continuity 清理与 screen 行为已有 focused automated coverage
- Batch C 的 workbench/public-surface 收口与 legacy 删除已经补过 fresh focused regression
- Batch D 第一轮 `VPS-native cache/projection` 已有 focused automated coverage：
  - provisional session detail seeding
  - no-thread-id direct subscribe
  - workspace/detail 命中 VPS cache 后跳过默认 mail sync
  - mail compatibility repair 不再覆盖 VPS-native detail
- `new_task` 当前仍缺 fresh Android / VPS / PC live smoke；因此不能把它误写成 raw `pc-control` websocket 已经在设备侧跑通
- `session-action` 当前仍缺 fresh device live smoke；尤其还需要 fresh `reply / attachment_continuation / /status / /kill / /end` 样本来确认真机 closeout 与 projection continuity
- `[SYNC]` 当前已证明 request path、waiting UI、follow-up refresh 与“不进入 session projection”边界，但对“同一轮 direct request 到 canonical reply”的时序闭环仍未完全关单
- transport readiness / observability 当前已有真机 + PC/VPS focused live evidence，但这些证据只覆盖 `transport_probe` 与 `/v1/files` 单样本，不直接扩展 TaskMail 业务 cutover 结论

详细验证摘要见 `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`。

## 当前实现侧焦点

- `new_task`：保持 facade cutover 主线，继续把 SessionDetail / route-key 承接到 VPS-first 主线，不再回头扩旧 `/relay` packet
- `workspace/detail`：继续围绕 `VPS-native projection cache` 的 live continuity、gap repair 与 reconnect 行为做窄验证
- `session-action`：继续围绕 live continuity、`command_id` closeout、以及 paused / resume public control 的后续承接做窄验证
- `[SYNC]`：继续联调 direct request 与 canonical reply 回流时间线，必要时再决定是否扩大 follow-up refresh 窗口

说明：

- 上述三条是“当前实现与兼容面仍需维护的焦点”，不是 2026-03-25 之后的 future-direction 唯一主线
- future-direction authority 已切到 `docs/taskmail/planning/android/taskmail-vps-first-multi-pc-authority-v0.1.md`

## 当前下一步

如果只看当前工程主线，最重要的是把 `session-action + VPS-native projection/snapshot` 的真机闭环补齐，再继续收口 paused / resume 与更宽 live smoke：

- `new_task` 继续围绕 `create-session facade -> session binding -> SessionDetail 承接` 推进
- `workspace/detail` 继续围绕 `VPS-native projection cache -> immediate visible result` 做真机闭环验证
- `session-action` 继续围绕 `pending submission -> latest_session_action.command_id closeout -> detail visible continuity` 做真机闭环验证
- paused / resume 后续若要进入 public detail，需要以 dedicated `session-action` control 方式承接，而不是回退到旧 mail 语义
- `[SYNC]` 继续围绕 direct request + canonical mail result 的边界闭环，而不是把它拉进 TaskMail session 投影
