# TaskMail 下一会话交接（2026-03-21，reply direct send-side seam）

## 当前决策

截至 2026-03-21，Android 端还不应该直接进入 direct `reply` 或 direct `/status` 的实现。

当前跨仓库边界仍然是：

- Android 仓库里的 direct 业务入口只在 `new task` 上闭环
- PC-side current protocol 仍明确把 direct ingress 限定在 `new_task`
- reply、等待态回答、`/status` 与其他 post-creation control action 仍是 mail-based canonical path

因此，本次会话没有猜测新的 direct reply 协议，而是先把 Android 现有 `new task` 的
bootstrap -> direct attempt -> mail fallback / hard rejection 编排抽成可复用的 send-side seam。

## 先读这些文档

1. `docs/taskmail/planning/android/taskmail-next-session-handoff-2026-03-21-phase2-direct-outbound-contract.md`
2. `docs/taskmail/planning/android/taskmail-android-public-plaintext-direct-connect-plan-v0.1.md`
3. `docs/taskmail/planning/android/taskmail-phase2-direct-outbound-contract-v0.1.md`
4. `E:\projects\mail_based_task_manager\docs/current/mail_protocol.md`
5. `E:\projects\mail_based_task_manager\docs/current/android_reply_method_rules.md`
6. `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
7. `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`

## 本次会话改了什么

- 新增 `RunTaskMailDirectOrFallback`
  - 统一处理 relay bootstrap、direct attempt、mail fallback、hard rejection 与 bootstrap 后断开连接
- `TaskNewTaskViewModel` 改为通过这条 seam 执行当前 Phase 2 direct `new task`
  - 用户可见行为不变
  - direct accepted / mail fallback / hard rejection 语义不变
- 新增 focused use-case coverage
  - direct accepted
  - bootstrap unavailable -> mail fallback
  - direct fallback -> mail fallback
  - hard rejection local stop
  - direct send throw -> mail fallback

## 当前边界

本次会话**没有**做以下事情：

- 没有定义 direct `reply` packet contract
- 没有定义 direct `/status` packet contract
- 没有把 `TaskSessionDetailViewModel` 接到 direct path
- 没有改动 mail reply headers、reply routing、`/resume`、multi-question `Answers:`、attachment reply 语义

这些边界是刻意保留的，因为当前 PC-side canonical docs 还没有把 direct post-creation actions 提升为 current protocol。

## 下一步最合理顺序

1. 先冻结跨仓库的 direct `reply` contract
   - 先决定是扩展 `phase2-direct-outbound-contract-v1`，还是单独起 session-actions contract
   - 明确普通 reply、single-question、multi-question `Answers:`、paused `/resume`、attachment-only continuation 的边界
2. 再冻结 direct `/status` contract
   - 保持当前 user-facing mail contract 不变
   - 不要把 read-side direct projection 混进这一轮 send-side contract
3. Android 侧在 contract 冻结后再接 `TaskSessionDetailViewModel`
   - 复用本次抽出的 `RunTaskMailDirectOrFallback`
   - 只替换 send-side transport decision，不要改本地 reply serialization 规则

## 本次验证

在 JDK 21 环境下，已重新执行并通过：

- `.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest`
- `.\gradlew.bat :feature:taskmail:internal:detekt`
- `.\gradlew.bat :feature:taskmail:internal:lintDebug`

## 会话总结

- 代码修改：有
- 文档修改：有（本交接说明）
- 行为改动：无用户可见协议变更
- 当前最重要的未完成项：direct `reply` / `/status` contract 仍未冻结
