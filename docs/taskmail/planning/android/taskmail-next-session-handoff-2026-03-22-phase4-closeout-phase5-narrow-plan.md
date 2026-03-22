# TaskMail Next Session Handoff - 2026-03-22 - Phase 4 Closeout / Phase 5 Narrow Plan

## 当前决策

- 当前这条 Android / PC / VPS 主线，Phase 4 应收口为 `new_task parity baseline`，不要再靠补旧样本扩大它的解释范围。
- `reply`、`/status`、更宽的 direct read-side transport 仍不进入当前 covered flow。
- Phase 5 Slice A / B 的文档冻结现在已补到 shared artifacts 本体：
  - 日常 `daily_closeout_bundle`
  - `direct accepted` 行的 same-run bind 顺序
  - evidence gap 与 mismatch / rollback 的分界
- Phase 5 Slice C 的显式结论现已落成：
  - `new_task` switch-review = `ready_for_direct_default_review`
- 后续 Android 窄代码判断也已补完 activation verdict：
  - 当前 formal-host `new_task` 的 direct business seam 仍天然限定在
    `TaskNewTaskViewModel -> RunTaskMailDirectOrFallback -> SendTaskMailDirectNewTask`
  - `reply` / `/status` 仍继续走 `TaskSessionDetailViewModel -> SendTaskMailReply` 的 mail-only 发送边界
  - 因此当前不需要再单独新增 `new_task` flow-scoped activation / config change
- PC review 提到的两处 shared-artifact 风险现在已收口：
  - 不再把 `raw_004.json` 写成 fallback / terminal mail 的稳定锚点
  - PC Layer 1 `canonical_summary.json` 字段口径现已补齐 `ingress_message_id` / `terminal_mail_subject`，对应 pytest 断言也已补上
- current positive same-run rows 继续承认为：
  - `thread_083` direct accepted
  - `thread_093` fallback
  - `thread_094` fallback replacement sample
  - `thread_095` fresh direct accepted after relay re-provision
- `thread_097` 已补上 freeze 之后的第一条 fresh closeout rerun：
  - `runs/20260322_184156_afc8/taskmail_daily_closeout_bundle.json` 已闭环 Android latest send evidence、Android terminal summary、PC canonical outcome、PC terminal mail
  - `same_run_bind.effective_bind_level = transport_message_id`
  - helper note = `android_request_id_missing`
- `thread_098` 又补上了安装当前 formal-host build 之后的 fresh closeout：
  - Android latest send record 已带出 `requestId = req_2a1e0790bdd54194bdce17e96c378e5e`
  - `runs/20260322_190954_7e1f/taskmail_daily_closeout_bundle.json` 记录 `same_run_bind.effective_bind_level = request_id`
  - helper notes 为空
- `thread_084` 继续只读作 historical retained-artifact gap，不再当当前 blocker。
- shared artifacts 继续按：
  - `parity checklist`
  - `mismatch ledger`
  - `rollback trigger`
  顺序消费。
- 当前还不能把 `new_task` 宣布为 `direct-default`；mail fallback 仍必须保持真实可执行。
- 当前 Android 侧的下一步也不再是“补一个最小 activation/config 实现”；更近一步只是把 `no additional activation/config`
  verdict 同步回 authority / handoff，并继续把 `reply` / `/status` 留在 scope 外。
- 下一阶段主线不是扩 flow，而是 Phase 5 第一刀：
  - 基于 `thread_097` / `thread_098` 已闭环的 frozen closeout workflow
  - guarded `new_task` direct-default review / decision note 已落地
  - guarded rollout / activation note 也已落地
  - 不把 `reply` / `/status` 提前拉进同一评审
- PC 侧现在已有最小 closeout helper：
  - `E:\projects\mail_based_task_manager\scripts\build_taskmail_closeout_bundle.py`
  - 它会优先消费 `canonical_summary.json`，按 `terminal_mail_message_id` / terminal subject 定位 `mail/raw_*.json`
  - 若提供 Android `taskmail_new_task_send_records.json`，会固定给出 `request_id` -> `transport_message_id / ingress_message_id` -> `last_summary` bind readout

## Read First

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/TASKMAIL-DEBUG-VALIDATION.md`
- `docs/TASKMAIL-MAIL-RULES.md`
- `docs/taskmail/planning/android/phase4_dual_stack_parity_checklist.md`
- `docs/taskmail/planning/android/phase4_mismatch_ledger.md`
- `docs/taskmail/planning/android/phase4_rollback_trigger_note.md`
- `docs/taskmail/planning/android/taskmail-phase4-closeout-and-phase5-narrow-plan-v0.1.md`
- `docs/taskmail/planning/android/taskmail-phase5-new-task-switch-review-v0.1.md`
- `docs/taskmail/planning/android/taskmail-phase5-new-task-direct-default-review-decision-v0.1.md`
- `docs/taskmail/planning/android/taskmail-phase5-new-task-direct-default-rollout-activation-note-v0.1.md`
- `docs/taskmail/planning/android/taskmail-next-development-plan-v0.2.md`
- `docs/taskmail/planning/android/taskmail-next-session-handoff-2026-03-22-phase4-new-task-matrix-reconciliation.md`
- `E:\projects\mail_based_task_manager\docs/current/mail_protocol.md`
- `E:\projects\mail_based_task_manager\docs/plans/phase4_dual_stack_parity_plan.md`
- `E:\projects\mail_based_task_manager\scripts\build_taskmail_closeout_bundle.py`

## 本次代码与验证

- 已改生产代码：是（此前已落地 PC helper；本轮 Android 无代码改动）
- 已改测试代码：是（此前已补 PC focused pytest；本轮无新增测试改动）
- 已更新 planning / handoff 文档：是
- 已做新的本地验证：是（此前 PC focused pytest，本轮又补了 `installFullDebug` + 两条 formal-host fresh closeout）

## 本轮 Android 补充判断

- 已改 Android 生产代码：否
- 已改 Android 测试代码：否
- 已更新 Android authority / handoff 文档：是
- 已做新的 Android 本地验证：是
- 当前 verdict：
  - `no_additional_new_task_activation_config_needed`
  - 理由不是“以后永远不做”，而是当前 guarded boundary 已由现有代码与测试表达清楚；现在再加一层 flag / config 只会制造重复控制面

本轮新增的 Android 窄验证是：

- `$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot'; .\gradlew.bat :feature:taskmail:internal:testDebugUnitTest :feature:taskmail:internal:detekt :feature:taskmail:internal:lintDebug`

