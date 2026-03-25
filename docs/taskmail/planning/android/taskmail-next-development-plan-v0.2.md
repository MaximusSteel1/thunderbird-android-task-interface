# TaskMail Android 下一阶段开发计划（v0.2）

更新时间：2026-03-25

## 状态

- 本文是 Android 侧在新 authority 下的总主线 roadmap。
- 本文不承担实现 truth，也不承担验证证据 authority。
- 本文只回答：在 `VPS-first 多 PC 控制面` 成为唯一主线后，Android 侧当前应如何排序后续工作。

## Read First

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-MAIL-RULES.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/taskmail/planning/android/taskmail-vps-first-multi-pc-authority-v0.1.md`
- `docs/taskmail/planning/android/taskmail-vps-first-multi-pc-user-requirements-authority-v0.1.md`
- `docs/taskmail/planning/android/taskmail-vps-first-multi-pc-information-architecture-v0.1.md`
- `docs/taskmail/planning/android/taskmail-vps-first-multi-pc-viewstate-action-mapping-v0.1.md`
- `docs/taskmail/planning/android/taskmail-vps-first-multi-pc-core-screen-low-fi-v0.1.md`
- `docs/taskmail/planning/android/taskmail-vps-first-multi-pc-visual-direction-v0.1.md`
- `docs/taskmail/planning/android/mockups/taskmail-vps-first-multi-pc-high-fi-preview-v0.1.html`
- `docs/taskmail/planning/android/taskmail-vps-first-multi-pc-page-api-requirements-v0.1.md`
- `docs/taskmail/planning/android/taskmail-vps-first-multi-pc-compose-screen-structure-v0.1.md`
- `docs/taskmail/planning/android/taskmail-vps-first-multi-pc-android-skeleton-implementation-plan-v0.1.md`
- `docs/taskmail/planning/android/taskmail-vps-first-multi-pc-session-slice1-file-plan-v0.1.md`
- `docs/taskmail/planning/android/taskmail-vps-first-multi-pc-pc-handshake-readiness-checklist-v0.1.md`
- `docs/taskmail/planning/platform/taskmail-vps-first-control-plane-freeze-v0.1.md`
- `docs/taskmail/planning/platform/taskmail-multi-pc-control-plane-v0.1.md`
- `docs/taskmail/planning/platform/taskmail-pc-vps-control-protocol-v0.1.md`
- `docs/taskmail/planning/platform/taskmail-execution-policy-appendix-v0.1.md`

## 跨仓实现参考

当 Android 侧任务从“冻结页面与字段”进入“开始按 Phase 1 骨架接线”时，推荐额外参考：

- `E:\projects\mail_based_task_manager\docs\plans\README.md`
- `E:\projects\mail_based_task_manager\docs\plans\vps_first_multi_pc_control_plane_mainline_v0.1.md`
- `E:\projects\mail_based_task_manager\docs\plans\vps_first_multi_pc_phase1_execution_plan_v0.1.md`

这些文档的作用是帮助 Android 侧理解：

- repo-side 当前 active mainline 的读法
- Phase 1 的切片顺序
- 哪些协议对象会优先进入 PC/VPS 代码实现

它们不替代：

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-MAIL-RULES.md`
- Android 侧 authority / companion docs

## 当前主线图

截至 2026-03-25，Android 侧 future-direction mainline 只保留一条：

1. `VPS-first 多 PC 控制面`

这条主线下，Android 侧当前不再把：

- `new_task`
- `reply` / `/status`
- `[SYNC] Project list`

继续读成三个并行扩 scope 的产品主线。

它们现在更准确的读法是：

- 当前遗留面
- 当前行为证据面
- 过渡期 closeout / reference 资料

## Android 侧当前角色

在这条新主线下，Android 侧当前角色应这样理解：

### 1. 保持 current behavior 不撒谎

- 继续按 `CURRENT-STATUS` 与 `MAIL-RULES` 准确描述今天已经存在的 mail / direct 行为
- 不把 future-direction authority 提前写成当前协议已经完成切换
- 但后续切片不再以“继续保活 mail / direct 兼容能力”为默认目标

### 2. 停止把旧的 direct / mail 结构当兼容基线

- `new_task`、`reply/status`、`[SYNC]` 现有 direct 切片保留 closeout / evidence 价值
- 但后续不再默认围绕它们继续扩大产品主线面积
- 已落地代码中的 compatibility seam 也只按待删过渡实现读取，不再继续变成稳定接口

### 3. 为新的 VPS 控制面收口客户端抽象

