# TaskMail 主线入口

更新时间：2026-03-25

本文是 Android 仓库内 TaskMail 文档的默认入口。

默认阅读只回答三个问题：

- Android TaskMail 现在已经做到什么程度
- 当前为什么只能这样读，哪些边界不能误写
- 接下来真正活跃且唯一的主线是什么

## 默认阅读顺序

1. `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
2. `docs/TASKMAIL-MAIL-RULES.md`
3. `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
4. `docs/taskmail/planning/android/taskmail-vps-first-multi-pc-authority-v0.1.md`
5. `docs/taskmail/planning/android/taskmail-vps-first-multi-pc-user-requirements-authority-v0.1.md`
6. `docs/taskmail/planning/platform/taskmail-vps-first-control-plane-freeze-v0.1.md`
7. `docs/taskmail/planning/android/taskmail-next-development-plan-v0.2.md`

只有在下列场景再继续扩展阅读：

- 需要设备 / debug / adb 路径时：`docs/TASKMAIL-DEBUG-VALIDATION.md`
- 需要背景上下文时：`docs/taskmail_project_overview.md`
- 需要当前活跃 planning 细节时：`docs/taskmail/planning/README.md`
- 需要追历史证据、handoff 或旧 planning 时：`docs/taskmail/archive/README.md`

## 当前 authority 分层

- 当前实现真相：`docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- 当前 mail 兼容协议 authority：`docs/TASKMAIL-MAIL-RULES.md`
- 当前验证摘要：`docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- future-direction mainline authority：`docs/taskmail/planning/android/taskmail-vps-first-multi-pc-authority-v0.1.md`
- future-direction user-requirement authority：`docs/taskmail/planning/android/taskmail-vps-first-multi-pc-user-requirements-authority-v0.1.md`
- 调试 / 设备路径：`docs/TASKMAIL-DEBUG-VALIDATION.md`

这些文档共同回答当前事实；历史 planning 与 handoff 不再承担当前 authority。

## 当前活跃主线

- 唯一主线 authority：`docs/taskmail/planning/android/taskmail-vps-first-multi-pc-authority-v0.1.md`
- 用户需求 authority：`docs/taskmail/planning/android/taskmail-vps-first-multi-pc-user-requirements-authority-v0.1.md`
  这份文档当前冻结了 Android 侧“用户如何理解并使用多 PC TaskMail 工作台”的主读法
- 信息架构 companion：`docs/taskmail/planning/android/taskmail-vps-first-multi-pc-information-architecture-v0.1.md`
  这份文档当前冻结了页面结构、导航层次与页面职责
- 页面状态与交互映射 companion：`docs/taskmail/planning/android/taskmail-vps-first-multi-pc-viewstate-action-mapping-v0.1.md`
  这份文档当前冻结了页面状态、VPS 领域对象与交互动作之间的映射
- 核心页面低保真方案：`docs/taskmail/planning/android/taskmail-vps-first-multi-pc-core-screen-low-fi-v0.1.md`
  这份文档当前冻结了首页、新任务页、Session 页的低保真结构
- 高保真视觉方向：`docs/taskmail/planning/android/taskmail-vps-first-multi-pc-visual-direction-v0.1.md`
  这份文档当前冻结了主线视觉气质、层级重点与高保真方向
- 高保真 HTML 预览：`docs/taskmail/planning/android/mockups/taskmail-vps-first-multi-pc-high-fi-preview-v0.1.html`
  这份文件当前提供首页、新任务页、Session 页的静态视觉预览
- 历史复盘高保真补充预览：`docs/taskmail/planning/android/mockups/taskmail-vps-first-multi-pc-history-review-high-fi-preview-v0.1.html`
  这份文件当前补充了“历史回合列表 / 回合详情”的静态视觉预览，并约束过程记录只在回合详情中展开
- 页面级 API 需求：`docs/taskmail/planning/android/taskmail-vps-first-multi-pc-page-api-requirements-v0.1.md`
  这份文档当前冻结了三张核心页面对 VPS 的最小能力需求
- Compose 页面结构与实现骨架：`docs/taskmail/planning/android/taskmail-vps-first-multi-pc-compose-screen-structure-v0.1.md`
  这份文档当前冻结了现有 `Workspace / NewTask / SessionDetail` 页面如何演进到 VPS-first 主线的 Compose 骨架
