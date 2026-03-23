# Task Mail Phase 0 调研

## 范围

本文记录的是：为这个 Thunderbird for Android fork 引入 Task Mail 功能时所做的 Phase 0 仓库调查。

Phase 0 的目标是：

- 找出当前代码库中最合适的集成点
- 为新功能提出模块边界建议
- 说明 normal mail 与 task mail 应如何分离
- 列出后续阶段很可能会触及的具体文件

本阶段不实现该功能。

> 状态说明（2026-03-13）：本文保留为最初的 Phase 0 调研记录。
> 仓库现在已经在 `feature/taskmail` 下包含了 Phase 1 TaskMail skeleton。
> 当前 Android 实现状态请看 `docs/taskmail/archive/android/planning/TASKMAIL-ANDROID-PHASE1.md`。
> 下一步执行计划请看 `docs/taskmail/archive/android/planning/TASKMAIL-ANDROID-PHASE1-5.md`。

## 本地仓库状态

- 在最初进行 Phase 0 调研时，工作区是这个 fork 的本地源码快照。
- 在当前工作区中，仓库已经有本地 `.git` 元数据，并且已经落地了 `feature/taskmail` 的 Phase 1 实现。
- 除非本文明确标注为当前事实，否则应将这里的实现状态理解为历史记录。

Fork URL：

- `https://github.com/MaximusSteel1/thunderbird-android-task-interface`

## 已阅读文档

- `README.md`
- `docs/CONTRIBUTING.md`
- `docs/architecture/adr/0009-api-internal-split.md`
- `docs/architecture/ui-architecture.md`
- `AGENTS.md`

## 高层结论

当前仓库结构在这个功能真正关心的集成点上，与预期的 Thunderbird for Android 模块化架构是一致的。

推荐的 Phase 0 结论在原则上没有变化，并且现在已经能对照本地代码快照再次确认：

- 新增一个隔离的 feature 区域：
  - `:feature:taskmail:api`
  - `:feature:taskmail:internal`
- 除非没有合理替代方案，否则 task-specific business logic 不要放进 `legacy:*`
- 在现有 navigation drawer action area 中增加一个 `Tasks` 入口
- 从该入口打开一个独立的 Task Mail host screen
- 保持 normal mail list 与 normal message view 不变
- 只在 Task Mail feature 内部执行 task state parsing 与 markdown rendering

## 仓库发现

## 1. 当前模块结构兼容新增 feature

项目在 `settings.gradle.kts` 中使用了清晰的 feature/core 模块结构。

相关的现有 feature 模式包括：

- `:feature:mail:message:list:api`
- `:feature:mail:message:list:internal`
- `:feature:mail:message:reader:api`
- `:feature:mail:message:reader:impl`
- `:feature:navigation:drawer:api`
- `:feature:navigation:drawer:dropdown`

相关 ADR：

- `docs/architecture/adr/0009-api-internal-split.md`

结论：

- 新的 `taskmail` feature 应遵循相同的 API/internal split
- 公共契约应放在 `:feature:taskmail:api`
- 实现、UI、data mapping 与 parsing 应放在 `:feature:taskmail:internal`
- DI 绑定应发生在 `app-common` 或 app modules，而不是塞进无关 feature

## 2. App 入口与接线

观察到的文件：

- `app-common/src/main/kotlin/net/thunderbird/app/common/MainActivity.kt`
- `app-common/src/main/kotlin/net/thunderbird/app/common/feature/AppCommonFeatureModule.kt`
- `app-common/src/main/kotlin/net/thunderbird/app/common/feature/MessageListLauncher.kt`

当前流程：

- `MainActivity` 把启动委托给 `StartupRouter`
- `MessageListLauncher` 负责拉起 `MessageHomeActivity`
- `app-common` 是放依赖接线与 launcher bindings 的正确位置

结论：

- 如果 Task Mail 要暴露 launcher contract，`app-common` 是最合适的组合点
- 这与现有仓库架构和 ADR 规则一致

## 3. Normal mail home screen host

观察到的文件：

- `legacy/ui/legacy/src/main/java/com/fsck/k9/activity/MessageHomeActivity.kt`

