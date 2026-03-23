# TaskMail 项目文档

## 1. 项目定位

本项目是一个**自用的、围绕 OpenCode / Codex 的异步任务协作系统**。

核心目标不是做一个通用 AI 平台，也不是做一个完整商业产品，而是解决一个非常具体的问题：

> 让用户可以在不一直盯着电脑终端的情况下，通过邮件线程或后续的安卓客户端，与本机上的 OpenCode / Codex 持续协作，完成工程类与代码类任务，并且支持多轮补充、状态查询、问题澄清与恢复执行。

当前项目分为两条主线：

1. **PC 端任务执行与邮件线程后端**
2. **Android 端专用客户端（基于 Thunderbird for Android fork 的任务邮件壳）**

这两条主线的关系是：

- PC 端是真正的执行面和状态面
- 邮件是当前的控制面
- Android 客户端是未来更顺手的交互壳，但底层通信仍然基于邮件

---

## 2. 为什么做这个项目

用户当前已经在大量使用：

- OpenCode：处理便宜、明确、代码执行类任务
- Codex：处理复杂、知识密集、上下文重、推理要求高的任务

现有 CLI/TUI 工作流的问题在于：

- 必须盯着终端或桌面
- 远程查看和补充上下文不方便
- 需要澄清时，交互入口不够顺手
- 手机端补充任务、回复问题、看结果不够自然

项目的目标是通过**异步线程化协作**解决这些问题。

---

## 3. 总体思路

项目总体采用三层思路：

### 3.1 执行面

由本机上的 OpenCode / Codex 完成实际任务执行。

执行面特点：

- 运行在用户自己的电脑上
- 可操作本地仓库和脚本环境
- 通过 CLI / session / resume 机制维持连续会话
- 最终产出日志、结果摘要、补丁、错误信息等

### 3.2 状态面

由本地文件系统保存真实状态。

原则：

- **本地状态文件才是真相来源**
- 邮件线程只是控制面和交互载体
- 邮件中的状态块（state capsule）只是辅助恢复和展示

### 3.3 控制面

当前控制面是邮件。

未来控制面可能包括：

- 邮件
- Android 专用客户端（发信 / 看信 / 任务视图）

控制面主要负责：

- 创建任务
- 更新任务
- 补充上下文
- 查询状态
- 回答澄清问题
- 重跑 / 终止

---

## 4. 当前已经达成的关键决策

### 4.1 不做自动路由

用户手工决定任务发给 OpenCode 还是 Codex，不做自动判断。

原因：

- 用户自己判断更可靠
- 自动路由会引入不必要复杂度
- 第一版重点是流程打通，而不是智能决策

当前邮件主题约定：

- `[OC]`：发给 OpenCode
- `[CX]`：发给 Codex
- `[KILL] <task_id>`：终止任务

### 4.2 不做完整 agent 平台

本项目不是 OpenClaw 替代品，不追求：

- 多平台通用 agent runtime
- 通用插件生态
- 通用消息总线
- 通用权限系统

项目的本质是一个**很薄的任务协作层**。

### 4.3 第一版优先邮件，而不是自建实时通信

原因：

- 邮件天然支持跨设备、跨网络、离线回合
- 交互是异步的，适合任务线程
- 用户可接受回合制，不追求实时聊天体验

### 4.4 后续安卓端不直接替代邮件协议

Android 端不是重新设计通信协议，而是先作为：

- 更好用的任务发信入口
- 更好读的任务线程视图
- 更方便的回复和查看工具

底层仍然以邮件为核心载体。

### 4.5 模型切换不作为当前阶段主功能

未来可能需要 profile 概念，例如：

- `fast`
- `strong`
- `vision`

但当前只允许在数据结构上预留 `profile` 字段，不做完整模型切换系统。

### 4.6 Markdown 只做只读渲染

只需要把 OpenCode / Codex 的回复正文渲染得更易读。

当前明确不做：

- Markdown 编辑器
- 所见即所得
- 富文本输入

