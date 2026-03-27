# TaskMail VPS-First 多 PC Environment Inventory Contract（v0.1）

更新时间：2026-03-27

## 状态

本文是 `VPS-first 多 PC` 主线下，Android-facing `environment inventory` 的 first-pass contract。

它负责冻结以下内容：

- Android-facing 环境库存读侧的最小业务语义
- `pc / workspace / capability` 的最小对象形状
- `online / offline / unknown`、`present / missing / stale` 等稳定状态家族
- 首页树形工作台与新任务页如何读取同一份环境库存
- Android-facing 环境库存与内部 `pc_hello / workspace_snapshot` 的最小映射关系

它不替代以下文档：

- 主线方向 authority：`docs/taskmail/planning/android/taskmail-vps-first-multi-pc-authority-v0.1.md`
- Android-facing facade authority：`docs/taskmail/planning/android/taskmail-vps-first-multi-pc-android-facing-facade-authority-v0.1.md`
- 页面级 API 需求：`docs/taskmail/planning/android/taskmail-vps-first-multi-pc-page-api-requirements-v0.1.md`
- Android-facing `CreateSessionCommand` contract：`docs/taskmail/planning/android/taskmail-vps-first-multi-pc-create-session-command-contract-v0.1.md`
- `PC <-> VPS` 控制面协议：`docs/taskmail/planning/platform/taskmail-pc-vps-control-protocol-v0.1.md`
- repo-side environment inventory facade requirements：`E:\projects\mail_based_task_manager\docs\plans\android_facing_environment_inventory_facade_requirements_v0.1.md`

这些文档分别回答“主线往哪走”“页面要什么”“怎么创建 session”“PC 与 VPS 如何说话”。
本文回答的是：

**Android-facing 主线到底应如何读取可选 `PC / workspace` 环境库存，而不是直接消费内部 `pc-control` 细节。**

## 一句话结论

Android-facing 环境库存固定读成一份薄投影：

- 一个读动作：`GetEnvironmentInventory`
- 一个主对象：`EnvironmentInventorySnapshot`
- 一个稳定层级：`pc -> workspace`

它的职责固定为：

- 给首页树形工作台提供环境锚点
- 给新任务页提供目标环境与可选 execution capability
- 给 Android 明确的 `online / offline / missing / stale` 读法

它不负责：

- 直接承载 `session` 列表
- 暴露原始 `pc_hello / workspace_snapshot` frame
- 让 Android 自己合并 `pc` 能力与 `workspace` 能力

## 范围

本文第一版只冻结以下内容：

1. 一个读动作：`GetEnvironmentInventory`
2. 一个返回对象：`EnvironmentInventorySnapshot`
3. `pc` 与 `workspace` 的最小字段和稳定状态
4. workspace 级 `effective execution capabilities`
5. 与内部 `pc_hello / workspace_snapshot` 的最小投影关系

本文第一版暂不冻结：

- 最终 endpoint URL
- 一定使用 REST / SSE / WebSocket 中的哪一种
- session 列表本身的 contract
- inventory 的实时增量 push 协议
- inventory 的本地缓存持久化格式
- 多账号环境下 inventory 的精细授权策略

## 设计原则

第一版 `environment inventory` contract 默认遵守以下原则：

1. Android 读取页面语义，不读取 raw `pc-control` frame。
2. `pc_id + workspace_id` 仍是环境路由主键。
3. `session` 列表继续由 session projection 提供，不混进 environment inventory。
4. 页面不自己做 `pc capability + workspace capability` 合并；workspace 必须直接给出 `effective` 结果。
5. `workspace missing` 是一等状态，不再只靠 UI 临时猜测。
6. `pc offline` 时仍允许返回最近缓存的 workspace，用于上下文和历史 session 定位。
7. `inventory stale / partial` 必须显式暴露，不能让 Android 靠时间戳自己猜。

## 1. 读动作 Contract

### 1.1 语义动作名

Android-facing 环境库存统一使用：

- `GetEnvironmentInventory`

它的语义是：

- 返回当前 Android 用户可见的 `pc` 列表
- 返回每台 `pc` 下当前可见的 `workspace` 列表
- 返回每个 `workspace` 当前可用于 `CreateSessionCommand` 的 `effective execution capabilities`

### 1.2 第一版最小输入

第一版不要求业务必填输入。

固定读法是：

- app admission 已在 facade 边界完成
- inventory 默认按当前 Android app 身份可见范围返回

