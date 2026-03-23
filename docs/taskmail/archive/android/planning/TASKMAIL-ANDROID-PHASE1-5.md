# TaskMail Android Phase 1.5 开工清单

## 1. 目标

Phase 1.5 的目标不是新增正式入口，而是把当前已经完成的 TaskMail Phase 1 骨架推进到：

- **真实邮件可读**
- **现有 `workspace -> session -> detail` UI 可消费真实数据**
- **仍然保持 feature 内部调试入口为主，不接正式宿主**

一句话说，Phase 1.5 要完成的是：

> 用真实本地邮件数据替换 `InMemoryTaskMailRepository()`，但暂时不做抽屉入口、TaskMailActivity 正式接线、
> Markdown 渲染和回复发送。

---

## 2. 当前基线

截至 2026-03-13，仓库里的当前基线是：

- `feature:taskmail:api` 与 `feature:taskmail:internal` 已落地
- parser、domain model、fake repository、workspace/detail UI、debug preview、debug activity 已完成
- `TaskWorkspaceViewModel` 与 `TaskSessionDetailViewModel` 仍默认依赖 `InMemoryTaskMailRepository()`
- `app-thunderbird` 与 `app-k9mail` 目前只在 `debugImplementation` 中依赖 `feature:taskmail:internal`

这意味着当前状态是：

- **UI 骨架完成**
- **协议解析完成**
- **真实邮件桥接未完成**
- **正式用户入口未完成**

---

## 3. 范围

### 3.1 本阶段要做

- 真实邮件线程读取
- TaskMail 线程识别
- 正文提取与状态块剥离
- 真实 `TaskMailRepository` 落地
- 用真实 repository 驱动现有 Workspace / Detail ViewModel
- 单元测试、必要的 UI 层回归测试
- 继续通过 debug 专用入口做设备/模拟器冒烟验证

### 3.2 本阶段不做

- `app-common` 正式接线
- 抽屉 `Tasks` 入口
- `MessageHomeActivity` 正式跳转 TaskMail
- `TaskMailActivity` 正式宿主
- Markdown 只读渲染
- 回复发送
- slash command 执行入口
- 搜索、筛选、批量操作

---

## 4. 架构边界

### 4.1 模块边界

- 新逻辑继续放在 `feature:taskmail:internal`
- `api` 模块仍然保持很薄，只承载稳定导航契约
- 不把新逻辑塞进 `legacy:*`
- 允许在 `internal` 的 data adapter 层读取 legacy mailstore / message API，但不要把 legacy 类型泄漏到
  TaskMail UI 层

### 4.2 数据边界

以 `docs/TASKMAIL-MAIL-RULES.md` 为准，重点遵守：

- `workspace` 主键优先用 `workspace_id`
- `session` 主键优先用 `session_id`
- 缺失时分别退化到 `repo_path + workdir` 与 `thread_id`
- `backend_session_id` 绝不作为 UI 主键
- 当前排序优先用邮件时间，暂不自发明 `updated_at`

### 4.3 UI 边界

- 现有 Compose 结构不推翻，优先替换数据来源
- 不把 `MessageViewInfo` 当成 TaskMail 正文输入
- 正文提取优先复用现有 plain text / html fallback 能力

### 4.4 隐私与日志

- 使用注入的 `Logger`
- 不记录邮箱地址、正文原文、token、账号凭证
- 日志只记录聚合过程和计数级信息，不记录 PII

---

## 5. 推荐实现路径

推荐的数据链路如下：

```text
LegacyAccountDtoManager
  -> 枚举账户
  -> MessageListRepository.getThreadedMessages()
  -> 识别候选 TaskMail 线程
  -> MessageListRepository.getThread()
  -> 按 account/folder/uid 加载 LocalMessage
  -> BodyTextExtractor / MessageExtractor 提取正文
  -> TaskMailSubjectParser / TaskStateCapsuleParser / TaskQuestionCapsuleParser
  -> user/system 正文展示提取
  -> DefaultTaskMailRepository
  -> GetTaskWorkspaceSummaries / GetTaskSessionDetail
  -> 现有 ViewModel / Screen
```

推荐原则：

- **先枚举线程，再补正文**
- **先做纯文本可读，再考虑 Markdown**
- **先替换 repository，再接正式入口**

---

## 6. 详细开工清单

### 6.1 Step 0：冻结协议输入

- [ ] 重新核对 `docs/TASKMAIL-MAIL-RULES.md`
- [ ] 确认当前 Android 侧只消费：
  - `workspace_id`
  - `session_id`
  - `session_name`
  - `thread_id`
  - `task_id`
  - `backend`
  - `repo_path`
  - `workdir`
  - `status`
  - `last_summary`