已确认的 `MessageHomeActivity` 职责：

- 承载 navigation drawer
- 承载 normal message list
- 承载 normal message view container
- 处理 split view 与 single-pane 行为
- 处理 folder opening、thread opening、search 与 message navigation

重要的本地证据：

- drawer 在 `initializeDrawer()` 与 `initializeFolderDrawer()` 中初始化
- `DropDownDrawer` 已经接好了 folder 与 settings actions
- 这个 activity 仍然是一个体量很大的 legacy host，用于承载 mail browsing 行为

结论：

- 在 Phase 1 中，不应通过深度修改 normal mail host 来实现 Task Mail
- 最稳妥的路径是：从 drawer 拉起一个独立的 Task Mail host screen

## 4. Navigation drawer 是最好的 Task 入口

观察到的文件：

- `feature/navigation/drawer/dropdown/src/main/kotlin/net/thunderbird/feature/navigation/drawer/dropdown/DropDownDrawer.kt`
- `feature/navigation/drawer/dropdown/src/main/kotlin/net/thunderbird/feature/navigation/drawer/dropdown/ui/DrawerContract.kt`
- `feature/navigation/drawer/dropdown/src/main/kotlin/net/thunderbird/feature/navigation/drawer/dropdown/ui/setting/FolderSettingList.kt`
- `feature/navigation/drawer/dropdown/src/main/res/values/strings.xml`
- `legacy/ui/legacy/src/main/java/com/fsck/k9/activity/MessageHomeActivity.kt`

当前 drawer action area 已包括：

- Sync account
- Manage folders
- Sync all accounts
- Settings

为什么这里是 Phase 2 最好的入口点：

- 它本来就用于全局 app actions，而不是 folder content
- 它不会改变 message list 语义或 folder hierarchy 语义
- 它能保持 normal mail entry 不变
- 它能把对 legacy host 的风险降到最低

推荐的 drawer 集成方式：

- 增加一个新的 drawer event，例如 `OnTasksClick`
- 增加一个新的 drawer effect，例如 `OpenTasks`
- 在 `FolderSettingList` 里增加一个新条目
- 新增一个 `Tasks` string resource
- 在 `DropDownDrawer` 中接入新的 callback
- 在 `MessageHomeActivity` 中处理 launch 行为

## 5. 不应直接复用 normal message list 作为 Task Mail UI

观察到的文件：

- `feature/mail/message/list/api/...`
- `feature/mail/message/list/internal/...`
- `legacy/ui/legacy/src/main/java/com/fsck/k9/ui/messagelist/MessageListFragmentBridgeContract.kt`

关键观察：

- normal mail list 不是一个可以轻量复用的独立 screen
- 它仍通过 legacy host bridge 承载，并且内含 folder/search/thread 假设
- 它的 UI model 面向的是 normal mail 字段，而不是 task-specific metadata

Task Mail list 需要额外字段，例如：

- backend
- latestStatus
- latestSummary
- messageCount
- unreadCount

结论：

- 可以在有帮助时复用数据访问模式
- 但不要强行把 Task Mail 塞进 `MessageListFragment` 或 `MessageListFragmentBridgeContract`
- 应在新的 feature module 中构建一个聚焦的 Task Mail list screen

## 6. Normal message detail 应保持不变

观察到的文件：

- `legacy/ui/legacy/src/main/java/com/fsck/k9/ui/messageview/MessageViewContainerFragment.kt`
- `legacy/ui/legacy/src/main/java/com/fsck/k9/ui/messageview/MessageViewFragment.kt`
- `legacy/ui/legacy/src/main/java/com/fsck/k9/ui/messageview/MessageContainerView.kt`

关键观察：

- normal message reading 仍然通过 legacy view code 流转
- `MessageContainerView` 围绕 `WebView`、HTML 与 plain rendering 构建
- 这套 message view stack 还承担 attachments、crypto、printing、copy/move/refile 以及其他 mail-specific logic

结论：

- 不要把 task markdown rendering 加进 normal mail reading stack
- 不要全局修改 normal message body rendering
- 应构建一个独立的 Task Mail detail screen，并只放 task-specific rendering

