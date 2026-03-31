# TaskMail Session 流式输出接入 Checklist v0.1

## 文档信息

- 日期：2026-03-30
- 状态：implementation checklist
- 依赖方案文档：`docs/taskmail/planning/android/taskmail-session-live-output-via-projection-plan-v0.1.md`

## 0. 目标与范围

本 checklist 对应的目标是：

- Android detail 在 `Running` 时，通过 `session-snapshot / session-updates` 实时显示当前 assistant 流式文本
- Android 继续只读 projection，不直接消费 raw `/pc-control output_chunk`
- raw `output_chunk` 保留为 relay lower-layer truth / replay owner

本轮不做：

- Android 直连 raw `/pc-control`
- Android 自己维护 `stream_id + after_seq`
- 把 raw chunk 直接映成 detail timeline 多条卡片

## 0.1 2026-03-30 默认决策

以下默认决策优先于本文后续旧口径：

- `live_output` 在 projection store 中按 `session_key` 只保留一行；`command_id / stream_id / last_seq` 只作为 relay 内部 owner 元数据，不进入 Android-facing current contract
- 聚合文本必须基于 command store 重建：优先最新完整 `text`，再拼后续连续 `delta`；遇到 gap 就停在最后连续 `seq`
- closeout 走两阶段：terminal `result` 先把 `live_output` 冻结为 `completed`；等稳定 round/result materialize 到 snapshot 后再清空
- `session_snapshot.live_output` app-facing wire shape 固定为 `object | null`，缺失时返回 `null`，不走“null 或省略”二选一
- 首轮 Android-facing 字段只冻结：
  - `text`
  - `updated_at`
  - `status`
- `session-updates` 继续沿用当前 snapshot payload 指纹去重推送；本期不要求 live output update 驱动 session projection version 前进
- `last_progress_at` 不回写 session row，由 snapshot builder 动态取 `max(session.last_progress_at, live_output.updated_at)`
- Android cache codec 必须同步更新 `TaskSessionDetailJsonCodec`
- `ActiveRun` 下状态卡 supporting text 和 `Latest progress` 都要优先读 `liveOutputText`

---

## 1. 阶段 A：冻结 Android-facing contract

### 1.1 relay / PC 仓库文档

owner：

- `E:\projects\mail_based_task_manager`

目标：

- 明确 `session_snapshot.live_output` 的 wire shape
- 明确它与 `output_chunk`、`timeline_items`、`history_rounds` 的关系

文件：

- `docs/current/android_session_snapshot_facade_contract.md`
- `docs/current/android_session_updates_facade_contract.md`
- `docs/current/README.md`

任务：

- 在 `session_snapshot` 字段说明中新增 `live_output`
- 冻结字段：
  - `text`
  - `updated_at`
  - `status`
- 明确：
  - `live_output` 是 Android-facing 聚合输出，不等于 raw chunk 列表
  - `timeline_items` 首轮不承接 raw streaming transcript
  - `session-updates` 的 `session_snapshot` 与 HTTP `session-snapshot` 继续同构

验收：

- current contract 文档里能单独回答“Android 运行态流式文本从哪里读、字段长什么样”

---

## 2. 阶段 B：relay projection store owner row

### 2.1 projection store schema

owner：

- `E:\projects\mail_based_task_manager`

文件：

- `mail_runner/relay_server/projection_store.py`
- 如有 schema 文档同步：
  - `docs/plans/vps_relay_projection_store_schema_v0.1.md`

任务：

- 新增 session-scoped live output durable row
- 推荐最小字段：
  - `session_key`
  - `workspace_id`
  - `session_id`
  - `command_id`
  - `stream_id`
  - `last_seq`
  - `text`
  - `status`
  - `updated_at`

实现要求：

- 只保存 Android-facing 聚合文本
- 不复制 raw chunk 全量历史
- 对同一 `session_key` 单行覆盖更新

验收：

- projection store 可以独立返回当前 session live output owner row

建议测试：

- `tests/test_relay_projection_store.py`
- `tests/test_android_projection_store_facade.py`

### 2.2 runtime 聚合写入