- [ ] 确认 reply 路由优先级只进入后续阶段设计，不在 Phase 1.5 中实现发送动作
- [ ] 如果后端协议与当前文档不一致，先更新 `docs/TASKMAIL-MAIL-RULES.md`，再写代码

完成标准：

- 当前阶段的输入协议已经明确，不需要在实现中临时发明字段或分组规则

### 6.2 Step 1：补齐 data adapter 骨架

- [ ] 在 `feature/taskmail/internal/build.gradle.kts` 加入真实邮件桥接所需的最小依赖
- [ ] 新建 `LegacyTaskMailMessageSource`
- [ ] 新建 `LegacyTaskMailBodyExtractor`
- [ ] 新建 `DefaultTaskMailRepository`
- [ ] 如实现复杂，增加仅在 `internal` 使用的 raw model / mapper helper

建议主文件：

- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/data/LegacyTaskMailMessageSource.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/data/LegacyTaskMailBodyExtractor.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/data/DefaultTaskMailRepository.kt`

实施要点：

- `MessageSource` 负责从本地仓库拿到“候选消息/线程”
- `BodyExtractor` 负责拿到适合 TaskMail 解析的纯文本正文
- `Repository` 负责把 raw mail world 映射成 TaskMail domain model

完成标准：

- data 层已经和 fake repository 分离，真实数据桥接代码有清晰落点

### 6.3 Step 2：实现线程枚举与候选识别

- [ ] 通过 `LegacyAccountDtoManager` 枚举账户
- [ ] 通过 `MessageListRepository.getThreadedMessages()` 或等价能力获取线程根消息
- [ ] 构建候选 TaskMail 线程识别规则
- [ ] 对每个候选线程保留 `accountUuid / folderId / uid / threadRoot`

推荐识别策略：

1. 快速命中：
   - 主题以 `[OC]` / `[CX]` 开头
   - 主题含状态标签与 `[S:session_id]`
2. 慢速确认：
   - 线程内任一正文包含 `---TASK-STATE-BEGIN---`
3. 负向过滤：
   - 普通邮件不进入 TaskMail 聚合

注意事项：

- 不要只靠“相同主题”串线程
- 不要因为 preview 中缺少状态块就提前排除整条线程
- 非 TaskMail 邮件应该被安全忽略，而不是报错

完成标准：

- 可以稳定得到 TaskMail 候选线程集合

### 6.4 Step 3：实现正文提取与 capsule 剥离

- [ ] 使用本地 message API 按 `account + folder + uid` 读取消息
- [ ] 优先提取 `text/plain`
- [ ] `text/plain` 缺失时回退到 `text/html -> text`
- [ ] 对正文解析最后一个完整 `TASK-STATE` 块
- [ ] 对正文解析最后一个完整 `TASK-QUESTION` 块
- [ ] 从展示正文中剥离状态块和问题块
- [ ] 对 user reply 提取“本轮新增 delta”
- [ ] 对 system message 提取适合 timeline 展示的摘要正文

建议优先复用：

- `com.fsck.k9.message.extractors.BodyTextExtractor`
- 或更底层的 `MessageExtractor`

注意事项：

- 不要依赖 `MessageViewInfo`
- 正文为空时要能优雅回退为空字符串
- html-only 邮件不应导致崩溃
- user / system message 的展示正文规则应分开处理

建议对齐后端当前行为：

- user message：
  - 先去掉 `TASK-STATE` / `TASK-QUESTION`
  - 再按 `On ... wrote:`、`回复:`、`答复:`、`Original Message`、`>` 引用等规则裁掉 quoted 内容
  - 若裁剪失败，再回退到规范化后的完整正文
- system message：
  - 先去掉 `TASK-STATE` / `TASK-QUESTION`
  - 优先取 `Reply:` 段
  - 其次取 `Summary:`
  - 其次取 `Status:` 之前的说明文本
  - 再回退到 `Question/Choices` 或 `Status`

完成标准：

- 每条 TaskMail 消息都能得到：
  - 结构化状态块
  - 结构化问题块
  - 干净的可展示正文
  - 对 user / system 都可读的 timeline 文本

### 6.5 Step 4：实现 domain 聚合映射

- [ ] 从线程消息映射出 `TaskWorkspaceSummary`
- [ ] 从线程消息映射出 `TaskSessionDetail`
- [ ] 统一实现 `workspace -> session` 分组规则
- [ ] 统一实现 session 当前状态推导规则
- [ ] 统一实现 `pendingQuestion` 推导规则
- [ ] 统一实现 timeline 排序规则

推荐规则：

- 一级分组优先 `workspace_id`，否则 `repo_path + workdir`
- 二级分组优先 `session_id`，否则 `thread_id`
- `lastUpdatedAt` 先用邮件时间
- session 当前状态优先取最新状态邮件 / 最新 capsule
- `pendingQuestion` 以最新 question capsule 是否存在为准

完成标准：

- fake repository 的输出形态可以被真实 repository 一比一替换

### 6.6 Step 5：替换 ViewModel 的 fake 默认依赖

- [ ] 移除生产路径里对 `InMemoryTaskMailRepository()` 的默认依赖
- [ ] 让 `TaskWorkspaceViewModel` 使用真实 repository
- [ ] 让 `TaskSessionDetailViewModel` 使用真实 repository
- [ ] 保留 preview / 单元测试中的 fake 数据能力

建议实施方式：

- 优先改为构造函数注入
- 如需要真正落地到运行时注入，新增 `TaskMailModule`
- debug activity / debug host 可以先只接 feature 内部 wiring

建议文件：

- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/di/TaskMailModule.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/workspace/TaskWorkspaceViewModel.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/TaskSessionDetailViewModel.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/workspace/TaskWorkspaceScreen.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/TaskSessionDetailScreen.kt`