### 1.3 第一版允许的可选查询提示

第一版允许但不强制以下可选提示：

- `include_offline`
- `include_missing_workspaces`
- `pc_ids[]`
- `workspace_ids[]`

固定读法：

- `include_offline`
  - 默认建议为 `true`
  - 用于首页树形保留离线 PC 的上下文锚点
- `include_missing_workspaces`
  - 默认建议为 `true`
  - 用于首页树形与历史 session 保留 `workspace missing` 节点
- `pc_ids[] / workspace_ids[]`
  - 只作过滤提示
  - 不改变对象语义

### 1.4 不要求页面提供的东西

第一版固定不要求 Android 页面提供：

- `connection_epoch`
- `message_id`
- `trace_id`
- `transport token`
- 原始 replay cursor

这些都属于 facade 内部或控制面内部复杂度，不应上翻到页面层。

## 2. 返回 Contract

### 2.1 顶层对象

Android-facing 返回统一按一个顶层快照对象理解：

- `EnvironmentInventorySnapshot`

第一版最少应包含：

- `snapshot_id`
- `generated_at`
- `inventory_state`
- `refresh_after_seconds`
- `pcs`

建议对象形状如下：

```json
{
  "snapshot_id": "env_snap_20260327_001",
  "generated_at": "2026-03-27T09:40:00Z",
  "inventory_state": "fresh",
  "refresh_after_seconds": 15,
  "pcs": []
}
```

### 2.2 顶层字段含义

- `snapshot_id`
  - 本次环境库存快照标识
  - 用于调试、日志和后续增量读取承接
- `generated_at`
  - 当前投影生成时间
- `inventory_state`
  - 当前快照整体状态
- `refresh_after_seconds`
  - 给 Android 的刷新提示
  - 不是严格 SLA
- `pcs`
  - 当前可见的 `pc` 列表

### 2.3 `inventory_state`

第一版固定以下取值：

- `fresh`
- `stale`
- `partial`

固定读法：

- `fresh`
  - facade 认为当前快照足以作为正常页面主读层
- `stale`
  - 返回的是缓存库存，当前 freshness 不够理想，但仍可用于上下文与大多数页面读取
- `partial`
  - 当前只拿到了一部分 `pc/workspace` 数据
  - 页面应展示“可继续使用，但信息可能不完整”

第一版不冻结产生这些状态的内部算法阈值，只冻结页面语义。

## 3. `pc` 对象 Contract

### 3.1 `pc` 最小字段

每个 `pc` 至少应包含：

- `pc_id`
- `display_name`
- `status`
- `last_seen_at`
- `workspace_inventory_state`
- `workspace_count`
- `pc_capabilities`
- `route_admission`
- `workspaces`

建议对象形状如下：

```json
{
  "pc_id": "pc_home",
  "display_name": "Home PC",
  "status": "online",
  "last_seen_at": "2026-03-27T09:39:54Z",
  "workspace_inventory_state": "fresh",
  "workspace_count": 3,
  "pc_capabilities": {
    "supported_backends": ["codex", "opencode"],
    "profile_catalogs": {
      "codex": ["fast", "strong", "vision"],
      "opencode": ["fast", "strong"]
    },
    "permission_modes": ["default", "highest"],
    "backend_transport_modes": {
      "codex": ["cli", "sdk"],
      "opencode": ["cli"]
    }
  },
  "route_admission": {
    "allowed": true,
    "reason_code": null,
    "reason": null
  },
  "workspaces": []
}
```

### 3.2 `pc.status`

第一版固定以下取值：

- `online`
- `offline`
- `unknown`

固定读法：

- `online`
  - 当前可认为该 `pc` 具备 live route 能力
- `offline`
  - 当前无 live route；但仍可保留缓存 workspace 作为上下文
- `unknown`
  - facade 当前无法稳定判断在线态

### 3.3 `workspace_inventory_state`

第一版固定以下取值：

- `fresh`
- `stale`
- `missing`

固定读法：

- `fresh`
  - 该 `pc` 的 workspace 列表是当前有效快照
- `stale`
  - 该 `pc` 的 workspace 列表是缓存值
- `missing`
  - 当前还没有该 `pc` 的 workspace 清单

### 3.4 `pc.route_admission`

`pc` 对象需要显式给出当前是否可作为新建 session 目标。

第一版固定形状为：

