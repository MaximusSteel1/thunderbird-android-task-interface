# TaskMail Android Phase 3

本文现在作为 TaskMail Android 交互阶段的历史 / 参考文档。

当前状态 authority：

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`

协议 authority：

- `docs/TASKMAIL-MAIL-RULES.md`

验证证据 authority：

- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`

## 日期

- 最后更新：2026-03-16

## 文档角色

阅读本文的用途是：

- 理解 “Phase 3” 最初指的是什么
- 理解哪些交互目标已经成为仓库基线
- 分辨哪些事项现在更像验证 closeout，而不是新的实现缺口

不要只依赖本文来回答下面两个问题：

- “当前仓库到底实现到了哪一步？”
- “哪些能力已经被完整验证？”

## 原始阶段目标

Phase 3 的目标，是把 TaskMail 从只读 viewer 变成围绕既有 task thread 的轻量交互面。

对应的用户结果应当是：

- 用户可以读取最新状态、summary 与 pending questions
- 用户可以直接在 TaskMail 内回复
- 用户可以不离开 TaskMail 就触发 `/status`
- 用户保持在既有 mail thread 上，而不是无意中 fork 出一个新线程

## 当前对齐结论

截至 2026-03-16 的文档对齐整理，Phase 3 的大部分交互面已经存在于仓库中。

### 仓库中已经存在的交互面

- reply context 取自最新 logical-session message
- mixed-account 或 missing-anchor 会话会禁用 reply
- session detail reply composer
- plain-text reply
- reply attachments
- 单题 quick answer
- 多题结构化 `Answers:` 模板回复
- `/status`
- `paused` 会话发送时自动前置 `/resume`
- 通过现有 mail send stack 完成 real sender integration
- timeline attachment metadata 与 `Open` / `Save` 动作

### 已经记录的验证证据

验证台账已经记录了以下可执行证据：

- reply sender unit tests
- `/status` dispatch
- paused-session `/resume` body serialization
- single-question quick-answer behavior
- multi-question structured-answer gating
- reply attachment selection 与 attachment-only continuation 行为
- detail-screen UI state handling
- Thunderbird 与 K-9 的 `fossDebug` assemble

### Phase 3 仍未完全收口的部分

现在更准确的理解应当是：Phase 3 已经“在仓库中实现”，但“尚未彻底完成验证收口”。

剩余 closeout 项包括：

- live end-to-end mail sending re-smoke
- attachment、paused session、多问题流程的 device/manual 验证
- TaskMail 窄范围之外的 repo-wide quality gates

## 当前与仓库一致的 Phase 3 定义

现在再提 “Phase 3”，更准确的含义是：

- session detail 已经是一个真实的交互中心
- Android 可以在既有 TaskMail thread 内回复
- Android 可以处理 paused-state 发送语义
- Android 可以区分单题等待与多题等待
- Android 可以在协议允许的前提下给 reply 附件

Phase 3 不再意味着：

- “reply 交互还只是设计草图”
- “attachments 还只是未来工作”
- “multi-question 仍然只是只读支持”

## Phase 3 的协议边界

当前 PC 侧协议表面仍然宽于 Android 当前显式 UI 表面。

因此，Android 当前更准确的理解是：

- 协议兼容
- 显式控件刻意保持较窄

### 当前已经存在的显式 UI 表面

- plain-text reply
- reply attachments
- single-question quick answers
- multi-question structured answers
- `/status`
- paused-session resume semantics

### 尚未做成专门 Android 控件的协议能力

- `Permission`
- `Profile`
- `Timeout`
- `Mode`
- `/pause`
- `/new`
- `/sessions`
- `/rerun`
- `/kill`

这是一条有意保留的 scope boundary，不是协议自相矛盾。

## Phase 3 剩余 closeout 工作

现在最值得补的 closeout 工作是：

- 在真机上验证 detail reply 流程
- 在真机上验证 attachment open/save 与 reply-attachment 流程
- 确认 paused session 与多问题交互文案在真实使用里可理解
- 当 mail control plane 变化时，持续同步 protocol 文档与 Android 文档

## 文档维护提示

如果交互面发生变化，按下面顺序联动更新：

1. `docs/TASKMAIL-MAIL-RULES.md`
2. `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
3. `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
4. `docs/taskmail/archive/android/planning/TASKMAIL-ANDROID-PHASE3.md`

## 推荐理解方式

当前最安全的理解是：

- Phase 3 的实现表面已经存在于仓库中
- 这层表面已经包括 attachments、paused-session handling 与 structured multi-question replies
- 完整真机 / 手工信心与 repo-wide quality closeout 仍待补齐
