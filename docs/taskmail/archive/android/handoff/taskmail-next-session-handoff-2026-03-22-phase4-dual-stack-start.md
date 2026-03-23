# TaskMail Next Session Handoff - 2026-03-22 - Phase 4 Dual-Stack Start

## 当前决策

- Phase 3 先按当前边界冻结，不再继续把剩余少量尾项当作当前阶段 blocker。
- duplicate pending question / duplicate answer line 已按 PC 侧 source `[QUESTION]` 输入问题处理，上游已修正；
  Android 侧暂不再围绕它继续做 Phase 3 本地修补。
- `detail` 编辑态下 refresh 期间的 draft / attachment 保持已经烟测通过，不再作为 Phase 3 收尾阻塞项。
- refresh-warning failure 可见性、更宽的 passive foreground-refresh smoke，以及 duplicate-question 修复后的
  Android 侧补回归验证都明确后延。
- 下一条主动工程主线切到 Phase 4，但这仍然表示“dual-stack parity + primary-path switch”，不是“立刻把所有
  TaskMail 流量都改成纯 direct”。

## Read First

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/TASKMAIL-MAIL-RULES.md`
- `docs/taskmail/planning/android/taskmail-android-public-plaintext-direct-connect-plan-v0.1.md`
- `docs/taskmail/archive/android/handoff/taskmail-next-session-handoff-2026-03-21-reply-direct-send-seam.md`
- `docs/taskmail/archive/android/handoff/taskmail-next-session-handoff-2026-03-21-phase2-direct-outbound-contract.md`

## Phase 4 起点约束

- 继续保留 mail fallback；Phase 4 不是移除 mail path。
- 先按已覆盖 flow 做 parity / rollback 设计，不要把“covered flows”偷换成“全部 TaskMail flow”。
- `new task` 仍是当前唯一已经在 Android 正式 flow 上完成 live direct closeout 的 direct business slice。
- `reply`、`/status`、以及更广的 read-side direct transport 仍不能在没有跨仓库 contract 冻结的前提下直接开写。

## 下一步最合理顺序

1. 先明确 Phase 4 当前要覆盖的 flows。
   - 最保守起点是先围绕已闭环的 `new task` 做 dual-stack parity / rollback 规则。
   - 如果要把 `reply` / `/status` 纳入 covered flows，先冻结跨仓库 direct contract。
2. 为 covered flows 建一份 parity matrix。
   - 对齐 direct accepted、mail fallback、hard rejection、summary / thread outcome、以及用户可见错误语义。
3. 定义 mismatch triage 与 rollback trigger。
   - 明确哪些差异只记日志，哪些差异要切回 mail，哪些差异需要中止 flow。
4. 再决定 primary-path switch 的启用边界。
   - 只对 parity 足够的 flow 切 direct-default，不要一次性放大全量范围。

## 本次是否改代码 / 验证

- 已改生产代码：否
- 已改测试代码：否
- 已改文档：是
- 已做新的本地验证：否

