# TaskMail Planning 索引

更新时间：2026-03-25

本目录只保留当前仍然活跃的 planning owner docs 与少量 reference docs。

它不承担当前实现事实，也不承担验证证据 authority。

## 当前 active planning

- 主线 authority：`android/taskmail-vps-first-multi-pc-authority-v0.1.md`
- 用户需求 authority：`android/taskmail-vps-first-multi-pc-user-requirements-authority-v0.1.md`
- Android 信息架构：`android/taskmail-vps-first-multi-pc-information-architecture-v0.1.md`
- 页面状态与交互映射：`android/taskmail-vps-first-multi-pc-viewstate-action-mapping-v0.1.md`
- 核心页面低保真方案：`android/taskmail-vps-first-multi-pc-core-screen-low-fi-v0.1.md`
- 高保真视觉方向：`android/taskmail-vps-first-multi-pc-visual-direction-v0.1.md`
- 高保真 HTML 预览：`android/mockups/taskmail-vps-first-multi-pc-high-fi-preview-v0.1.html`
- 新任务页高保真补充预览：`android/mockups/taskmail-vps-first-multi-pc-new-task-high-fi-preview-v0.1.html`
- 首页树形工作台高保真补充预览：`android/mockups/taskmail-vps-first-multi-pc-home-tree-high-fi-preview-v0.1.html`
- 历史复盘高保真补充预览：`android/mockups/taskmail-vps-first-multi-pc-history-review-high-fi-preview-v0.1.html`
- Session 状态轮替高保真补充预览：`android/mockups/taskmail-vps-first-multi-pc-session-mode-high-fi-preview-v0.1.html`
- UI 到代码接口实现清单：`android/taskmail-vps-first-multi-pc-ui-interface-implementation-checklist-v0.1.md`
- UI Skeleton 本次执行边界：`android/taskmail-vps-first-multi-pc-ui-skeleton-boundary-v0.1.md`
- 页面级 API 需求：`android/taskmail-vps-first-multi-pc-page-api-requirements-v0.1.md`
- 环境库存 contract：`android/taskmail-vps-first-multi-pc-environment-inventory-contract-v0.1.md`
- Compose 页面结构与实现骨架：`android/taskmail-vps-first-multi-pc-compose-screen-structure-v0.1.md`
- Android 骨架实施计划：`android/taskmail-vps-first-multi-pc-android-skeleton-implementation-plan-v0.1.md`
- Session Slice 1 文件计划：`android/taskmail-vps-first-multi-pc-session-slice1-file-plan-v0.1.md`
- 与 PC 端握手联调时机清单：`android/taskmail-vps-first-multi-pc-pc-handshake-readiness-checklist-v0.1.md`
- 控制面全量字段冻结：`platform/taskmail-vps-first-control-plane-freeze-v0.1.md`
- 平台控制面设计：`platform/taskmail-multi-pc-control-plane-v0.1.md`
- `PC <-> VPS` 协议草案：`platform/taskmail-pc-vps-control-protocol-v0.1.md`
- 执行策略附录：`platform/taskmail-execution-policy-appendix-v0.1.md`
- 旧 mail 语义映射附录：`platform/taskmail-legacy-mail-to-control-plane-mapping-v0.1.md`
- `command / event` payload 附录：`platform/taskmail-command-event-payload-appendix-v0.1.md`
- `result / artifact / error_code` 附录：`platform/taskmail-result-artifact-errorcode-appendix-v0.1.md`
- Android 总主线 roadmap：`android/taskmail-next-development-plan-v0.2.md`

## 跨仓 repo-side 参考

以下文档建议作为 Android planning 阶段的跨仓参考输入保留：

- `E:\projects\mail_based_task_manager\docs\plans\README.md`
- `E:\projects\mail_based_task_manager\docs\plans\vps_first_multi_pc_control_plane_mainline_v0.1.md`
- `E:\projects\mail_based_task_manager\docs\plans\vps_first_multi_pc_phase1_execution_plan_v0.1.md`
- `E:\projects\mail_based_task_manager\docs\plans\android_facing_environment_inventory_facade_requirements_v0.1.md`

这些文档当前的正确读法是：

- repo-side mainline / phase ordering / implementation sequencing reference
- 不替代 Android 当前 truth
- 不替代 Android authority / companion planning 链

## 当前兼容 / closeout / reference docs

- `android/taskmail-phase5-new-task-guarded-rollout-observation-plan-v0.1.md`
- `android/taskmail-phase5-reply-status-android-implementation-plan-v0.1.md`
- `android/taskmail-phase5-project-sync-android-implementation-plan-v0.1.md`
- `android/taskmail-android-public-plaintext-direct-connect-authority-v0.1.md`
- `android/taskmail-android-public-plaintext-direct-connect-plan-v0.1.md`
- `android/taskmail-phase0-public-plaintext-baseline-v1.md`
- `android/taskmail-phase2-direct-outbound-contract-v0.1.md`
- `android/taskmail-phase4-dual-stack-boundary-freeze-v0.1.md`
- `android/phase4_dual_stack_parity_checklist.md`
- `android/phase4_mismatch_ledger.md`
- `android/phase4_rollback_trigger_note.md`

## archive 在哪里

- handoff：`../archive/android/handoff/`
- 历史 planning / phase：`../archive/android/planning/`
- 长版验证历史：`../archive/validation/`

## 当前 owner 规则

- 同一条 active 主线只保留一份 owner planning 文档。
- 当 owner doc 需要拆出“收敛蓝图”或“调试 / harness”这类配套设计时，允许少量 companion docs 与 owner doc 并存，但它们必须显式依附于该 owner doc。
- 只有出现新的主线阶段边界时，才允许新建 owner doc。
- 细碎的会话续接、live smoke 过程、运行态 pitfall，默认进入 archive handoff。
- active planning 的 `Read First` 不再要求 handoff 作为必读输入。
