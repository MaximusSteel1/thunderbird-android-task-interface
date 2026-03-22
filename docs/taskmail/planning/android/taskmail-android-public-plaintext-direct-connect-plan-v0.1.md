# TaskMail Android Public Plaintext Direct-Connect Plan（v0.1）

更新时间：2026-03-21

## 状态

本文是当前 public-IP plaintext direct-connect 方向在 Android 侧的 staged execution plan。

它把：

- `docs/taskmail/planning/android/taskmail-android-public-plaintext-direct-connect-authority-v0.1.md`

翻译成 Android 侧可执行的具体 phases。

它不会替代：

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/TASKMAIL-MAIL-RULES.md`

这些文件仍然描述当前 mail-first 实现及其验证证据。

## 目的

本文回答的是一个问题：

> 在“Android public plaintext direct-connect 成为预期主路径”这一新决策下，接下来应该如何分阶段推进，
> 同时又不假装仓库今天已经实现了这条路径？

## 当前规划位置

Android 侧当前的 planning position 是：

- direct-connect 是预期主路径
- 当前 mail path 仍然是必需 fallback
- 当前代码整体上仍偏 mail-first
- 规划需要用可评审的 slices 把当前实现桥接到新的 direct path

这份计划刻意保持务实。
它优先复用目前已经落地的 relay bootstrap seams，而不是等到未来再做一次 clean-room API-first redesign。

## 方向摘要

direct path 当前选定的 operational baseline 是：

- public host / IP
- configured port
- plaintext `http/ws`
- 用 `/healthz` 做诊断
- 用 `/relay` 做连接建立以及后续 business traffic
- token-based auth
- rollout 期间保留 mail fallback

这个 baseline 在 Android 侧 Phase 0 的精确冻结记录见：

- `docs/taskmail/planning/android/taskmail-phase0-public-plaintext-baseline-v1.md`

## 当前不再是 active plan 的假设

本文不再假设：

- Android direct-connect 必须等 DNS / TLS closeout 完成后才能开始
- direct-connect 必须一直停留在 debug-only
- Android 必须维持 mail-first 直到未来某个 API-only phase
- raw relay 的能力增长会被自动禁止

## Phase Map

- Phase 0: Direction reset and baseline freeze
- Phase 1: Bootstrap promotion and reusable connection seam
- Phase 2: Direct outbound action bridge
- Phase 3: Direct inbound update bridge
- Phase 4: Dual-stack parity and primary-path switch
- Phase 5: Long-term default hardening

## 当前阶段状态

- Phase 0 对 Android-side planning 层已经关闭。
- Phase 1 是当前活跃的 Android implementation-planning phase。

## Phase 0: Direction Reset And Baseline Freeze

### Goal

在实现开始前，先让 planning 层保持一致。

### Deliverables

- 新的 Android-side authority doc
- 本 staged plan
- README / index 更新，把较早的 mail-first / TLS-gated planning 线降级
- 一份给下一次实现会话使用的简洁 handoff note
- 一份冻结后的 baseline note：
  - `docs/taskmail/planning/android/taskmail-phase0-public-plaintext-baseline-v1.md`

### Closeout Condition

- 不再有任何 active Android-side planning doc 把 mail-first 或 TLS trust 写成控制 direct-connect 的主 gate
- 新的 direct-connect main-path 决策是显式的
- direct-connect baseline 已冻结在一份可评审的 Android-side note 中

## Phase 1: Bootstrap Promotion And Reusable Connection Seam

### Goal

把现有 relay debug / bootstrap seam 提升为可复用的 direct-connect 基础设施。

### Android Work

- 在当前 debug screen 之上提取或复用一个 connection manager
- 让 host / port / token / use-plaintext config 能从 internal TaskMail flows 中访问
- 支持：
  - `healthz`
  - connect
  - `hello -> hello_ack`
  - connection-state visibility
- 当 direct connect 不可用时，定义回退到 mail 的显式行为

### Guardrails

- 当前 debug screen 在过渡期可以继续保留，但 connection logic 不应再永久停留在 debug-screen-only
- 不要移除 mail path

### Closeout Condition

- Android 能足够稳定地建立 public plaintext relay session，并且这条连接能在 debug screen 之外复用
- failure states 对用户或调试面是可见的，并且可以回退到 mail fallback

## Phase 2: Direct Outbound Action Bridge

### Goal

让 direct path 能承载价值最高的若干 outbound user actions，同时 mail 仍保留为 fallback。

### First-Scope Actions

- new task
- plain continuation reply
- `/status`
- `/pause`
- `/resume`
- `/end`

### Android Work

- 与 PC / VPS 一起定义第一版 Android direct-connect outbound contract
- 把当前 TaskMail intent 序列化为 direct payloads
- 对 unsupported 或 failed direct actions，继续保留当前 mail compose path 作为 fallback
- 在 logs / debug surfaces 中显式记录 direct-send 与 mail-fallback 行为

### Closeout Condition

- 第一批 outbound actions 可以通过 direct path 发送
- direct failures 可以干净地回退到既有 mail path

## Phase 3: Direct Inbound Update Bridge

### Goal

让 Android 能足够消费 direct-side updates，以驱动现有 TaskMail UI。

### Android Work

- 定义 direct updates 如何映射到：
  - workspace summaries
  - session detail timeline
  - question states
  - paused / running / done / failed status
- 尽可能保留当前 local repository boundary
- 在过渡期间允许 mail-derived state 与 direct-derived state 并存

### Closeout Condition

- Android 可以从 direct path 维持有用的 read-side state，同时不丢失 mail fallback

## Phase 4: Dual-Stack Parity And Primary-Path Switch

### Goal

让 direct-connect 成为实际主路径，同时仍然保留可信的 rollback 能力。

### Android Work

- 把代表性的 direct-path outcomes 与现有 mail-derived outcomes 做对账
- 定义 mismatch triage rules
- 定义 rollback triggers，使受影响 flows 能切回 mail
- 当 parity 足够时，对 covered flows 把 primary route 切到 direct-connect

### Closeout Condition

- covered flows 默认走 direct path
- mail fallback 仍然可用且有验证

## Phase 5: Long-Term Default Hardening

### Goal

把选定的长期默认路径稳定下来，而不是把 direct-connect 当成临时实验。

### Android Work

- 记录长期 token handling 与 rotation expectations
- 强化 reconnect 与 stale-session 行为
- 收敛 direct-vs-mail 的主要边界问题
- 保持明确的 operator-facing 与 user-facing fallback 行为

### Closeout Condition

- direct path 足够稳定，能够被当作正常路由看待
- mail fallback 仍然是一个真实可操作的 escape hatch，而不是死代码

## 推荐的第一版实现切片

在完成这次 planning rewrite 之后，第一版实现 slice 应该是：

1. 保持现有 mail-first UI 与 repository 不变
2. 把当前 relay bootstrap code 提升为可复用的 internal connection seam
3. 增加一条很窄的 direct-send action path，优先选 `new task` 或 `/status`
4. 其他所有情况继续保留 mail fallback

这样第一步仍然是可逆的。

## 有意延后的事项

本文不要求第一版实现 slice 立即解决：

- 一个全新的、打磨好的 app-facing API 设计
- 移除 mail fallback
- 完整 read-side cutover
- 与已选 plaintext baseline 相矛盾的 secrecy 或 transport-hardening 决策

## 参考边界

当前仓库状态、fallback 行为与可执行验证证据，应从以下文档读取：

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/TASKMAIL-MAIL-RULES.md`

较早的 Android-side intermediate planning 集已经在 2026-03-21 清理中被裁剪，不再打算继续在本仓库里作为参考材料使用。
