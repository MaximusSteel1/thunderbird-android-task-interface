# TaskMail Next Session Handoff 2026-03-23

## 当前决定

- Android 侧第一批 `transport_probe` 基础设施已经落地，目标是先把 Android -> relay/PC 的 transport 可观察性做实，再继续推进 `unified control plane` 大重构。
- 这批实现仍然是 debug / infrastructure slice，不改 `new_task`、`reply/status`、`[SYNC]` 的业务执行主链。

## Read First

- `docs/taskmail/planning/android/taskmail-unified-control-plane-architecture-v0.1.md`
- `docs/taskmail/planning/android/taskmail-unified-control-plane-cutover-map-v0.1.md`
- `docs/taskmail/planning/android/taskmail-android-pc-control-artifact-contract-v0.1.md`
- `docs/taskmail/planning/android/taskmail-android-pc-communication-development-conditions-v0.1.md`
- `docs/taskmail/planning/android/taskmail-transport-probe-payload-contract-v0.1.md`
- `docs/taskmail/planning/android/taskmail-transport-observability-harness-v0.1.md`
- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-MAIL-RULES.md`
- `E:\projects\mail_based_task_manager\docs\plans\taskmail_android_pc_control_artifact_companion_note_v0.1.md`
- `E:\projects\mail_based_task_manager\docs\plans\taskmail_transport_probe_payload_companion_note_v0.1.md`

## 下一步

1. 在 PC / VPS 侧补最小 `transport_probe` 闭环：`command -> command_ack -> result/event` 与 `/v1/files` 样本上传下载。
2. 用 Android debug relay screen 做一次真实 probe smoke，确认 artifact 目录、relay result 观察窗、PC 侧回包都按合同对齐。
3. probe 稳定后，再开始把 `operation/event/projection` 壳层接入现有 TaskMail 主链。

## 本次会话做了什么

- 新增 generic relay inbound frame：`event` / `result`，并把 relay client 扩展为统一暴露 `serverEvents` 与 `serverResults`。
- 新增 file-backed `transport_probe` artifact/event store，会写 `manifest.json`、`events.jsonl`、`timeline.json`、`timeline.md`。
- 新增 `RelayTaskMailTransportProbeSender` 与 `SendTaskMailTransportProbe`，支持 direct probe 发包、短窗口等待 result、落本地 artifact。
- 在 debug relay screen 增加 probe payload 输入、`Send direct probe` 入口，以及最近一次 probe summary / artifact path 展示。
- 补齐对应单测，并把旧 relay fake client 对齐到新的 generic frame 通道。

## 验证

- 已运行：
  - `.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest :feature:taskmail:internal:detekt :feature:taskmail:internal:lintDebug --console=plain`
- 未运行：
  - `connectedAndroidTest`
  - 真实设备 / PC / VPS 联调