```json
{
  "allowed": false,
  "reason_code": "pc_offline",
  "reason": "Target PC is currently offline."
}
```

第一版建议稳定 `reason_code`：

- `pc_offline`
- `inventory_stale`
- `admission_blocked`
- `unknown`

固定读法：

- 这是 route 级 admission
- 不是 execution-policy 级 admission
- 不替代提交时的最终 `CreateSessionCommand` 校验

## 4. `workspace` 对象 Contract

### 4.1 `workspace` 最小字段

每个 `workspace` 至少应包含：

- `workspace_id`
- `pc_id`
- `display_name`
- `repo_path`
- `workdir`
- `presence`
- `last_snapshot_at`
- `effective_execution_capabilities`
- `route_admission`

建议对象形状如下：

```json
{
  "workspace_id": "workspace_android_task_manager",
  "pc_id": "pc_home",
  "display_name": "android_task_manager",
  "repo_path": "E:/projects/android_task_manager",
  "workdir": "feature/taskmail/internal",
  "presence": "present",
  "last_snapshot_at": "2026-03-27T09:39:50Z",
  "effective_execution_capabilities": {
    "supported_backends": ["codex", "opencode"],
    "profile_catalogs": {
      "codex": ["fast", "strong"],
      "opencode": ["fast", "strong"]
    },
    "permission_modes": ["default", "highest"],
    "backend_transport_modes": {
      "codex": ["sdk"],
      "opencode": ["cli"]
    }
  },
  "route_admission": {
    "allowed": true,
    "reason_code": null,
    "reason": null
  }
}
```

### 4.2 `workspace.presence`

第一版固定以下取值：

- `present`
- `missing`
- `stale`

固定读法：

- `present`
  - 该 workspace 在当前有效 inventory 中存在
- `missing`
  - 该 workspace 是已知身份，但当前不在最新 inventory 中
  - 仍可能有历史 session 挂在它下面
- `stale`
  - 该 workspace 数据来自缓存，当前 freshness 不足

### 4.3 `workspace.route_admission`

workspace 也必须显式返回 route 级 admission。

第一版建议稳定 `reason_code`：

- `workspace_unavailable`
- `pc_offline`
- `inventory_stale`
- `admission_blocked`
- `unknown`

固定规则：

1. `presence = missing` 时，`route_admission.allowed` 默认应为 `false`。
2. `presence = stale` 时，可以：
   - 返回 `allowed = false`
   - 或返回 `allowed = true` 但显式给出“使用缓存 inventory”说明。
3. 是否允许 `stale` workspace 发起创建，由 facade owner 决定；但页面必须能直接读到决定结果，不能自己猜。

### 4.4 `workspace missing` 的固定读法

第一版固定支持 `workspace missing` 作为一等状态。

它的页面含义是：

- 首页树形允许继续展示该 workspace 节点
- 该节点下仍允许挂历史 session
- 新任务页默认不应把它当作正常可选目标

换句话说：

- `workspace missing` 不是“把节点直接删掉”
- 也不是“Android 自己从 session 列表反推一个假 workspace 才能展示”

facade 若已知该 `workspace_id` 仍有历史语义价值，应优先把它作为 `presence = missing` 的正式节点返回。

## 5. Capability Contract

### 5.1 固定对象形状

第一版 capability 对象统一按下面四组字段理解：

- `supported_backends`
- `profile_catalogs`
- `permission_modes`
- `backend_transport_modes`

建议对象形状如下：

```json
{
  "supported_backends": ["codex", "opencode"],
  "profile_catalogs": {
    "codex": ["fast", "strong", "vision"],
    "opencode": ["fast", "strong"]
  },
  "permission_modes": ["default", "highest"],
  "backend_transport_modes": {
    "codex": ["cli", "sdk"],
    "opencode": ["cli"]
  }
}
```

### 5.2 `pc_capabilities` 与 `effective_execution_capabilities`

第一版固定区分两层：

- `pc_capabilities`
- `workspace.effective_execution_capabilities`

固定读法：

- `pc_capabilities`
  - 表示该 `pc` 自身上报的能力上限或原始能力
- `workspace.effective_execution_capabilities`
  - 表示对当前 `workspace` 真正可用于 `CreateSessionCommand` 的最终能力集合

页面规则固定为：

1. 首页可显示 `pc_capabilities` 作为摘要。
2. 新任务页优先使用 `workspace.effective_execution_capabilities`。
3. Android 不得自行把 `pc_capabilities` 与 `workspace` 特性做二次合并推断。

