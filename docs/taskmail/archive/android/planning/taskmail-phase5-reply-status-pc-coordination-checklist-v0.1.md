# TaskMail Phase 5 `reply` / `/status` PC Coordination Checklist（v0.1）

更新时间：2026-03-22

## 状态

- 本文承接：
  - `docs/taskmail/planning/android/taskmail-phase5-reply-status-direct-contract-prerequisites-v0.1.md`
  - `docs/taskmail/planning/android/taskmail-phase5-new-task-guarded-rollout-observation-plan-v0.1.md`
- 本文是 Android 侧给相邻 PC 侧的一份短版 review checklist。
- 本文不要求本仓直接改 PC 代码，也不授权 Android 直接进入 `reply` / `/status` 实现。

## 目的

把较长的 prerequisites note 压缩成一份可直接 review 的清单，方便快速回答：

1. 相邻 PC 侧当前需要先冻结哪些 current-protocol 问题？
2. Android 侧当前推荐的收敛选项是什么？
3. 哪些答案一旦明确，Android 侧才会起 direct `reply` / `/status` 的实现批次？

## Android 侧当前推荐

以下推荐是 Android 侧基于当前 authority 的收敛建议，不是替相邻 PC 侧单方面拍板。

### 推荐 1：第一批 scope 先收窄到最小子集

优先推荐：

- current-session plain reply
- current-session `/status`

而不是第一批就同时纳入：

- single-question quick answer
- multi-question `Answers:`
- paused `/resume`
- attachment-only continuation

原因：

- 这是当前最容易与 mail path 做一一对账的最小子集
- 它能先验证 session targeting、anchor 等价物和 `/status` canonical outcome
- 它避免第一轮 contract freeze 被 mode-driven reply 语义压得过宽

### 推荐 2：contract ownership 另起 session-action contract

优先推荐：

- 不扩写现有 `phase2-direct-outbound-contract-v1`
- 另起一份 direct post-creation session-action contract

原因：

- `new_task` 与 post-creation actions 的语义边界不同
- 这样更容易保持 Phase 2 既有 closeout 与 current evidence 读法稳定
- Android 侧也能更清楚地区分 `TaskNewTaskViewModel` 与 `TaskSessionDetailViewModel` 的 direct seam

### 推荐 3：第一批 target 先冻结为 current-session only

优先推荐：

- direct `reply` 和 direct `/status` 第一批只接受当前 detail session
- 不在第一批引入 targeted-session variant

原因：

- 这能先关闭 current-session routing 的等价命中问题
- 避免把 `/continue <session_id>`、`/status <session_id>`、cross-workspace targeting 混进第一批

## 需要 PC 侧先回答的问题

### A. current-protocol freeze

请先在 PC 侧 current-protocol 层回答并冻结：

1. 第一批 scope 是否接受 Android 侧的最小建议：
   - current-session plain reply
   - current-session `/status`
2. contract ownership 是否接受另起 session-action contract，而不是继续扩写 `phase2-direct-outbound-contract-v1`
3. 第一批 target boundary 是否冻结为 current-session only

### B. session identity / anchor 等价物

请明确：

1. direct current-session target 的最小 identity 是什么：
   - `session_id`
   - `thread_id`
   - 还是两者都要
2. direct `reply` 如何表达当前 mail reply 的 anchor 等价物
3. direct `/status` 第一批是否完全禁止 targeted-session variant

### C. canonical semantics

请明确：

1. direct `reply` accepted 后，后续结果是否仍按当前 canonical mail truth layer 产出
2. direct `/status` accepted 后，是否仍产出当前 canonical status mail
3. 如果仍产出，subject / terminal semantics 是否保持与 mail path 完全一致

### D. error classification

请至少明确 direct post-creation action 的 machine-readable 分类：

- `fallback_required`
- `switch_blocker`
- `hard_stop`

Android 侧需要这套分类，才能沿用当前 `new_task` 的 rollback / mismatch discipline。

### E. closeout artifact plan

请至少明确后续 closeout 是否能保留以下字段：

- `action_type`
- `target_session_identity`
- `request_id`
- `ingress_message_id`
- `terminal_mail_subject`
- `last_summary`
- same-run bind readout

## Android 侧在拿到答案前不会做的事

- 不把 `TaskSessionDetailViewModel` 接到 direct seam
- 不猜 direct `reply` / `/status` packet schema
- 不把 quick answer、多题 `Answers:`、paused `/resume`、attachment continuation 自动打包进第一批
- 不把 mail serializer 改成 direct 专用 serializer

## 通过条件

如果相邻 PC 侧能把上面 A-E 回答到 current-protocol / closeout 读法层，Android 侧就可以进入下一步：

- 起 direct `reply` / `/status` 的 Android 实现 planning
- 明确 focused tests
- 明确 formal-host live closeout 入口

在此之前，这份 checklist 的目标不是催实现，而是先把跨仓冻结问题说清楚。
