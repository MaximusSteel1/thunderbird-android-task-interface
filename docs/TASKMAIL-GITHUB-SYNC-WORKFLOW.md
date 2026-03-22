# TaskMail GitHub 同步工作流

本文记录这份 fork 在多台电脑之间继续推进 TaskMail 开发时的当前协作流程。

截至 2026-03-21：

- 当前活跃开发分支：`taskmail-dev`
- 当前远端：`origin = https://github.com/MaximusSteel1/thunderbird-android-task-interface`
- 日常 TaskMail 开发不要直接使用 `origin/main`

常规 `pull / push` 迭代应基于 `taskmail-dev`，除非你是有意从它切出一个短期实验分支。

## 新电脑首次设置

先 clone 仓库，再切到当前活跃的 TaskMail 分支：

```powershell
git clone https://github.com/MaximusSteel1/thunderbird-android-task-interface
cd thunderbird-android-task-interface
git checkout taskmail-dev
git pull --rebase origin taskmail-dev
git status --short --branch
```

期望结果：

- 当前分支是 `taskmail-dev`
- 上游跟踪分支是 `origin/taskmail-dev`
- 工作树是干净的

如果新机器还要参与构建，请在运行 Gradle 前设置 Java 21：

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot"
```

## 每日开始前检查

开始写代码前，先确认自己仍在共享分支上，并且已经同步到最新：

```powershell
git status --short --branch
git pull --rebase origin taskmail-dev
```

如果 `git status` 在 pull 前就显示工作树不干净，先停下来，先提交本地工作，或者明确地自己决定 stash。

## 标准双机切换

### 在电脑 A 停下时

如果要把工作切到另一台电脑，不要把重要改动只留在 working tree 里。

```powershell
git status
git add -A
git commit -m "chore(taskmail): checkpoint current workspace state"
git push origin taskmail-dev
```

说明：

- 如果这次 checkpoint 已经是一个真实的逻辑步骤，请把提交信息换成更具体的 `feat:` 或 `fix:`
- `chore(taskmail): checkpoint ...` 只适合“需要一个安全同步点，但工作仍在进行中”的场景
- 不要把 `git stash` 当作主要的跨电脑交接方式

### 在电脑 B 恢复时

```powershell
git checkout taskmail-dev
git pull --rebase origin taskmail-dev
git status --short --branch
```

之后再继续正常开发。

## 每次会话结束时

结束当前会话时，按下面的 checklist 收口：

1. 运行 `git status`
2. 提交你希望保留的工作
3. push 到 `taskmail-dev`
4. 确认 GitHub 上已经能看到最新提交

推荐命令：

```powershell
git status
git add -A
git commit -m "feat(taskmail): <short summary>"
git push origin taskmail-dev
```

## 需要实验分支时

如果你想做一个高风险尝试，但又不想打扰共享分支：

```powershell
git checkout taskmail-dev
git pull --rebase origin taskmail-dev
git checkout -b taskmail-dev-experiment
git push -u origin taskmail-dev-experiment
```

实验分支应尽量短生命周期。实验完成后，把有效改动 merge 或 cherry-pick 回 `taskmail-dev`，再继续把
`taskmail-dev` 作为多机同步主线。

## 处理 pull 时的冲突

如果 `git pull --rebase origin taskmail-dev` 报冲突：

1. 打开冲突文件
2. 解决冲突标记
3. stage 已解决的文件
4. 继续 rebase

命令如下：

```powershell
git status
git add <resolved-files>
git rebase --continue
```

如果这次 rebase 已经混乱到不适合继续：

```powershell
git rebase --abort
```

然后先检查分支状态，再决定下一步。

## 必须保持本地的文件

仓库已经配置为让本地开发产物不进入 Git。不要强行 add 这些路径。

当前忽略的本地专属路径包括：

- `.android-user/`
- `.gradle-user/`
- `.tmp/`
- `_mailin_*`

这些目录通常是机器相关缓存、调试数据或验证产物，不应参与多机同步。

## 建议记住的安全命令

日常大多数工作只需要这些命令：

```powershell
git checkout taskmail-dev
git pull --rebase origin taskmail-dev
git status --short --branch
git add -A
git commit -m "feat(taskmail): <summary>"
git push origin taskmail-dev
```

## 正常同步时尽量避免的命令

不要在这条 TaskMail 开发线上随手使用下面这些命令：

- `git push origin master:main`
- `git push --force`
- 在没有先备份本地工作的前提下使用 `git reset --hard`
- 直接把 `origin/main` 当日常工作分支

## 可选改进

如果你希望未来新机器 clone 之后更自然地落到正确分支，可以在 GitHub 仓库设置里把这个 fork 的默认分支从
`main` 改成 `taskmail-dev`。这不是硬性要求，但可以减少误操作。
