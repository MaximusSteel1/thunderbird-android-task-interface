# TaskMail Artifact 打开 / 保存 First-Pass 临时实施计划

更新时间：2026-03-31

## 背景

当前 Android `SessionDetail` 已经能显示 `result` 区中的 artifact 名称、类型和大小，但这条链路仍停留在“可见不可用”状态：

- control-plane artifact 进入 UI 时丢失了 `download_ref` 等可消费引用
- `Latest result` 区虽然能显示附件列表，但 `Open / Save` 没有接到真实动作
- 现有附件动作 handler 仍主要服务 legacy mail attachment

与此同时，仓库里已经存在可复用基础：

- Android 已有 `/v1/files` client，支持 `getMetadata()` 与 `downloadContent()`
- `TaskSessionDetailScreen` 已有系统保存对话框与系统打开 intent 的动作入口
- `TaskSessionDetail` 已有统一附件行组件与 open/save 事件流

因此当前最合理的临时目标不是一次做完完整 artifact 体验，而是先收口一版 **result artifact 真能打开、真能保存** 的 first-pass 闭环，并保证它与长期规则兼容。

## 本文目的

本文只回答四个问题：

1. 这轮 first-pass 准备做到哪里
2. 具体按什么切片推进
3. 哪些内容明确不在这轮范围内
4. 做完以后如何验收

本文是临时实施计划，不承担长期规则 authority。

## 目标

本轮 first-pass 完成后，Android 至少应满足：

1. `Latest result` 区的 result artifact 在有有效 `download_ref` 时可以 `Open`
2. 同一批 result artifact 在有有效 `download_ref` 时可以 `Save`
3. 打开和保存都走 Android 系统能力，不自造文件管理器
4. 动作失败时有明确错误提示，不再只有灰按钮
5. 设计与数据模型不堵死后续内置预览、缓存和历史页扩展

## 非目标

本轮明确不做：

- 完整内置 Markdown / JSON / PDF / 图片预览器
- 首页文件信号与 mini artifact 体验升级
- 历史页所有 artifact 的统一动作闭环
- 离线缓存治理、下载去重、后台下载队列
- 分享、复制链接、批量导出
- repo-side / VPS 侧新的 file surface 协议扩展

## 范围边界

### 本轮必须覆盖

- `SessionDetail` 首页的 `Latest result` 区
- control-plane `artifact_manifest` 投影到 UI 的附件行
- `Open` 与 `Save` 的系统动作打通
- 错误原因的最小用户提示

### 本轮尽量顺手覆盖

- 同页其他使用同一附件行组件的位置
- action availability 的统一判定
- 与现有 legacy mail attachment 动作链的一致性检查

### 本轮先不碰

- workspace 首页
- 新任务输入附件体验重构
- 历史页的 artifact 下载完整闭环

## 方案总览

本轮实现原则是：

**统一 artifact 动作层，先让系统能力跑通，再逐步叠加更重体验。**

也就是说：

- `Open` first-pass 先下载到本地临时文件或 cache，再交给系统 `ACTION_VIEW`
- `Save` first-pass 先让用户选目标 `Uri`，再把下载到的 bytes 写进去
- 页面层只发动作，不直接拿 `content_url` 和 token 发请求

## 切片设计

### 切片 A：模型与投影收口

目标：

- 让 control-plane artifact 不再在 UI 投影时丢失可消费信息

建议落点：

- `feature/taskmail/internal/.../ui/detail/TaskSessionDetailUiState.kt`
- `feature/taskmail/internal/.../ui/detail/TaskSessionControlPlaneUi.kt`
- 必要时新增 Android 侧 artifact action model

本切片至少要做：

- 在 UI attachment model 中保留 `download_ref` 所需字段
- 区分 `可打开 / 可保存 / 仅预览 / 不可用`
- 不再把 control-plane artifact 一律映射为 `isActionAvailable = false`

验收口径：

- fake data 或 representative data 下，result artifact 的动作状态能被明确投影

### 切片 B：统一 artifact 动作层

目标：

- 新增一条既能服务 control-plane artifact，也不破坏 legacy attachment 的统一动作层

建议落点：

