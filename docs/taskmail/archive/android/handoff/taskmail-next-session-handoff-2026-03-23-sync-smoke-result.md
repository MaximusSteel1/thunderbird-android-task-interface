# TaskMail Next Session Handoff - 2026-03-23 - [SYNC] Smoke Result

## 当前结论

- formal-host `Project list` 页面可以正常读取并渲染最新 `[SYNC] Project Folder List` 结果。
- `Use this repo` 回填链路正常，会把选中的 repo path 带回 `New task` 的 `Repo` 字段。
- `[SYNC]` 结果仍然停留在 bootstrap discovery 通道，没有被错误投进 TaskMail workspace/session/detail 投影。

## 本次手工烟测路径

1. 从 `Tasks` 工作区进入 `Project list`
2. 点击 `Sync project list`
3. 留在 `Project list` 页面观察结果
4. 点击 `D:\projects\fahad_diana` 下方的 `Use this repo`
5. 验证跳回 `New task` 且 `Repo` 已回填
6. 返回 `Tasks` 工作区，确认没有新增 `[SYNC]` / `Project Folder List` 任务卡片

## 现场结果

### 1. `Project list` 页面

- 页面不是空态，也不是错误态。
- 当前页面展示：
  - `Latest project list`
  - `Scanned at 2026-03-23T09:18:36`
  - 根目录 `D:\projects`
    - `fahad_diana`
    - `filesystem`
  - 根目录 `E:\projects`
    - 多个可选项目
- 可见多个 `Use this repo` 按钮。

### 2. `Use this repo -> New task`

- 点击 `D:\projects\fahad_diana` 下方 `Use this repo` 后，成功跳转到 `New task`。
- `New task` 页面中的 `Repo*` 字段实际回填值为：
  - `D:\projects\fahad_diana`
- 用户肉眼回报时写成了 `D:\projects\Fahad_diana`，但 UI dump 中的实际字段值为小写目录名 `D:\projects\fahad_diana`，与 `Project list` 来源一致。

### 3. 不进入 TaskMail 会话投影

- 用户回到 `Tasks` 工作区后确认：
  - 没有新增 `[SYNC]` 卡片
  - 没有新增 `Project Folder List` 卡片
- 本地 TaskMail 投影缓存快照里也没有搜到 `[SYNC]` / `Project Folder List` 记录：
  - `taskmail_unified_messages_current.utf8.json`
  - `taskmail_session_details_current.utf8.json`

## 这轮 smoke 能说明什么

- `[SYNC]` 当前状态可以定性为：
  - 功能存在
  - formal-host UI 可用
  - repo 回填链路可用
  - “不进入 TaskMail session projection” 这一协议边界仍成立
- 这轮 smoke 没有证明“刚刚点击 `Sync project list` 后一定生成了一封全新的 `[SYNC]` 往返邮件”，因为页面上展示的是一份已存在的最新 project-list 结果，当前保留证据更偏向：
  - 读取/渲染正确
  - 回填正确
  - 投影边界正确

## 本次保留的 artifact

- `_tmp_device/taskmail_project_sync_after_tap.xml`
- `_tmp_device/taskmail_new_task_after_use_repo.xml`
- `_tmp_device/taskmail_unified_messages_current.utf8.json`
- `_tmp_device/taskmail_session_details_current.utf8.json`

## 下一步

1. 如果要把 `[SYNC]` 彻底关单，下一轮应额外补 mailbox-side 证据：
   - 点击 `Sync project list` 后，确认新 `[SYNC]` 请求邮件真的发出
   - 确认新的 `[SYNC] Project Folder List` 回信真的到达
   - 对上该回信与页面上 `Latest project list` 的 `scannedAt` / roots / projects
2. 但就 Android formal-host 功能烟测而言，这一轮已经正向证明：
   - `Project list` 可读
   - `Use this repo` 可回填
   - `[SYNC]` 不污染 TaskMail 会话列表
