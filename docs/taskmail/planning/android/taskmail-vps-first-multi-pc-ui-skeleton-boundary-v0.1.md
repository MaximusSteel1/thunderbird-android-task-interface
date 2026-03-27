# TaskMail VPS-first 多 PC UI Skeleton 执行边界 v0.1

更新时间：2026-03-27

本文档是本次编码执行的临时边界冻结稿，只回答四个问题：

- 这次准备实际做什么
- 这次明确不做什么
- 哪些能力允许先占位
- 本次完成后应看到什么结果

## 本次目标

本次目标不是把 VPS-first 主线一次做完，而是把三张核心页切到已经确认过的 UI 骨架，并且保留现有真实链路能继续工作。

本次完成后，Android 侧应至少具备：

- 首页已经按 `PC -> workspace -> session` 的树形工作台读法展示
- 新任务页已经按 `目标环境 -> 任务输入 -> 输入附件 -> 弱化执行策略` 组织
- Session 页已经按 `结果主导 / 当前轮输入主导` 两种模式组织
- 缺失能力允许先以占位 UI、禁用动作或说明文案存在

## 本次要做

### 1. 首页 skeleton

- 把当前 `Needs attention / Active / Recent / Routed workspaces` 的分区首页，切成树形工作台骨架
- 首层固定为 `PC`
- 二层固定为 `workspace`
- 三层固定为 `session`
- `PC` 和 `workspace` 支持折叠
- `session` 作为真正可点击进入详情的叶子节点
- `session` 显示 `活跃 / 非活跃` 与当前状态
- `PC` 显示 `在线 / 离线`
- 允许 `workspace` 缺失时仍显示 session，但要带明确标注

### 2. 新任务页 skeleton

- 把新任务页重排成：
  - 目标环境
  - 任务输入
  - 输入附件
  - 高级执行设置
  - 发送区
- 保留现有 `create-session` 真实提交通路
- 继续保留 `backend / profile / permission` 的可编辑能力，但不让它们抢主输入区
- 输入附件这次要接成真实选择与提交 first-pass

### 3. Session 页 skeleton

- 把 Session 首页重排成两种模式：
  - 结果主导模式
  - 当前轮输入主导模式
- 固定标题区，保留 `历史对话` 入口
- 固定环境锚点，使用 `PC + 工作目录`
- 结果卡首行显式展示输出人，例如 `Codex:`
- `过程记录` 只做折叠入口
- 回复时允许选择权限
- 运行中模式提供：
  - 查看状态
  - 引导
  - 停止运行
- 等待回复模式提供：
  - 添加附件
  - 发送
  - 结束活跃
- 页面底部补 `Session 详细信息`

## 本次明确不做

### 1. 首页真实 PC 数据源

这次原本不计划补真正的 `PC` 数据接口，但在 skeleton 收口后已继续推进了环境库存 first-pass。

当前已经接通：

- Android-facing `environment inventory` 读取接口
- `PC online/offline/unknown`
- `workspace present/missing/stale`
- 首页树形所需的 `PC -> workspace -> session` 真数据投影
- 新任务页 `PC / workspace` 真实可选项

当前仍未完成：

- 首页树形排序策略
- 折叠状态持久化
- 增量刷新 / push

### 2. 新任务输入附件的更重能力

这次已经补到输入附件端到端 first-pass，但仍不做更重的附件能力扩展。

本次仍不做：

- 独立文件上传 endpoint
- 大文件分片 / 断点续传
- 输入附件在 Android 端的独立下载缓存与预览体系
- repo-side 更完整的附件审计 / 限额 / 清理策略

### 3. Session direct lifecycle contract 扩展

这次不补 `停止运行 / 结束活跃` 的真实 direct request type，也不扩共享 `/control` contract。

当前已经收口为：

- `引导` 复用现有 plain-text reply 语义
- `停止运行` 先映射为 canonical mail `/kill`
- `结束活跃` 先映射为 canonical mail `/end`

本次仍不做：

- 新的 direct lifecycle control packet
- repo-side `/control` v2 contract 扩展

### 4. 历史复盘 first-pass 之外的增强

这次已经实现独立历史复盘页的 first-pass，但仍不补新的 repo-side round model，也不扩历史页之外的协议层。

当前已经完成：

- 新增独立历史复盘 route
- 支持同页多回合同时展开
- 展开态支持 `输入 / 过程记录 / 结果 / 附件`
- `过程记录` 仍保留为回合内折叠入口

本次仍不做：

- repo-side 专用 round projector repository
- 新的历史复盘协议字段或 repo-side round API
- 折叠/展开状态持久化

## 占位原则

本次允许占位，但占位必须满足下面三条：

