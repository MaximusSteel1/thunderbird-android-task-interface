# TaskMail VPS-First 多 PC Artifact 体验规则（v0.1）

更新时间：2026-03-31

## 状态

本文是 `VPS-first 多 PC` 主线下，Android 侧关于“文件 / 附件在产品里应该如何被用户理解和消费”的 companion doc。

它依附于以下文档：

- `docs/taskmail/planning/android/taskmail-vps-first-multi-pc-user-requirements-authority-v0.1.md`
- `docs/taskmail/planning/android/taskmail-vps-first-multi-pc-information-architecture-v0.1.md`
- `docs/taskmail/planning/android/taskmail-vps-first-multi-pc-page-api-requirements-v0.1.md`
- `docs/taskmail/planning/platform/taskmail-result-artifact-errorcode-appendix-v0.1.md`
- `docs/taskmail/planning/platform/taskmail-pc-vps-control-protocol-v0.1.md`

本文不替代：

- 当前实现真相
- `/v1/files` 或 `download_ref` 的 wire contract
- 某一轮临时实施计划

本文回答的问题是：

**Android 侧以后应该如何把 `artifact` 做成用户真正能使用的文件体验，而不是停留在“可见但不可消费”的附件列表。**

## 一句话结论

在当前主线下，`artifact` 的正确产品读法是：

**文件是一等结果对象，不是某条消息上的附属按钮。**

对用户来说：

- 输入附件是“我提供给这条任务的文件”
- 结果附件是“这条任务产出的文件”
- 历史附件是“某一轮输入或结果留下的文件证据”

它们都应被 Android 作为统一文件对象消费，而不是继续拆成多套互不兼容的附件逻辑。

## 1. 文档目的

本文只冻结以下内容：

1. Android 用户如何理解 `artifact`
2. Android 页面上文件动作的长期规则
3. `first-pass` 与长期体验必须共同遵守的边界
4. 哪些实现做法会把后续体验锁死

本文不冻结：

- endpoint URL
- transport 细节
- 某一版缓存目录结构
- 某一轮内置预览的组件选型

## 2. 固定用户心智

Android 用户不会把 `artifact_manifest`、`file_id`、`download_ref` 理解成协议对象。

用户理解的是：

1. 这条任务生成了哪些文件
2. 这些文件属于当前轮输入、当前结果，还是历史某轮结果
3. 我能不能直接查看、保存、分享
4. 如果现在不能，原因是什么

因此固定以下规则：

- `artifact` 在 UI 上默认叫“文件”或“附件”，不叫 `artifact_manifest`
- 结果文件默认属于结果区，而不是调试层
- 历史文件默认按回合归属展示，而不是按原始 frame 或 mail 顺序散落
- 文件动作必须直接服务用户，不要求用户先理解 transport

## 3. Artifact 的统一对象模型

Android 侧长期应把以下来源统一投影到同一种文件对象：

- 新任务输入附件
- follow-up / reply 输入附件
- Session 当前结果文件
- Session 历史回合文件
- 可选的系统生成文件，例如结构化 sidecar 或缩略图

统一对象模型至少应稳定包含：

- `artifact_id`
- `file_id`
- `name`
- `kind`
- `role`
- `visibility`
- `content_type`
- `size`
- `sha256`
- `download_ref`
- `inline_preview`
- 本地缓存状态
- 动作可用性与不可用原因

固定规则：

- `artifact_id` 表示逻辑文件身份
- `file_id` 表示 transport-facing 文件对象身份
- 同一个 `artifact_id` 在不同上传轮次或不同 binding 历史里，可对应不同 `file_id`
- 同一个 `file_id` 必须始终对应同一份 bytes 与 metadata
- Android 不允许把 `artifact_id` 当作稳定下载键、缓存主键或正式文件真相键
- 两者不允许混成一个“万能 id”
- UI 默认不直接暴露 raw id，除非处于 debug / 诊断入口
- 统一对象模型不等于默认把全量 artifact 暴露给用户
- `artifact_index.json`、binding sidecar、probe/debug artifact 等对象，只有在被显式标记为用户可消费时，才进入默认文件列表
- 默认用户文件列表至少应同时受 `role`、`visibility` 与页面上下文过滤

## 4. 文件动作规则

### 4.1 主动作

文件行的主动作固定为：

- `预览 / 打开`

默认规则：

- 点击整行优先进入可消费动作
- 若 Android 已支持该类型的内置预览，优先走应用内预览
- 若没有内置预览，则回退到系统 `ACTION_VIEW`

### 4.2 次级动作

文件行的次级动作至少应包含：

- `保存到设备`

可选后续动作：

- `分享`
- `复制外部可分享链接`
- `查看文件信息`

固定约束：

- 只有当 artifact 明确提供面向用户的 `external_url`、`share_url` 或语义等价的外部链接时，才显示“复制外部可分享链接”
- 默认不把受 transport token 保护的 `download_ref`、`metadata_url` 或 `download_url` 直接当作用户可分享链接
- 若诊断入口确实需要复制 raw URL，也只允许放在 debug / 诊断入口，不进入默认用户动作

### 4.3 保存规则

`保存到设备` 的固定读法是：

- 用户选择目标位置
- Android 把文件写入用户明确选定的目标 `Uri`

固定约束：

- 默认不做静默自动保存
- 默认不把内部 cache 目录伪装成“用户已经保存”
- 即使以后增加“导出到下载目录”快捷动作，系统 document picker 仍然是稳定基线

### 4.4 不可用动作规则

不允许长期存在“灰按钮但没有原因”的状态。

若文件当前不可操作，页面必须显式给出以下之一：

