# TaskMail VPS-First 多 PC Android-Facing 薄 Facade Authority（v0.1）

更新时间：2026-03-26

## 状态

本文是 `VPS-first 多 PC 控制面` 主线下，Android-facing 入口边界的 companion authority。

它负责冻结以下内容：

- 为什么 Android 主线不能直接把内部 `pc-control` 协议当成 app API 使用
- 为什么仍然不需要另起一套重协议
- Android-facing `薄 facade` 的最小职责边界
- `submit ack`、`session binding` 与内部 `command_ack` 的关系
- Android app auth 与内部 transport auth 的边界

同仓配套 contract：

- `docs/taskmail/planning/android/taskmail-vps-first-multi-pc-create-session-command-contract-v0.1.md`

它不替代以下文档：

- 主线方向 authority：`docs/taskmail/planning/android/taskmail-vps-first-multi-pc-authority-v0.1.md`
- 用户需求 authority：`docs/taskmail/planning/android/taskmail-vps-first-multi-pc-user-requirements-authority-v0.1.md`
- 页面级 API 需求：`docs/taskmail/planning/android/taskmail-vps-first-multi-pc-page-api-requirements-v0.1.md`
- 当前 Android 协议边界：`E:\projects\mail_based_task_manager\docs\current\android_runner_communication_contract.md`
- 当前 repo-side truth：`E:\projects\mail_based_task_manager\docs\current\README.md`
- `PC <-> VPS` 控制面协议：`docs/taskmail/planning/platform/taskmail-pc-vps-control-protocol-v0.1.md`

这些文档分别回答“主线往哪走”“页面需要什么”“今天已实现什么”“PC 与 VPS 如何说话”。
本文回答的是：

**Android-facing 主线入口应以什么边界接到现有控制面上。**

## 一句话结论

Android-facing 主线应采用：

- `薄 facade`
- 不另起重协议
- 不把内部 `pc-control` 原样暴露给 Android

更具体地说：

- Android 看到的是面向页面语义的 `CreateSessionCommand / session snapshot / timeline / replay`
- VPS 内部仍尽量复用既有 `command_dispatch -> command_ack -> event -> output_chunk -> result`
- 差异只补在 Android 不能直接承担、但页面又确实需要的那一小层边界上

## 为什么需要这份文档

到 2026-03-26 为止，跨仓已能确认两件事同时成立：

1. `VPS-first` 统一控制面是唯一 future-direction 主线。
2. 当前 live `pc-control` 仍是 `PC runtime <-> VPS` 内部控制面，不是已冻结的 Android-facing 正式 app API。

如果直接把“Android 连到某个 live WebSocket”误读成“Android 已经可以原封不动复用内部协议”，会立刻混淆下面三层：

- 内部 runtime 协议
- Android-facing 页面语义
- app credential / operator transport capability

本文的目的，就是把这三层重新拉开，但不把主线重新做重。

## 固定读法

从本文生效后，Android-facing 主线默认按下面的顺序理解：

1. Android 页面要的能力，优先按页面级 API 需求读取
2. Android-facing 对外入口，按本文的 `薄 facade` 读
3. VPS 内部到 `pc_control_runtime` 的转发与投影，尽量直接复用现有控制面字段和运行时
4. `pc-control` 本身继续按内部 owner 协议读取，而不是直接提升成 Android app protocol

## 薄 Facade 的定义

本文所说的 `薄 facade`，不是：

- 再造一套并列控制面
- 再发明一套与 `pc-control` 无关的新字段族
- 再做一条与主线并列的 owner lane

它指的是一层很薄的 Android-facing 投影边界，最小只做下面几件事：

1. 承接 Android-facing app auth / admission
2. 接收页面语义动作，例如 `CreateSessionCommand`
3. 把该动作映射成内部 `command_dispatch`
4. 补齐页面立即需要、但内部协议当前没有直接给出的绑定信息
5. 把内部 `event / output_chunk / result` 投影成 Android 可读的 snapshot / timeline / replay
6. 隐藏 `hello/hello_ack`、`connection_epoch`、schema negotiation、operator diagnostics 这类内部细节

## 为什么不能原封不动透传

“原封不动转发”在内部调试链路里可以成立，但它不能直接等于 Android-facing 主线。

原因固定为以下 5 条：

1. `pc-control` 的 client 身份是 `PC runtime`，不是 Android app。
2. 页面级需求要求 `CreateSessionCommand` 提交后拿到 `command_ack + session binding`，而当前内部 `command_ack` 并不天然承担这层语义。
3. Android 页面需要的是 `pc/workspace/session/run/result/artifact` 的读法、timeline 与 replay，不是原始 frame dump。
4. 当前 transport token 仍是 narrow、operator-provisioned capability，不应直接升级成通用 Android app credential。
5. 页面层已明确不应直接承担 envelope、`connection_epoch`、节点 token 与 terminal result 判定。

因此，Android-facing 主线若完全“裸透传”，并不是减少系统复杂度，而是把复杂度错误地下沉到客户端。

## 可直接复用的字段与形状

为了避免“薄 facade”长成第二套重协议，以下字段与语义应优先直接复用内部主线读法：

