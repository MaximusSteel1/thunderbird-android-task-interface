# TaskMail Android 改造计划（v0.1）

更新日期：2026-03-14

## 1. 文档目的

本文件用于定义 **TaskMail Android** 在当前阶段的改造方向、范围边界、实施顺序与验收标准。

这份计划不是从零重写 Android 端，而是基于现有实现进行**收口式改造**。核心目标是让 Android 成为一个稳定、可验证、可扩展的移动控制面，而不是让它自己定义协议、复制后端状态逻辑或提前承担过多平台职责。

---

## 2. 现状判断

### 2.1 已有基础能力

根据现有文档，Android 端已经具备以下基础：

- 已有 `feature:taskmail:api` 与 `feature:taskmail:internal` 模块
- 已有 `LegacyTaskMailMessageSource`、`LegacyTaskMailBodyExtractor`、`DefaultTaskMailRepository`
- `workspace -> session -> detail` 已可由真实本地邮件数据驱动
- debug deep link 可打开 TaskMail workspace
- workspace 与 detail 已支持滚动
- repository 已支持跨多个物理邮件线程合并为一个逻辑 TaskMail session
- JSON replay 验证与 repository regression 测试链路已存在

### 2.2 当前最大问题

当前最大问题不是“移动端没有基础”，而是**文档状态存在错位**：

- `TASKMAIL-ANDROID-CURRENT-STATUS.md` 仍将系统描述为 **Phase 1.5 的 debug-only 原型**
- `TASKMAIL-ANDROID-PHASE2.md` 将正式入口、FeatureLauncher、Drawer 接线视为下一阶段目标
- `TASKMAIL-ANDROID-PHASE3.md` 又将 formal launcher routing、formal drawer entry、reply composer、real sender integration 视为已实现并可 build

这说明 Android 端当前处于一种 **代码已前进，但基线文档未完全收口** 的状态。

### 2.3 关键判断

因此，Android 改造的第一步不是继续铺新功能，而是：

1. 先统一“当前真实基线”
2. 再收口“正式入口与交互边界”
3. 最后再做协议增强与质量冻结

---

## 3. 改造目标

### 3.1 总体目标

将 TaskMail Android 从“调试可用、文档分叉、协议部分依赖约定”的状态，推进为：

- **正式可进入**
- **读取稳定**
- **交互边界清晰**
- **协议与 mail_task_manager 对齐**
- **具备后续扩展能力**

### 3.2 这一轮改造的具体目标

本轮改造只追求四件事：

1. **统一 Android 当前基线**
2. **冻结正式入口与宿主结构**
3. **冻结首期交互范围**
4. **让 Android 成为 mail-thread / task-session 的薄控制面**

---

## 4. 非目标

本轮改造明确不做以下内容：

- 不做 Android 侧“新建任务”完整入口
- 不做完整传统邮件编辑器替代
- 不做富文本 / Markdown 渲染
- 不做附件发送与多媒体回复
- 不做搜索 / 筛选 / 通知 / 后台轮询
- 不做独立于邮件协议之外的 Android 私有协议
- 不在 Android 端复制一套 task manager / state machine
- 不把 Android 提前做成“完整平台前端”

---

## 5. 核心设计原则

### 5.1 Android 是控制面，不是协议所有者

协议的唯一权威应保持在 mail_task_manager / mail rules / shared state model 一侧。  
Android 只负责：

- 展示状态
- 收集输入
- 通过现有邮件线程语义发回动作

Android 不应自行发明新的 subject 规则、新的 session 规则、新的状态机。

### 5.2 Android 是薄壳，不复制后端状态逻辑

Android 不应该把后端的 thread / session / workspace 路由、waiting state、question set 解析逻辑再复制一遍。  
Android 只消费稳定的 read model，并按协议输出有限动作。

### 5.3 先收口再扩展

先把以下四个面收住：

- 入口
- 导航
- 交互范围
- 协议边界

再考虑 Phase 4 之后的 richer features。

### 5.4 兼容现有真实数据链路

不要推翻已实现的真实 repository 链路。  
应继续复用：

- `LegacyTaskMailMessageSource`
- `LegacyTaskMailBodyExtractor`
- `DefaultTaskMailRepository`

---

## 6. 目标架构定位

改造完成后的 Android 端，应稳定落在以下结构中：

### 6.1 宿主层

负责正式入口、导航承载和返回栈行为。

建议继续采用：

- `FeatureLauncher`
- 统一 `TaskMailRoute`
- 抽屉或正式 feature 入口

