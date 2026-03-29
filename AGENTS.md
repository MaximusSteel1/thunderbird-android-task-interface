# AGENTS

目的：让 AI 用尽量少的无效过程，把代码推向目标态。

## 优先级

1. 满足用户真实目标。
2. 长期方向明确时，优先落到目标态，不默认选择兼容层、过渡层、渐进迁移。
3. 处理 TaskMail 时，优先主线与主线阻塞。
4. 保持正确性与可维护性。
5. 控制上下文、范围、流程，但不要为了缩小改动而固化短期妥协。

不要借这些优先级无端扩大范围。优先能直接落到目标态的最小闭环改动。兼容层、过渡层、分阶段迁移默认不是目标，只在用户明确要求渐进式推进，或直接切换会带来不可接受的产品/交付风险时考虑。

## 上下文加载

- 先读最近相关的文件、模块、文档。
- 先尽快判断当前决策是否真的需要更大上下文；不需要时保持局部。
- 若局部证据不足、附近实现可能是遗留/过渡模式、或风险较高，只补能解锁当前决策的最小必要上下文。
- 若架构、模块边界、repo 工作流、目标态判断会影响决策，读取对应的最小必要文档或 ADR；决策解锁后停止继续扩读。
- 查看附近实现是为了识别约束和约定，不是为了复制已有模式。

## 默认偏好

- 先把 happy path 和当前主线做窄、做通、做可验证，再补高价值边界。
- 先判断例外情况的概率、影响、当前处理成本、后补成本；低概率且低损失情况默认不预埋冗余设计，优先显式失败、暴露日志或留下可补点，不默认加静默 fallback。
- 优先沿用本地模式；若本地模式与目标态冲突，不继续延续它。
- 没有第二个真实使用点，不提前抽象公共层、adapter、facade、interface。
- 变更跨文件时，同步代码、测试、文档。
- 不做无关清理；但若错误方向的旧模式会直接扭曲本次设计，可顺手移除与本改动直接相关的部分。
- 如实报告验证范围，不假装完成了更大范围的验证。
- 新增或更新仓库文档默认使用中文，除非任务明确需要其他语言。
- 面向用户解释时，默认按混合技术背景写；术语只做必要简释。
- 默认不为向后兼容或迁移 staging 增加结构；只有任务要求、产品约束或发布风险需要时才加入。

## 架构 / 技术默认

- 保持当前 `:api` / `:internal` 拆分；除非任务明确要求调整，或现有拆分阻碍目标态。
- 新代码默认：Kotlin、Compose（新功能）、Atomic Design 组件、Koin、Coroutines/Flow、MVI。
- 测试默认：fake 优先于 mock，AAA 结构，`testSubject` 命名。
- 无充分理由不引入新框架或新架构；但不要因“改动更大”就拒绝更清晰的目标态方案。
- 目标态不等于抽象更多、分层更多、对象更多；优先更直接的实现。

## TaskMail

真值层级：

- 实现状态：`docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- 验证证据：`docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- Android 协议权威：`docs/TASKMAIL-MAIL-RULES.md`

按需参考：

- `docs/TASKMAIL-DEBUG-VALIDATION.md`
- `docs/taskmail/planning/android/taskmail-next-development-plan-v0.2.md`
- `docs/taskmail/planning/android/taskmail-refresh-live-update-plan-v0.1.md`
- `docs/taskmail/planning/android/taskmail-bootstrap-entry-and-new-thread-plan-v0.1.md`
- PC 端参考工程：`E:\projects\mail_based_task_manager`
- PC 端协议：`E:\projects\mail_based_task_manager\docs\current\mail_protocol.md`
- PC 端 Android 回复规则：`E:\projects\mail_based_task_manager\docs\current\android_reply_method_rules.md`

读取策略：

- 不默认通读所有 TaskMail 文档。
- 先读最小相关集合；若需要区分主线意图与旧阶段/支线行为，立即补读。
- 旧 phase 文档不能覆盖 current-status 或 mail-rules。

行为默认：

- 保持 mode-driven reply 语义，除非任务明确要求变更。
- 保持 single-question wait 与 multi-question wait 的区别。
- multi-question 回复使用结构化 `Answers:` 载荷，不使用自由文本捷径。
- `paused` 视为一等状态。
- 保持 `Re:`、`[STATUS]`、`[S:session_id]` 与后端路由 token 等 canonical subject identity。
- 非任务所需时，不改 quoted-body、reply-anchor、transport 行为。

TaskMail 优先级：

- 优先推进主线设计或解除主线阻塞；优先直接靠近主线设计，不优先服务旁路、兼容路径、临时 staging layer；不主动进入支线、纯打磨工作、并行子线，除非用户明确要求。

TaskMail 验证：

- 先窄后宽，例如 `.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest`
- 需要时再扩大到 `detekt`、`lintDebug` 等。
- 设备自动化不稳定时，向用户请求精确手动步骤，不靠暴力点击硬推。

TaskMail 交接：

- 跨会话切片通常在 `docs/taskmail/planning/android/` 留简短 handoff。
- 优先文件名：`taskmail-next-session-handoff-YYYY-MM-DD.md`

## 验证

- 运行最小相关检查。
- 报告已运行项、未运行项、未运行原因。
- 若 repo 级验证被无关失败阻塞，不为了“全绿”去修无关文件。

## 工具链说明

- 本仓库 Gradle 需要 Java 21+。
- Windows 下优先使用 `.\gradlew.bat ...`。
- PowerShell 读取仓库文本默认走 UTF-8：设置 `[Console]::OutputEncoding = [System.Text.Encoding]::UTF8`，并显式使用 `Get-Content -Raw -Encoding utf8 <path>`。
- 不要先判断“文件是不是中文”再决定编码；默认按 UTF-8 读取，只有结果仍异常时再排查其他编码。
- 如果 Gradle 选到了 Java 11，先修正 `JAVA_HOME` / `PATH`，不要盲目重试。

## 决策

- 默认推进。
- 用户目标优先；用户当前判断、方案、步骤、实现想法不自动成立。
- 判断优先基于代码、文档、验证结果；若用户做法与目标、代码事实、文档事实、运行结果或项目约束冲突，直接指出，并给出更符合目标的建议；不为迎合用户而假装同意，也不为反对而反对。
- 若不确定性会明显影响对外可见行为、架构或模块边界、长期技术方向、难以回退的实现选择，尽早暂停并提问。
- 目标态已经明确时，不要因为“更快推进”而回退到更小、更保守、更兼容的方案。
- 提问时只说明：决策点、当前默认假设、哪些假设会改变实现。
- 若用户明确技术方向与现有代码习惯冲突，优先用户方向；不要静默退回渐进兼容方案。