Android 侧真正需要为新主线准备的，是后续与统一控制面接轨的抽象边界，例如：

- `pc`
- `workspace`
- `session`
- `run`
- `execution_policy`
- `command`
- `event`
- `output_chunk`
- `result`
- `artifact`

同时，Android 侧对这些抽象的用户读法，应以：

- `docs/taskmail/planning/android/taskmail-vps-first-multi-pc-user-requirements-authority-v0.1.md`

为默认 authority，而不是回退到 mail thread 或底层协议对象直读。

而 Android 侧对这些抽象的页面组织与页面状态映射，应默认继续读取：

- `docs/taskmail/planning/android/taskmail-vps-first-multi-pc-information-architecture-v0.1.md`
- `docs/taskmail/planning/android/taskmail-vps-first-multi-pc-viewstate-action-mapping-v0.1.md`
- `docs/taskmail/planning/android/taskmail-vps-first-multi-pc-core-screen-low-fi-v0.1.md`
- `docs/taskmail/planning/android/taskmail-vps-first-multi-pc-visual-direction-v0.1.md`
- `docs/taskmail/planning/android/mockups/taskmail-vps-first-multi-pc-high-fi-preview-v0.1.html`
- `docs/taskmail/planning/android/taskmail-vps-first-multi-pc-page-api-requirements-v0.1.md`
- `docs/taskmail/planning/android/taskmail-vps-first-multi-pc-compose-screen-structure-v0.1.md`

其中 `execution_policy` 当前应按以下稳定读法进入计划，而不要继续散落在旧 mail 字段或本地脚本参数里：

- `backend = codex | opencode`
- `profile` 作为稳定档位标签，而不是 raw model id
- `permission = default | highest`
- 可选 `backend_transport`
- `resolved_model` 由目标 `PC` 按本地配置解析并回报

## 当前建议顺序

1. 先以 `taskmail-vps-first-control-plane-freeze-v0.1.md` 作为共享字段基线，稳定 `VPS-first 多 PC 控制面` 的领域模型与协议读法，包含 `backend / profile / permission / backend_transport` 的 `execution_policy`。
2. Android 侧不再以“维持现有 mail/direct 兼容线稳定”作为默认目标，而是按 control-plane hard cutover 思路逐步替换旧入口。
3. 任何迁移期 adapter 只允许短期存在于边界层，不再继续长进页面 state、路由、主仓库接口或产品主流程。
4. 在字段形状已冻结的前提下，按页面级 API 需求、Compose 页面骨架与主线页面信息架构推进 Android 新控制面的接入顺序。
5. Android 编码阶段默认按 `taskmail-vps-first-multi-pc-android-skeleton-implementation-plan-v0.1.md` 的 slice 顺序进入第一批骨架改造。
6. 编码起手第一刀默认先按 `taskmail-vps-first-multi-pc-session-slice1-file-plan-v0.1.md` 落 `TaskSessionDetail` 的骨架改造。
7. 与 PC 端的第一轮正式握手联调，默认按 `taskmail-vps-first-multi-pc-pc-handshake-readiness-checklist-v0.1.md` 判断是否已达到合适时机。

## 无 Legacy Baseline 约束

从本计划生效后，后续切片默认不再保留或扩展以下对象作为长期基线：

- `RunTaskMailDirectOrFallback`
- `SendTaskMailNewTask`
- `threadId` route fallback
- `mail-backed summary`
- UI/state 中的 compatibility getter 与 bridge 文案

如果迁移期暂时还存在这类结构，它们也只应被视为待删过渡实现，而不是新的稳定设计。

## 当前历史 / closeout 参考线

以下文档继续保留，但它们不再构成未来唯一主线：

- `taskmail-phase5-new-task-guarded-rollout-observation-plan-v0.1.md`
- `taskmail-phase5-reply-status-android-implementation-plan-v0.1.md`
- `taskmail-phase5-project-sync-android-implementation-plan-v0.1.md`
- `taskmail-android-public-plaintext-direct-connect-authority-v0.1.md`
- `taskmail-android-public-plaintext-direct-connect-plan-v0.1.md`

## 当前结论

当前最重要的规划动作，不再是继续为旧的 direct-mail 混合结构增写 planning 或保兼容，而是把 Android 侧 future-direction 读法统一收敛到：

- 一个 `VPS-first` 主控制面
- 多个 `PC` 执行节点
- `pc-scoped workspace`
- 一等 `execution_policy`
- 结构化 `command / event / output_chunk / result / artifact`
