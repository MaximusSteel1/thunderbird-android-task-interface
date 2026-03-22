# TaskMail Phase 4 Mismatch Ledger（Android 侧 skeleton + 首轮 readout）

更新时间：2026-03-22

## 状态

- 本文是 Android 侧对 `phase4_mismatch_ledger.md` 的首版 skeleton，加上第一轮 Android/PC 三场景矩阵对账
  readout。
- 当前仅针对 `new_task` 预留记录形状。
- 本文当前先冻结字段和严重度，不宣称已经发现或关闭了全部 mismatch。

## 当前 Readout

- 截至 2026-03-22，Android 侧首轮 `new_task` 三场景 matrix reconciliation 尚未形成需要立即登记的
  Android-side confirmed mismatch。
- Android 正式 flow 上的 `direct accepted`、`fallback_to_mail`、`hard_rejection_stop` 现在都已有 focused
  automated evidence，并且都已有 2026-03-21 live-device closeout 对应样本，因此当前空表不是因为 Android 侧还没有
 任何 Phase 4 证据。
- 当前空表同样不表示“Android / PC 已全部对齐”。
  仍待继续补齐的 shared evidence 包括：
  - 三份 shared artifacts 在真实操作流里的稳定消费方式
  - latest direct evidence 与后续 canonical mail outcome 的自动绑定
- `thread_083`、`thread_093`、`thread_094` 这三行现在已经有正向 same-run parity 样本，因此“同一 run 对账”不再只是
  局部读数，而是已经覆盖当前 `new_task` matrix 的 direct / fallback 主行。
- `thread_084` 当前保留到的 Android live artifact 是更早的 `Unknown + prompt` workspace card；但这条现在应读作
  历史 retained-artifact gap，已经被 `thread_094` 替代，不再是当前 active blocker，也不是 confirmed mismatch。
- 当前更稳妥的做法仍是：没有 machine-readable evidence 的差异先不要升级为 ledger item。

## 严重度冻结

当前严重度先冻结为三档：

- `observe_only`
- `fallback_required`
- `switch_blocker`

## 字段形状冻结

本 ledger 先固定使用以下字段：

- `mismatch_id`
- `covered_flow`
- `scenario`
- `observed_behavior`
- `expected_behavior`
- `severity`
- `owner_repo`
- `current_status`
- `evidence`
- `rollback_impact`

`owner_repo` 当前建议只使用：

- `android`
- `mail_runner`
- `shared`

## 日常登记规则

- 先更新 parity checklist，再决定是否进入 ledger；没有 checklist row 的差异不直接登记。
- 没有完整 `daily_closeout_bundle` 的样本先留在 checklist，不创建 ledger item。
- `daily_closeout_bundle` 当前至少包含：sender-account-scoped latest send evidence、Android terminal-summary artifact、PC canonical outcome artifact；若同 run 已有 `canonical_summary.json`，还应优先记录 `request_id`、`ingress_message_id`、`last_summary`。
- 只有同时具备 `observed_behavior` 与 `expected_behavior` 的 machine-readable evidence 时，才创建 ledger item；至少要有 Android send-side 或 terminal-summary 证据，以及 PC canonical mail outcome。
- 对 `direct accepted` 行，只有 `request_id` 或 `transportMessageId <-> ingress_message_id` 的 same-run bind 已成立时，才允许把 `accepted_without_expected_outcome` / `summary_outcome_drift` 升级进 ledger。
- 若样本只剩 `last_summary token` 弱对齐，或 canonical outcome 还没完成 bind，先记 checklist open note，不直接升为 mismatch。
- `thread_084` 这类 retained-artifact gap 继续留在 checklist，不直接进 ledger，因为它无法区分“终态没对齐”与“终态已对齐但 Android terminal dump 未留存”。
- `severity = observe_only` 用于已见漂移但暂不影响 fallback 可用性或 switch 判定。
- `severity = fallback_required` 用于 direct path 暂不能放大，但 mail fallback 仍必须继续保留且可执行。
- `severity = switch_blocker` 用于 user-visible summary、expected outcome、或 direct/mail 边界已经出现高风险漂移，必须阻止 `direct-default` 评估。
- ledger 留空是允许状态；空表只表示当前没有 confirmed mismatch，不表示 checklist / trigger 已全部收口。

## Ledger

| mismatch_id | covered_flow | scenario | observed_behavior | expected_behavior | severity | owner_repo | current_status | evidence | rollback_impact |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |

## 备注

- 当前表格留空是刻意的：先把字段与严重度冻结，再由第一轮 parity evidence 回填实际 mismatch。
- 本轮 Android-side readout 后继续留空，同样是刻意的：当前更需要继续补 shared evidence，而不是凭主观感觉预登记
  “可能存在的差异”。
- `thread_084` 的 retained-Android-summary 缺口目前仍不单独登记为 mismatch item；而在 `thread_094` 已补出同等级
  fallback-row same-run parity 样本后，这条更应被视为历史留存不完整，而不是当前需要升格的 drift。
- 如果某条差异只靠“人工看日志感觉不对”，但没有 machine-readable evidence，优先先补证据口径，不要直接把它升级成 switch blocker。
