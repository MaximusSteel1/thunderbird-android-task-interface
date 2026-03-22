# TaskMail Android Phase 2

本文现在作为 TaskMail Android 正式宿主接入阶段的历史 / 参考文档。

当前状态 authority：

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`

验证证据 authority：

- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`

## 日期

- 最后更新：2026-03-16

## 文档角色

阅读本文的用途是：

- 理解 “Phase 2” 最初想解决什么问题
- 理解哪些正式入口目标现在已经成为仓库基线
- 区分哪些事项仍属于 host / navigation closeout，而不是交互层工作

不要只依赖本文来回答下面两个问题：

- “当前仓库到底已经实现了什么？”
- “哪些能力已经被完整验证？”

## 原始阶段目标

Phase 2 的目标，是把 TaskMail 从 debug-only 原型变成正式可进入的应用内功能。

对应的用户结果应当是：

- 用户可以在应用内部打开 TaskMail
- TaskMail 使用真实的 repository-backed read path
- 这个功能不再依赖 adb 或 debug-only 路由
- debug host 仍然保留，作为兜底验证路径

## 当前对齐结论

截至 2026-03-16 的文档对齐整理，Phase 2 的核心表面已经不再只是 future work，而是仓库当前基线。

### 仓库中已经存在的 Phase 2 范围

- `FeatureLauncherTarget.TaskMail`
- `FeatureLauncherNavHost` 中的 TaskMail route 注册
- `TaskMailNavigation` / `DefaultTaskMailNavigation`
- 从 `app-common` 引入的 `taskMailModule`
- drawer-level `Tasks` 入口接线
- formal host 与 debug host 共享的 `TaskMailRoute` 契约
- 保留的 `TaskMailDebugActivity`

### 已经记录的 Phase 2 验证证据

验证台账已经记录了以下可执行证据：

- launcher route contract tests
- drawer event / effect plumbing tests
- drawer-to-launcher TaskMail handoff
- debug-host back-stack fallback
- Thunderbird 与 K-9 的 `fossDebug` assemble

### Phase 2 仍未完全收口的部分

现在更准确的理解应当是：Phase 2 已经“在仓库中实现”，但“尚未彻底完成验证收口”。

剩余 closeout 项包括：

- 更宽范围的 device/manual formal entry 与 back stack 烟测
- 确认 drawer click-through 在真机上稳定
- TaskMail 窄范围之外的 repo-wide quality 任务

## 当前与仓库一致的 Phase 2 定义

现在再提 “Phase 2”，更准确的含义是：

- TaskMail 已经可以在应用内正式进入
- TaskMail 通过正常的 launcher / navigation 栈承载
- TaskMail drawer 入口已经存在
- debug host 仍然作为兜底验证面保留

Phase 2 不再意味着：

- “formal entry 还只是未来 checklist”
- “TaskMail 仍然只能通过 debug deep link 进入”

## 不应继续放进 Phase 2 讨论的内容

下面这些内容已经不适合继续归入 Phase 2 范围：

- reply 交互语义
- 多问题结构化回答
- attachment send / open / save 行为
- paused session resume 行为
- 更复杂的 protocol / UI 边界决策

这些内容应分别回到 current status、protocol 文档或 Phase 3 参考材料中讨论。

## 文档维护提示

如果 formal host path 发生变化，按下面顺序联动更新：

1. `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
2. `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
3. `docs/TASKMAIL-ANDROID-PHASE2.md`

## 推荐理解方式

当前最安全的理解是：

- Phase 2 的实现表面已经存在于仓库中
- launcher / drawer / build 路径已经有窄范围可执行证据
- formal entry path 的完整真机 / 手工信心仍待补齐