---

## 5. PC 端系统：目标与架构

### 5.1 PC 端系统目标

构建一个**邮件驱动的任务分发器 / 线程协作后端**。

核心能力：

1. 收取任务邮件
2. 解析主题和正文
3. 将任务交给 OpenCode 或 Codex
4. 保存线程状态与执行结果
5. 用邮件回传状态 / 结果 / 问题
6. 支持线程式多轮回复

### 5.2 PC 端系统核心原则

- 单用户
- 自用
- 串行执行优先
- 文件系统保存状态
- 先 Mock，再接真实后端
- 先让 happy path 稳定，再补边界情况

### 5.3 PC 端主要模块（逻辑层面）

#### 5.3.1 `mail_ingress`

负责收邮件。

职责：

- IMAP 轮询新邮件
- 提取邮件头和正文
- 转为统一的 `MailEnvelope`

#### 5.3.2 `thread_resolver`

负责找到这封邮件属于哪个任务线程。

优先依据：

1. `In-Reply-To`
2. `References`
3. 规范化后的 `Subject`

#### 5.3.3 `context_layer`

负责组合：

- 当前邮件新增正文
- 本地线程状态
- 状态块（若有）

输出本轮解析上下文。

#### 5.3.4 `intent_parser`

负责把邮件语义压缩成结构化动作。

目标动作包括：

- `NEW_TASK`
- `UPDATE_TASK`
- `APPEND_CONTEXT`
- `STATUS_QUERY`
- `RERUN`
- `KILL`
- `UNKNOWN`

#### 5.3.5 `task_compiler`

将：

- 线程状态
- 当前动作
- 本轮新增正文

合成为新的 `TaskSnapshot`。

#### 5.3.6 `worker_dispatcher`

根据 `backend` 分发到：

- `OpenCodeAdapter`
- `CodexAdapter`

#### 5.3.7 `workspace_manager`

管理：

- 任务目录
- snapshot
- stdout/stderr
- result.json
- summary.md
- artifacts

#### 5.3.8 `mail_reporter`

发送系统状态邮件。

包括：

- `[ACCEPTED]`
- `[RUNNING]`
- `[DONE]`
- `[FAILED]`
- `[STATUS]`
- `[KILLED]`
- `[QUESTION]`（未来支持）

---

## 6. 任务状态与真相来源

### 6.1 设计原则

项目必须遵守：

> 本地状态文件才是真相来源。

具体顺序为：

1. 本地 `thread_state.json` / `snapshot.json` / `result.json`
2. 邮件中的 state capsule
3. 邮件原始线程引用文本

不能反过来。

### 6.2 典型本地目录结构

```text
runner/
  tasks/
    thread_001/
      thread_state.json
      snapshots/
        20260311_153000_a1b2.json
      runs/
        20260311_153000_a1b2/
          prompt.txt
          stdout.log
          stderr.log
          result.json
          summary.md
          artifacts/
      mail/
        raw_001.json
        raw_002.json
```

### 6.3 为什么要 state capsule

邮件线程天然会保留上下文，但它并不稳定：

- 用户可能删掉部分引用
- 邮件客户端可能折叠内容
- 自动引用格式不统一

所以每封系统邮件尾部应追加一个机器可读状态块，例如：

```text
---TASK-STATE-BEGIN---
thread_id: thread_001
task_id: 20260311_153000_a1b2
backend: opencode
repo_path: D:\proj\my_repo
workdir: src\postprocess
mode: modify
status: done
last_summary: 已完成 dataclass 重构，public API 未改动
---TASK-STATE-END---
```

其作用：

- 帮助回复解析恢复状态
- 帮助 Android 客户端结构化展示
- 提高容错性

---

## 7. 会话连续性设计

### 7.1 目标

用户希望任务能像 CLI 中一样持续会话，而不是每封邮件都重开全新上下文。

### 7.2 已确认结论

外部调用 OpenCode CLI / Codex CLI 时，只要显式保存并恢复 session/thread，即可实现“连续会话”的核心效果。

