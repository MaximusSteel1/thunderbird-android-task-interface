# TaskMail Next Session Handoff 2026-03-30

## 本轮已完成

- PC/relay 侧已按 projection-first 接入 `live_output`
  - `projection_store` 新增 session-scoped live output owner row
  - `pc_control_runtime` 在 `output_chunk` 后基于 command store 重建聚合文本
  - terminal `result` 先冻结为 `completed`，稳定 round/result materialize 后再清空
  - `session-snapshot / session-updates` 已对外暴露 `live_output`
- Android 侧已接入 `live_output`
  - facade parser 解析 `session_snapshot.live_output`
  - `TaskSessionDetail` 与 cache codec 已承接 `liveOutput`
  - `TaskSessionHistorySnapshotDetailMapper` 会把 snapshot `live_output` 映到 detail
  - ActiveRun 下 `Latest progress` 与状态卡 supporting text 都优先显示 `liveOutputText`
- 文档已收口
  - PC current contract 已补 `live_output`
  - Android plan/checklist 已补 2026-03-30 默认决策

## 当前默认决策

- projection store 中 `live_output` 按 `session_key` 只保留一行
- `command_id / stream_id / last_seq` 只作为 relay 内部元数据，不进 Android-facing current contract
- app-facing `session_snapshot.live_output` 固定是 `object | null`
- 首轮只暴露：
  - `text`
  - `updated_at`
  - `status`
- `session-updates` 继续沿用 snapshot payload 指纹去重推送，不要求 version-driven
- `last_progress_at` 由 snapshot builder 动态取 `max(session.last_progress_at, live_output.updated_at)`

## 已验证

- Android
  - `.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest --tests "*OkHttpTaskSessionHistorySnapshotFacadeRepositoryTest" --tests "*OkHttpTaskSessionUpdatesFacadeRepositoryTest" --tests "*TaskSessionDetailJsonCodecTest" --tests "*TaskSessionDetailViewModelTest" --tests "*TaskSessionDetailScreenKtTest"`
- PC/relay
  - `.\.venv\Scripts\python.exe -m pytest tests/test_relay_projection_store.py tests/test_relay_server_pc_control_runtime.py tests/test_android_projection_store_facade.py tests/test_android_session_read_surfaces.py -q`

## 下轮可继续

1. 真机/联调验证 running session 的 live output 是否按预期持续刷新。
2. 评估是否需要在 Android 运行态增加更明确的 `streaming / completed` 文案。
3. 如果后续需要 richer transcript，再讨论是否在 debug/advanced 模式里暴露内部 `stream_id / last_seq`。