### 5.3 为什么必须给 `effective` 能力

这一条必须写死，因为页面级 API 需求已明确：

- 页面不能自己推断 capability
- 页面只应消费稳定投影结果

因此：

- 若 workspace 没有特殊限制，facade 可以直接把 `pc_capabilities` 投影成 `effective_execution_capabilities`
- 若 workspace 有特殊限制，facade 必须在服务端先合并后再返回

## 6. 首页与新任务页如何读取这份 Contract

### 6.1 首页

首页按下面的组合关系读取：

- 环境锚点：`EnvironmentInventorySnapshot`
- session 叶子节点：`session list / session projection`

固定规则：

1. environment inventory 不直接塞 `session` 列表。
2. 首页树形由 Android 按 `pc_id + workspace_id` 把两份数据 join 起来。
3. `workspace missing` 节点优先由 inventory 正式返回；若 inventory 当前没有该 missing 节点，但 session 仍引用该 `workspace_id`，Android 允许临时兜底合成一个 “missing workspace” 节点。

### 6.2 新任务页

新任务页固定只依赖这份 contract 的环境层：

- 选择 `pc`
- 选择 `workspace`
- 渲染当前可选 execution capability

固定规则：

1. 新任务页默认不应展示 `presence = missing` 的 workspace 作为可选目标。
2. `route_admission.allowed = false` 的 `pc/workspace`，页面应展示但默认不可提交。
3. 选择 workspace 后，提交页能力读取一律来自 `effective_execution_capabilities`。

## 7. 推荐返回示例

```json
{
  "snapshot_id": "env_snap_20260327_001",
  "generated_at": "2026-03-27T09:40:00Z",
  "inventory_state": "fresh",
  "refresh_after_seconds": 15,
  "pcs": [
    {
      "pc_id": "pc_home",
      "display_name": "Home PC",
      "status": "online",
      "last_seen_at": "2026-03-27T09:39:54Z",
      "workspace_inventory_state": "fresh",
      "workspace_count": 2,
      "pc_capabilities": {
        "supported_backends": ["codex", "opencode"],
        "profile_catalogs": {
          "codex": ["fast", "strong"],
          "opencode": ["fast", "strong"]
        },
        "permission_modes": ["default", "highest"],
        "backend_transport_modes": {
          "codex": ["sdk"],
          "opencode": ["cli"]
        }
      },
      "route_admission": {
        "allowed": true,
        "reason_code": null,
        "reason": null
      },
      "workspaces": [
        {
          "workspace_id": "workspace_android_task_manager",
          "pc_id": "pc_home",
          "display_name": "android_task_manager",
          "repo_path": "E:/projects/android_task_manager",
          "workdir": "feature/taskmail/internal",
          "presence": "present",
          "last_snapshot_at": "2026-03-27T09:39:50Z",
          "effective_execution_capabilities": {
            "supported_backends": ["codex", "opencode"],
            "profile_catalogs": {
              "codex": ["fast", "strong"],
              "opencode": ["fast", "strong"]
            },
            "permission_modes": ["default", "highest"],
            "backend_transport_modes": {
              "codex": ["sdk"],
              "opencode": ["cli"]
            }
          },
          "route_admission": {
            "allowed": true,
            "reason_code": null,
            "reason": null
          }
        },
        {
          "workspace_id": "workspace_old_branch",
          "pc_id": "pc_home",
          "display_name": "old_branch",
          "repo_path": "E:/projects/android_task_manager",
          "workdir": "feature/old_branch",
          "presence": "missing",
          "last_snapshot_at": "2026-03-26T22:10:00Z",
          "effective_execution_capabilities": {
            "supported_backends": ["codex"],
            "profile_catalogs": {
              "codex": ["fast", "strong"]
            },
            "permission_modes": ["default"],
            "backend_transport_modes": {
              "codex": ["sdk"]
            }
          },
          "route_admission": {
            "allowed": false,
            "reason_code": "workspace_unavailable",
            "reason": "Workspace is no longer present on the target PC."
          }
        }
      ]
    },
    {
      "pc_id": "pc_office",
      "display_name": "Office PC",
      "status": "offline",
      "last_seen_at": "2026-03-27T07:15:00Z",
      "workspace_inventory_state": "stale",
      "workspace_count": 1,
      "pc_capabilities": {
        "supported_backends": ["codex"],
        "profile_catalogs": {
          "codex": ["fast", "strong"]
        },
        "permission_modes": ["default", "highest"],
        "backend_transport_modes": {
          "codex": ["cli"]
        }
      },
      "route_admission": {
        "allowed": false,
        "reason_code": "pc_offline",
        "reason": "Target PC is currently offline."
      },
      "workspaces": [
        {
          "workspace_id": "workspace_repo_b",
          "pc_id": "pc_office",
          "display_name": "repo_b",
          "repo_path": "E:/projects/repo_b",
          "workdir": null,
          "presence": "stale",
          "last_snapshot_at": "2026-03-27T07:14:40Z",
          "effective_execution_capabilities": {
            "supported_backends": ["codex"],
            "profile_catalogs": {
              "codex": ["fast", "strong"]
            },
            "permission_modes": ["default", "highest"],
            "backend_transport_modes": {
              "codex": ["cli"]
            }
          },
          "route_admission": {
            "allowed": false,
            "reason_code": "pc_offline",
            "reason": "Workspace is attached to an offline PC."
          }
        }
      ]
    }
  ]
}
```

