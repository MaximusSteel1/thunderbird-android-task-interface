# TaskMail Unified Control Plane 总体架构（v0.1）

更新时间：2026-03-23

## 状态

- 本文是 TaskMail Android 下一轮大重构的总体架构 owner doc。
- 本文定义目标形态、统一边界、核心抽象、存储与投影结构，以及应删除的旧旁支。
- 本文不承担当前实现事实 authority；当前事实仍以 `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md` 为准。

## Read First

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/TASKMAIL-MAIL-RULES.md`
- `docs/taskmail/planning/android/taskmail-next-development-plan-v0.2.md`
- `docs/taskmail/planning/android/taskmail-unified-control-plane-cutover-map-v0.1.md`
- `docs/taskmail/planning/android/taskmail-android-pc-control-artifact-contract-v0.1.md`
- `docs/taskmail/planning/android/taskmail-android-pc-communication-development-conditions-v0.1.md`
- `docs/taskmail/planning/android/taskmail-transport-probe-payload-contract-v0.1.md`
- `docs/taskmail/planning/android/taskmail-transport-observability-harness-v0.1.md`
- `E:\projects\mail_based_task_manager\docs\current\mail_protocol.md`
- `E:\projects\mail_based_task_manager\docs\current\android_runner_communication_contract.md`
- `E:\projects\mail_based_task_manager\docs\plans\taskmail_bootstrap_control_contract_v2.md`
- `E:\projects\mail_based_task_manager\docs\plans\post_creation_session_action_contract_v1.md`

## 1. 一句话目标

把 Android TaskMail 从“mail-first 主线下叠加若干 direct 旁支”的结构，重构成：

- 一个统一的 outbound control plane
- 一个统一的 inbound event plane
- 一个统一的 local truth store
- 多个从同一 truth 投影出的 UI surface

最终 Android 内部不再按 `new_task`、`reply/status`、`[SYNC]` 分别维护三套 direct/mail 发送与接收逻辑。

## 2. 为什么要重构

当前结构的问题不是功能缺失，而是结构分叉：

- `new_task`
  - 一套 direct sender
  - 一套 mail fallback
  - 一套 send record
- `reply` / `/status`
  - 一套 direct sender
  - 一套 mail fallback
  - 一套 session-action send record
  - 一套 detail read-side direct sidecar
- `[SYNC]`
  - 一套 direct sender
  - 一套 mail fallback
  - 一套 mailbox result reader
  - 一套 project-sync waiting UI

这导致：

- 相同的 fallback / rejection / replay 语义被重复实现
- 接收面没有统一 truth layer
- 新协议继续叠在旧旁支上，而不是收敛到统一框架
- closeout、验证、debug 都必须按功能线分别完成

本轮重构的判断是：

- 不再继续在旧结构上追加新直连切片
- 直接统一发送、接收、存储、投影与 UI 调用面
- 允许删除当前为分阶段 rollout 服务的旧旁支

## 3. 架构原则

### 3.1 单一控制面

Android 内部只保留一个 TaskMail control plane。

对 UI 来说：

- 用户只是在发起一个 `TaskMailOperation`
- 不再区分“这是 new_task sender”、“这是 session-action sender”、“这是 project-sync requester”

### 3.2 单一接收面

Android 内部只保留一个 inbound event plane。

所有输入都先变成统一事件：

- mail ingress
- relay `packet_ack`
- relay `bootstrap_result`
- relay `session_snapshot`
- relay `session_delta`
- future relay-native result / artifact frame

UI 不得直接读取 websocket sidecar，也不得自行扫描 mailbox 特例结果。

### 3.3 单一本地真相

本地真相不再分散在：

- session snapshot cache
- project-sync mail reader
- new-task send record
- session-action send record
- viewmodel 内存态 direct overlay

统一改为三层本地真相：

1. `OperationLedger`
2. `EventStore`
3. `ProjectionStore`

### 3.4 Projection First

所有 UI surface 只读 projection：

- workspace
- session detail
- new task latest evidence
- project sync latest result

UI 不直接理解 relay frame，也不直接理解 raw mailbox message。

### 3.5 Mail 与 Relay 都只是 transport / event source

mail 和 relay 在目标形态中都不是“另一套业务结构”。

它们只是：

- outbound transport
- inbound event source
- artifact source

业务语义统一由 operation schema、event schema、projection schema 定义。

## 4. 目标分层

目标形态下，`feature:taskmail:internal` 内部建议收敛为五层。

### 4.1 UI Layer

职责：

- 收集用户输入
- 渲染 projection
- 发出统一 UI event

约束：

- 不直接调用 mail transport / relay transport
- 不做 direct/mail 判断
- 不持有 action-specific send result 类型

### 4.2 Application Layer

职责：

- 把 UI event 编译为 `TaskMailOperationDraft`
- 执行 `ExecuteTaskMailOperation`
- 观察 `ObserveTaskMailProjection`
- 执行 pending recovery / reconnect replay

这里是新的 orchestration 层，替代当前各 ViewModel 内嵌的分叉状态机。

### 4.3 Domain Layer

职责：

- 定义统一 operation / event / projection 模型
- 定义 fallback、replay、recovery、artifact bind 规则
- 定义各类 serializer / compiler contract

### 4.4 Gateway Layer

职责：

- `RelayTaskMailGateway`
- `MailTaskMailGateway`
- `MailIngressGateway`
- `RelayIngressGateway`

这里只处理 transport 与协议编解码，不保留 action-specific 业务状态机。

### 4.5 Store & Projector Layer

职责：

- ledger 持久化
- event store 持久化
- projection repository
- workspace / session / project-sync projector

这是新的本地 truth core。

## 5. 核心抽象

### 5.1 `TaskMailOperation`

统一的 outbound 业务动作。

建议至少包含：

- `operationId`
- `operationType`
- `requestId`
- `packetId`
- `createdAt`
- `origin`
- `target`
- `payload`
- `policy`

为避免 read-side 回退到启发式 bind，operation draft / ledger 在语义成立时还应显式携带：

- `workspaceId`
- `sessionId`
- `sourceId`
- `messageId`
- `artifactIds`
- `fileIds`

其中：

- `operationType`
  - `new_task`
  - `session_reply`
  - `session_status`
  - `project_sync`
  - future types
- `target`
  - `new_task` 可为 `workspace draft target`
  - `session_*` 必须是 canonical session target
  - `project_sync` 是 bootstrap target
- `policy`
  - `relay_then_mail_fallback`
  - `mail_only`
  - `relay_only`

### 5.2 `TaskMailOperationLedgerEntry`

统一记录每次用户可见发送尝试。

建议字段：

- `operationId`
- `operationType`
- `status`
- `requestId`
- `packetId`
- `receiptId`
- `resultId`
- `transportMode`
- `fallbackReason`
- `errorClass`
- `errorMessage`
- `artifactAnchors`
- `recordedAt`
- `lastUpdatedAt`

### 5.3 `TaskMailEvent`

统一的 inbound 事实事件。

建议事件类型：

- `MailMessageObserved`
- `RelayPacketAckObserved`
- `RelayBootstrapResultObserved`
- `RelaySessionSnapshotObserved`
- `RelaySessionDeltaObserved`
- `OperationFallbackTriggered`
- `OperationRecoveryStarted`
- `OperationRecoveryFailed`

建议统一事件头至少包含：

- `eventId`
- `eventType`
- `sourceKind`
- `causationKind`
- `sourceId`
- `workspaceId`
- `sessionId`
- `messageId`
- `artifactIds`
- `fileIds`
- `recordedAt`

约束：

- event 是 append-only
- event 保存原始 source metadata
- projector 只消费 event，不直接访问 transport

### 5.3.1 `sourceKind` 与 `causationKind`

`TaskMailEvent` 里既会有外部事实，也会有内部决策，因此必须显式区分，而不是混在一个“事件流”里靠命名猜。

建议冻结：

- `sourceKind`
  - `mail_ingress`
  - `relay_ingress`
  - `http_artifact`
  - `local_decision`
  - `local_recovery`
- `causationKind`
  - `external_fact`
  - `internal_decision`
  - `internal_transition`

对应规则：

- `MailMessageObserved` / `RelayPacketAckObserved` / `RelayBootstrapResultObserved` / `RelaySessionSnapshotObserved` / `RelaySessionDeltaObserved`
  - `causationKind = external_fact`
- `OperationFallbackTriggered`
  - `sourceKind = local_decision`
  - `causationKind = internal_decision`
- `OperationRecoveryStarted` / `OperationRecoveryFailed`
  - `sourceKind = local_recovery`
  - `causationKind = internal_transition`

这样回放、调试和 projector 重建时，能明确区分：

- 哪些是 transport / mailbox 提供的原始事实
- 哪些是 Android 本地在这些事实之上做出的决策

### 5.4 `TaskMailProjection`

统一的读模型族。

建议至少包含：

- `WorkspaceProjection`
- `SessionProjection`
- `ProjectSyncProjection`
- `OperationStatusProjection`

## 6. 统一发送架构

### 6.1 UI 到 Operation Draft

各页面不再直接持有具体 sender。

它们只负责生成：

- `NewTaskDraft`
- `SessionReplyDraft`
- `SessionStatusDraft`
- `ProjectSyncDraft`

然后交给统一 compiler 编译成 `TaskMailOperationDraft`。

### 6.2 `ExecuteTaskMailOperation`

这是新的单一入口。

职责：

1. 规范化 draft
2. 分配稳定 `operationId + requestId + packetId`
3. 写入 `OperationLedger` 为 `pending`
4. 按 policy 选择 relay 或 mail
5. 处理 `packet_ack`
6. 若进入 accepted direct path，则等待 / 恢复 direct result
7. 若 direct 前失败且允许 fallback，则切 mail
8. 把过程中的所有关键节点写入 ledger 和 event store

### 6.3 Relay Gateway

删掉 action-specific direct sender。

统一为：

- `send(operationEnvelope)`
- `awaitOperationFrames(operationId or packetId)`
- `replay(operationEnvelope)`

统一处理：

- `packet_ack`
- `bootstrap_result`
- `session_update`
- future result frames

### 6.4 Mail Gateway

删掉 `TaskMailNewTaskTransport` / `TaskMailReplyTransport` 的动作割裂。

统一为：

- `sendMail(operationEnvelope)`

内部再按 operation type 选择 serializer：

- `NewTaskMailSerializer`
- `ReplyMailSerializer`
- `ProjectSyncMailSerializer`

mail 只是一种 outbound 编译目标，不再是独立业务主线。

## 7. 统一接收架构

### 7.1 Ingress 统一入口

所有来自 mailbox 与 relay 的输入都先进 `TaskMailEventIngestor`。

不允许：

- project-sync 单独扫 mailbox
- detail 页面单独起一个 screen-scoped relay projector
- viewmodel 直接消费 relay raw frame

### 7.2 Mail Ingress

mail ingress 继续存在，但角色改变为：

- source adapter
- canonical artifact source
- fallback truth source

mail ingress 解析后生成 `TaskMailEvent`。

### 7.3 Relay Ingress

relay ingress 不再是“临时 sidecar”。

它是正式事件源之一。

它接收：

- ack
- result
- snapshot
- delta

统一写入 event store。

## 8. Store 结构

### 8.1 Operation Ledger

保存用户发起过的控制动作与执行轨迹。

作用：

- replay
- pending recovery
- latest evidence
- closeout / validation anchor

### 8.2 Event Store

保存 transport 与 mailbox 输入形成的事实事件。

作用：

- 统一投影输入
- 离线恢复
- 调试与回放
- projector 重建

一致性要求：

- 事件写入必须是 append-only 原子提交
- 新事件 durable 落盘前，不得更新 projection checkpoint
- 同一 `eventId` 重放必须幂等

### 8.3 Projection Store

保存面向 UI 的读模型。

建议最少包括：

- `taskmail_workspace_projections.json`
- `taskmail_session_projections.json`
- `taskmail_project_sync_projections.json`
- `taskmail_operation_status_projections.json`

当前 `taskmail_session_details.json` 可以作为此层的前身，但不应继续承担全部真相。

### 8.4 File-Backed 一致性与恢复规则

首版即使继续使用 file-backed store，也必须先冻结一致性边界；否则 Android 最先坏掉的会是 crash recovery，而不是业务功能。

最低要求：

1. 原子写
   - ledger / event store / projection store 都必须采用 `write temp -> fsync -> rename` 等价原子替换策略
2. 版本头
   - 每个文件都必须带 `schemaVersion`
   - 版本升级必须有显式 migration path 或 fail-fast 规则
3. checkpoint 分离
   - event append 与 projection checkpoint 不能写在同一个易损更新步骤里
4. crash recovery
   - 启动时必须能检测：
     - temp 文件残留
     - projection 落后于 event log
     - partially written checkpoint
5. rebuild 能力
   - projection store 损坏时，必须允许从 ledger + event store 重建

## 9. Projection 体系

### 9.1 Workspace Projection

来源：

- session 相关 mail event
- session 相关 relay event

输出：

- workspace 列表
- session 列表
- 最新状态、摘要、更新时间

### 9.2 Session Projection

来源：

- mail event
- relay session snapshot / delta
- outbound operation ledger anchor

输出：

- detail header
- timeline
- pending questions
- action availability
- latest direct/mail evidence

注意：

- 不再使用“mail detail + direct overlay”的临时合并读法
- projector 直接输出最终可展示 session projection

### 9.3 Project Sync Projection

来源：

- mail `[SYNC]` artifact event
- relay `bootstrap_result`
- operation ledger

输出：

- latest result
- latest pending request
- current source
  - `relay_direct`
  - `mail_artifact`
- recovery state
- repo choice list

### 9.4 Operation Status Projection

来源：

- ledger
- ack event
- result event
- fallback event

输出：

- latest evidence card
- pending / accepted / finalized / rejected / recovery_failed

## 10. 状态机

统一 operation 状态建议冻结为：

- `draft`
- `dispatching`
- `accepted`
- `finalizing`
- `finalized`
- `fallback_required`
- `fallback_succeeded`
- `fallback_failed`
- `rejected`
- `recovery_pending`
- `recovery_failed`

约束：

- accepted 不等于 finalized
- accepted 后不得静默切回 mail
- replay 必须复用同一 `requestId + packetId`
- fresh user tap 才能创建新 operation

## 11. 当前结构中应删除的旁支

### 11.1 删除 action-specific direct sender

删除：

- `RelayTaskMailDirectNewTaskSender`
- `RelayTaskMailDirectSessionActionSender`
- `RelayTaskMailDirectProjectSyncSender`

统一为一个 relay gateway。

### 11.2 删除 action-specific direct result 类型

删除：

- `TaskMailDirectNewTaskResult`
- `TaskMailDirectSessionActionResult`
- `TaskMailDirectProjectSyncResult`

统一为 `TaskMailOperationDispatchResult`。

### 11.3 删除分裂的 send record repository

删除：

- `TaskMailNewTaskSendRecordRepository`
- `TaskMailSessionActionSendRecordRepository`

改为：

- `TaskMailOperationLedgerRepository`

### 11.4 删除 project-sync mailbox special reader

删除：

- `MailStoreBackedTaskMailProjectSyncResultReader`

project sync 改为统一 event -> projection。

### 11.5 删除 detail read-side direct sidecar

删除：

- `ObserveTaskMailDirectSessionDetail`
- `TaskMailDirectSessionProjector`
- ViewModel 内 direct overlay 合并路径

direct update 改为统一 ingress -> event store -> session projector。

### 11.6 删除 ViewModel 内 direct/mail 分支

重写：

- `TaskNewTaskViewModel`
- `TaskSessionDetailViewModel`
- `TaskProjectSyncViewModel`

它们只面向 operation 与 projection。

## 12. 模块内建议组织

本轮尽量不改 Gradle module graph，但在 `feature:taskmail:internal` 内按 package 收敛。

建议新增 package：

- `...internal.domain.controlplane`
- `...internal.domain.operation`
- `...internal.domain.event`
- `...internal.domain.projection`
- `...internal.data.gateway`
- `...internal.data.ledger`
- `...internal.data.eventstore`
- `...internal.data.projection`

当前 legacy 或 phase-specific package 进入 archive / delete path。

## 13. Cutover 策略

这次不是长期双栈共存，而是一次 cutover。

### 13.1 前提

- PC / VPS 同步提供统一 vNext contract
- Android 不再继续基于旧 phase schema 扩实现

### 13.2 Android 侧顺序

1. 先建立 vNext operation / event / projection 模型
2. 建立统一 ledger + event store + projector
3. 建立统一 relay gateway 与 mail gateway
4. 让 workspace / detail / project sync 全部改读 projection
5. 让 new task / reply-status / project sync 全部改走统一 executor
6. 删除旧 sender、旧 send record、旧 special reader、旧 direct sidecar

### 13.3 文档顺序

1. 先冻结 vNext cross-repo contract
2. 再写 Android 实施计划
3. 落地后再改 `CURRENT-STATUS` / `VALIDATION-LEDGER`

## 14. 验证要求

新的测试矩阵不再按旧 phase 文档切，而按 control-plane 行为切。

至少覆盖：

1. operation draft -> envelope compile
2. relay accepted -> result finalize
3. relay accepted -> disconnect -> replay recovery
4. fallback-classified rejection -> mail fallback
5. hard rejection -> no fallback
6. mail ingress -> event store -> projection
7. relay ingress -> event store -> projection
8. session detail 在 mail-only / relay-only / mixed event 下的同构投影
9. project sync direct result 与 legacy mail artifact 的统一读法
10. operation ledger reload 后的 pending recovery

## 15. 当前非目标

本文当前不直接决定：

- 新的 cross-repo schema 具体字段名
- 是否把 TaskMail 从 `feature:taskmail:internal` 拆成更多 Gradle module
- 是否引入数据库替代当前 file-backed store

这些属于后续详细设计。

## 16. 当前结论

本轮重构的目标不是继续把旧结构修平，而是把 TaskMail Android 改造成：

- 统一 operation
- 统一 gateway
- 统一 event store
- 统一 projection
- 统一 UI 调用面

如果这个方向成立，当前 `phase2` / `phase5` 式按功能线分裂的 direct/mail 结构应视为待淘汰实现，而不是未来继续扩展的基础。

## 17. 配套文档

本文只冻结总体方向；具体收敛路径、共享协议与调试基础设施拆到三份配套文档：

- 收敛与替换蓝图：`docs/taskmail/planning/android/taskmail-unified-control-plane-cutover-map-v0.1.md`
- 控制面与文件面合同：`docs/taskmail/planning/android/taskmail-android-pc-control-artifact-contract-v0.1.md`
- Android-PC 通讯开发条件：`docs/taskmail/planning/android/taskmail-android-pc-communication-development-conditions-v0.1.md`
- `transport_probe` payload：`docs/taskmail/planning/android/taskmail-transport-probe-payload-contract-v0.1.md`
- 独立通讯 harness：`docs/taskmail/planning/android/taskmail-transport-observability-harness-v0.1.md`