因此本项目要求在本地线程状态中保存：

- `backend_type`
- `backend_session_id` 或 `backend_thread_id`
- `runtime_mode`

### 7.3 两种运行模式

#### CLI resume 模式（当前优先）

- 每轮收到邮件时新起一个 CLI 进程
- 但使用 resume / continue 接到原会话
- 成本低，适合当前 MVP

#### 长驻服务模式（未来可选）

- OpenCode 走 `opencode serve`
- Codex 走 App Server / thread runtime
- 更接近真正持续在线的交互体验
- 当前不作为第一阶段目标

### 7.4 当前建议

先实现：

- `cli_resume`

后续如有必要再演进到：

- `server_runtime`

---

## 8. 多轮协作的边界

### 8.1 可以支持的多轮

本项目可以支持的多轮交互包括：

- 同一任务跨多封邮件持续补充
- 更新任务要求
- 补充上下文
- 查询状态
- 问题澄清
- 挂起后恢复
- 重跑
- 终止

### 8.2 不适合支持的多轮

本项目不适合追求：

- 高频实时聊天
- 像 IDE 配对编程那样连续来回问答
- 秒级流式交互
- 高密度边看边改边追问

项目应定位为：

> 异步任务线程多轮协商系统

而不是实时聊天系统。

### 8.3 建议增加的线程记录文件

为了更稳地支持多轮，建议项目后续维护：

- `turn_log.jsonl`
- `conversation_summary.md`

作用：

- `turn_log.jsonl`：记录每个回合发生了什么
- `conversation_summary.md`：压缩当前线程共识，减少重复喂历史正文

---

## 9. Codex 反问 / 用户选择机制

### 9.1 有必要支持

Codex 在复杂任务中可能提出：

- 缺失信息问题
- 风险确认问题
- 方案选择问题

邮件完全可以承接这种交互。

### 9.2 设计原则

不要把它设计成开放式聊天，而应做成受控的问答协议。

推荐只支持三类问题：

- `CHOICE_REQUIRED`
- `MISSING_INFO`
- `CONFIRM_BEFORE_RISKY_CHANGE`

### 9.3 建议新增状态

线程状态机后续应允许：

- `accepted`
- `running`
- `awaiting_user_input`
- `done`
- `failed`
- `killed`

### 9.4 建议问题邮件格式

```text
[QUESTION] <task_id>

Task: 20260312_101500_ab12
Backend: codex
Status: awaiting_user_input

Question:
是否允许修改 public API？

Choices:
A. 不允许
B. 允许，但尽量少改
C. 允许，按最佳方案处理

Current summary:
...
```

用户回复时尽量采用结构化格式，例如：

```text
Choice: A
Optional note: 先保守处理
```

---

## 10. 模型 / profile 策略

### 10.1 当前共识

当前不做完整模型切换功能。

### 10.2 允许做的预留

在以下结构中允许存在：

- `TaskSnapshot.profile`
- `ThreadState.profile`
- `ParsedMailAction.profile`

语义只到 profile 层，例如：

- `fast`
- `strong`
- `vision`

### 10.3 当前不做的内容

- 不允许邮件直接指定原始 model id
- 不做 profile -> model 的完整映射
- 不在当前阶段更改 adapter 执行逻辑

---

## 11. Android 方向：为什么不从零做邮箱

### 11.1 初始结论

如果只是做“发信助手”，从零做一个小 Android app 很轻。

但如果希望：

- 直接读取真实任务线程
- 更好地展示结果
- 基于真实邮件回复

那么继续自己造邮箱层并不划算。

### 11.2 最终选择

项目选择 **fork Thunderbird for Android** 作为 Android 端基础。

原因：

- 已有完整邮箱内核
- 活跃维护
- Apache-2.0 许可更适合 fork
- 模块化程度较好
- 比从零实现邮箱层划算太多

### 11.3 Android 端的定位

不是做另一个通用邮箱，而是做一个：