## 8. 与内部 `pc_hello / workspace_snapshot` 的映射

### 8.1 可直接复用的字段

Android-facing inventory 应优先复用内部主线字段与对象语义：

- `pc_id`
- `workspace_id`
- `display_name`
- `repo_path`
- `workdir`
- `supported_backends`
- `profile_catalogs`
- `permission_modes`
- `backend_transport_modes`
- `last_seen_at`
- `last_snapshot_at`

### 8.2 默认映射关系

第一版默认映射读法如下：

- `pc.pc_id`
  - 来自 `ControlPlanePcHelloMessage.pcId`
- `pc.display_name`
  - 来自 `ControlPlanePc.payload.displayName`
- `pc.last_seen_at`
  - 来自 `ControlPlanePc.payload.lastSeenAt`，或由 facade 根据实时链路更新时间补齐
- `pc.pc_capabilities`
  - 来自 `ControlPlanePc.payload.capabilities`
- `workspace.workspace_id`
  - 来自 `ControlPlaneWorkspace.workspaceId`
- `workspace.pc_id`
  - 来自 `ControlPlaneWorkspace.pcId`，若缺失则继承外层 `pc_id`
- `workspace.repo_path`
  - 来自 `ControlPlaneWorkspace.repoPath`
- `workspace.workdir`
  - 来自 `ControlPlaneWorkspace.workdir`
- `workspace.display_name`
  - 来自 `ControlPlaneWorkspace.displayName`
- `workspace.last_snapshot_at`
  - 来自 `ControlPlaneWorkspace.lastSnapshotAt`
- `workspace.effective_execution_capabilities`
  - 由 facade 在 `pc` 能力与 `workspace` 限制之间完成合并后给出

### 8.3 Android 不应看到的内部字段

Android-facing inventory 第一版不应直接暴露：

- `connection_epoch`
- `message_id`
- `trace_id`
- `host_fingerprint`
- `runtime_fingerprint`
- 内部 transport admission 细节

这些字段可以在 facade 内部用于：

- 路由
- debugging
- staleness 判定
- fencing

但不应成为 Android 页面的正式 contract。

## 9. Android 侧实现约束

Android 侧接这份 contract 时，默认应遵守以下约束：

1. 首页树形只把 inventory 读作环境层，不把它读成 session 真相层。
2. 新任务页不得继续靠 `repo_path / workdir` 反推 `workspace_id`。
3. 页面不自己做 capability 合并，不自己根据 `pc status` 推断 workspace 可用性。
4. `workspace missing` 必须有明确视觉标注，不能与正常 workspace 混读。
5. `pc offline` 时，仍允许展示该 PC 及其缓存 workspace，但默认不允许正常发起新建 session。

## 10. 非目标

本文第一版明确不做：

- session 列表 contract
- `reply / status / kill / guide` contract
- inventory 实时增量事件 contract
- Android 本地 inventory cache schema
- repo prefill 如何回填 inventory
- debug / operator-only inventory 入口

这些内容若后续需要，应继续拆 companion doc，而不是塞回这份 first-pass contract。

## 一句话结论

**Android-facing `environment inventory` 的 first-pass contract 只做一件事：稳定返回页面可直接消费的 `pc -> workspace -> effective capability` 环境层，让首页和新任务页停止依赖占位符，同时不把 Android 降级成内部 `pc-control` client。**