这与产品要求直接一致：

- markdown 只用于 task mail
- normal mail experience 不应改变

## 7. 现有 Compose host 模式在概念上可复用

观察到的文件：

- `feature/launcher/src/main/kotlin/app/k9mail/feature/launcher/FeatureLauncherActivity.kt`
- `feature/launcher/src/main/kotlin/app/k9mail/feature/launcher/FeatureLauncherTarget.kt`
- `feature/launcher/src/main/kotlin/app/k9mail/feature/launcher/FeatureLauncherExternalContract.kt`

这个仓库已经存在一种模式：

- 一个 feature 有自己的 host activity
- 通过 deep links 或 route targets 打开
- app wiring 仍然留在 composition modules 中

结论：

- Task Mail 可以沿用类似的 feature-host 模式
- 对 Phase 2 来说，独立的 `TaskMailActivity` 仍然是最简单的路径
- 如果需要，后续也可以再加 route-based launcher

## 8. 历史说明：在 Phase 0 时没有发现现成的 taskmail 实现

在最初的 Phase 0 调研时，本地仓库搜索没有发现以下内容的现有实现：

- `TaskMail`
- `taskmail`
- `TASK-STATE`
- `Markwon`

结论：

- 这个 feature 可以作为一个全新的区域被干净引入
- 当时不存在必须优先保留或重构的半成品实现

当前说明（2026-03-13）：

- 对当前工作区来说，这一点已经不再成立。
- `feature/taskmail:api` 与 `feature/taskmail:internal` 已经存在，并应作为所有后续工作的实现基线。

## 推荐的功能设计

## 模块提案

新增：

- `:feature:taskmail:api`
- `:feature:taskmail:internal`

推荐的 package roots：

- `net.thunderbird.feature.taskmail`
- `net.thunderbird.feature.taskmail.internal`

## API module 职责

建议 `:feature:taskmail:api` 包含：

- `TaskThreadSummary`
- `TaskMessageDetail`
- `TaskStateCapsule`
- `TaskThreadDetail`
- `RenderMode`
- public repository/query contracts
- 如有需要，可放 launcher 或 navigation contract

## Internal module 职责

建议 `:feature:taskmail:internal` 包含：

- `TaskThreadDetector`
- `TaskStateCapsuleParser`
- `TaskMessageExtractor`
- `TaskMarkdownRenderer`
- repository implementation
- mappers 与 use cases
- list/detail screen UI
- ViewModels
- Activity 或 navigation host

## 推荐的数据流

```text
local mail/thread data
  -> task thread detector
  -> message text extraction
  -> state capsule parsing
  -> task repository
  -> list/detail use cases
  -> task list/detail ViewModels
  -> task mail UI
```

## Normal mail 与 task mail 的分离方式

推荐规则：

- Normal mail entry 继续使用现有的 message list 与 message view 流程
- Task Mail entry 只加载匹配 detector rules 的 threads
- Task parsing 只发生在 Task Mail flows 内
- Markdown rendering 只发生在 Task Mail detail 内
- 如果 task message 解析失败，fallback 是在 Task Mail detail 中按 plain text 展示
- 如果消息不是 task message，它就继续留在 normal mail path 上

## 推荐的 host 策略

推荐的 Phase 2 host：

- `TaskMailActivity`

原因：

- 它能与体量巨大的 legacy `MessageHomeActivity` 保持隔离
- 它能减少与 split view 及 normal mail back stack 行为的耦合
- 它符合这个仓库偏好的 isolated feature UI 模式
- 它能把 markdown 代码隔离在 normal mail stack 之外

v1 可行但不推荐的替代方案：

- 在 `MessageHomeActivity` 中嵌入 task fragment

当前不推荐的原因：

- 会更深地耦合到 legacy navigation 与 back stack 行为
- 对 normal mail experience 产生副作用的风险更高

## 后续阶段可能涉及的具体文件

## Phase 1 可能涉及的文件

