# TaskMail Android 验证台账

本文只保留 TaskMail Android 当前验证摘要。

长版历史记录已迁到：

- `docs/taskmail/archive/validation/taskmail-android-validation-ledger-history-2026-03-23.md`

## 日期

- 最后更新：2026-03-23
- 当前摘要覆盖到的最新可执行验证会话：2026-03-23

## 文档维护约定

- 本文件只回答“现在哪些能力已经有当前验证证据，哪些仍未完全闭环”
- 详细命令序列、样本过程、长篇 live/manual 记录不再留在本文件
- 当前实现事实请看 `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- 协议 authority 请看 `docs/TASKMAIL-MAIL-RULES.md`

## 当前验证矩阵

| 能力 | 当前证据 | 仍未闭环 |
| --- | --- | --- |
| 真实读路径、session 投影、协议感知解析 | 已有 focused repository/parser/test coverage；当前读法包括 `[SYNC]` 不进入 session 投影 | 更宽的外部样本 replay 仍不是当前 captured summary 的一部分 |
| formal-host 入口、drawer handoff、workspace/detail 读面 | 已有 focused navigation/UI/test 证据与设备 smoke | 更宽设备覆盖仍有限 |
| `new_task` direct-first + mail fallback | 已有 focused sender/usecase/ViewModel/screen/persistence coverage；current latest evidence 保留 `requestId` / `receiptId` / `transportMessageId`；live evidence 已闭到 observation 边界 | 不再缺 Android 侧 narrow blocker；后续只在 fresh rollback / mismatch signal 出现时再扩大 |
| `reply` / `/status` guarded direct lane | 已有 focused persistence/ViewModel/screen coverage；formal-host `thread_105` 已正向证明 `/status` 与 plain reply 的 direct accepted path | fallback artifact 的 same-run strong bind 仍未完全收口；PC-side canonical fallback artifact 仍需补齐 `action_type`、`target_session_identity` 与 ingress anchors |
| plain reply / attachment / quick answer / multi-question / paused `/resume` mail path | 已有 focused tests 和既有 live/manual smoke，当前仍应按 mail semantics 读取 | 更宽语料库覆盖不是当前摘要目标 |
| `[SYNC] Project list` 渲染、repo prefill、projection boundary | formal-host smoke 已证明页面可读、`Use this repo` 可回填、`[SYNC]` 不进入 session/detail 投影 | “这次点击触发的 direct request”与“同轮 canonical `[SYNC] Project Folder List` 回流”仍需更强 closeout |
| `[SYNC]` direct-first request path | 当前已证明 relay ack 恢复到亚秒级，waiting UI 生效，有限 follow-up refresh 生效，且当前未观察到新的本机 mail fallback | 上游 canonical reply 回流时间线仍可能超出当前有限窗口；是否需要更长 follow-up 仍取决于联调结论 |
| `[SYNC]` device file debug trace switch | `TaskMail relay debug` 现可保存 `Project sync debug file logging` 开关；真机已证明开启时会写 `project-sync-debug.log`，关闭并删除旧文件后再次点 `Sync project list` 不会重建该文件 | debug-only retained capability；只应用于 focused repro，并在抓取后及时关闭 |
| runtime bot-mailbox settings | 已有 focused repository/ViewModel/UI/navigation coverage，且设备 happy path 已有正向 smoke | invalid / clear edge 行为仍主要依赖 focused automated coverage |

## 最近一次代表性 clean commands

以下命令代表当前 TaskMail Android 近端验证的最新干净读法：

```powershell
.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest --tests "*RelayTaskMailDirectProjectSyncSenderTest" --tests "*TaskProjectSyncViewModelTest" --tests "*OkHttpRelayConnectionClientTest" --console=plain
.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest --tests "*TaskSessionDetailViewModelTest" --tests "*RelayTaskMailDirectSessionActionSenderTest" --console=plain
.\gradlew.bat :feature:taskmail:internal:detekt --console=plain
.\gradlew.bat :feature:taskmail:internal:lintDebug --console=plain
.\gradlew.bat :app-thunderbird:assembleFossDebug --console=plain
```

更细的历史命令与会话过程请回查 archive validation history。

## 当前仍未完全闭环的点

- `reply` / `/status`：需要继续收口 fallback artifact 的 stronger same-run bind，而不是继续扩 direct scope
- `[SYNC]`：需要拿到 direct request、relay ingress、canonical `[SYNC] Project Folder List` reply 的同轮时间线，再判断是否需要扩大 Android follow-up refresh 窗口
- `Project list` 当前 direct-first 只应按 single-account available 读取，多账号严格协商不在当前验证结论内
- repo-wide 更宽构建 / 质量门并不是当前摘要的一部分；本文件只保留 TaskMail 窄验证结论

## archive 使用说明

当你需要以下内容时，再进入 archive validation history：

- 某一条 dated 段落最初怎么写
- 某次设备 / live smoke 的完整过程
- 某个命令组合是在什么上下文里跑的