结果：`BUILD SUCCESSFUL`

本次新增的实现是：

- `E:\projects\mail_based_task_manager\mail_runner\taskmail_closeout.py`
  - 新增 repo-side `daily_closeout_bundle` helper
  - 固定优先消费 `runs/<task_id>/canonical_summary.json`
  - 不再把 `raw_004.json` 当成稳定锚点，而是按 `terminal_mail_message_id`、terminal subject、terminal status label 依次定位 `mail/raw_*.json`
  - 可直接读取 Android `taskmail_new_task_send_records.json`，输出 same-run bind 结果
- `E:\projects\mail_based_task_manager\scripts\build_taskmail_closeout_bundle.py`
  - 提供命令行入口
  - 可直接写出 `runs/<task_id>/taskmail_daily_closeout_bundle.json`

本次新增的测试是：

- `E:\projects\mail_based_task_manager\tests\test_taskmail_closeout.py`
  - 覆盖 `request_id` 强绑定
  - 覆盖 terminal mail subject fallback，证明 helper 不依赖固定 raw 编号
  - 覆盖仅剩 `last_summary` 弱绑定时的 readout
  - 覆盖默认 run artifact 落盘

此前 closeout helper 会话新增的窄验证是 PC 侧：

- `.\.venv\Scripts\python.exe -m pytest tests\test_taskmail_closeout.py`

结果：`4 passed`

本轮 fresh closeout 补充验证另外新增了：

- formal-host live sample `Phase 5 fresh closeout 20260322 E`
- PC live closeout `thread_097 / runs/20260322_184156_afc8`
- `build_taskmail_closeout_bundle.py` 生成 `taskmail_daily_closeout_bundle.json`
- bundle `same_run_bind.effective_bind_level = transport_message_id`
- helper note `android_request_id_missing`
- `.\gradlew.bat :app-thunderbird:installFullDebug`
- formal-host live sample `Phase 5 fresh closeout 20260322 F`
- PC live closeout `thread_098 / runs/20260322_190954_7e1f`
- Android latest send evidence 现已带出 `requestId`
- bundle `same_run_bind.effective_bind_level = request_id`
- helper notes 为空

## 当前已知边界 / 小坑

- PowerShell 下不要用 `adb exec-out run-as ... cat <db> > local.db` 直接重定向 SQLite 二进制；会损坏文件。要么改用
  `cmd /c`，要么先写到设备再 `adb pull`。
- formal-host 冷启动烟测仍沿用：桌面启动 Thunderbird，然后手动进入 `Tasks`；不要把 adb deep link 或 debug-host
  路径当等价替代。
- 当 PC `canonical_summary.json` 已存在时，direct 行不要再默认只靠 `raw_001` / `raw_004` 人工拼接。
- 现在优先用 `build_taskmail_closeout_bundle.py` 生成同 run `taskmail_daily_closeout_bundle.json`；只在 helper 输出仍停在
  `weak_bind_only` / `none` 时，才回到 checklist note，不要先手工拼接再倒推结论。
- `thread_097` / `thread_098` 已证明 frozen closeout workflow 可以在 fresh sample 上复用，并且当前 formal-host build 已闭环 `request_id` 首键 bind。
- 如果后续 Phase 5 planning 再次把 `ack-level hard rejection live closeout` 写成 remaining tail item，先按文档漂移处理：
  - Android authority 已把这条线读成 closed
  - `Phase2 hard reject smoke B` 已证明 `thread_085` 之后不再出现新的 adjacent-runtime thread
  - 当前仓头 focused tests 也继续锁定 `hard rejection -> local stop + draft retention + no implicit mail fallback`
  - 因此默认不要回头补 Android direct-send logic、activation flag 或额外 config；除非 current formal-host build 出现新回归

## 下一步最合理顺序

1. 不默认再先追同类 fresh bind sample；`thread_097` / `thread_098` 已经关闭当前 narrow evidence blocker。
2. guarded rollout / activation note 现已落地；后续 Android 窄代码判断也已完成，当前结论是不需要单独新增
   `new_task` flow-scoped activation / config change。
3. 因此下一步不是 Android 代码实现，而是继续把这个 verdict 保持在 authority / handoff 里，并继续把 `reply` /
   `/status` 留在 scope 外。
4. 如果后续 fresh sample 再次退回到 `transportMessageId` / `ingress_message_id` 才能闭环，或 helper 重新出现
   `android_request_id_missing`，再把结论退回 `not_ready_keep_mail_default`。
5. 不重新开启 `thread_084` 这类历史 retained-artifact 追补，除非它再次变成当前 gate 的直接 blocker。
