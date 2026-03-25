# TaskMail 执行策略附录（v0.1）

更新时间：2026-03-25  
状态：平台层 companion appendix；用于冻结 `execution_policy` 读法，不替代 current-truth 文档

> 说明：
>
> 本文是 `VPS-first 多 PC 控制面` 主线下的执行策略附录。
>
> 它负责把 `backend / profile / permission / backend_transport / resolved_model` 的控制面语义收口成同一套稳定读法。
>
> 本文不声称当前代码已经全部实现这些字段。
>
> 当前实现事实仍以：
>
> - `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
> - `docs/TASKMAIL-MAIL-RULES.md`
> - `E:\projects\mail_based_task_manager\docs\current\mail_protocol.md`
> - `E:\projects\mail_based_task_manager\docs\plans\backend_permission_control_plan.md`
>
> 为准。

## 1. 文档目的

本文只回答一个问题：

**在 `VPS-first 多 PC 控制面` 方向下，`Codex / OpenCode` 的模型选择与执行权限应如何进入统一控制面。**

本文聚焦：

- `execution_policy` 的稳定字段形状
- `profile -> 模型` 的控制面语义
- `permission -> backend-specific 执行投影` 的边界
- `PC` 能力上报、`VPS` 校验、`result` 审计回报

本文不负责：

- Android UI 的具体选择器设计
- 当前 mail-first 协议的完整事实说明
- 多用户 ACL 或细粒度组织权限系统

## 2. 本附录冻结的结论

当前主线应固定以下结论：

1. `backend / profile / permission / backend_transport` 必须进入控制面主协议，不能继续散落在 mail-era 字段或本地脚本参数里。
2. `profile` 解决“模型档位选择”，不直接把 raw model id 作为用户主输入。
3. `permission` 解决“执行权限档位”，不等于能力开关，也不应隐式突破全局能力边界。
4. `resolved_model` 是执行侧回报字段，不是控制面主输入。
5. `Codex` 与 `OpenCode` 的底层实现可以不同，但外层控制面字段必须保持同一语义。

## 3. Canonical 对象

建议 `execution_policy` 固定为：

```json
{
  "backend": "codex",
  "profile": "strong",
  "permission": "highest",
  "backend_transport": "sdk",
  "resolved_model": null
}
```

字段说明：

- `backend`
  - 选择执行后端族
  - 第一版固定为 `codex | opencode`
- `profile`
  - 选择稳定模型档位
  - 例如 `fast | strong | vision | android`
  - 这些 label 只是例子，不要求每台 PC 完全相同
- `permission`
  - 选择执行权限档位
  - 第一版固定为 `default | highest`
- `backend_transport`
  - 选择执行侧路由
  - 例如 `cli | sdk`
  - 属于实现维度，不是用户主语义
- `resolved_model`
  - 表示目标 PC 最终解析出的真实模型
  - 只作为执行侧回报与审计字段

## 4. 能力上报与路由校验

### 4.1 `PC` 能力上报

`PC` 至少应上报：

- `supported_backends`
- `profile_catalogs`
- `permission_modes`
- `backend_transport_modes`

其中：

- `supported_backends` 表示这台 PC 当前可运行哪些后端
- `profile_catalogs` 表示每个 backend 支持哪些 profile label
- `permission_modes` 表示这台 PC 当前支持哪些权限档位
- `backend_transport_modes` 表示每个 backend 当前可用的 transport

### 4.2 `workspace` 能力上报

`workspace.capabilities` 可以比 `PC.capabilities` 更窄。

也就是说：

- 一台 PC 可能总体支持 `codex + opencode`
- 但某个具体 `workspace` 只允许其中一部分 backend 或 profile

### 4.3 `VPS` 派发前校验

`VPS` 在 `command_dispatch` 前至少应校验：

1. 目标 `pc_id` 是否在线且 `connection_epoch` 有效
2. 目标 `workspace_id` 是否从属于该 `pc_id`
3. `backend` 是否被目标 `PC/workspace` 支持
4. `profile` 是否在对应 backend 的 `profile_catalog` 中
5. `permission` 是否被支持
6. 若指定了 `backend_transport`，目标节点是否支持

若任一步失败，应显式拒绝，而不是静默回退。

## 5. 新任务与 Follow-up 继承规则

### 5.1 新任务

`new_task` 建议固定如下：

- 必须显式给 `backend`
- 可选给 `profile`
- 可选给 `permission`
- 可选给 `backend_transport`

若省略：

- `profile` 表示使用 backend 默认档位
- `permission` 表示使用 backend 默认权限
- `backend_transport` 表示使用执行侧默认 transport

### 5.2 Follow-up

对 `reply / status / pause / resume / kill` 这类 follow-up：

- 若未显式给 `backend`，默认继承当前 `session` 的 backend
- 若未显式给 `profile`，默认继承当前 `session` 的 profile
- 若未显式给 `permission`，默认继承当前 `session` 的 permission
- 若未显式给 `backend_transport`，默认继承当前 `session` 的 transport 或使用 backend 默认值

### 5.3 V1 边界

第一版建议固定：

- 不支持 running session 的 backend 切换
- 不要求所有 follow-up 都允许切 profile / permission
- 但控制面对象本身要先留出这些字段

## 6. `profile -> 模型` 规则

### 6.1 为什么 `profile` 不直接等于 model id

控制面不应把 raw model id 做成主输入，原因是：

1. 不同 backend 的 model 命名并不统一
2. 同一个 profile 在不同 PC 上可能映射到不同具体模型
3. 用户真正稳定需要的是档位语义，而不是每次手写模型名

### 6.2 `profile` 的正确读法

`profile` 应理解为：

- `fast`：更看重速度
- `strong`：更看重推理与改动质量
- `vision`：需要图像或多模态能力
- `android`：保留给面向 Android 任务的本地约定档位

这些 label 是控制面语义，不等于全局固定模型映射表。

### 6.3 `resolved_model`

`resolved_model` 必须由目标 `PC` 按本地配置解析并回报。

控制面规则应固定为：

- `command_dispatch` 不要求携带 raw model id
- `event/result` 可以回报 `resolved_model`
- `VPS` 可以持久化 `resolved_model` 做审计与回放
- `VPS` 不要求自己持有每台 PC 的完整 raw model map

### 6.4 失败语义

若 `profile` 无法在目标 backend 上解析到具体模型，应显式拒绝，例如：

- `profile_model_unresolved`

控制面不应在这种情况下静默回退到别的模型。

## 7. `permission -> 执行投影` 规则

### 7.1 固定语义

第一版建议固定三种语义：

1. 省略 `permission`
   - 新任务：使用 backend 默认权限
   - follow-up：继承当前 session 权限
2. `permission = default`
   - 显式恢复 backend 默认权限
3. `permission = highest`
   - 显式请求当前仓库允许的最高执行权限

### 7.2 与能力开关的关系

`permission` 不应隐式打开被全局关闭的能力。

例如：

- 若搜索能力在当前运行环境里被禁用
- 即使 `permission = highest`
- 也不应自动获得搜索能力

这条规则的目的，是把：

- “能力是否启用”
- “本次执行权限多高”

明确分成两层。

## 8. Codex 投影

### 8.1 `default`

- 保持当前 backend 默认行为
- `backend_transport` 可为 `cli` 或 `sdk`
- 其他能力，如搜索，仍由独立全局开关决定

### 8.2 `highest`

对 `Codex`，当前推荐投影为：

```text
--dangerously-bypass-approvals-and-sandbox
```

这表示：

- 跳过 approval
- 跳过 sandbox
- 这是当前控制面应认定的 Codex 最高权限语义

### 8.3 审计要求

`Codex` 实际执行后，`event/result` 建议回报：

- `backend = codex`
- `profile`
- `permission`
- `backend_transport`
- `resolved_model`

## 9. OpenCode 投影

### 9.1 `default`

- 保持当前 backend 默认行为
- 不额外注入提权 overlay

### 9.2 `highest`

对 `OpenCode`，当前推荐投影为：

**run-scoped merged config overlay**

也就是：

1. 在当前 run 目录生成临时配置
2. 基于当前基础配置做 merge
3. 只覆盖仓库关心的权限相关项
4. 只作用于当前 subprocess

### 9.3 必须守住的边界

`OpenCode` 的最高权限投影必须满足：

- 不直接覆盖用户全局配置
- 不因为提权而把 provider/model 基础配置冲掉
- 不把“当前机器上恰好存在的未知本地配置”直接当作 canonical 语义

### 9.4 审计要求

`OpenCode` 实际执行后，`event/result` 同样建议回报：

- `backend = opencode`
- `profile`
- `permission`
- `backend_transport`
- `resolved_model`

## 10. 请求值与实际生效值

控制面应区分两层：

- 请求值：`command.execution_policy`
- 实际生效值：`event/result.effective_execution_policy`

这样可以稳定回答两个不同问题：

1. 用户想要什么
2. 目标 PC 最终实际用了什么

这对以下场景都重要：

- profile 由默认值补全
- transport 由执行侧自动选择
- `resolved_model` 只在执行后才知道

## 11. 推荐拒绝码

第一版建议先固定这批拒绝码：

- `unsupported_backend`
- `unsupported_profile`
- `unsupported_permission`
- `profile_model_unresolved`

后续若需要，再单独扩：

- `unsupported_backend_transport`

但第一版不必把拒绝码一次性扩得过细。

## 12. V1 非目标

第一版执行策略明确不做：

- 不把 raw model id 作为用户主输入
- 不支持 running session 中途切 backend
- 不引入 per-tool 细粒度 ACL
- 不把 `permission` 读成“自动放开所有受限能力”
- 不要求 `VPS` 保存每台 PC 的完整本地模型配置细节

## 13. 一句话结论

**`VPS-first` 主线下，`Codex / OpenCode` 的模型选择与权限选择应统一收口为一个 `execution_policy` 对象：用户只选择稳定档位与权限档位，真实模型解析与 backend-specific 投影留在 `PC` 执行侧，并通过 `effective_execution_policy` 回报。**
