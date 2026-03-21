# TaskMail Next Session Handoff - 2026-03-22 - Phase 3 Placeholder Session Filter

## 当前结论

- `2026-03-22` 的 live 复验里，`phase3-workspace-refresh-A73D2C9F11` 在发送新任务后先出现了可点击的 `Unknown` session card。
- 用户保持 detail 打开时，后续 QUESTION mail 已经到达，但 detail 没有自然刷新；用户手动退回 workspace 并刷新后，workspace 能看到 `WaitingUser`。
- 设备本地 `files/taskmail/taskmail_session_details.json` 进一步坐实了根因：
  同一个 title 同时存在两条 placeholder detail（`threadId=201`、`threadId=202`，都是 `Unknown`），后面又另外出现 canonical session。
- 因此这次 detail 没刷新的直接原因更像是：
  用户打开的是“仅本地新任务请求生成的 placeholder session”，而不是后续真正承载 QUESTION / DONE 状态链的 canonical session。

## 本次代码改动

- 在 `TaskMailSessionProjector` 里过滤“仅本地新任务请求、且还没有任何 canonical session identity”的 placeholder session。
  - 条件收窄到新任务 body 形态：
    `Repo:` 起头，随后是可选的 `Workdir/Timeout/Mode/Profile/Permission`，再有空行和 `Task:` 段。
  - 不会误伤现有的 thread-id fallback case。
- 把 `FileBackedTaskSessionDetailRepository` 的 `STORAGE_VERSION` 从 `2` 升到 `3`。
  - 目的：让旧设备上的 placeholder snapshot 在升级后被丢弃。
  - 下一次 `SyncTaskMailCache` 会因为“有 cached messages 但没有 stored session details”走一次 full rebuild。
  - 结合新的 projector 过滤，旧 placeholder session 不会再被重建回来。
- 补了 repository 单测，锁定：
  - request-only placeholder 不再出现在 workspace summaries
  - placeholder request 与 canonical QUESTION mail 共存时，workspace 只保留 canonical session

## 已完成验证

- `.\gradlew.bat --no-daemon :feature:taskmail:internal:testDebugUnitTest --tests "net.thunderbird.feature.taskmail.internal.data.DefaultTaskMailRepositoryTest" --tests "net.thunderbird.feature.taskmail.internal.data.cache.FileBackedTaskSessionDetailRepositoryTest" --console=plain`
- `.\gradlew.bat --no-daemon :feature:taskmail:internal:detekt :feature:taskmail:internal:lintDebug :feature:taskmail:internal:testDebugUnitTest --tests "net.thunderbird.feature.taskmail.internal.data.DefaultTaskMailRepositoryTest" --tests "net.thunderbird.feature.taskmail.internal.data.cache.FileBackedTaskSessionDetailRepositoryTest" --console=plain`

补充说明：

- 模块级 `:feature:taskmail:internal:testDebugUnitTest` 全量跑法仍会撞到仓库内现有的 `TaskMailValidationRunner` 外部文件依赖：
  `E:\projects\mail_based_task_manager\scripts\test_fetch_latest_100.json`
- 这不是本次 placeholder 修复引入的新失败；本次改动相关的 repository / cache 测试已经单独通过。
- `:feature:taskmail:internal:spotlessCheck` 仍被模块 `build.gradle.kts` 的现有换行风格问题拦住，本次没有顺手改 unrelated formatting。

## 下次优先事项

1. 重装或冷启动 debug 包后，重新做 `phase3-workspace-refresh-*` 真机复验。
2. 重点确认发送新任务后是否还会先出现 `Unknown` placeholder card。
3. 如果 placeholder 已消失，再复验：
   - QUESTION 到达后 detail 是否自然进入 `WaitingUser`
   - reply 后 detail 是否继续自然推进到 `Done`
   - workspace 从 detail 返回后是否也能自然刷新
4. 如果 workspace 仍需要手动刷新，再继续沿 `TaskWorkspaceViewModel` / foreground refresh 路径排查；如果 detail 仍失败，则再看是否还有 canonical route / navigation key 问题残留。

## Read First

- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/TASKMAIL-DEBUG-VALIDATION.md`
- `docs/TASKMAIL-MAIL-RULES.md`
- `docs/taskmail/planning/android/taskmail-next-session-handoff-2026-03-22-phase3-durable-mail-closeout.md`

## 本次是否改代码 / 验证

- 已改生产代码：是
- 已改测试代码：是
- 已改文档：是
- 已做代码侧验证：是
- 已做新的真机 closeout：否
