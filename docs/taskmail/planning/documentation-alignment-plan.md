# TaskMail Documentation Alignment Plan

更新日期：2026-03-16

> 2026-03-16 说明：
>
> 本计划中的主要收口项已经在当前仓库文档中落实。
>
> 现在的主真相源已经是：
>
> - `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
> - `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
> - `docs/TASKMAIL-MAIL-RULES.md`
> - older superseded Android next-development planning notes that have since been removed from the active archive index

## 1. 目的

本文件用于约束当前会话内的文档工程工作范围，并定义 TaskMail / Task Manager 文档体系的收口顺序。

本会话目标明确为：

- 只做文档工作
- 不做代码改动
- 归档已有文档
- 修正文档与最终目标不一致的部分
- 为下一步开发形成可执行的文档基线与阶段计划

---

## 2. 当前文档问题概览

目前仓库中的 TaskMail 文档已经积累出较多有效信息，但存在三个明显问题。

### 2.1 当前状态文档互相冲突

以 2026-03-14 标记的几份 Android 文档为例，存在如下错位：

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md` 将系统描述为 Phase 1.5 的 debug-only 原型
- `docs/TASKMAIL-ANDROID-PHASE2.md` 将正式入口、FeatureLauncher、Drawer 接线视为下一阶段目标
- `docs/TASKMAIL-ANDROID-PHASE3.md` 又将 formal launcher routing、formal drawer entry、reply composer、real sender integration 写成已实现并可 build
- `docs/taskmail/planning/android/taskmail-android-refactor-plan-v0.1.md` 明确指出现状是“代码已前进，但基线文档未完全收口”

这意味着当前最大问题不是“缺文档”，而是“文档没有共同基线”。

### 2.2 文档角色没有分层

当前文档混合了几类不同性质的内容：

- 项目定位文档
- 协议规则文档
- 当前状态文档
- 分阶段实施文档
- 面向未来的重构/平台规划文档

如果不区分这些角色，就会出现“计划文档覆盖当前状态”“旧 phase 文档继续假装是下一步”“归档材料与当前基线混写”的问题。

### 2.3 TaskMail 与 Task Manager 的边界还未文档化收口

这批新归档文档已经把视角扩展到：

- Android TaskMail feature
- mail-thread protocol
- PC 端 `mail_based_task_manager`
- 更上层的 Task Manager platform / tools / schemas

但现有仓库文档还没有明确回答：

- 哪些文档描述“本仓库中的 Android 事实”
- 哪些文档描述“跨仓库协议事实”
- 哪些文档只是“未来平台规划参考”

---

## 3. 文档分层建议

为避免后续继续混乱，建议把文档体系固定为四层。

### 3.1 L1：当前真相源

这类文档回答“现在到底做到哪里”。

建议只保留少量文档承担这个职责，例如：

- Android 当前状态
- 当前协议真相源
- 当前验证状态

要求：

- 描述必须尽量基于已验证事实
- 对“已实现但未验证”与“已设计未实现”明确区分
- 不能与 phase 计划文档相互打架

### 3.2 L2：协议与架构约束

这类文档回答“系统应该遵守什么边界与规则”。

例如：

- `docs/TASKMAIL-MAIL-RULES.md`
- 平台工具接口规范
- schema package

要求：

- 明确 authority
- 明确与后端仓库、平台文档之间的关系
- 避免把实现猜测写成协议事实

### 3.3 L3：阶段计划与实施路线

这类文档回答“下一步怎么做”。

例如：

- `TASKMAIL-ANDROID-PHASE2.md`
- `TASKMAIL-ANDROID-PHASE3.md`
- Android refactor plan
- platform roadmap / delivery

要求：

- 必须显式声明其前提基线
- 必须标识哪些内容是 planned / implemented / validated
- 不得反向覆盖 L1 当前真相源

### 3.4 L4：历史与归档材料

这类文档保留历史思路、旧阶段设计、外部导入材料，但不再承担当前真相职责。

例如：

- phase 0 research
- 早期阶段文档
- 归档规划资料

要求：

- 可阅读
- 可引用
- 但不再作为唯一基线

---

## 4. 当前建议的真相源划分

在本仓库内，建议采用如下职责划分。

### 4.1 Android 当前状态

建议由一个单独文档承担唯一职责：

- 当前 Android TaskMail 做到了什么
- 哪些能力只是代码存在但未验证
- 哪些能力还处于计划阶段

当前候选：

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`