文件：

- `mail_runner/relay_server/pc_control_runtime.py`
- 视实际 helper 拆分情况，可能新增 live output projector helper

任务：

- 在 `handle_output_chunk()` 成功写 command store 后，增量更新 session live output projection
- 聚合规则：
  - 只聚合同一 `command_id + stream_id`
  - 优先使用 chunk 的最新完整 `text`
  - 再按连续 `seq` 追加后续 `delta`
  - 遇到 gap 就停在最后连续 `seq`
- 当文本或 `last_seq` 实际变化时 upsert live output row

注意：

- 不要让 Android snapshot builder 去旁读 command store
- owner write path 必须落在 projection store

验收：

- 新 `output_chunk` 到来后，projection store 能读到更新后的聚合 live output

建议测试：

- `tests/test_pc_control_plane_client.py`
- `tests/test_pc_projection_publisher.py`
- 新增或扩展 runtime/projection 相关测试

### 2.3 closeout 清理

文件：

- `mail_runner/relay_server/pc_control_runtime.py`
- 可能涉及 `android_projection_store_facade.py` / session projection builder

任务：

- 同一 command/session 收到 terminal `result` 后：
  - 先冻结 live output 为 `completed`
  - 若稳定结果已 materialize，再清理 live output row

验收：

- detail 不会同时长期显示一份 live output 和一份完全相同的 stable result

建议测试：

- `tests/test_android_session_snapshot_facade.py`
- `tests/test_android_session_read_live.py`

---

## 3. 阶段 C：session snapshot / session updates 接入 live_output

### 3.1 snapshot builder

owner：

- `E:\projects\mail_based_task_manager`

文件：

- `mail_runner/relay_server/android_session_snapshot_facade.py`
- `mail_runner/relay_server/android_projection_store_facade.py`
- 如 builder 走 phase3 emitter / facade helper，也可能涉及：
  - `mail_runner/relay_server/phase3_emitter.py`

任务：

- 在 `session_snapshot` payload 中增加 `live_output`
- 读取来源改为 projection store 的 live output owner row
- 保持当 `live_output` 不存在时固定返回 `null`

验收：

- `GET /v1/android/session-snapshot` 能返回同构的 `live_output`

建议测试：

- `tests/test_android_session_snapshot_facade.py`
- `tests/test_android_session_read_surfaces.py`

### 3.2 session-updates push

文件：

- `mail_runner/relay_server/app.py`
- `mail_runner/relay_server/android_projection_store_facade.py`

任务：

- 当 live output row 更新并改变 `session_snapshot` payload 指纹时，`WS /v1/android/session-updates` 推送新 snapshot
- 不新增第二套 push 通道
- 继续保持 `session-updates` 与 `session-snapshot` payload 同构

验收：

- detail 打开期间，live output 增长会推动新的 `session_snapshot` push

建议测试：

- `tests/test_android_session_read_live.py`
- `tests/test_android_session_read_surfaces.py`

---

## 4. 阶段 D：Android parser / domain

owner：

- `E:\projects\android_task_manager`

### 4.1 domain model

文件：

- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/model/TaskSessionHistorySnapshot.kt`

任务：

- 新增 `TaskSessionLiveOutputSnapshot`
- 在 `TaskSessionHistorySnapshot` 增加：
  - `liveOutput: TaskSessionLiveOutputSnapshot? = null`

建议字段：

- `text`
- `updatedAt`
- `status`

验收：

- Android domain 层能无损承接 `session_snapshot.live_output`

### 4.2 facade parser

文件：

- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/data/facade/OkHttpTaskSessionUpdatesFacadeRepository.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/data/facade/OkHttpTaskSessionHistorySnapshotFacadeRepository.kt`

任务：

- 解析 `session_snapshot.live_output`
- 更新 snapshot response payload model
- 映射到 `TaskSessionLiveOutputSnapshot`

验收：

- HTTP snapshot / WS updates 都能解析同一字段

建议测试：

- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/data/facade/OkHttpTaskSessionUpdatesFacadeRepositoryTest.kt`
- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/data/facade/OkHttpTaskSessionHistorySnapshotFacadeRepositoryTest.kt`

