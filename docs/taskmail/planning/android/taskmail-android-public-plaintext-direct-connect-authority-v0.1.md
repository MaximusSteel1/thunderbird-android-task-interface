# TaskMail Android Public Plaintext Direct-Connect Authority（v0.1）

更新时间：2026-03-21

## 状态

本文是 2026-03-21 选定的 public-IP plaintext direct-connect 方向在 Android 侧的当前宏观规划 authority。

它负责冻结以下内容：

- Android TaskMail 近期与长期连接方向的预期
- 哪些较早期假设现在已经退役
- 在新路径建设期间必须保留的 fallback 边界
- 哪些文档仍然是 implementation-truth，哪些文档属于 future-direction authority

它不会替代以下当前 implementation-truth 或 protocol-truth 文档：

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/TASKMAIL-MAIL-RULES.md`
- `E:\projects\mail_based_task_manager\docs/current/mail_protocol.md`
- `E:\projects\mail_based_task_manager\docs/current/android_runner_communication_contract.md`

这些 current-truth 文档仍然描述“今天已经实现了什么”。
本文定义的是 planning 层从现在开始应当假定的方向。

## 目的

本文用于在 Android 侧冻结一条新的跨仓方向：

- Android direct-connect 成为 TaskMail 预期的主路径
- 第一条 direct path 允许使用当前 public plaintext relay 入口
- 当前 mail path 仍然必须保留，作为 fallback 与兼容通道
- 先前的 mail-first / TLS-gated direct-connect 假设不再是活跃 planning baseline

这是一次有意为之的方向重置。
它不是在声称仓库已经实现了新的 direct-connect 产品路径。

## 当前固定假设

除非未来有新的 authority 文档重新打开，下列假设现在已经对 Android-side planning 固定：

1. Android TaskMail 预期的主路径是 `Android -> VPS`。
2. Public plaintext transport 在当前工作流中被接受为默认选择，并且相关安全与部署权衡需要显式承认。
3. 在 direct-connect 的 parity 与 rollback 行为被显式验证前，当前 mail path 必须继续保留为 fallback。
4. 基于 token 的 transport authentication 可以作为 direct-connect 早期阶段的可接受方案。
5. 当前 public relay transport 可以作为本阶段 Android 面向的 direct-connect 实际边界。
6. PC 仍然是 task execution truth；direct-connect 变更不会把任务执行迁到 VPS。
7. 在代码实际变化之前，当前 implementation-truth 文档仍然对仓库状态保持 authority。

## 已明确退役的假设

下列较早期假设不再是本仓库中的 active planning authority：

1. `Android must remain mail-first for now.`
2. `The stable live connection target is PC -> VPS only.`
3. `Raw /relay is not an Android product protocol.`
4. `Public DNS + stock-client-trusted TLS are required before Android direct-connect work may begin.`
5. `Android must not hold relay transport credentials in the direct path.`

这些假设在历史上仍然重要，因为它们能解释旧文档与旧代码边界，但在 2026-03-21 的决策之后，它们已经不再是
被选中的 planning baseline。

## 选定的产品边界

对当前这条 planning 线，选定的产品边界是：

- Android 可以直接连接 public VPS endpoint
- 连接在近期可以使用 plaintext transport，并且按当前选择，它也是被接受的默认姿态，除非后续 authority
  文档改变这个立场
- direct path 允许从 debug-only probing 扩展到主交互 TaskMail path
- 在 direct 与 mail 行为被证明足够对齐之前，mail fallback 仍然是必需项

这意味着 Android-side planning 层现在被允许：

- 把 direct transport 视为不止是 debug/bootstrap seam
- 把 direct-create、direct-reply 与 direct-status flows 规划成真实产品切片
- 规划从 direct path 提供 read-side state updates，而不必等到未来某个 API-only phase

## Guardrails

这次方向变化并不意味着所有约束都被移除。
以下规则仍然有效：

- 不要在 `CURRENT-STATUS` 或 validation ledger 中误述当前实现状态
- 不要把 tokens、secrets 或 operator credentials 提交进仓库
- 在 parity、fallback triggers 与 rollback 行为没有写清楚之前，不要移除 mail fallback
- 不要让 direct-connect planning 在没有对应 contract note 的前提下，随意改写既有 TaskMail 业务语义
- 关于 plaintext 的决定必须在文档和实现边界中保持显式，而不是继续藏在面向 TLS 的措辞后面，导致与当前选定
  方向不一致

## 本 authority 立即改变的内容

以下 planning 结果现在立即生效：

1. 旧的 mail-first / TLS-gated Android direct-connect blockers 不再是启动 Android direct-connect planning 的阶段门槛。
2. Android relay/bootstrap 工作不再被限制为永远只能停留在 debug-only。
3. 当前活跃 phased plan 现在必须围绕“direct-connect 主路径 + mail fallback”重写。
4. 较早的 Phase 0 relay TLS trust 记录仍然是有效历史证据，但它们不再阻塞这次 newly chosen plaintext
   direct-connect 方向。

## Android 侧立即产生的规划后果

这份 authority 在仓库侧立即带来的结果是：

1. Android direct-connect baseline 被冻结在一份可评审说明里。
2. Android 分阶段执行计划围绕 direct-connect 主路径与 mail fallback 重新组织。
3. 较早的 mail-first / TLS-gated planning 文档不再属于 active docs set。
4. 在代码变更真正落地前，当前 implementation-truth 文档保持不变。

## 跨仓对齐说明

本文是 Android 侧的 authority reset。

相邻的 PC-side authority 与协同执行文档，随后也已经围绕相同的 public plaintext direct-connect 方向完成重写。

可以把它们当作跨仓 planning references 使用，但不要让它们覆盖本仓库当前的 implementation-truth 或
protocol-truth 文档。

## 清理结果

较早的 mail-first / TLS-gated Android planning 集已经在 2026-03-21 清理过程中被有意裁剪。

当前方向现在应从本文、配套 staged plan，以及 current implementation-truth 文档中读取。