> Thunderbird for Android 的任务邮件专用壳

即：

- 保留邮箱内核
- 新增任务线程识别、任务视图与模板回复能力
- 普通邮件功能保留，但不是重点

---

## 12. Android 端：当前目标范围

### 12.1 必做

- 识别任务邮件线程
- 提供任务入口
- 任务线程列表页
- 任务线程详情页
- 解析状态块
- 将模型回复正文进行 Markdown 只读渲染

### 12.2 不做

- Markdown 编辑器
- 所见即所得
- 通知系统
- 复杂后台能力
- 自定义实时协议
- 复杂附件预览
- 改写整个邮箱渲染核心

---

## 13. Android 端任务邮件视图架构

### 13.1 总思路

在 Thunderbird Android 上新增一个 task-mail feature，而不是大面积侵入普通邮件渲染链。

### 13.2 推荐模块边界

建议新增：

- `feature:taskmail:api`
- `feature:taskmail:internal`

### 13.3 关键组件

#### `TaskThreadDetector`

负责识别任务线程。

强规则：

- subject 以 `[OC]` 开头
- subject 以 `[CX]` 开头
- 正文包含状态块标记

#### `TaskStateCapsuleParser`

负责：

- 提取状态块
- 解析成结构化字段
- 将状态与正文分离

#### `TaskMessageExtractor`

负责：

- 获取邮件正文
- 去除状态块
- 形成适合 Markdown 渲染的人类可读正文

#### `TaskMarkdownRenderer`

负责：

- 将正文按 Markdown 只读方式渲染
- 如果失败则回退为纯文本
- UI 不应直接依赖第三方 Markdown 库 API

#### `TaskThreadListScreen`

展示任务线程列表。

#### `TaskThreadDetailScreen`

展示任务线程详情。

每条消息建议卡片化显示：

- 发件人 / 时间
- 结构化状态区
- Markdown 渲染的正文区

### 13.4 数据流

```text
真实邮件线程
-> TaskThreadDetector
-> TaskMessageExtractor
-> TaskStateCapsuleParser
-> TaskMarkdownRenderer
-> TaskThreadList / TaskThreadDetail UI
```

### 13.5 为什么状态块不能直接进 Markdown

因为状态块是机器可读协议，不是正文内容。

如果直接参与 Markdown 渲染，会导致：

- 可读性下降
- 正文被污染
- 后续 UI 很难做结构化展示

因此必须分离：

- 状态块 -> 结构化展示
- 正文 -> Markdown 渲染

---

## 14. Android 端 Markdown 方案

### 14.1 已确认需求

只做**只读渲染**，不做编辑。

### 14.2 当前推荐

在 Thunderbird Android fork 路线上，优先使用 **Markwon**。

原因：

- 原生 Android 渲染
- 不依赖 WebView
- 成熟
- 适合只读展示

### 14.3 当前支持目标

优先级较高的 Markdown 能力：

- 标题
- 粗体 / 斜体
- 列表
- 引用
- 行内代码
- 代码块
- 链接

后续可选：

- 表格
- 任务列表
- 语法高亮

但不能影响第一版落地。

---

## 15. 当前阶段的开发节奏

### 15.1 PC 端任务分发器

当前已有阶段性规划：

- Phase 0：初始化与骨架
- Phase 1：本地状态层与 Mock 执行链
- Phase 2：邮件收发与新任务创建
- Phase 3：回复邮件上下文与状态查询
- Phase 4：真实后端 adapter 骨架
- Phase 5：稳定性补强与文档

后续新增需求已确认：

- 这些新增内容主要从 Phase 2 / Phase 3 起纳入
- 不应回滚已完成的 Phase 0 / 早期骨架

### 15.2 Android fork 功能链

当前建议阶段：

- Phase 0：仓库调研与接入点确认
- Phase 1：领域模型、解析链、fake repository、workspace/detail 骨架
- Phase 1.5：真实邮件桥接与 debug 冒烟验证
- Phase 2：抽屉入口与正式宿主接线
- Phase 3：任务线程详情增强与回复交互
- Phase 4：Markdown 只读渲染接入
- Phase 5：样式与稳定性补强

