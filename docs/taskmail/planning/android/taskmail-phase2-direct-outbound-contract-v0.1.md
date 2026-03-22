# TaskMail Android Phase 2 Direct Outbound Contract Mirror（v0.1）

更新时间：2026-03-21

## 状态

本文是第一版共享 Phase 2 direct outbound contract freeze 在 Android 侧的镜像说明。

相邻的 PC-side canonical note 为：

- `E:\projects\mail_based_task_manager\docs\plans\phase2_direct_outbound_contract_v1.md`

本文并不声称 Android 已经开始发送 direct business traffic。
它冻结的是：一旦实现开始，Android 应如何把当前 `New task` 表单映射成第一版 direct payload。

## 范围

Android 侧 v0.1 mirror 目前只覆盖：

- direct `new task`

它不会把 Android direct 行为扩展到：

- plain continuation reply
- `/status`
- `/pause`
- `/resume`
- `/end`
- direct read-side updates

这些内容仍然不属于第一版 direct slice，并继续停留在当前 mail path 上。

## 共享现状判断

Android 仓库当前状态仍然是：

- Phase 1 bootstrap reuse 已经进入仓库
- 正式 `new task` 目前仍然发送真实邮件
- relay bootstrap result 目前只承担 preflight 与 fallback signal 角色

因此，Phase 2 的第一个 slice 是：

- 用一次 direct packet send 替换当前 `new task` 的 preflight-plus-mail 路径
- 继续保留当前 mail `new task` 路径作为 fallback
- 在后续阶段另行改动前，session 与 detail 的 read-side updates 继续保持 mail-driven

## Android 到 Shared V1 Payload 的映射

Android 应把当前 `TaskMailNewTaskDraft` 映射到 shared payload，规则如下：

- `senderAccountId -> origin.sender_account_uuid`
- `backend.wireValue -> new_task.backend`
- `repoPath -> new_task.repo_path`
- `taskText -> new_task.task_text`
- `subjectTitle -> new_task.subject_title`
- `workdir` 为空字符串或 null -> `new_task.workdir = null`
- `workdir` 非空 -> `new_task.workdir`
- `timeoutMinutes -> new_task.timeout_minutes`
- `mode.wireValue -> new_task.mode`
- `profile` 为空字符串或 null -> `new_task.profile = null`
- `profile` 非空 -> `new_task.profile`
- `permission = Default -> new_task.permission = null`
- `permission = Highest -> new_task.permission = highest`
- `acceptanceCriteria` 在当前 trim 与空项过滤之后 -> `new_task.acceptance`

Android 应继续把当前 `TaskMailNewTaskBodySerializer` 与 subject builder 用作：

- mail fallback projection
- 在比较 direct intent 与当前 mail 行为时最快速的 parity reference

## Android 对 Transport 的理解

Android 应把这份 shared contract 理解为：

- 当前 bootstrap seam 继续复用
- `hello -> hello_ack` 仍然是发送任何 direct business packet 之前的 gate
- 第一份 business payload 通过现有 relay `packet` wrapper 承载
- Android 不得把当前 PC outbound status-delivery 中 `task_run_packet` 的旧含义误当成 Android 产品 contract

在这第一版 direct slice 中，Android 复用的是当前 relay transport shell，而不是继承旧 PC-only mail
delivery business meaning。

## Android Fallback Rule

当前 Android fallback rule 在 Phase 2 v0.1 中继续生效：

- bootstrap unavailable：
  - 回退到当前 mail `new task`
- connect 或 transport send 在 accepted packet 之前失败：
  - 回退到当前 mail `new task`
- 显式 server capability rejection，例如 unsupported action：
  - 回退到当前 mail `new task`
- 显式 server hard rejection，例如 invalid payload 或 unauthorized：
  - 不得静默回退
  - 保留 draft
  - 向用户显示 direct-send failure
- accepted direct packet：
  - 不要同时再发一封 mail
  - 显示成功消息，但仍提醒用户任务会在后续 TaskMail status mail 到来后才出现

## Android UI 后果

第一版 direct slice 应保留以下当前 UI 事实：

- `New task` 在任何发送尝试前仍然执行相同的本地校验
- 尽管 `sender_account_uuid` 在 direct payload 中只承担 provenance 角色，但当前 sender-account resolution
  与 selection 行为可以继续保持
- 当前 success UX 仍然不应承诺“在第一封 status mail 到来前就立即出现在 workspace”
- 当前 debug visibility 应能区分：
  - direct accepted
  - direct rejected
  - direct unavailable 且使用了 mail fallback

## 不属于这一版 Slice 的内容

不要把第一版 direct 实现扩展到：

- reply composer direct serialization
- 通过 direct transport 做 status polling
- direct timeline projection
- direct workspace hydration
- 移除 mail fallback

## 当前结论

Android-side Phase 2 第一版 slice contract 现在已经冻结得足够明确，可以开始实现。

Android 下一步实现应当是：

- 保留现有 bootstrap preflight
- 在成功路径上，用 direct packet send 替换 `new task` mail send
- 对 unsupported 或 failed direct cases 继续保留现有 mail fallback
