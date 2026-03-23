# TaskMail Phase 5 `[SYNC]` Project List Android 实施规划（v0.1）

更新时间：2026-03-23

## 状态

- 本文是 `[SYNC] Project list` 主线的 owner planning doc。
- 本文负责维护 direct-first request、canonical mail result、waiting UI 与 closeout 读法。
- 这条主线是本轮清理后从 handoff 正式提升出来的 active planning。

## Read First

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/TASKMAIL-MAIL-RULES.md`
- `docs/taskmail/planning/android/taskmail-next-development-plan-v0.2.md`

## 当前 scope

当前 `[SYNC]` 主线只做：

- formal-host `Project list` 请求入口
- direct-first request
- canonical `[SYNC] Project Folder List` mail 结果面
- `Use this repo` 回填
- waiting UI 与有限 follow-up refresh

当前明确不做：

- relay-native 结果协议
- `[SYNC]` 投影进 TaskMail session/detail
- 多账号 sender-address 严格协商
- 移除 mail result truth layer

## 当前事实

截至 2026-03-23，这条主线应这样读：

- `Project list` 页面可稳定读取并渲染最新 `[SYNC] Project Folder List`
- `Use this repo` 可稳定回填到 `New task`
- `[SYNC]` 仍不进入 TaskMail session/detail 投影
- direct `[SYNC]` request 当前已经进入主路径
- relay `packet_ack` 已恢复到亚秒级
- 页面可显式显示 waiting 状态，并会在有限窗口内继续 `checkMail`
- 当前没有新的本机 mail fallback 证据

## 当前 open gap

当前未闭环的重点不是 Android request path，而是同一轮结果回流时间线：

- direct request 何时进入 relay
- relay / PC 何时开始处理
- canonical `[SYNC] Project Folder List` 何时回到用户邮箱
- Android 当前 `30s + 90s` follow-up 是否足够覆盖真实链路延迟

只要这个时间线还没有稳定闭合，就不能把当前状态误写成“`[SYNC]` 已完全关单”。

## 当前建议

1. 继续把 `[SYNC]` 读成 `direct request + canonical mail result`
2. 优先拿到同一 `request_id` 的端到端时间线
3. 如果分钟级回流是常态，再决定是否扩大 Android follow-up refresh 窗口
4. 在拿到更强 closeout 前，不要把 `[SYNC]` 拉进 TaskMail session 投影，也不要扩到多账号协商

## 当前结论

`[SYNC] Project list` 现在已经是独立 active 主线；它不再只是 bootstrap 辅助边角料，也不能继续只靠 handoff 描述。
