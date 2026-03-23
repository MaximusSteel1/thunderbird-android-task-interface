# TaskMail 文档清理工程 Charter（v0.1）

更新时间：2026-03-23

## 1. 这份文档的角色

本文是本轮 TaskMail 文档清理工程的施工期规范。

它回答的是：

- 这次清理要把文档结构收成什么样
- 哪些材料留在主路径，哪些进入 archive
- active planning 如何定义 owner doc
- 什么条件算“这轮清理完成”

它不是 TaskMail 内容 authority。

## 2. 当前问题

当前 TaskMail 文档存在以下结构性噪音：

- authority、planning、handoff 混在同一阅读路径
- `CURRENT-STATUS` 与 `VALIDATION-LEDGER` 过长，已经接近会话日志
- active planning 中存在大量“承接上一份”的连续 note
- 最新事实被临时 handoff 吞掉，导致主线读法滞后

## 3. 目标结构

### 3.1 默认阅读路径

默认阅读路径固定为：

1. `docs/taskmail/README.md`
2. `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
3. `docs/TASKMAIL-MAIL-RULES.md`
4. `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
5. active planning owner docs

### 3.2 authority 分层

- 当前实现：`CURRENT-STATUS`
- 当前规则：`MAIL-RULES`
- 当前验证：`VALIDATION-LEDGER`
- 调试与设备路径：`DEBUG-VALIDATION`

### 3.3 planning 分层

- 每条 active 主线只有一份 owner planning doc
- 本轮 active owner docs 固定为：
  - `taskmail-next-development-plan-v0.2.md`
  - `taskmail-phase5-new-task-guarded-rollout-observation-plan-v0.1.md`
  - `taskmail-phase5-reply-status-android-implementation-plan-v0.1.md`
  - `taskmail-phase5-project-sync-android-implementation-plan-v0.1.md`

### 3.4 archive 分层

- handoff：`docs/taskmail/archive/android/handoff/`
- historical planning：`docs/taskmail/archive/android/planning/`
- validation history：`docs/taskmail/archive/validation/`

## 4. 施工规则

- 不再把 `taskmail-next-session-handoff-*` 放回主阅读路径。
- `CURRENT-STATUS` 只保留当前真相与当前边界，不再堆时间线叙事。
- `VALIDATION-LEDGER` 只保留当前验证摘要，不再承担长篇会话记录。
- active planning 的状态变化直接回写 owner doc，不再追加 sibling note。
- 只有仍在约束当前读法的文档才能留在 planning 目录；其余统一归档。

## 5. 批次顺序

1. 建 archive 骨架并切断默认入口
2. 重写主入口与 planning 索引
3. 缩写 `CURRENT-STATUS`
4. 缩写 `VALIDATION-LEDGER`
5. 收口 active planning owner docs
6. 更新 `SUMMARY.md` 与其余入口引用
7. 用搜索检查 handoff 是否仍暴露在主路径

## 6. 验收标准

- 默认阅读路径中不再出现 handoff。
- `CURRENT-STATUS` 与 `VALIDATION-LEDGER` 都能被当成 summary docs 读取。
- 所有仍活跃的主线都已提升为 owner planning doc。
- archive 下能找到 handoff、历史 planning 与长版验证历史。
- `planning/README.md` 不再把 handoff 列为 active planning。

## 7. 完成后如何处理

- 如果后续还需要持续治理，这份 charter 可保留为短期施工记录。
- 如果结构已稳定，长期规则应回写到 `docs/taskmail/README.md`，本文再降级为 archive。