### 6.2 读取层

负责从现有 repository 获取：

- workspace 列表
- logical session 列表
- timeline
- 当前状态摘要
- pending question 摘要

这一层不直接关心底层 IMAP/SMTP 协议，只消费标准化后的 TaskMail read model。

### 6.3 交互层

首期只支持三种交互：

- plain-text reply
- single-question quick action
- `/status`

必要约束：

- reply 必须锚定已有 session thread
- 不新建独立状态主题
- 不暴露 `/new`、`/sessions`、`/rerun`、`/kill` 作为首期 UI 动作
- 多题情况下不提供误导性的单键选择

### 6.4 协议适配层

这一层的职责是把 Android UI 行为转换成符合 mail rules 的邮件回复内容，例如：

- plain reply -> 用户原文
- one-tap single-choice -> choice canonical key 或允许标签
- status -> `/status`

如果当前 session 处于多题等待态，则 Android 必须遵从结构化 answer 规则，而不能继续假装“一键按钮就够”。

---

## 7. 推荐实施顺序

## Phase A：统一基线与文档真相源

### 目标

先解决“现在到底做到哪里”的问题。

### 工作内容

- 选定一份唯一的 `CURRENT STATUS` 文档作为 Android 当前真相源
- 明确代码现实到底属于：
  - 仅 Phase 1.5
  - 已做完 Phase 2 但未验证
  - 已有 Phase 3 主路径但未完成质量收尾
- 将 `CURRENT-STATUS / PHASE2 / PHASE3` 三份文档按真实状态对齐
- 明确哪些能力是：
  - 已实现
  - 已实现但未验证
  - 已设计未实现
  - 暂缓

### 退出条件

- Android 当前状态不再出现自相矛盾描述
- 形成唯一“当前真相文档”
- 后续所有 phase 文档都以这份基线为前提

### 说明

这是第一优先级。  
如果这一阶段不做，后续开发会持续混淆“补实现”和“补验证”。

---

## Phase B：正式入口与宿主收口

### 目标

把 TaskMail 从 debug-only 可达，推进为正式可达 feature。

### 工作内容

- 冻结 `FeatureLauncher` 作为正式宿主
- 冻结 `TaskMailRoute` 作为统一 route contract
- 接通正式 app shell / drawer entry
- 确保 `Workspace -> Detail -> Back` 行为稳定
- 保留 `TaskMailDebugActivity` 作为调试与回归路径，不移除

### 范围控制

这一阶段只做 reachability 和 host integration，不做 richer interaction。

### 退出条件

- 用户可从正式 app 内进入 TaskMail
- 不依赖 adb / debug deep link 才能访问
- debug host 仍可作为 fallback 验证路径
- 正式入口与 debug 入口共享同一套 route contract

### 备注

如果代码里这部分实际上已经落地，那么该阶段应转为：

- 验证
- 清理
- 对齐文档
- 固化回归测试

而不是重复开发。

---

## Phase C：首期交互边界冻结

### 目标

将 Android 首期交互严格限定在“轻交互闭环”。

### 支持动作

1. **Plain-text reply**
2. **单题 quick action**
3. **`/status`**

### 明确不支持

- `/new`
- `/sessions`
- `/rerun`
- `/kill`
- 多题 one-tap 乱选
- 新任务创建
- 附件回复
- 富文本 compose

### 具体要求

- 使用现有 mail send stack，不新增第二条发送链路
- 保持 `In-Reply-To` / `References` / normalized subject reply semantics
- outgoing body 默认新文本 only，不附整段旧引用
- 若 mixed-account session 无法安全 reply，应明确禁用 reply

### 退出条件

- session detail 中的 reply / status / single-question choice 可以稳定使用
- 不会意外 fork 新 thread
- 不会因 UI 方便而偏离协议

---

## Phase D：多问题协议兼容改造

### 目标

让 Android 与 mail_task_manager 的多题协议保持兼容，但不抢跑做复杂 UI。

### 原因

当前协议方向已经明确：

- 一次等待态可以有多个 `TASK-QUESTION` capsule
- 多题回复应使用结构化 `Answers:` / `question_id: value`
- resume 输入应来自 canonical answers，而不是 raw text

Android 不能继续只围绕“一个 pending question + 一键按钮”设计。

### 实施策略

#### D1 先做读兼容

Android read side 先支持：

- 识别一个 question set 下的多个问题
- 识别 question set id
- 展示 remaining questions
- 展示 allowed values
- 展示 received answers（如果有）

#### D2 再做写兼容