- `pc_id`
- `workspace_id`
- `session_id`
- `command_id`
- `result_id`
- `execution_policy = backend / profile / permission / backend_transport`
- `ack_status`
- `event`
- `output_chunk`
- `result`
- `effective_execution`
- `artifact`

换句话说：

- `薄 facade` 允许改边界
- 不鼓励改主线对象名
- 不鼓励再造一套平行 payload 语义

## 写入口的最小冻结

Android-facing 第一批正式写入口，默认只冻结到页面语义层，不冻结最终 endpoint 名称。

第一优先动作固定为：

- `CreateSessionCommand`

它的最小业务输入仍按页面 authority 读取：

- `pc_id`
- `workspace_id`
- `prompt`
- `execution_policy`

VPS 在 Android-facing 边界收到它之后，内部默认读法是：

- 做 app-facing admission / validation
- 生成或确认 `command_id`
- 映射成内部 `command_dispatch(new_task)`
- 交给 live `pc_control_runtime`

## Submit Ack 与 Session Binding

从本文生效后，Android-facing `new_task` 提交回包统一按两层理解：

1. `submit ack`
2. `session binding`

它们可以在实现上靠得很近，甚至在第一版里连续返回，但语义上不能混成一层。

### submit ack

`submit ack` 负责回答：

- 这次提交是否被接收
- 当前是 `accepted / accepted_but_queued / rejected`
- 若被拒绝，稳定错误家族是什么

### session binding

`session binding` 负责回答：

- 这次 `command_id` 最终绑定到了哪个 `session_id`
- 该 `session` 属于哪个 `pc_id + workspace_id`

### 固定规则

1. 不要求内部 `command_ack` 自己长成 Android-facing 完整回包。
2. 如果内部 `command_ack` 尚不包含 `session_id`，Android-facing facade 必须自己补齐 `session binding` 层。
3. Android 页面拿到的“立即回包”可以表现成一个组合回包，但文档语义必须仍按 `submit ack + session binding` 理解。

## 读入口的最小冻结

Android-facing 主线不只要能发 `new_task`，还要能读页面所需的最小状态面。

因此薄 facade 至少要逐步提供：

- `session snapshot`
- `timeline stream`
- `replay`
- `artifact download_ref`

这些能力的目标不是暴露原始协议历史，而是支撑：

- 首页聚合读
- Session 当前态
- 直播恢复
- 结果与文件查看

## Auth 边界

从本文生效后，auth 一律按下面的边界读取：

1. 当前内部 `transport_token` 继续只读作 operator / transport capability。
2. 它不自动升级成 Android-facing 通用 app credential。
3. Android-facing facade 必须有自己的 app-facing admission 边界。
4. facade 内部可以继续复用既有 transport verifier、host 或 runtime wiring，但这不改变外部 credential 的产品含义。

换句话说：

- 可以共用底层实现
- 不能共用外部产品语义

## Endpoint 与 Transport 规则

本文不冻结：

- 最终 endpoint URL 名称
- 一定是 REST 还是 WebSocket
- 是否复用现有 host / port

本文只冻结一条原则：

**Android-facing 主线可以很薄，但不能要求 Android 直接扮演 `pc-control` 的内部 client。**

因此后续无论最终入口是：

- 一个新的 app-facing endpoint
- 复用现有 host 上的新 path
- 复用现有 WebSocket infra 的 Android-facing wrapper

都必须先满足本文边界，而不是先追求“少一层路径”。

## 与 Debug / Operator 入口的关系

当前存在的 operator-only debug 注入口，仍按 debug 能力读取。

它的价值是：

- 验证 live `pc_control_runtime` 是否通
- 验证 `command_dispatch -> command_ack -> event -> result` 是否通

它不自动构成：

- Android-facing 正式入口
- Android app 可依赖的业务 API
- 对 `submit ack + session binding + snapshot/timeline/replay` 的正式冻结替代

## 明确不做的事

本文明确不支持把下面这些做法混进主线：

- 直接把 `/pc-control` 宣布成 Android app protocol
- 让 Android 页面或 ViewModel 自己管理 `hello/hello_ack`
- 让 Android 页面自己管理 `connection_epoch`
- 让 Android 直接持有 operator-facing transport capability 作为长期 app credential
- 为 Android-facing 入口重新发明一套与主线对象脱节的新字段族
- 为了“看起来零改动”而把页面语义缺口推给客户端补

## 立即产生的执行后果

从本文生效后，后续跨仓 first-pass 默认按下面的顺序推进：

1. 先冻结 Android-facing `CreateSessionCommand` 的 app-facing 语义
2. 再冻结 `submit ack + session binding` 的返回边界
3. 再接最小 `session snapshot / timeline / replay` 读法
4. 内部尽量继续复用 `pc_control_runtime` 与既有控制面字段
5. 若发现某个信息缺口影响 Android 页面语义，优先做薄补充，不回退成“直接裸透传”

## 一句话结论

**Android-facing 主线不是“另起一套重协议”，也不是“把内部 `pc-control` 裸露给 Android”；它是一层只补必要语义缺口的薄 facade。**