- `feature/taskmail/internal/.../data/`
- `feature/taskmail/internal/.../domain/`
- `TaskMailModule.kt`

建议新增：

- `TaskMailArtifactActionHandler` 或等价命名

职责至少包括：

- 根据 `download_ref` 下载文件
- 生成可供系统打开的本地 `Uri`
- 把文件写入用户选定的目标 `Uri`
- 对外返回用户可消费的错误

建议复用：

- 现有 `RelayFileSurfaceClient`
- 现有 `TaskTransportConfigRepository`
- 现有系统打开 / 保存入口

验收口径：

- 不需要页面知道 `/v1/files` 和 token 细节

### 切片 C：Session 结果区动作接线

目标：

- 让 `Latest result` 区的 `Open / Save` 不再是空壳

建议落点：

- `TaskSessionDetailViewModel.kt`
- `TaskSessionDetailContent.kt`
- `ResultSummaryCard.kt`
- `TimelineMessageCard.kt`

本切片至少要做：

- `ResultSummaryCard` 透传 `onOpenAttachment / onSaveAttachment`
- `TaskSessionDetailViewModel` 对 control-plane artifact 触发统一动作层
- `CreateAttachmentDocument` / `OpenAttachment` effect 继续复用现有屏幕层

验收口径：

- result artifact 按钮不再无故灰掉
- 有 viewer 时能打开
- 选定保存位置后能写出文件

### 切片 D：错误与文案收口

目标：

- 用户能理解为什么打不开、为什么不能保存

本切片至少要做：

- 将下载失败、无 `download_ref`、未配置 relay、无可打开应用等情况映射成短文案
- 不再让 result artifact 长期以“不可点击 + 无原因”状态存在

建议文案类型：

- 当前文件不可下载
- 当前离线，且本地没有缓存文件
- 当前设备没有应用能打开这个文件
- 当前连接配置不可用，请检查 TaskMail relay 设置

### 切片 E：验证

本轮最小验证建议：

- 单元测试：
  - control-plane artifact -> UI action state 投影
  - `download_ref` 解析
  - 动作层成功 / 失败分支
- 组件 / ViewModel 测试：
  - result 区附件在可用时触发 open/save 事件
  - 不可用时显示明确错误
- 真机 smoke：
  - 打开 markdown/text 文件
  - 保存图片或文本到用户选定位置
  - 断网后验证“已缓存可打开 / 未缓存不可打开”的最小行为

## 系统能力使用策略

本轮 first-pass 固定采用以下策略：

- `Open`：下载到 app 可控临时文件后，交给系统 `ACTION_VIEW`
- `Save`：通过 Android system document picker 获取目标 `Uri` 后写入

这样做的理由：

- 改动窄
- 与现有 legacy attachment 路径一致
- 不会阻塞后续增加内置预览

## 风险与依赖

### 外部依赖

- result artifact 必须已有可消费 `download_ref`
- 当前 relay/file surface 配置必须可用

### 代码侧风险

- 当前 UI attachment model 过于轻量，补字段时容易影响多处组件
- 若继续把 control-plane artifact 和 legacy attachment 彻底分两套 handler，后续体验会继续分叉

### 用户侧风险

- 文本文件若继续被兜底成 `application/octet-stream`，系统打开成功率会受影响
- 即使 file surface 可用，设备若没有合适 viewer，`Open` 仍可能失败；这类情况必须有明确提示

## 完成定义

本轮 first-pass 可视为完成，当且仅当同时满足：

1. `Latest result` 区的可下载 artifact 不再只是可见列表
2. 用户能在真机上成功执行至少一次 `Open`
3. 用户能在真机上成功执行至少一次 `Save`
4. 失败场景不再只有灰按钮
5. 新增实现仍然符合长期 artifact 体验规则文档

## 后续衔接

本轮完成后，下一阶段最自然的延伸是：

1. 把同一套 artifact 动作扩到历史页
2. 引入最小本地缓存状态
3. 为 `image / markdown / json / pdf` 增加应用内预览
4. 再评估首页文件信号与分享能力

届时本文可以：

- 归档
- 或把已完成部分回写进更正式的 owner / companion planning
