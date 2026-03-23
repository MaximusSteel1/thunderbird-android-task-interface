# TaskMail Next Session Handoff - 2026-03-22 - Phase 4 New Task Durable Evidence

## 当前决策

- Phase 4 当前仍只围绕唯一 covered flow `new task` 推进，不把 `reply`、`/status` 或更广的 read-side direct transport 提前拉进实现范围。
- Android 侧现在已经把本次 `new task` 发送的 direct-or-fallback 结果收敛成 machine-readable
  `TaskMailDirectSendEvidence`，并且把 latest record 按 `senderAccountId` 落到本地持久化。
- `TaskNewTaskViewModel` 现在会在 sender account 解析或切换后重新加载该账号的 latest evidence，因此这批
  Phase 4 parity 证据不再只活在当前 screen instance 的内存里。
- 这批 closeout 仍只是 Android 本地 durable evidence；它还没有自动回填进三份共享 Phase 4 工件，也还没有新增
  live-device 证据去证明“重进页面后 review persisted evidence”。

## Read First

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/TASKMAIL-MAIL-RULES.md`
- `docs/taskmail/planning/android/taskmail-next-development-plan-v0.2.md`
- `docs/taskmail/planning/android/taskmail-phase4-dual-stack-boundary-freeze-v0.1.md`
- `docs/taskmail/planning/android/phase4_dual_stack_parity_checklist.md`
- `docs/taskmail/planning/android/phase4_mismatch_ledger.md`
- `docs/taskmail/planning/android/phase4_rollback_trigger_note.md`

## 本次代码与验证

- 已改生产代码：是
- 已改测试代码：是
- 已更新 authority 文档：是
- 已做本地验证：是
- 通过的命令：
  - `.\gradlew.bat -Dkotlin.compiler.execution.strategy=in-process :feature:taskmail:internal:testDebugUnitTest --tests "net.thunderbird.feature.taskmail.internal.domain.usecase.RunTaskMailDirectOrFallbackTest" --tests "net.thunderbird.feature.taskmail.internal.ui.newtask.TaskNewTaskViewModelTest" --tests "net.thunderbird.feature.taskmail.internal.data.cache.FileBackedTaskMailNewTaskSendRecordRepositoryTest"`
  - `.\gradlew.bat -Dkotlin.compiler.execution.strategy=in-process :feature:taskmail:internal:detekt :feature:taskmail:internal:lintDebug`
- 未做的验证：
  - repo-wide `build` / `assemble` / `lint` / `detekt`
  - `connectedAndroidTest`
  - 新的 Phase 4 durable-evidence live-device smoke

## 当前已知边界 / 小坑

- 这批持久化记录是按 `senderAccountId` 读取 latest record，不是按 thread 或 session truth 读取；这是有意保持在
  `new task` pre-session 阶段，不要误塞进 session snapshot truth layer。
- `TaskNewTaskViewModel` 在继续堆叠 Phase 4 行为时已经碰到 `detekt` 的 `TooManyFunctions` 阈值；这轮通过
  `@Suppress("TooManyFunctions")` 沿用当前结构，不应把它理解成可以无限继续往同一个 ViewModel 塞逻辑。

## 下一步最合理顺序

1. 把当前 durable evidence 映射到三份共享 Phase 4 工件的稳定填写字段：
   `packet_ack.accepted`、`receipt_id`、可选 `transport_message_id`、fallback / hard rejection 分类，以及
   `thread / mail outcome`。
2. 做最小 live/manual closeout，至少各补一条 `direct accepted`、`fallback required`、`hard-stop` 样本，并确认
   这些 evidence 在重进页面后仍可 review。
3. 只有在 evidence consumption 与 live rollback trigger 都收紧后，才评估 `new task` 的 direct-default
   switch gate 是否足够可靠。
