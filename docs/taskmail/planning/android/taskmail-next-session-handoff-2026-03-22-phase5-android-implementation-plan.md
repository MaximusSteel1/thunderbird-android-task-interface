# TaskMail Next Session Handoff - 2026-03-22 - Phase 5 Android Implementation Plan

## 当前决策

- 相邻 PC 侧的 shared planning-layer contract 已经落地：
  - `E:\projects\mail_based_task_manager\docs\plans\post_creation_session_action_contract_v1.md`
- Android 侧当前不再停留在“等待 PC 侧回答”或“继续扩 prerequisites 文档链”。
- Android 侧已经完成 Batch A / Batch B / Batch C，下一步应进入 Batch D 与 closeout 评估，而不是直接进入实现 rollout。
- 第一批 scope 固定为：
  - `current-session plain reply`
  - `current-session /status`
- 第一批先前的首个 blocker 已明确并已关闭：
  - Batch A 之前，detail route / `TaskSessionKey` 尚未稳定提供 canonical `workspace_id + session_id`

## Read First

- `docs/taskmail/planning/android/taskmail-phase5-reply-status-pc-alignment-readout-v0.1.md`
- `docs/taskmail/planning/android/taskmail-phase5-reply-status-android-implementation-plan-v0.1.md`
- `docs/TASKMAIL-MAIL-RULES.md`
- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `E:\projects\mail_based_task_manager\docs\plans\post_creation_session_action_contract_v1.md`
- `feature/taskmail/api/src/main/kotlin/net/thunderbird/feature/taskmail/api/TaskMailRoute.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/model/TaskSessionKey.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/TaskSessionDetailViewModel.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/usecase/RunTaskMailDirectOrFallback.kt`

## 本次代码与验证

- Android 生产代码改动：有
- Android 测试代码改动：有
- planning / handoff 文档改动：有
- 新的 Gradle 验证：有
- 新的设备验证：无

## 本轮收口了什么

- shared contract 已不再是待办，而是已存在的跨仓 planning-layer artifact
- Android 侧已把“继续追 PC 问题”收口为“开始 Android 实现规划”
- Batch A 已完成：
  - `TaskMailRoute.SessionDetail` 现已显式携带 `workspaceId`
  - `TaskSessionKey` 现已扩展为 `workspaceId + sessionId + threadId`
  - workspace 列表到 detail 导航现已透传 canonical `workspaceId`
  - repository / snapshot / cache 对 legacy 无 `workspaceId` key 已补兼容读取
- Batch B 已完成：
  - `RelayTaskMailDirectSessionActionSender` 已按 shared contract 覆盖
    `current-session plain reply` 与 `current-session /status`
  - `SendTaskMailDirectSessionAction` 已接入 DI
- Batch C 已完成：
  - `TaskSessionDetailViewModel` 现已对 plain reply 与 `/status` 做 canonical-target guarded direct gating
  - quick answer / structured reply / paused `/resume` / attachment continuation 继续留在 mail path
- 第一批 scope、非目标、后续分批顺序都已写入
  `docs/taskmail/planning/android/taskmail-phase5-reply-status-android-implementation-plan-v0.1.md`
- 本轮 focused 验证已通过：
  - `.\gradlew.bat :feature:taskmail:internal:detekt`
  - `.\gradlew.bat :feature:taskmail:internal:lintDebug`
  - `.\gradlew.bat :feature:taskmail:api:testDebugUnitTest`
  - `.\gradlew.bat :feature:launcher:testDebugUnitTest`
  - focused `:feature:taskmail:internal:testDebugUnitTest`
- 仍需注意的环境阻塞：
  - 全量 `:feature:taskmail:internal:testDebugUnitTest` 中的 `TaskMailValidationRunner`
    依赖相邻 PC 仓的 `scripts/test_fetch_latest_100.json`
  - 当前该文件缺失，因此不宜把该失败误判为本轮 Phase 5 guarded slice 回归

## 下一步最合理顺序

1. 进入 Batch D：把 closeout 字段规划、focused evidence 读法、以及 live / mailbox closeout 边界收口。
2. 在 authority / ledger / handoff 中明确：当前 guarded direct lane 已实现，但还不是 current protocol / direct-default。
3. 如果后续开始 live closeout，优先验证：
   - accepted direct `reply` 是否稳定收敛到 canonical mail reply outcome
   - accepted direct `/status` 是否稳定生成 canonical `[STATUS]` mail
   - same-run bind 是否能沿既有 `request_id`-first 读法成立

## 当前明确不做

- 不直接开始 live rollout
- 不把 quick answer / `Answers:` / `/resume` / attachment continuation 并进第一批
- 不把 `TaskSessionDetailViewModel` 在缺少 canonical `workspace_id + session_id` 时接到 direct seam
- 不把 shared planning-layer contract 误写成 current protocol
