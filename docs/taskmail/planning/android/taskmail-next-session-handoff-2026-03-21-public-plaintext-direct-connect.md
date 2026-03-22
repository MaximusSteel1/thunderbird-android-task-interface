# TaskMail Next Session Handoff（2026-03-21，public plaintext direct-connect reset）

## 当前决策

截至 2026-03-21，Android-side planning 方向已经重置为：

- Android public direct-connect 作为预期的主 TaskMail 路径
- public plaintext transport 作为选定 baseline 被接受
- mail 继续保留为 fallback

Android-side planning 在 Phase 0 上的收口现在已经到位。
这条线也不再只是 doc-only。
Android-side 的 Phase 1 bootstrap-manager、bootstrap-classification 与 `new task` bootstrap-reuse slices 已经进入
仓库。此前把 bootstrap 困在 debug surface 之后的那个 Android-side repository 依赖，已经不再是主 blocker；但
direct business transport 与 live plaintext validation 仍然没有收口。

## Read First

1. `docs/taskmail/planning/android/taskmail-android-public-plaintext-direct-connect-authority-v0.1.md`
2. `docs/taskmail/planning/android/taskmail-android-public-plaintext-direct-connect-plan-v0.1.md`
3. `docs/taskmail/planning/android/taskmail-phase0-public-plaintext-baseline-v1.md`
4. `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
5. `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
6. `docs/TASKMAIL-MAIL-RULES.md`

## 本次会话改了什么

- planning README 已更新，指向新的 active planning docs
- 为 public plaintext direct-connect 决策新增了一份 Android-side authority doc
- 为同一方向新增了一份 staged plan
- 新增了一份紧凑的 Phase 0 baseline note，用来冻结 Android-side public plaintext endpoint 的精确形态
- Android-side planning 层现在把 Phase 0 视为已关闭，把 Phase 1 视为活跃阶段
- 在当前 relay debug ViewModel 之上新增了一个可复用的 `RelayBootstrapManager`
- 当前 debug relay screen 现在通过这个 manager 委托 config load/save 与 `healthz` / connect / disconnect，而不再
  直接持有那些底层依赖
- 新增了结构化 `RelayBootstrapStatus` / `RelayBootstrapResult` 模型，使 Android relay bootstrap 在仓库代码里
  就能对 `hello_ack` 及代表性失败结果做分类，而不再只依赖原始异常字符串
- bootstrap manager 现在暴露了这条 classification path，并且 focused manager coverage 已经锁定代表性的
  `not_configured`、`invalid_http_response`、`token_id_mismatch`、`unauthorized` 与 `hello_ack` 结果
- 正式 `new task` ViewModel 现在会在 mail send 之前复用 bootstrap result，记录最后一次 bootstrap
  classification，断开临时的 `hello_ack` preflight connection，并在 direct bootstrap 不可用时显式发出
  mail-fallback success message
- focused manager coverage 已落地，并且重新跑过了 clean 的窄范围 Gradle 验证

## 本次没有改什么

- 没有新增 direct business-action transport
- 没有把 current-status 或 validation-ledger 里的事实改写成 future tense
- 没有针对 live plaintext VPS path 跑新的 device validation

## 下一步的具体动作

1. 保持已冻结的 Phase 0 baseline 稳定，除非未来有新的 authority doc 显式改写它
2. 与相邻的 PC/VPS 仓库一起冻结 `new task` 的第一版 Android direct outbound contract
3. 把当前 `new task` 的 preflight-plus-mail 路径替换成真实的 direct-send 路径，同时仍保留在 bootstrap 或
   send 非成功情况下的 mail fallback
4. 决定 `new task` 之后下一条纳入覆盖的 direct action：
   - `/status`
   - plain continuation reply
5. 在第一版真实 direct outbound slice 落地后，补 device/manual validation，验证 live public plaintext path
6. 从第一版 direct implementation slice 开始就持续保留 mail fallback

## Scope Boundary Reminder

较早的 mail-first / TLS-gated Android planning docs 已经在 2026-03-21 的清理中被裁剪。
不要再试图从那些已删除的中间 planning notes 里恢复方向；请改用新的 authority / plan pair，以及当前
implementation-truth docs。
