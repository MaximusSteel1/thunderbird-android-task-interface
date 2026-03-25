# TaskMail VPS-First 多 PC 主线 Authority（v0.1）

更新时间：2026-03-25

## 状态

本文是 2026-03-25 选定的 `VPS-first 多 PC 控制面` 方向在 Android 仓库侧的当前宏观规划 authority。

它负责冻结以下内容：

- TaskMail 后续唯一主线的产品边界
- `VPS / PC / workspace / session` 的主职责划分
- 哪些旧 planning 假设现在已经退役
- 当前 implementation-truth 与 future-direction authority 的分层
- Android 侧用户需求 authority 的挂接位置

它不会替代以下当前 implementation-truth 或 protocol-truth 文档：

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/TASKMAIL-MAIL-RULES.md`
- `E:\projects\mail_based_task_manager\docs\current\mail_protocol.md`
- `E:\projects\mail_based_task_manager\docs\current\android_runner_communication_contract.md`

这些文档仍然回答“今天已经实现了什么”。
本文定义的是从现在开始应当假定的主线方向。

跨仓对应 authority：

- `E:\projects\mail_based_task_manager\docs\plans\android_pc_vps_evolution_authority.md`

同仓配套用户需求 authority：

- `docs/taskmail/planning/android/taskmail-vps-first-multi-pc-user-requirements-authority-v0.1.md`

## 目的

本文用于把 TaskMail 的 future-direction authority 从：

- `public plaintext direct-connect`
- `TaskMail direct relay/control/file`
- `mail-first 主线下叠加若干 direct 窄切片`

正式切到：

- `VPS-first` 统一控制面
- 多台 `PC` 作为执行节点
- `workspace` 明确为 `pc-scoped` 本地目录
- `mail` 降级为备份、导出或兼容面，而不是产品主线

这是一条新的唯一主线。

它不是在声称 Android 或 PC 仓已经完成了这次切换。

## 当前固定假设

除非后续有新的 authority 文档重新打开，下列假设现在已经固定：

1. TaskMail 的唯一主线方向是 `VPS-first` 统一工作台，而不是继续扩张 mail-first direct sidecar。
2. `VPS` 是统一控制面与主投影层。
3. `PC` 仍是 task execution truth，但其角色应收敛为执行节点，而不是产品主控制面。
4. 一个平台可以同时管理多台 `PC`。
5. `workspace` 是某台 `PC` 下的本地唯一目录，因此它天然是 `pc-scoped` 对象，而不是平台级共享资源。
6. `session` 一旦创建，就固定绑定到某个 `workspace`，从而也固定绑定到某台 `PC`。
7. 第一版不支持跨 PC 热迁移运行中的 `session` 或 `run`。
8. 流式输出可以进入正式协议，但必须作为 `output_chunk` 一类对象存在，不能替代结构化 `event` 与最终 `result`。
9. 当前 mail path 可以继续保留，但它的长期角色应是 backup / export / notification / compatibility，而不是主产品路径。
10. 在代码实际变化之前，current-truth 文档仍然对仓库现状保持 authority。

## 已明确退役的假设

下列较早期假设不再是本仓库中的 active planning authority：

1. `Android direct-connect public plaintext` 是当前唯一需要继续扩张的主线。
2. `TaskMail direct relay/control/file` 仍是未来产品主路径。
3. `new_task`、`reply/status`、`[SYNC]` 这三条 Android 直连切片应继续作为唯一 active 工程主线并行扩张。
4. `workspace` 可以按平台级共享资源来规划。
5. mail-first 与 direct-first 的混合长期共存是默认终局。

这些假设仍然能解释旧 planning 与旧验证证据，但它们已经不再是被选中的主线 baseline。

## 选定的产品边界

当前选定的主线产品边界是：

- Android 最终应面向 `VPS` 的统一控制面，而不是继续以 mail 或窄 `/relay` 切片为主协议
- `VPS` 负责 `pc / workspace / session / run / command / event / result / artifact metadata`
- `PC` 负责本地 repo、workdir、backend 进程、native session 与原始 artifact 文件
- `backend / profile / permission / backend_transport` 应作为控制面一等执行策略字段进入主线，而不是继续散落在 mail 语义或本地隐式配置里
- Android、PC、VPS 三边应逐步收敛到统一的 `command / event / output_chunk / result / artifact` 协议
- mail 如继续保留，应从 canonical control-plane state 派生，而不是继续决定主 UI / 主时间线

这意味着 Android-side planning 现在被允许：

- 把当前 mail-first / direct-slice 结构读成兼容基线，而不是未来产品终局
- 围绕 `VPS-first 多 PC 控制面` 规划新的读写模型
- 把当前 `new_task`、`reply/status`、`[SYNC]` 的 direct/mail 证据更多读成过渡期 closeout，而不是长期主线 owner 面

## Guardrails

以下规则仍然有效：

- 不要在 `CURRENT-STATUS` 或 validation ledger 中误述当前实现状态
- 不要把“未来主线已经改了”误写成“今天的协议已经切换完成”
- 不要把共享 workspace、多 PC 共同执行、跨 PC 热迁移在第一版里偷带进来
- 不要把流式输出误写成最终业务真相
- 不要把 mail protocol authority 提前删掉；在代码仍以 mail 为 current behavior 时，它仍然是当前协议事实的一部分

## 本 authority 立即改变的内容

以下 planning 结果现在立即生效：

1. Android TaskMail 的唯一主线正式切到 `VPS-first 多 PC 控制面`。
2. `new_task`、`reply/status`、`[SYNC]` 的现有 direct/mail 切片不再被读作未来产品主线，而改读为当前兼容面与 closeout 资料。
3. Android planning index 与主线入口应围绕新的 authority 与新的多 PC 控制面设计重排。
4. 旧的 public-plaintext direct-connect authority 保留为历史 reference，但不再是 active authority。

## Android 侧立即产生的规划后果

这份 authority 在 Android 仓库侧立即带来的结果是：

1. Android 的 active planning 不再默认围绕三个 direct-mail 混合切片扩 scope。
2. Android 侧总主线文档应改为围绕 `VPS-first 多 PC 控制面` 解释角色、阶段和后续对接。
3. 现有 `new_task`、`reply/status`、`[SYNC]` 文档继续保留，但应按 compatibility / closeout / evidence 读法维护。

## 清理结果

从 2026-03-25 起，默认主线应从以下组合读取：

- 本文
- `docs/taskmail/planning/android/taskmail-vps-first-multi-pc-user-requirements-authority-v0.1.md`
- `docs/taskmail/planning/platform/taskmail-multi-pc-control-plane-v0.1.md`
- `docs/taskmail/planning/android/taskmail-next-development-plan-v0.2.md`
- current-truth 文档

较早的 public-plaintext direct-connect authority 继续保留，但只承担历史背景与转向证据价值。