- 文件当前不可下载
- 当前离线且本地无缓存
- 当前没有可用打开程序
- 文件引用缺失
- 权限或会话已失效

## 5. 可用性与缓存规则

### 5.1 打开规则

文件可打开，当且仅当满足以下任一条件：

- 本地已有可用缓存
- 当前有有效 `download_ref`，且 Android 能先下载再打开
- 当前只有受限 `inline_preview`，但预览动作本身成立

### 5.2 保存规则

文件可保存，当且仅当满足以下任一条件：

- 当前有有效 `download_ref`
- 本地已有完整缓存文件

`inline_preview` 只能支撑极小预览时：

- 允许预览
- 不默认等价于“可保存正式文件”
- 不等于正式文件真相，也不单独承担正式文件下载身份

### 5.3 本地缓存规则

Android 长期应维护自己的 artifact cache，而不是把每次下载都视作一次性临时文件。

缓存至少应满足：

- 以 `file_id` 为 transport-facing 主键
- 以 `sha256` 做内容校验与缓存失效判断
- 能区分“仅预览缓存”和“完整文件缓存”
- 能判断离线时是否仍可打开

明确禁止：

- 直接使用 PC 本地磁盘路径作为 Android 侧 canonical 文件定位
- 让 UI 依赖“上次某个临时 Uri 恰好还没失效”

## 6. 页面职责规则

### 6.1 Session 首页

Session 首页文件区负责承载：

- 当前结果文件
- 与最近稳定结果强相关的附件

默认不做：

- 把所有历史文件平铺在当前页
- 把输入附件和结果附件混成一个无归属列表

### 6.2 历史页

历史页文件区负责承载：

- 某一轮输入附件
- 某一轮结果附件

固定规则：

- 历史文件按回合归属展示
- 至少区分“用户输入文件”和“本轮产出文件”
- 同一文件对象的动作规则必须与 Session 首页一致

### 6.3 新任务 / Reply 输入区

输入附件区负责承载：

- 已选择文件列表
- 添加 / 移除动作
- 基本预览入口

固定规则：

- 发送前的输入附件与发送后的结果文件使用同一套文件读法
- 发送前允许 `移除`
- 发送后不再把同一文件退回成“临时选择项”语义

### 6.4 Workspace 首页

Workspace 首页当前只需要承担文件信号，而不是完整文件消费页。

例如：

- 最近结果是否带文件
- 结果中是否有图片或文档

默认不要求首页直接承载完整打开 / 保存体验。

## 7. 文件类型与用户文案规则

Android 页面不应优先显示 raw `kind` / raw MIME / 协议字段名。

用户侧应优先看到：

- 图片
- 文档
- Markdown 说明稿
- JSON 结果
- PDF
- 其他文件

`content_type`、`kind`、`role` 的正确使用方式是：

- 用于投影 icon、预览能力、动作可用性
- 在次级 metadata 行中给出准确说明
- 在 debug / 诊断入口中保留 raw 值

## 8. First-Pass 与长期目标的兼容边界

为了保证 `first-pass` 不把长期方案锁死，固定以下边界：

### 8.1 允许

- `Open` 先通过系统 `ACTION_VIEW` 完成
- `Save` 先通过 Android system document picker 完成
- 当前不做内置 Markdown / JSON / PDF 预览
- 当前只打通 Session 结果附件，再逐步扩到历史页

### 8.2 不允许

- 为 control-plane artifact 单独发明一套和 legacy mail attachment 完全不同的 UI 语义
- 继续只在页面层保留 `name + size`，把 `download_ref` 丢掉
- 用“按钮永远 disabled”冒充功能未完成
- 把受 transport token 保护的 `download_ref` 或 `download_url` 直接包装成默认可分享链接
- 让页面直接拼 `content_url`、token 或原始 file surface 请求
- 让 Android 依赖 PC 本地路径或 mail body 兜底读取正式文件

## 9. 错误映射规则

文件能力至少应稳定映射以下错误家族：

- `artifact_not_found`
- `download_ref_unavailable`
- `file_surface_unavailable`
- `unauthorized`
- `offline`
- `no_viewer_available`

页面层默认读法应分别投影为：

- 文件不存在
- 当前结果没有可消费下载引用
- 文件服务暂不可用
- 当前登录或 token 已失效
- 当前离线，且本地没有可用缓存
- 当前设备没有应用能打开该文件

错误默认必须带用户下一步动作，而不是只输出技术文本。

## 10. 对后续实现的直接约束

后续 Android 设计或实现若要符合当前主线，必须同时满足：

1. `artifact` 被当作统一文件对象，而不是每个页面各写一套附件逻辑
2. `Open / Save` 的动作语义在 Session 首页、历史页、输入附件区保持一致
3. `first-pass` 使用系统能力时，不把长期内置预览路径堵死
4. 页面消费的是“文件能力模型”，而不是原始 `download_ref` 细节
5. 文件不可用时，用户能明确知道原因

如果做不到这些，说明实现还停留在“附件列表可见性”，没有进入“文件能力可用性”。

## 11. 与其他文档的关系

本文回答的是：

- Android 用户如何理解和消费文件
- 文件动作的长期规则是什么

其他相关文档分别回答：

- 平台字段如何编码：`docs/taskmail/planning/platform/taskmail-result-artifact-errorcode-appendix-v0.1.md`
- 页面至少需要哪些文件能力：`docs/taskmail/planning/android/taskmail-vps-first-multi-pc-page-api-requirements-v0.1.md`
- 当前这段时间怎么实现：临时实施计划文档

因此后续若出现冲突：

- 用户体验和页面职责，以本文为准
- 协议字段与 `download_ref` 形状，以平台附录为准
- 本轮切片顺序和范围，以临时实施计划为准