完成标准：

- Workspace / Detail 页面在 debug 入口中已经不再依赖 fake repository

### 6.7 Step 6：测试补齐

- [ ] 为真实 `DefaultTaskMailRepository` 补单元测试
- [ ] 为线程识别补测试
- [ ] 为正文提取与 capsule 剥离补测试
- [ ] 为 reply delta 提取补测试
- [ ] 为 system message 摘要提取补测试
- [ ] 为 `workspace -> session` 聚合补测试
- [ ] 为缺字段兜底补测试
- [ ] 为 question / awaiting_user_input 场景补测试
- [ ] 复核现有 ViewModel / Screen 测试是否需要调整

建议测试文件：

- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/data/DefaultTaskMailRepositoryTest.kt`
- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/data/LegacyTaskMailMessageSourceTest.kt`
- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/data/LegacyTaskMailBodyExtractorTest.kt`

测试重点：

- 使用 fakes，不优先引入 mocks
- 按 AAA 写法组织
- `testSubject` 作为被测对象命名

完成标准：

- 真实数据桥接的关键规则都能被自动化验证

### 6.8 Step 7：debug 冒烟验证

- [ ] 继续使用 `TaskMailDebugActivity`
- [ ] 验证无 TaskMail 邮件时能正常显示 empty state
- [ ] 验证单 workspace / 单 session
- [ ] 验证单 workspace / 多 session
- [ ] 验证多 workspace
- [ ] 验证 `[QUESTION]` 场景
- [ ] 验证 html-only 正文回退
- [ ] 验证无法解析 capsule 的回退展示

完成标准：

- debug 入口下可以稳定浏览真实 TaskMail 邮件

---

## 7. 推荐 PR 切分

为保持变更可 review，建议拆成 3 个 PR：

### PR 1：真实消息读取

- `build.gradle`
- `LegacyTaskMailMessageSource`
- `LegacyTaskMailBodyExtractor`
- 对应单元测试

### PR 2：真实 repository 聚合

- `DefaultTaskMailRepository`
- domain 聚合 mapper
- use case 接真实 repository
- 对应单元测试

### PR 3：UI 接线与验证

- ViewModel / Screen 从 fake 默认依赖切到真实依赖
- debug host 调整
- 文档收尾
- 冒烟验证

---

## 8. Definition Of Done

Phase 1.5 完成时应满足：

- `TaskWorkspaceScreen` 显示真实 `workspace -> session` 数据
- `TaskSessionDetailScreen` 显示真实 timeline、summary、question、plain text 正文
- 生产路径不再默认依赖 `InMemoryTaskMailRepository()`
- debug 入口可稳定打开真实数据
- 普通邮件功能路径没有被改坏
- 文档已同步更新到当前状态

---

## 9. 建议验证命令

先跑最窄相关任务：

- [ ] `./gradlew :feature:taskmail:internal:compileDebugKotlin`
- [ ] `./gradlew :feature:taskmail:internal:testDebugUnitTest`

在变更接近收尾时补：

- [ ] `./gradlew :feature:taskmail:internal:lint`
- [ ] `./gradlew :feature:taskmail:internal:detekt`
- [ ] `./gradlew spotlessCheck`
- [ ] `./gradlew :app-thunderbird:assembleDebug`
- [ ] `./gradlew :app-k9mail:assembleDebug`

如果环境允许，再补：

- [ ] `./gradlew connectedAndroidTest`

---

## 10. 当前阶段最重要的提醒

- 这一步的目标是**真实数据桥接**，不是正式入口上线
- 不要为了快把 TaskMail UI 塞进 `MessageHomeActivity`
- 不要把 `backend_session_id` 当主键
- 不要把状态块直接当正文渲染
- 不要在没有协议依据时额外发明字段