但该文件需要重写或至少大幅修订，原因是它与 Phase 2 / 3 文档已经不一致。

### 4.2 协议约束

建议继续由以下文档承担：

- `docs/TASKMAIL-MAIL-RULES.md`

但需要增加更清晰的 authority 说明，明确：

- 该文档是 Android 侧消费的协议视图
- 若与 `mail_based_task_manager` 实现冲突，以后端实际协议为准
- 若与平台级 Task Manager 规划文档冲突，平台规划只能作为 future direction，不能直接覆盖当前协议事实

### 4.3 阶段实施文档

建议保留但调整角色：

- `docs/TASKMAIL-ANDROID-PHASE2.md`
- `docs/TASKMAIL-ANDROID-PHASE3.md`

这些文件应改为：

- 计划文档
- 或“实施/验证状态文档”

但不应继续以“唯一下一步”口吻描述明显已经发生冲突的基线。

### 4.4 平台规划文档

建议保留在：

- `docs/taskmail/planning/`

并明确它们是：

- 后续重构与平台化工作的上位规划材料
- 不是 Android 当前实现事实的唯一来源

---

## 5. 本会话的文档工程顺序

建议按以下顺序推进。

### Step 1：冻结文档边界

完成内容：

- 明确本会话只做文档
- 明确文档层级与 authority
- 明确哪些文档是当前真相源，哪些是规划，哪些是归档

本文件即承担这一步。

### Step 2：统一 Android 当前基线

目标：

- 修订 `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`

重点回答：

- 当前到底处于 Phase 1.5、Phase 2、还是“部分 Phase 3 已实现但未完成验证”
- 哪些功能已实现
- 哪些功能已实现但未验证
- 哪些能力只是文档设计

### Step 3：重写 Phase 2 / Phase 3 文档角色

目标：

- 让 `PHASE2` 和 `PHASE3` 不再与 current status 互相冲突

可选处理方式：

- 标注为“historical implementation plan”
- 或改成“实施与验证 checklist”
- 或基于统一基线重新写成“next actionable phase”

### Step 4：收口协议文档

目标：

- 修订 `docs/TASKMAIL-MAIL-RULES.md`

重点：

- authority 更清晰
- 当前支持动作与 Android 首期 UI 暴露动作分层说明
- 多问题 reply / structured answer 的现状与 future support 说清楚

### Step 5：形成下一步开发文档计划

目标：

- 在不改代码的前提下，给出下一阶段最合理的开发切片

这部分应以：

- 当前真相源
- 协议文档
- Android refactor plan
- platform roadmap / delivery

共同收敛后的结果为准。

---

## 6. 文档修订原则

本会话内所有文档修改建议遵守以下原则。

### 6.1 不虚构代码现实

如果没有经过当前会话重新验证，就不要把“文档里曾写过 implemented”的内容直接当作确定事实。

可使用三种状态：

- 已实现
- 已实现但未在本轮文档收口中重新验证
- 已规划未实现

### 6.2 不让 phase 文档覆盖 current status

当前状态文档优先级高于 phase 计划文档。

phase 文档只能建立在当前状态之上，不能反过来重定义现实。

### 6.3 规划文档不直接改写协议事实

平台设计、交付、路线、schema 等上位文档可以提出 future direction，但不能在没有协议确认的情况下直接改写 `TASKMAIL-MAIL-RULES.md` 的事实语义。

### 6.4 优先减少歧义，而不是增加篇幅

文档收口的目标不是“写更多”，而是：

- 更少的冲突
- 更清晰的 authority
- 更明确的 next step

---

## 7. 预期产出

如果本会话按计划完成，至少应得到以下结果：

1. 归档资料有固定入口和结构
2. Android 当前状态有唯一真相源
3. Phase 2 / 3 文档不再与 current status 打架
4. 协议文档与平台规划的边界清楚
5. 下一步开发计划可以直接作为后续实现会话的输入

---

## 8. 当前下一步

本文件完成后，文档工程的第一优先动作应是：

- 修订 `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`

理由：

- 它是最适合被提升为 Android 当前唯一真相源的文档
- 也是当前与其他文档冲突最明显的节点
