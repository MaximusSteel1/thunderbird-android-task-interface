# TaskMail Android 代码引用显示归一化临时方案

更新时间：2026-03-30

## 背景

最近 direct Android 会话的 PC 侧文本实际存在多种代码引用形式：

- rich text markdown 文件链接，例如 `[README.md#L34](/E:/projects/.../README.md#L34)`
- plain text 里的 raw markdown 引用
- 裸绝对路径，例如 `E:\projects\...\index.js`
- summary / operator 文本里的命令和工作目录路径，例如 `node E:\projects\...\dist\index.js`

如果 Android 端原样显示这些长路径，手机阅读体验会明显变差。

## 目标

- 只改 Android 端显示层。
- 不改 VPS 输出，不改协议，不改缓存，不改 session 数据。
- 统一收口到现有 `TaskCodeLocatorText` 渲染入口，不为这件事单独再造页面级逻辑。

## V1 规则

### 生效范围

- `session detail` 主页的结果、最新进展、运行过程正文
- `history detail` 的 result / process records
- plain text、summary、code block

先不处理：

- 用户输入区
- `Repository / Workdir / Session ID` 等结构化 metadata
- rich text paragraph 已经解析出的 link 目标地址

### 识别对象

- markdown 文件引用：
  - `[README.md#L34](/E:/projects/.../README.md#L34)`
- 裸绝对路径：
  - `/E:/projects/.../README.md#L34`
  - `file:///E:/projects/.../index.js:151:22`
  - `E:\projects\...\dist\index.js`

### 显示规则

- markdown 文件引用：优先显示 label。
- 如果 label 本身仍是长绝对路径，则继续压成文件名或短尾巴。
- 裸绝对路径：显示最后一级名称。
- 如果原文带 `#L34` 或 `:151:22` 这类短定位尾巴，默认保留到折叠结果里。
- 只替换引用 token 本身，周围正文保持原样。

### 交互规则

- 点击折叠后的 token，弹出气泡显示完整原始引用。
- 气泡提供 `Copy full reference`。
- 再次点击同一个 token 关闭；点击别的 token 切换；点击空白关闭。

## 实现范围

- 继续复用：
  - `PlainTextBody`
  - `TaskRichTextBody` 的 `RichCodeBlock`
  - `ResultSummaryCard`
  - `TaskLatestProgressCard`
  - `TaskSessionHistoryContent`
- 继续复用现有 `TaskCodeLocatorText` 组件，但把内部识别从“仅 line locator”扩到“代码引用归一化”。

## 验证

最小验证应覆盖：

- parser：markdown 文件引用、裸绝对路径、带 `#L` / `:line:col`、无匹配原样保留
- component：显示短引用、点击弹气泡、复制完整原文
- detail / history：结果或进展正文里的长路径被压缩显示