### 4.3 snapshot -> detail mapper

文件：

- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/data/facade/TaskSessionHistorySnapshotDetailMapper.kt`

任务：

- 把 `liveOutput` 映到 `TaskSessionDetail`
- 推荐在 `TaskSessionDetail` 领域模型上新增 session-scoped 字段，而不是把它挤进 timeline

建议新增字段：

- `liveOutput: TaskSessionLiveOutput?`

验收：

- session snapshot 应用后，detail domain 能直接拿到运行态 live output

建议测试：

- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/domain/usecase/GetTaskSessionDetailTest.kt`
- 视需要新增 mapper 专项测试

---

## 5. 阶段 E：Android UI 承接

owner：

- `E:\projects\android_task_manager`

### 5.1 ui state

文件：

- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/TaskSessionDetailUiState.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/TaskSessionDetailViewModel.kt`

任务：

- 在 `TaskSessionDetailUiState` 增加运行态 live output 字段
- `toUiState()` 时把 domain live output 映到 UI state
- `statusTimingRows()` 继续直接读取 detail 上游已经合成好的 `lastProgressAt`
- 同步更新 `TaskSessionDetailJsonCodec`

验收：

- UI state 层能明确区分：
  - stable result
  - 当前运行中的 live output

### 5.2 内容渲染

文件：

- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/TaskSessionDetailContent.kt`

任务：

- `ActiveRun` 下的 `Latest progress` 卡优先显示 `liveOutputText`
- `ActiveRun` 下状态卡 supporting text 也优先显示 `liveOutputText`
- `liveOutputText` 为空时，fallback 到现有 `recentContext.latestAssistantMessage`
- 不把首轮 live output 渲染成多条 process/timeline item

验收：

- Running 场景下用户能直接看到当前 assistant 流式文本块持续增长

建议测试：

- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/TaskSessionDetailScreenKtTest.kt`
- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/TaskSessionDetailViewModelTest.kt`

---

## 6. 阶段 F：回归与验收

### 6.1 relay / PC 仓库建议回归

优先：

- `tests/test_pc_control_plane_client.py`
- `tests/test_relay_projection_store.py`
- `tests/test_android_session_snapshot_facade.py`
- `tests/test_android_session_read_live.py`
- `tests/test_android_projection_store_facade.py`

目标：

- output chunk 继续增量发送
- projection store 能聚合 live output
- session-snapshot / session-updates 能返回并推送 `live_output`
- result closeout 后不重复

### 6.2 Android 仓库建议回归

优先：

- `.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest --tests "*OkHttpTaskSessionUpdatesFacadeRepositoryTest" --tests "*OkHttpTaskSessionHistorySnapshotFacadeRepositoryTest" --tests "*TaskSessionDetailViewModelTest" --tests "*TaskSessionDetailScreenKtTest" --console=plain`

目标：

- snapshot / updates 解析 live_output
- Running detail 优先显示 live output
- 无 live_output 时旧 fallback 不回归

---

## 7. 实施顺序建议

按风险从低到高、从 owner 收口到 consumer 收口，建议顺序固定为：

1. 冻结 contract 文档
2. relay projection store live output owner row
3. runtime 聚合写入 + closeout
4. snapshot / session-updates 暴露 `live_output`
5. Android parser / domain / mapper
6. Android UI 承接
7. 跨仓 focused validation

不建议顺序：

- 先改 Android UI，再去猜 relay 最终字段
- 先把 raw chunk 硬塞进 timeline，再回头清理重复

---

## 8. 开工前的默认决策

如果没有新的产品约束，本 checklist 默认采用以下决策：

- Android-facing 首轮字段名：`live_output`
- Android 首轮只显示聚合文本，不显示 `stream_id / last_seq`
- raw `output_chunk` 继续保留 lower-layer replay 语义
- `timeline_items` 首轮不承接 chunk transcript
- turn terminal closeout 后，live output 让位于稳定 result

以上默认决策如需改变，应先更新：

- 方案文档
- current contract 文档

再开始代码改动。
