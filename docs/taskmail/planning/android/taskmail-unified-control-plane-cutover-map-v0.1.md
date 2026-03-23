# TaskMail Unified Control Plane 收敛与替换蓝图（v0.1）

更新时间：2026-03-23

## 状态

- 本文是 `taskmail-unified-control-plane-architecture-v0.1.md` 的实施蓝图。
- 本文聚焦 Android 仓内现有类簇如何收敛到统一 control plane。
- 本文不冻结 cross-repo wire schema 字段；字段冻结属于后续 vNext contract 工作。

## Read First

- `docs/taskmail/planning/android/taskmail-unified-control-plane-architecture-v0.1.md`
- `docs/taskmail/planning/android/taskmail-android-pc-control-artifact-contract-v0.1.md`
- `docs/taskmail/planning/android/taskmail-android-pc-communication-development-conditions-v0.1.md`
- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-MAIL-RULES.md`
- `E:\projects\mail_based_task_manager\docs\current\android_runner_communication_contract.md`

## 1. 当前结构的核心问题

当前 `feature:taskmail:internal` 在 `TaskMailModule` 里同时挂着三套发送链与三套读取链：

- `new_task`
- `reply` / `/status`
- `[SYNC] project list`

它们共享的是少量 relay/bootstrap 设施与 mail store 能力；它们不共享统一的 operation、event、projection 和 recovery 脊柱。

因此本次 cutover 的目标不是继续清理调用点，而是把当前并排 wiring 收敛成一条单脊柱：

- `TaskMailDraftCompiler`
- `ExecuteTaskMailOperation`
- `RelayTaskMailGateway`
- `MailTaskMailGateway`
- `TaskMailEventIngestor`
- `TaskMailOperationLedgerRepository`
- `TaskMailEventStoreRepository`
- `TaskMailProjectionRepository`
- `TaskMailProjectorEngine`
- `ObserveTaskMailProjection`

## 2. 目标脊柱与当前入口的对应关系

### 2.1 UI 与 orchestration

| 当前入口 | 当前职责 | 目标归宿 | 处理方式 |
| --- | --- | --- | --- |
| `TaskNewTaskViewModel` | 表单校验、direct/mail 分流、send evidence 展示 | `NewTaskDraftCompiler` + `ExecuteTaskMailOperation` + `ObserveTaskMailProjection` | 重写 |
| `TaskSessionDetailViewModel` | composer、command、direct sidecar 合并、mail fallback | `SessionReplyDraftCompiler` / `SessionCommandDraftCompiler` + `ExecuteTaskMailOperation` + `ObserveTaskMailProjection` | 重写 |
| `TaskProjectSyncViewModel` | `[SYNC]` 请求、mail retry、follow-up refresh | `ProjectSyncDraftCompiler` + `ExecuteTaskMailOperation` + `ObserveProjectSyncProjection` | 重写 |
| `RunTaskMailDirectOrFallback` | direct 尝试与 pre-accept fallback 统一壳 | `ExecuteTaskMailOperation` 内部状态机 | 删除 |
| `GetLatestTaskMailNewTaskSendRecord` | latest evidence 读取 | `GetOperationStatusProjection` | 删除后替换 |
| `GetLatestTaskMailSessionActionSendRecord` | latest evidence 读取 | `GetOperationStatusProjection` | 删除后替换 |
| `GetLatestTaskMailProjectSyncResult` | `[SYNC]` latest result 读取 | `GetProjectSyncProjection` | 删除后替换 |
| `RecordTaskMailNewTaskSendRecord` | new-task send evidence 落盘 | executor 直接写 ledger | 删除 |
| `RecordTaskMailSessionActionSendRecord` | session-action send evidence 落盘 | executor 直接写 ledger | 删除 |
| `RequestTaskMailProjectSync` | project-sync repository façade | `ExecuteTaskMailOperation(project_sync)` | 删除 |

### 2.2 Relay 发送与接收

| 当前类 | 当前职责 | 目标归宿 | 处理方式 |
| --- | --- | --- | --- |
| `RelayTaskMailDirectNewTaskSender` | `new_task` packet 组装与 ack 处理 | `RelayTaskMailGateway.send(operationEnvelope)` | 删除 |
| `RelayTaskMailDirectSessionActionSender` | `reply/status` packet 组装与 ack 处理 | `RelayTaskMailGateway.send(operationEnvelope)` | 删除 |
| `RelayTaskMailDirectProjectSyncSender` | `[SYNC]` packet 组装与 ack 处理 | `RelayTaskMailGateway.send(operationEnvelope)` | 删除 |
| `RelayTaskMailDirectSessionDetailSubscriber` | detail screen-scoped subscribe | `RelayIngressGateway` + `TaskMailEventIngestor` | 删除 |
| `RelayProtocolJsonCodec` | relay frame codec | `RelayFrameCodec` | 改责保留或重命名 |
| `OkHttpRelayConnectionClient` | ws connect / send / listen | `RelayConnectionSession` | 改责保留或重命名 |
| `DefaultRelayBootstrapManager` | relay config + health + connect bootstrap | `TaskMailControlPlaneBootstrapper` | 折叠 |
| `TaskMailDirectNewTaskResult` | action-specific send result | `TaskMailOperationDispatchResult` | 删除 |
| `TaskMailDirectSessionActionResult` | action-specific send result | `TaskMailOperationDispatchResult` | 删除 |
| `TaskMailDirectProjectSyncResult` | action-specific send result | `TaskMailOperationDispatchResult` | 删除 |

### 2.3 Mail 发送

| 当前类 | 当前职责 | 目标归宿 | 处理方式 |
| --- | --- | --- | --- |
| `EmailTaskMailNewTaskTransport` | `new_task` 邮件发送 | `MailTaskMailGateway.send(operationEnvelope)` | 删除壳、保留底层适配 |
| `EmailTaskMailReplyTransport` | reply 邮件发送 | `MailTaskMailGateway.send(operationEnvelope)` | 删除壳、保留底层适配 |
| `RealTaskMailNewTaskSender` | `new_task` transport façade | `ExecuteTaskMailOperation(mail path)` | 删除 |
| `RealTaskMailReplySender` | reply transport façade | `ExecuteTaskMailOperation(mail path)` | 删除 |
| `LegacyTaskMailNewTaskMimeMessageFactory` | `new_task` MIME 构造 | `NewTaskMailSerializer` 的底层 adapter | 暂时保留并改名/改责 |
| `LegacyTaskMailMimeMessageFactory` | reply MIME 构造 | `ReplyMailSerializer` 的底层 adapter | 暂时保留并改名/改责 |
| `LegacyTaskMailMimeMessageSender` | SMTP/Outbox 发信底座 | `MailOutboxAdapter` | 保留底座 |
| `StorageBackedTaskMailDestinationAddressProvider` | bot mailbox 目标地址 | `MailGatewayAddressResolver` | 保留或折叠 |
| `LegacyTaskMailSenderAccountSource` | sender account 列举 | `TaskMailAccountResolver` | 保留或折叠 |

### 2.4 Mail ingress、解析与投影

| 当前类 | 当前职责 | 目标归宿 | 处理方式 |
| --- | --- | --- | --- |
| `EmailIngress` | 从 local store 抽取原始邮件 | `MailIngressGateway` | 保留并改责 |
| `EmailMessageParser` | raw message -> `TaskMailMessage` | `MailIngressParser` | 保留并改责 |
| `TaskMailMessageDetector` | TaskMail mail detect | `MailIngressParser` 内部依赖 | 保留 |
| `LegacyTaskMailBodyExtractor` | body 归一化 | `MailProjectionSupport` | 保留 |
| `LegacyTaskMailAttachmentMetadataExtractor` | 附件抽取 | `MailProjectionSupport` | 保留 |
| `TaskMailProjectSyncResultParser` | `[SYNC]` result 文本解析 | `ProjectSyncProjector` 的 mail artifact parser | 保留 |
| `MailStoreBackedTaskMailProjectSyncResultReader` | 特例 mailbox `[SYNC]` reader | `MailIngress -> EventStore -> ProjectSyncProjection` | 删除 |
| `TaskMailSessionProjector` | mail messages -> detail snapshot | `TaskMailProjectorEngine` 的一个 projector | 拆分/吸收 |
| `TaskWorkspaceSummaryProjector` | session snapshot -> workspace summaries | `TaskMailProjectorEngine` 的一个 projector | 拆分/吸收 |
| `SnapshotBackedTaskMailRepository` | workspace/detail 读仓储 | `TaskMailProjectionRepository` | 删除后替换 |
| `DefaultTaskMailProjectSyncRepository` | `[SYNC]` 结果 façade | `ProjectSyncProjectionRepository` | 删除后替换 |

### 2.5 当前 store 与 truth 层

| 当前类 | 当前职责 | 目标归宿 | 处理方式 |
| --- | --- | --- | --- |
| `FileBackedUnifiedMessageRepository` | 原始 TaskMail message cache | `TaskMailEventStoreRepository` 的 mail artifact source 或迁移输入 | 改责 |
| `FileBackedMessageSyncStateRepository` | mailbox sync cursor | `MailIngressCheckpointRepository` | 保留 |
| `FileBackedTaskSessionDetailRepository` | session detail snapshot | `TaskMailProjectionRepository` | 删除后替换 |
| `TaskMailNewTaskSendRecordRepository` | new-task evidence store | `TaskMailOperationLedgerRepository` | 删除 |
| `TaskMailSessionActionSendRecordRepository` | session-action evidence store | `TaskMailOperationLedgerRepository` | 删除 |
| `FileBackedTaskMailNewTaskSendRecordRepository` | new-task evidence file store | `FileBackedTaskMailOperationLedgerRepository` | 删除 |
| `FileBackedTaskMailSessionActionSendRecordRepository` | session-action evidence file store | `FileBackedTaskMailOperationLedgerRepository` | 删除 |
| `TaskMailNewTaskSendRecordJsonCodec` | send record codec | `TaskMailOperationLedgerJsonCodec` | 删除 |
| `TaskMailSessionActionSendRecordJsonCodec` | send record codec | `TaskMailOperationLedgerJsonCodec` | 删除 |

### 2.6 screen-scoped direct 旁支

| 当前类 | 当前职责 | 目标归宿 | 处理方式 |
| --- | --- | --- | --- |
| `ObserveTaskMailDirectSessionDetail` | detail 页面 direct subscribe 抽象 | `RelayIngressGateway.observeFrames()` | 删除 |
| `DefaultObserveTaskMailDirectSessionDetail` | subscribe orchestration | `TaskMailRecoveryCoordinator` / `RelayIngressGateway` | 删除 |
| `TaskMailDirectSessionProjector` | direct frame -> detail overlay | `RelayEventProjector` | 删除 |

## 3. 哪些东西要直接删，哪些东西可以留作底座

### 3.1 cutover 后直接删除

- `RunTaskMailDirectOrFallback`
- `RelayTaskMailDirectNewTaskSender`
- `RelayTaskMailDirectSessionActionSender`
- `RelayTaskMailDirectProjectSyncSender`
- `RelayTaskMailDirectSessionDetailSubscriber`
- `ObserveTaskMailDirectSessionDetail`
- `DefaultObserveTaskMailDirectSessionDetail`
- `TaskMailDirectSessionProjector`
- `TaskMailDirectNewTaskResult`
- `TaskMailDirectSessionActionResult`
- `TaskMailDirectProjectSyncResult`
- `TaskMailNewTaskSendRecordRepository`
- `TaskMailSessionActionSendRecordRepository`
- `FileBackedTaskMailNewTaskSendRecordRepository`
- `FileBackedTaskMailSessionActionSendRecordRepository`
- `TaskMailNewTaskSendRecordJsonCodec`
- `TaskMailSessionActionSendRecordJsonCodec`
- `MailStoreBackedTaskMailProjectSyncResultReader`
- `TransportBackedTaskMailProjectSyncRequester`

### 3.2 可以保留但必须改责

- `EmailIngress`
- `EmailMessageParser`
- `TaskMailMessageDetector`
- `LegacyTaskMailBodyExtractor`
- `LegacyTaskMailAttachmentMetadataExtractor`
- `TaskMailProjectSyncResultParser`
- `RelayProtocolJsonCodec`
- `OkHttpRelayConnectionClient`
- `DefaultRelayBootstrapManager`
- `LegacyTaskMailMimeMessageFactory`
- `LegacyTaskMailNewTaskMimeMessageFactory`
- `LegacyTaskMailMimeMessageSender`
- `StorageBackedTaskMailDestinationAddressProvider`
- `LegacyTaskMailSenderAccountSource`
- `FileBackedUnifiedMessageRepository`
- `FileBackedMessageSyncStateRepository`

这些类如果继续存在，必须进入新的 package 与职责边界，不能再以旧 action-specific 名称暴露给 UI 和 use case。

## 4. 新 package 建议

本轮先不改 Gradle module graph，只在 `feature:taskmail:internal` 内收敛 package。

建议新增：

- `...internal.domain.operation`
- `...internal.domain.event`
- `...internal.domain.projection`
- `...internal.domain.controlplane`
- `...internal.data.gateway.mail`
- `...internal.data.gateway.relay`
- `...internal.data.ledger`
- `...internal.data.eventstore`
- `...internal.data.projection`
- `...internal.data.probe`

建议进入删除/迁移路径的旧 package：

- `...internal.data.relay` 中 action-specific sender 部分
- `...internal.data.direct`
- `...internal.domain.newtask` 中 direct result / sender 相关部分
- `...internal.domain.sessionaction` 中 direct result / sender 相关部分
- `...internal.domain.projectsync` 中 direct result / sender 相关部分
- `...internal.domain.usecase` 中 action-specific direct/fallback 壳

## 5. `TaskMailModule` 的目标形态

当前 `TaskMailModule` 的问题不是 Koin 本身，而是 DI graph 暴露了太多 action-specific wiring。

目标形态下，DI 应围绕 4 个簇组织：

### 5.1 Control Plane Core

- `TaskMailDraftCompilerRegistry`
- `ExecuteTaskMailOperation`
- `TaskMailRecoveryCoordinator`

### 5.2 Gateways

- `RelayTaskMailGateway`
- `MailTaskMailGateway`
- `MailIngressGateway`
- `RelayIngressGateway`

### 5.3 Stores

- `TaskMailOperationLedgerRepository`
- `TaskMailEventStoreRepository`
- `TaskMailProjectionRepository`
- `MailIngressCheckpointRepository`

### 5.4 Projection & UI Read

- `TaskMailProjectorEngine`
- `ObserveTaskMailProjection`
- `GetTaskMailProjection`

ViewModel 不再直接拿：

- `SendTaskMailDirectNewTask`
- `SendTaskMailDirectSessionAction`
- `SendTaskMailNewTask`
- `SendTaskMailReply`
- `RequestTaskMailProjectSync`
- `GetLatestTaskMailNewTaskSendRecord`
- `GetLatestTaskMailSessionActionSendRecord`
- `GetLatestTaskMailProjectSyncResult`

## 6. 分支内实施顺序

这是“一次 cutover”，但在实现分支内仍有推荐顺序：

1. 先加 `operation / event / projection` 模型与 file-backed store。
2. 再把 mail ingress 改成 `event store` 写入者。
3. 再把 relay ingress 改成 `event store` 写入者。
4. 建 `TaskMailProjectorEngine`，先让 workspace/detail/project-sync 都能从 projection 读。
5. 建 `ExecuteTaskMailOperation`，让三个 UI surface 全部改走统一发送。
6. 删旧 sender、旧 send record、旧 special reader、旧 screen-scoped sidecar。

## 7. 禁止留下的中间态

为了避免“重构后还有四套半系统”，本轮明确禁止以下中间态残留到主干：

- 长期保留 `RunTaskMailDirectOrFallback`
- 保留 action-specific relay sender 作为新 gateway 的薄包装
- 用 `CompositeProjectSyncResultReader` 把 direct result 和 mailbox `[SYNC]` 临时缝在一起
- 继续让 `TaskSessionDetailViewModel` 读取 direct overlay
- 继续让 `TaskProjectSyncViewModel` 持有 `canRetryWithMail` 这类旧 mail-result 心智字段
- 继续让 send record 与 operation ledger 双写长期共存

分支内允许短期适配层，但在 merge 前必须删掉。

## 8. 当前结论

这次 cutover 的本质不是把现有类改名，而是重建脊柱：

- `TaskMailOperation` 取代 action-specific send model
- `TaskMailEvent` 取代 mail/direct 各自的局部事实
- `TaskMailProjection` 取代 workspace/detail/project-sync 各自读法
- `TaskMailOperationLedgerRepository` 取代 send record
- `RelayTaskMailGateway` / `MailTaskMailGateway` 取代动作分裂的 transport façade

如果不按本文的收敛边界删除旧旁支，新的 unified control plane 最终只会变成旧系统外面再包一层壳。
