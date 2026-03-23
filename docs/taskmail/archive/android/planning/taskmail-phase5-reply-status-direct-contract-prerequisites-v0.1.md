# TaskMail Phase 5 `reply` / `/status` Direct Contract Freeze Prerequisites（v0.1）

更新时间：2026-03-22

## 状态

- 本文承接：
  - `docs/taskmail/planning/android/taskmail-phase5-new-task-guarded-rollout-observation-plan-v0.1.md`
  - `docs/taskmail/archive/android/handoff/taskmail-next-session-handoff-2026-03-21-reply-direct-send-seam.md`
- 本文不把 `reply` / `/status` 提前拉进 direct 实现。
- 本文只回答一个更窄的问题：

> 当 `new_task` 的 Phase 5 guarded review 已进入 observation 边界后，`reply` / `/status` 若要进入 direct contract freeze，Android 侧还需要哪些 current-protocol 前提、范围裁剪和相邻 PC 侧配合？

## 先读这些文档

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/TASKMAIL-MAIL-RULES.md`
- `docs/taskmail/planning/android/taskmail-phase5-new-task-guarded-rollout-observation-plan-v0.1.md`
- `docs/taskmail/archive/android/handoff/taskmail-next-session-handoff-2026-03-21-reply-direct-send-seam.md`
- `E:\projects\mail_based_task_manager\docs/current/mail_protocol.md`
- `E:\projects\mail_based_task_manager\docs/current/android_reply_method_rules.md`

## 当前基线

截至 2026-03-22，以下读法仍然成立：

- Android 仓里当前唯一已闭环的 direct business slice 仍是 formal-host `new_task`
- `reply`、quick answer、多题 `Answers:`、paused `/resume`、attachment continuation 与 `/status` 当前仍走 mail-based canonical path
- `TaskSessionDetailViewModel` 当前没有复用 `RunTaskMailDirectOrFallback` 或 `SendTaskMailDirectNewTask`
- 当前 Android 侧已经有可执行验证，证明 mail path 下的 session-detail 行为成立：
  - plain-text reply
  - reply attachments
  - single-question quick answer
  - multi-question `Answers:`
  - paused `/resume`
  - `/status`

因此，下一步不是“把现有 detail send path 接到 direct seam 看看”，而是先决定：哪些 mail 语义必须先被 current-protocol 层明确冻结，Android 才能开始 direct contract 设计。

## 范围内

- `reply` / `/status` direct contract freeze 的进入条件
- Android 当前 mail 语义里哪些点必须原样保留
- 第一批 direct post-creation action 的范围裁剪建议
- 继续推进时需要 PC 侧先提供的 current-protocol / artifact 配合项

## 范围外

- Android 生产代码改动
- PC 仓生产代码改动
- 直接定义最终 direct packet schema
- 把 `/pause`、`/end`、`/rerun`、`/kill`、cross-workspace targeting 一并拉进第一批 direct contract
- 改写当前 mail reply serialization 规则

## 进入 contract freeze 前必须先固定的事项

### 1. 先固定第一批 flow scope

在 direct post-creation action 进入 freeze 之前，PC / Android 必须先明确第一批到底只覆盖哪一层。

当前建议至少做出二选一，而不是边实现边扩：

- 选项 A：只冻结 current-session plain reply + current-session `/status`
- 选项 B：冻结当前 Android detail surface 上已经显式暴露的最小完整子集：
  - plain reply
  - single-question quick answer
  - multi-question `Answers:`
  - paused `/resume`
  - attachment-only continuation
  - `/status`

如果这个范围不先固定，Android 侧无法判断：

- 是否能只替换 transport decision 而不改 composer / serializer
- 哪些 fallback / hard-stop 规则应由同一 contract 统一承担
- 哪些 live 验证必须算在同一批 closeout 里

### 2. 先固定 contract ownership

PC 侧 current-protocol 层需要先明确：

- direct `reply` / `/status` 是扩展现有 `phase2-direct-outbound-contract-v1`
- 还是另起一份独立的 session-action contract

在这件事冻结前，Android 侧不应猜测 envelope 结构或提前把 `TaskSessionDetailViewModel` 接到 direct seam。

### 3. 先固定 session identity / reply-anchor 等价物

mail path 当前的命中优先级是：

1. `In-Reply-To`
2. `References`
3. state capsule
4. `[S:session_id]`

如果 direct path 要承接 `reply` / `/status`，PC 侧 current protocol 至少要明确：

- direct action 最小 target identity 是什么
  - `session_id`
  - `thread_id`
  - 还是更窄的 current-session only target
- direct `reply` 如何等价表达当前 mail reply 的 anchor 语义
- direct `/status` 是否允许 targeted session variant，还是第一批只接受当前 detail session

在这些字段冻结前，Android 侧不能证明 direct path 与 current mail routing 是“等价命中”，只能做不可信的近似映射。

### 4. 先固定 mode-driven reply semantics

当前 Android / PC mail authority 已经固定以下语义，direct contract 不能把它们压扁成 generic free text：

- plain reply continuation
- single-question quick answer
- multi-question structured `Answers:`
- paused `/resume`
- attachment-only continuation
- `/status`

特别要先冻结的点包括：

- multi-question 仍必须发送结构化 `Answers:`，不能退回自由文本
- quick-answer UI 可以显示 label，但 wire 上仍要发送 canonical answer key / value
- paused session 仍必须显式 `/resume`
- attachment 仍是补充输入，不是结构化答案替代物

### 5. 先固定 `/status` 的 canonical outcome

`/status` 当前是 mail control plane 上的 current-state query。

如果要进入 direct contract freeze，PC 侧 current protocol 需要先明确：

- direct accepted 的 `/status` 是否仍产出当前 canonical status mail
- 如果仍产出，mail-visible subject / terminal semantics 是否完全不变
- Android direct path 是否只负责“发命令”，后续结果仍按 mail truth layer 消费

在这件事写清楚前，Android 侧不能把 direct `/status` 当成只是“换一个 transport”这么简单。

## 建议的第一批非目标

为了避免第一批 contract freeze 失控，以下内容当前建议保持 scope 外：

- `/pause`
- `/end`
- `/rerun`
- `/kill`
- `/new`
- `/sessions`
- same-workspace targeted commands beyond当前 detail session
- cross-workspace switching

这些动作不是永远不做，而是当前不应和第一批 `reply` / `/status` direct freeze 混成一个不收口的大包。

## Android 侧必须保持不变的语义

如果未来进入 direct contract freeze，Android 侧至少要保住以下现有 mail 语义：

- 不改现有 reply subject identity 保留规则
- 不改 existing composer / serializer 对 `Answers:`、`/resume`、`Permission:`、`Timeout:`、`Task:` 等字段的纯文本输出
- 不把 hard rejection 静默降级成 mail fallback
- 不因为 direct 化就删除 reply attachments、draft retention、或 current local validation

换句话说，Android 侧更合理的实现目标仍是：

- 复用当前 `TaskSessionDetailViewModel` 产生的 canonical body
- 只替换 send-side transport decision
- 不在 direct slice 里重写协议语义

## 需要 PC 侧先配合的事项

下面这些是 Android 侧继续推进 contract freeze 前，对相邻 PC 侧的明确要求。

### A. current-protocol freeze

PC 侧需要把以下问题提升到 `docs/current/*` 层，而不是停留在 planning：

- direct post-creation actions 的 contract ownership
- 第一批 action scope
- current-session vs targeted-session boundary
- `reply` / `Answers:` / `/resume` / attachment continuation / `/status` 的 direct wire semantics
- `/status` direct accepted 后的 canonical mail outcome 约定

### B. machine-readable error classification

PC 侧需要明确 direct post-creation action 的错误分类，至少能区分：

- `fallback_required`
- `switch_blocker`
- `hard_stop`

否则 Android 侧无法沿用当前 `new_task` Phase 4/5 的 mismatch / rollback discipline。

### C. evidence / closeout artifacts

如果 direct post-creation contract 真的进入 freeze 或后续 live closeout，PC 侧需要预留与 `new_task` 同等级的 machine-readable 读法，例如：

- action type
- target session identity
- `request_id`
- `ingress_message_id`
- `terminal_mail_subject`
- `last_summary`
- same-run bind readout

Android 侧不要求现在就实现这些字段，但如果 PC 侧完全没有 closeout artifact 规划，Android 侧就不应进入实现评审。

## 进入 Android 实现前的最小通过条件

只有当以下条件同时成立，Android 侧才应该考虑 direct `reply` / `/status` 的实现批次：

1. `new_task` guarded observation 仍未触发回退
2. 第一批 post-creation action scope 已冻结
3. contract ownership 已冻结
4. session identity / reply-anchor 等价物已冻结
5. mode-driven reply semantics 已冻结
6. `/status` 的 canonical outcome 已冻结
7. PC 侧至少给出基本 error classification 与 closeout artifact 方案

在此之前，Android 侧当前最合理的动作仍是：保留 mail-based detail send path，不提前接 direct seam。

## 当前结论

Phase 5 继续推进后，`reply` / `/status` 的下一步不是实现，而是 direct contract freeze prerequisites。

对 Android 侧来说，这一步的意义是：

- 把“不能做什么”继续守住
- 把“想做时必须先拿到什么”写清楚
- 把对 PC 侧的配合要求从口头提醒提升成 reviewable planning 输入