### 15.3 Android 当前状态（2026-03-14）

当前 Android 端已经完成：

- `feature:taskmail:api`
- `feature:taskmail:internal`
- 主题解析、`TASK-STATE` 解析、`TASK-QUESTION` 解析
- 真实邮件桥接与只读 repository 聚合
- `workspace -> session detail` 的正式宿主接线
- 抽屉 `Tasks` 入口与 `FeatureLauncher` 正式入口
- read-only `workspace` / `session detail` 页面
- debug preview、debug activity 与 debug deep link 验证路径
- `workspace -> detail -> workspace` 返回链路的真机冒烟验证

这段 Android 阶段描述现在已经是历史快照。

当前 Android 端的默认入口和最新读法请改读：

- `docs/taskmail/README.md`
- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/taskmail/planning/android/taskmail-next-development-plan-v0.2.md`

历史 phase 文档已迁到：

- `docs/taskmail/archive/android/planning/`

---

## 16. 关键非目标清单

为避免后续会话误判项目方向，以下内容明确不是当前项目目标：

- 通用 AI agent 平台
- 多用户 SaaS
- 实时聊天系统
- 通用自动路由器
- 自动策略规划系统
- 完整模型管理平台
- Android 通用邮箱产品
- 自建消息协议替代邮件
- Markdown 编辑器
- 复杂富文本系统
- 浏览器自动化客户端
- 高度可配置插件生态

---

## 17. 当前最重要的实现优先级

如果后续会话需要快速对齐优先级，应按以下顺序理解：

### 第一优先级

把 **PC 端任务线程后端** 做稳：

- 邮件收发
- 线程解析
- 状态保存
- Mock -> 真实 adapter
- 多轮回复
- 问题 / 恢复机制

### 第二优先级

在 **Android 端** 做一个：

- 基于 Thunderbird fork 的任务邮件壳
- 能更好地读任务线程
- 能识别状态块
- 能只读 Markdown 渲染回复正文

当前执行顺序应进一步收敛为：

- 在保持 `Phase 2` 正式入口稳定的前提下，先进入 `Phase 3` 的详情增强与回复交互
- 再进入 `Phase 4` 的 Markdown 只读渲染接入

### 第三优先级

在 Android 端增强：

- 模板回复
- 结构化选择回复
- 更好的任务入口
- 更好的状态卡片

---

## 18. 对后续会话的交接说明

如果别的会话接手本项目，必须理解以下几点：

1. 用户不是要一个通用聊天产品，而是要一个自用工程协作系统。
2. OpenCode 和 Codex 的分工已经明确：
   - OpenCode：便宜、明确、执行型代码任务
   - Codex：复杂、知识型、上下文重任务
3. 当前不做自动路由，用户手工决定 backend。
4. 任务线程的真相来源是本地状态文件，不是邮件引用文本。
5. 邮件是当前控制面；Android 客户端是未来更顺手的壳，但底层仍以邮件为核心。
6. Android 客户端不是从零做邮箱，而是 fork Thunderbird for Android。
7. Android 当前最重要功能不是发信，而是：
   - 任务线程识别
   - 状态块解析
   - Markdown 只读渲染
8. Android 当前最近一步不是正式入口，而是 `Phase 1.5` 的真实邮件桥接。
9. Markdown 只做只读显示，不做编辑。
10. 当前一切开发都应优先压缩范围、减少返工、先跑通主链。

---

## 19. 一句话总结

本项目是一个**自用的、邮件驱动的 AI 任务协作系统**：

- PC 端负责真实执行与状态管理
- 邮件线程提供异步多轮控制面
- Android 端通过 fork Thunderbird 提供更易用的任务邮件视图
- 重点是让 OpenCode / Codex 的长期协作更顺手，而不是做一个新的通用平台
