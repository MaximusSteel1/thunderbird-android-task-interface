# TaskMail Android 当前状态

本文是 Android 仓库内 TaskMail 当前实现读法的事实源。

如果本文件与旧 planning、handoff 或历史 phase 文档冲突，应以本文件为当前基线，再按需要回查 archive。

## 日期

- 最后更新：2026-03-24

## 文档维护约定

- 本文件只回答“Android TaskMail 当前已经具备什么能力，以及这些能力应如何读”
- 协议 authority 仍由 `docs/TASKMAIL-MAIL-RULES.md` 承担
- 当前验证摘要仍由 `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md` 承担
- 详细历史过程已迁到 `docs/taskmail/archive/`

## 本文回答的问题

> Android TaskMail 现在到底已经做到哪一步，哪些边界已经固定，哪些主线仍在推进？

## 当前基线

截至 2026-03-24，Android TaskMail 不再是 debug-only 原型。

当前更准确的读法是：

- 真实 TaskMail 邮件已接入本地 mail store，并驱动 `workspace -> session -> detail`
- formal host 已存在，`Tasks` 入口、launcher 路径和正式宿主链路都已落地
- `New task`、`Project list`、workspace、detail 都是正式 TaskMail 宿主内的真实表面，而不是只留在 debug activity 的实验路径
- mail 仍然是当前 canonical truth layer；direct lane 只能按已验证的窄边界理解
- `TaskMail relay debug` 当前还承载一组 transport readiness / observability debug-only 入口，但这不等于业务 cutover 已完成

## 当前已实现的主要能力

### 1. 读路径与投影

- TaskMail 真实邮件读取、聚合和协议感知解析已经存在
- `workspace_id + session_id` 是当前 TaskMail 会话主键读法
- `[SYNC]` bootstrap 邮件仍保持在 TaskMail session/detail 投影之外
- rich-text detail、attachment timeline、refresh/live-update、draft/attachment 保持都已在仓库内落地

### 2. `new_task`

- formal-host `New task` 页面与 sender-account 解析已经存在
- 当前 `new_task` 发送是 direct-first / mail-fallback
- hard rejection 会显式停止，不会静默回退
- latest direct result 已归一到本地 durable evidence，并可在正式 `New task` 页面复读
- Android 侧 `new_task` latest evidence 现在保留 `requestId`、可用时的 `receiptId` 和可用时的 `transportMessageId`
- 当前 `new_task` 主线已经进入 observation / guardrail 读法，而不是继续开新配置开关

### 3. `reply` / `/status`

- 当前 guarded direct lane 已接入 formal-host detail
- v1 scope 仍只覆盖 `current-session plain reply` 与 `current-session /status`
- latest session-action evidence 会按 canonical `workspace_id + session_id` 持久化，并在 detail 页面复读
- Android fallback evidence 现在也保留 `requestId`，并在可用时保留 `receiptId` / `transportMessageId`
- `thread_105` 的 formal-host live rerun 已正向证明：
  - `/status` 可 direct accepted，并回收到 canonical `[STATUS]` mail
  - plain reply 可 direct accepted，并回收到 `[ACCEPTED] -> [RUNNING] -> [DONE]`
- 当前这条主线不应再读成“全部 reply 语义都已经 direct 化完成”，而应读成“guarded lane 已存在，closeout 仍在继续收口”

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
- plain reply、attachment continuation、single-question quick answer、多问题 `Answers:` 模板、`/status`、`paused` `/resume` 语义都仍以 mail path 为当前稳定语义
- drawer `Tasks` 入口、launcher handoff、workspace/detail refresh、attachment open/save 都已存在

## 当前不能误写的边界

- mail 仍然是当前 user-visible outcome 的 canonical truth layer
- `[SYNC]` 仍然是 bootstrap discovery 行为，不创建 task/thread/session，也不进入 session 投影
- `reply` / `/status` 的 direct lane 仍只覆盖当前最窄 v1 scope；quick answer、多问题 `Answers:`、attachment continuation、paused `/resume` 仍不能被误写成 direct scope
- `new_task` 当前不是“继续加 activation/config 开关”的阶段，而是 guarded observation 阶段
- `[SYNC]` 当前不是“多账号都已严格支持”的阶段；当前只应按 single-account available 读
- `transport_probe` 与 `/v1/files` debug harness 当前只应按 readiness / observability 基础设施读取，不能误写成 TaskMail 业务主链已经完成 `/control` cutover

## 当前验证读法

截至 2026-03-24，应这样理解验证状态：

- 读路径、projection、reply 基础语义、attachments、refresh、`new_task` durable evidence、`reply/status` durable evidence、`Project list` 渲染与 repo prefill 都已有 focused automated coverage
- `new_task` live evidence 已闭到 observation 边界
- `reply/status` 已有正向 direct accepted live 样本，但仍需继续收口 same-run strong bind 与 fallback artifact 对齐
- `[SYNC]` 当前已证明 request path、waiting UI、follow-up refresh 与“不进入 session projection”边界，但对“同一轮 direct request 到 canonical reply”的时序闭环仍未完全关单
- transport readiness / observability 当前已有真机 + PC/VPS focused live evidence，但这些证据只覆盖 `transport_probe` 与 `/v1/files` 单样本，不直接扩展 TaskMail 业务 cutover 结论

详细验证摘要见 `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`。

## 当前活跃主线

- `new_task`：保持 observation 与 rollback guardrail，不再主动扩 scope
- `reply` / `/status`：继续收口 same-run strong bind、PC fallback artifact gaps，以及 relay-visible task root 前置条件
- `[SYNC]`：继续联调 direct request 与 canonical reply 回流时间线，必要时再决定是否扩大 follow-up refresh 窗口

## 当前下一步

如果只看当前工程主线，最重要的不是继续新增文档，而是保持三条主线读法稳定：

- `new_task` 不再制造新的平行 decision note
- `reply` / `/status` 继续围绕 closeout 做窄验证，而不是误写成全量 direct 化
- `[SYNC]` 继续围绕 direct request + canonical mail result 的边界闭环，而不是把它拉进 TaskMail session 投影
