# TaskMail 文档导航与维护约定

更新时间：2026-03-22

本文用于收口 Android 仓库内的 TaskMail 文档入口，并统一后续维护约定。

当前这条文档线的主要问题不是文件级坏编码，而是：

- authority 文档、planning 文档、handoff 文档混在一起读
- 新旧文档的中英文风格不一致
- 部分较长文档已经进入持续更新阶段，局部段落仍保留历史英文表述

## 1. 先读这些文档

判断 Android TaskMail 当前事实、协议和验证状态时，先读以下文档：

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/TASKMAIL-MAIL-RULES.md`
- `docs/TASKMAIL-DEBUG-VALIDATION.md`

如需继续看规划或历史材料，再读：

- `docs/taskmail/planning/README.md`

## 2. 文档分层

当前建议把 TaskMail 文档按以下层次理解：

- 当前事实：`docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- 协议规则：`docs/TASKMAIL-MAIL-RULES.md`
- 验证证据：`docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- 调试/设备验证路径：`docs/TASKMAIL-DEBUG-VALIDATION.md`
- 当前活跃 planning：`docs/taskmail/planning/android/` 中仍被 `planning/README.md` 点名为 active 的文档
- 历史/归档材料：旧 `phase` 文档、旧 planning 文档、旧 handoff 文档
- 补充规范与协作文档：如 `docs/TASKMAIL-GITHUB-SYNC-WORKFLOW.md`、`docs/taskmail/taskmail-reply-email-ideal-requirements.md`

历史文档可以保留上下文，但不能覆盖当前 authority 文档。

## 3. 编码与语言基线

TaskMail 文档后续维护统一遵循以下约定：

- Markdown 文件编码遵循仓库 `.editorconfig`：`utf-8`、`lf`、文件末尾保留换行
- 新增或更新的仓库文档默认使用中文
- 协议字段名、代码标识、文件路径、命令行、契约标题保持原文，不做意译
- 历史长文在增量维护时，优先把被修改到的标题、导语和新增段落改成中文，不要求一次性全文重译
- 不再新增“同一角色但重复命名”的平行说明文档，优先补到最近的 authority 或 index 文档

## 4. 推荐更新顺序

当 TaskMail 行为或文档发生变化时，建议按这个顺序维护：

1. 协议或语义变化：先改 `docs/TASKMAIL-MAIL-RULES.md`
2. 实现状态变化：再改 `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
3. 验证证据变化：再改 `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
4. 调试或设备路径变化：补 `docs/TASKMAIL-DEBUG-VALIDATION.md`
5. 规划边界或下阶段工作变化：最后改 `docs/taskmail/planning/README.md` 与相关 planning / handoff 文档

如果只是历史理解或入口导航变更，不要反向改写 authority 文档的事实结论。

## 5. 当前整理结论

本次仓库内排查的直接结论是：

- TaskMail 相关 Markdown 文件本身已经基本是标准 UTF-8，无明显坏编码或替换字符
- 更需要收口的是“入口、角色、语言风格”
- 当前 authority 文档可以继续沿用原文件名，避免打断已有引用
- 较长文档中的历史英文段落可在后续实际维护时继续逐步中文化