- 结构是真的，不是纯静态图
- 状态是真的，页面能区分当前处于哪种模式
- 后续真实接口接入时，不需要推翻页面结构

允许的占位形式：

- placeholder data
- disabled action
- explanatory text
- synthesized projection

不允许的占位形式：

- 伪造“已接通”的真实能力文案
- 用临时结构替代最终结构
- 让占位实现堵死后续真实接口接入路径

## 本次验收边界

本次完成后，至少应满足：

1. 三张核心页都已经切到新 UI 骨架
2. 新任务页仍然能走真实 `create-session` 提交
3. Session 页仍然能走真实读取、reply、`/status`
4. Session 的 `引导 / 停止运行 / 结束活跃` 至少已有 first-pass 真动作，不再只是占位
5. 代码中已经留下明确的后续接线落点

## 本次建议实现顺序

1. 新任务页 skeleton
2. Session 页 skeleton
3. 首页树形 skeleton
4. 聚焦验证

## 本次完成后仍然待做

- Session 生命周期控制的 direct contract 扩展
- 历史复盘的专用 round model 与 repo-side round projector
- 首页树形排序与折叠状态持久化
- 环境库存的增量刷新、缓存策略与更完整的缺失 workspace 回填

## 本次执行结果

截至 2026-03-27，本次 skeleton 边界内已经实际完成：

- 首页已经切到 `PC -> workspace -> session` 树形工作台骨架
- 首页已经支持 `PC / workspace` 折叠
- 首页已经补了 `在线 / 离线`、`活跃 / 非活跃`、`workspace 缺失` 的占位型展示
- 新任务页已经重排成 `目标环境 -> 任务输入 -> 输入附件 -> 执行与投递 -> 发送`
- 新任务页仍保留真实 `create-session` 提交通路
- 新任务页现在已经支持真实选择输入附件、移除附件，并把附件写入 `TaskMailNewTaskDraft`
- Android `create-session` facade client 现在会把输入附件编码成 `attachments[]` payload 一并提交
- repo-side `POST /v1/android/create-session` 现在已接收并透传 `attachments[]`
- PC-side `new_task` command 现在会把 inline input attachments materialize 到目标 `repo_path/workdir`，并把最终本地路径写入 `TaskSnapshot.attachments`
- Session 页已经切到 `结果主导 / 当前轮输入主导` 双模式骨架
- Session 页已经补上 `历史对话` 入口、环境锚点、底部详细信息
- Session 页已经补上 `权限选择`，并将 `引导 / 停止运行 / 结束活跃` 接成 first-pass 真动作
- `引导` 现在通过现有 direct plain reply 语义发送
- `停止运行` 现在通过 canonical mail `/kill` 发送
- `结束活跃` 现在通过 canonical mail `/end` 发送
- 独立历史复盘页已经实现 first-pass route，并接入 `Session -> 历史复盘` 导航
- 历史复盘页当前通过本地投影 `timeline -> rounds` 支持多回合同页展开
- 历史复盘页已经补出 Android 侧专用 round projector，不再把分轮规则内嵌在页面文件里
- 历史复盘页展开态已经支持 `输入 / 过程记录 / 结果 / 附件` 四段式读法
- Android 已新增 `session snapshot` facade 读取仓储，并在历史复盘页优先消费服务端 `history_rounds`
- repo-side `GET /v1/android/session-snapshot` 已增量返回 durable `history_rounds`
- 当前历史复盘页在服务端 `history_rounds` 不可用时，仍会回退到 Android 本地 projector
- 服务端 round 的正文、过程和附件预览已接入历史页；同轮本地附件若存在，历史页会优先保留本地可操作附件
- 结构化 reply 已补最小保护：不再因为 `WaitingUser` 被拦住，但空模板仍会被阻止发送
- Android-facing `environment inventory` contract 已实现 first-pass 读取链
- 首页已经开始消费真实 `PC / workspace` inventory，而不是纯占位树
- 新任务页已经开始消费真实 `PC / workspace` inventory 作为目标环境选项
- 环境库存已覆盖 `PC online/offline/unknown`、`workspace present/missing/stale`、能力投影与缺失 workspace backfill 的 first-pass
- `:feature:taskmail:internal:testDebugUnitTest` 已在环境库存接线后再次通过

本次仍明确没有完成：

- Session 生命周期控制的 direct action / shared control contract 扩展
- repo-side 历史 round model 目前仍是 first-pass，只挂在 `session snapshot.history_rounds`，还没有独立 history endpoint
- repo-side 更完整的 round projector、round 持久化与更稳定的跨端分轮规则仍未完成
- 首页树形排序与折叠状态持久化
- 环境库存的 live push / 增量刷新
- repo-side 更大范围 relay server 测试当前被现有 `pc_ingress_store.py` dataclass 字段顺序问题阻塞