- Android 骨架实施计划：`docs/taskmail/planning/android/taskmail-vps-first-multi-pc-android-skeleton-implementation-plan-v0.1.md`
  这份文档当前冻结了 Android 第一批编码切片、文件落点与推荐顺序
- Session Slice 1 文件计划：`docs/taskmail/planning/android/taskmail-vps-first-multi-pc-session-slice1-file-plan-v0.1.md`
  这份文档当前把 `TaskSessionDetail` 第一刀细化到字段、组件、文件与测试落点
- 与 PC 端握手联调时机清单：`docs/taskmail/planning/android/taskmail-vps-first-multi-pc-pc-handshake-readiness-checklist-v0.1.md`
  这份文档当前冻结了“不要太早联调”时 Android 侧应达到的最低握手门槛
- 控制面全量字段冻结：`docs/taskmail/planning/platform/taskmail-vps-first-control-plane-freeze-v0.1.md`
  这份文档当前作为 Android 与 PC/VPS 并行开发的共享字段冻结入口
- 控制面设计草案：`docs/taskmail/planning/platform/taskmail-multi-pc-control-plane-v0.1.md`
  这份文档当前也冻结了 `execution_policy = backend / profile / permission / backend_transport`
- `PC <-> VPS` 协议草案：`docs/taskmail/planning/platform/taskmail-pc-vps-control-protocol-v0.1.md`
  这份文档当前明确了 `execution_policy`、`resolved_model` 与能力上报/拒绝语义
- 执行策略附录：`docs/taskmail/planning/platform/taskmail-execution-policy-appendix-v0.1.md`
  这份文档当前专门收口 `Codex / OpenCode` 的模型档位、权限档位与 backend-specific 投影
- 旧 mail 语义映射附录：`docs/taskmail/planning/platform/taskmail-legacy-mail-to-control-plane-mapping-v0.1.md`
  这份文档当前收口 legacy mail 语义如何落到 `command / event / result`
- `command / event` payload 附录：`docs/taskmail/planning/platform/taskmail-command-event-payload-appendix-v0.1.md`
  这份文档当前收口不同 `command_type / event_type` 的具体 payload 形状
- `result / artifact / error_code` 附录：`docs/taskmail/planning/platform/taskmail-result-artifact-errorcode-appendix-v0.1.md`
  这份文档当前收口结果字段、artifact 描述与 machine-readable 错误码
- Android 侧总主线 roadmap：`docs/taskmail/planning/android/taskmail-next-development-plan-v0.2.md`

## 跨仓实现参考

以下文档建议作为 `PC/VPS repo-side implementation reference` 挂在主入口里，方便 Android 进入实际接线阶段时对齐，但它们不替代 Android 当前 truth，也不替代 Android future-direction authority：

- PC 计划索引：`E:\projects\mail_based_task_manager\docs\plans\README.md`
- PC 主线 note：`E:\projects\mail_based_task_manager\docs\plans\vps_first_multi_pc_control_plane_mainline_v0.1.md`
- PC Phase 1 实施计划：`E:\projects\mail_based_task_manager\docs\plans\vps_first_multi_pc_phase1_execution_plan_v0.1.md`

## 当前兼容 / closeout 参考线

- `docs/taskmail/planning/android/taskmail-phase5-new-task-guarded-rollout-observation-plan-v0.1.md`
- `docs/taskmail/planning/android/taskmail-phase5-reply-status-android-implementation-plan-v0.1.md`
- `docs/taskmail/planning/android/taskmail-phase5-project-sync-android-implementation-plan-v0.1.md`
- `docs/taskmail/planning/android/taskmail-android-public-plaintext-direct-connect-authority-v0.1.md`
- `docs/taskmail/planning/android/taskmail-android-public-plaintext-direct-connect-plan-v0.1.md`

这些文档仍有现实价值，但不再是默认主线入口，也不再共同构成 future-direction mainline。

## archive 的用途

`docs/taskmail/archive/` 只承接三类材料：

- 历史 handoff
- 退役 planning / phase 文档
- 长版验证历史

如果你只是想知道“现在是什么”，不要先读 archive。

## 后续维护规则

- 不再把 `taskmail-next-session-handoff-*` 放回默认阅读路径。
- 同一条 active 主线只保留一份 owner planning 文档；状态变化直接回写 owner doc。
- 文档更新顺序固定为：`MAIL-RULES -> CURRENT-STATUS -> VALIDATION-LEDGER -> authority / active planning`。
- 运行过程、重试过程、临时样本一律优先进入 archive，而不是继续堆进 authority 文档。
