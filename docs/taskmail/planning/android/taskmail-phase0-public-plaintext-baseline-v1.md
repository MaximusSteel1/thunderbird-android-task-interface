# TaskMail Android Phase 0 Public Plaintext Baseline（v1）

更新时间：2026-03-21

## 状态

本文用于冻结 Android 侧 direct-connect baseline，并据此收口 Phase 0 中纯 planning 的部分。

它并不声称 Android 已经端到端实现或验证了这条 runtime path。

## 相关文档

- `docs/taskmail/planning/android/taskmail-android-public-plaintext-direct-connect-authority-v0.1.md`
- `docs/taskmail/planning/android/taskmail-android-public-plaintext-direct-connect-plan-v0.1.md`
- `docs/taskmail/planning/android/taskmail-next-session-handoff-2026-03-21-public-plaintext-direct-connect.md`
- `E:\projects\mail_based_task_manager\docs\plans\phase0_relay_readiness_note.md`

## 冻结后的 Baseline

当前 Android-side direct-connect baseline 为：

- public IP：`124.223.41.153`
- configured port：`8787`
- diagnostic endpoint：`http://124.223.41.153:8787/healthz`
- connection endpoint：`ws://124.223.41.153:8787/relay`
- transport auth：基于 token 的 bootstrap / auth admission
- token boundary：Android 可以为了 direct-connect bootstrap 在本地持有 transport token，但 token 绝不能出现在
  文档、日志、截图或邮件正文中
- fallback rule：如果 host、port、token、connect 或 `hello -> hello_ack` bootstrap 失败，用户 flow 仍留在
  当前 mail path 上，而不是假装 direct path 可用

## 当前共享 readiness 判断

相邻的 PC/VPS readiness note 目前记录的是：已检查的 public deployment 看起来仍然是 TLS-backed，而不是已经按
这份 plaintext baseline 提供服务。

这是对 deployment state 的判断。
它不会重新打开 Android-side planning reset，也不会推翻这份已冻结的 baseline 本身。

## Phase 0 收口判断

对 Android-side planning 层来说，Phase 0 现在已经由以下内容收口：

- 新的 authority note
- staged phase plan
- 对冲突 Android planning 文档的清理
- 本文这份精确 baseline freeze
- 指向下一次实现会话进入 Phase 1 的当前 handoff

Android 下一条活跃阶段现在是 Phase 1：bootstrap promotion and reusable connection seam。