只在 mail_task_manager 侧多题协议稳定后，Android 再补：

- 结构化 answers 模板填充
- 多题 answer body 组装
- partial answers 的重新编辑与继续发送

#### D3 暂不做复杂表单

首版不要在 Android 端发明复杂表单系统。  
优先策略应是：

- 展示结构化多题模板
- 提供复制/填充辅助
- 让协议先稳定

### 退出条件

- Android 不会错误地把多题等待态当成单题
- Android 不会在多题场景下发送不合规的一键回复
- 多题 read path 与 mail rules 对齐

---

## Phase E：验证、质量收口与冻结

### 目标

把“能跑”推进为“可冻结”。

### 必做验证

- repository regression tests 继续绿
- JSON replay validation 可持续使用
- host navigation regression 验证
- on-device smoke
- `connectedAndroidTest`
- `lint`
- `detekt`
- `spotlessCheck`

### 推荐额外验证

- 多线程合并后的 logical session 展示稳定
- mixed-account session reply disable 正确
- 中文客户端 reply 引用截断后的正文展示合理
- QUESTION mail 中 state capsule / question capsule 不被原样误展示为正文噪音

### 冻结条件

满足以下条件后，Android 端才适合称为“可冻结”：

1. 当前基线文档统一
2. 正式入口稳定
3. 首期交互边界稳定
4. 多题协议至少达到 read-compatible
5. 设备烟测通过
6. repo 级质量任务通过
7. 与 mail_task_manager 的协议没有明显分叉

---

## 8. 与 mail_task_manager 的依赖边界

Android 端的改造，不应脱离 mail_task_manager 侧的改造独立进行。

### Android 依赖 mail 侧先收口的部分

- waiting state 的正式状态模型
- question set / pending questions 的统一结构
- 多题结构化答案协议
- canonical answer resume 规则
- `TASK-STATE` / `TASK-QUESTION` 的稳定解析与展示边界

### Android 可以先独立推进的部分

- 正式入口
- 宿主/导航
- repository read path
- detail UI 的首期轻交互
- smoke / host / build 质量收口

### 结论

Android 端可以先推进到：

- **host stabilized**
- **read stable**
- **single-question interaction stable**
- **multi-question read-compatible**

但不应在 mail 侧协议未定时，抢先做复杂多题编辑 UI。

---

## 9. 建议保留 / 调整 / 新增 / 暂缓

## 9.1 保留

- `feature:taskmail:api`
- `feature:taskmail:internal`
- 真实 repository 链路
- logical session merge 行为
- debug host 作为 fallback
- detail 中已有 reply composer / quick actions 的主结构
- 复用现有 mail send stack

## 9.2 调整

- 统一当前状态文档
- 冻结正式宿主与 route
- 收窄首期交互面
- 把多题协议视为正式约束，而不是后补需求
- 将 Android 明确定位为“mail-thread 控制面”

## 9.3 新增

- current truth 文档
- host/navigation regression 测试
- 多题 read compatibility 验收项
- 多题 structured answer 辅助策略
- 阶段性冻结标准

## 9.4 暂缓

- 富文本渲染
- 附件
- 搜索/筛选
- 通知/后台轮询
- 新任务创建
- 更复杂 slash command UI
- Android 私有状态机

---

## 10. 近期最合理的实施切片

### 切片 1：基线收口

目标：

- 更新一份唯一 current status
- 明确 Phase 2/3 现实状态
- 对齐文档中的 implemented / in-progress / pending

### 切片 2：正式入口验证

目标：

- 验证 FeatureLauncher / drawer / route 是否已真实通
- 若已通，补测试与文档
- 若未通，按 Phase 2 checklist 补齐

### 切片 3：交互边界冻结

目标：

- 只保留 plain reply / single-question quick action / `/status`
- 明确禁用不在首期范围内的动作

### 切片 4：多题 read-compatible

目标：

- detail 能正确展示 question set
- 遇到多题时不误导用户点单选按钮
- 为后续 structured answer UI 预留位

### 切片 5：质量收尾

目标：

- device smoke
- connected tests
- lint/detekt/spotless
- 回归验证文档

---

## 11. 结论

Android 端当前并不需要推倒重来。

更合理的路线是：

1. **先统一当前基线**
2. **再收口正式入口**
3. **冻结首期轻交互**
4. **补齐多题协议兼容**
5. **最后做质量冻结**

一句话概括：

> Android TaskMail 应被推进为一个依附共享邮件协议的稳定移动控制面，而不是一个自己发明协议和状态机的第二平台。