- `settings.gradle.kts`
- `feature/taskmail/api/build.gradle.kts`
- `feature/taskmail/internal/build.gradle.kts`
- `feature/taskmail/api/src/main/kotlin/...`
- `feature/taskmail/internal/src/main/kotlin/...`
- `feature/taskmail/internal/src/test/kotlin/...`

## Phase 1.5 可能涉及的文件

- `feature/taskmail/internal/build.gradle.kts`
- `feature/taskmail/internal/src/main/kotlin/.../data/...`
- `feature/taskmail/internal/src/main/kotlin/.../domain/repository/...`
- `feature/taskmail/internal/src/main/kotlin/.../ui/workspace/...`
- `feature/taskmail/internal/src/main/kotlin/.../ui/detail/...`
- `feature/taskmail/internal/src/test/kotlin/.../data/...`
- `feature/taskmail/internal/src/test/kotlin/.../domain/...`
- `docs/taskmail/archive/android/planning/TASKMAIL-ANDROID-PHASE1-5.md`

## Phase 2 可能涉及的文件

- `settings.gradle.kts`
- `app-common/src/main/kotlin/net/thunderbird/app/common/feature/AppCommonFeatureModule.kt`
- `feature/navigation/drawer/dropdown/src/main/kotlin/net/thunderbird/feature/navigation/drawer/dropdown/DropDownDrawer.kt`
- `feature/navigation/drawer/dropdown/src/main/kotlin/net/thunderbird/feature/navigation/drawer/dropdown/ui/DrawerContract.kt`
- `feature/navigation/drawer/dropdown/src/main/kotlin/net/thunderbird/feature/navigation/drawer/dropdown/ui/DrawerViewModel.kt`
- `feature/navigation/drawer/dropdown/src/main/kotlin/net/thunderbird/feature/navigation/drawer/dropdown/ui/setting/FolderSettingList.kt`
- `feature/navigation/drawer/dropdown/src/main/res/values/strings.xml`
- `legacy/ui/legacy/src/main/java/com/fsck/k9/activity/MessageHomeActivity.kt`
- `feature/taskmail/api/src/main/kotlin/...`
- `feature/taskmail/internal/src/main/...`

## Phase 3 可能涉及的文件

- `feature/taskmail/internal/src/main/kotlin/.../ui/detail/...`
- `feature/taskmail/internal/src/main/kotlin/.../ui/list/...`

## Phase 4 可能涉及的文件

- `feature/taskmail/internal/build.gradle.kts`
- `feature/taskmail/internal/src/main/kotlin/.../render/TaskMarkdownRenderer.kt`
- `feature/taskmail/internal/src/test/kotlin/.../render/...`

## Phase 0 验收摘要

在以下事项确认后，Phase 0 可视为完成：

- 本地仓库适合新增一个独立的 `taskmail` feature 区域
- 最好的用户入口是 navigation drawer action area
- 最好的实现策略是一个独立的 Task Mail host screen
- normal mail list 与 normal mail detail 应保持不变
- parsing 与 markdown 逻辑应放在新 feature 中，而不是放进 legacy mail view code
- 后续阶段可能触及的文件，已经从真实仓库中识别出来

当前说明（2026-03-13）：

- 上述架构结论仍然成立。
- 实现基线已经推进到“Phase 1 skeleton complete，Phase 1.5 pending”。

## Phase 0 期间发现的本地限制（历史）

- 那个 Phase 0 工作区中的仓库只是源码快照，没有 `.git` 历史
- 这个 Codex 会话里的默认 shell 环境仍会把 `java` 解析到 Java 17，除非为命令显式设置 `JAVA_HOME`
- 本地已安装 JDK 21，路径为 `C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot`
- 当把这个 JDK 21 路径注入命令环境时，Gradle 可以正常工作
- `.\gradlew.bat help` 已在 JDK 21 下验证通过
- 更宽范围的 `.\gradlew.bat :app-thunderbird:assembleDebug --dry-run` 探测因为超时而未完成，因此 full build
  readiness 仍未完全确认

这些限制不会阻塞仓库调研工作。对于实现与测试，请在命令环境中显式使用 JDK 21，或者在更新系统环境变量后重启终端。
