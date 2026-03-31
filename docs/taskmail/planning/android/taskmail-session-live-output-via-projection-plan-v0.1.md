# TaskMail Session 流式输出接入计划 v0.1

## 文档信息

- 日期：2026-03-30
- 状态：planning
- 目标读者：Android / relay / PC-control 实现者

## 1. 背景与结论

当前上游已经具备 `output_chunk` 的第一版正式基础设施：

- PC sidecar 会在任务运行中持续读取本地 `stream.events.jsonl`，按 `seq` 增量发 `output_chunk`
- turn 结束时只补尚未发送的尾部 chunk，不再整份输出重打一遍
- 断线后仍沿用 `replay + output_resume_request(after_seq)` 语义补洞
- relay runtime / command store 已能消费并持久化 `output_chunk`

这些能力说明：`output_chunk` 作为控制面正式对象已经成立。

但 Android 当前还没有直接消费 raw `/pc-control output_chunk`。当前 detail 主读 seam 仍是：

- `GET /v1/android/session-snapshot`
- `WS /v1/android/session-updates`

它们统一读取 relay-native projection store，并向 Android 暴露投影后的 session snapshot。

### 本计划的核心决策

长期主线采用：

- `output_chunk` 继续作为 lower-layer canonical streaming object
- relay projection store 负责把 raw chunk 投影成 Android-facing 的 session live output
- Android 继续只读 `session-snapshot / session-updates`

不建议 Android 直接消费 raw `/pc-control output_chunk`。

### 1.1 2026-03-30 实施默认决策

以下默认决策优先于本文后续旧口径：

- `live_output` 在 projection store 中按 `session_key` 只保留一行；`command_id / stream_id / last_seq` 仅作为 relay 内部 owner 元数据
- Android-facing `session_snapshot.live_output` 固定为 `object | null`，缺失时返回 `null`
- app-facing 首轮字段只包含：
  - `text`
  - `updated_at`
  - `status`
- 聚合文本必须基于 command store 重建：优先最新完整 `text`，再拼后续连续 `delta`；遇到 gap 就停在最后连续 `seq`
- closeout 走两阶段：terminal `result` 先冻结 `live_output=completed`，等稳定 round/result materialize 后再清空
- `session-updates` 继续沿用当前 snapshot payload 指纹去重推送，不要求 live output 更新时 bump session projection version
- `last_progress_at` 不回写 session row，由 snapshot builder 动态取 `max(session.last_progress_at, live_output.updated_at)`

## 2. 为什么采用 projection-first，而不是 Android 直连 raw chunk

### 2.1 projection-first 的优点

- Android 继续只依赖一个 authoritative read seam，不引入第二套 detail 实时协议
- 断线恢复继续由 relay 侧统一处理，Android 不需要自己维护 `stream_id + seq` cursor
- 多 consumer 可以复用同一套投影结果，而不是每个端各自理解 raw chunk
- 后端差异被隔离在上游，Android 不需要感知 `codex / opencode / future backend` 的 stream 细节
- `session-snapshot` 首帧即可带当前 live output，不需要 Android 先补历史 chunk 再拼文本

### 2.2 为什么第一版不建议把 raw `output_chunk` 直接映成 `timeline_items`

直接把 chunk 暴露成 timeline item 看起来省事，但不是当前最佳方案，原因有三类：

1. UI 语义不对
   - `output_chunk` 是一个持续增长中的 assistant block，不是天然的一串独立 timeline 卡片
   - 如果一条 chunk 一条 timeline item，detail 很容易被流式噪音刷屏

2. Android 当前 merge 语义不适合
   - Android 当前 `session_snapshot.timeline_items -> detail.timeline` 是“新增项合并”读法
   - 同一个 logical stream 的增量更新如果沿用同一个 `business_event_key`，现有 merge 会保留旧项、丢掉新文本
   - 如果每个 chunk 都用新 key，则 UI 又会碎成大量小条目

3. 产品面真正需要的是“当前可读的流式 assistant 输出”
   - 产品主面要的是当前正在生成的可读文本块
   - raw chunk 更适合留在 lower-layer replay / debug / audit，不适合作为 Android 首轮 public contract

因此，本计划建议：

- lower layer 继续保留 raw `output_chunk`
- Android-facing snapshot 首轮只暴露“已聚合的 live output block”

## 3. 目标态

### 3.1 用户可见行为

当 session 处于 `Queued / Running` 时：

- detail 顶部状态卡持续更新 `Last progress`
- `Latest progress` 卡显示当前 assistant 正在生成的最新可读文本
- 文本会随着新的 stream chunk 到达而增长
- 断线重连或切前后台后，首帧 `session_snapshot` 能恢复当前 live output，不需要从头重放
- turn 结束后，live output 从“运行中预览”自然切换到稳定结果/历史轮次，不出现整段重复

### 3.2 owner 关系

- raw streaming truth：`pc-control output_chunk + command_store`
- Android-facing live read truth：relay projection store
- Android detail consumer seam：`session-snapshot / session-updates`

## 4. 推荐协议与投影形态

### 4.1 Android-facing 新增字段

建议在 `session_snapshot` 下新增可选对象：

```json
{
  "live_output": {
    "text": "当前已聚合的可见 assistant 文本",
    "updated_at": "2026-03-30T20:10:02Z",
    "status": "streaming"
  }
}
```

字段建议：

- `text`
- `updated_at`
- `status`
  - 首轮可约束为 `streaming | completed`

### 4.2 为什么首轮推荐 `live_output`，而不是 `output_chunks[]`

- Android detail 需要的是一个“当前文本块”，不是 chunk 列表
- `session_snapshot` 首帧恢复时，直接给聚合文本比 replay chunk 成本更低
- 可以把 chunk replay/resume 继续封装在 relay lower layer，不把复杂度泄漏到 Android

### 4.3 与现有字段关系

- `last_progress_at`：
  - 继续保留
  - 当 live chunk 更新更晚时，用 live output 的最新时间推进它
  - 这个推进只在 snapshot build 时动态合成，不回写 session row
- `timeline_items`：
  - 首轮不直接承载 raw streaming transcript
  - 仍只承载状态/问题/终态等稳定 timeline 语义
- `history_rounds`：
  - 终态后继续承接稳定 round/result
  - 首轮不要求把 live chunk 明细塞进 `history_rounds.process.items`

## 5. Relay / PC / Android 的实现切口

## 5.1 PC sidecar

本轮原则：

- 不重做现有 `output_chunk` 发送语义
- 不改 `output_resume_request(after_seq)` contract

PC 侧只需保持当前能力：

- active run 增量发送
- finish flush 只补尾巴
- reconnect selective replay

因此 PC 侧本轮不是主改动面。

## 5.2 Relay runtime / projection store

这是本轮主 owner。

### 5.2.1 当前已具备

- runtime 已能处理 `output_chunk`
- command store 已能按 `stream_id + seq` 持久化 chunk
- Android `session-snapshot / session-updates` 已经统一读 projection store

### 5.2.2 当前缺口

当前缺的不是 chunk 接收，而是：

- `output_chunk -> Android projection store` 的 owner write path
- `live output` 进入 `session_snapshot` payload 并触发同构 push
- `result / round closeout` 后的 live output 清理与去重

### 5.2.3 推荐新增 durable projection

建议在 relay projection store 增加一张 session-scoped live output projection 表，例如：

- `projection_session_live_output`

建议字段：

- `session_key`
- `workspace_id`
- `session_id`
- `command_id`
- `stream_id`
- `last_seq`
- `text`
- `status`
- `updated_at`

注意：

- 这里保存的是“聚合后的 Android-facing 当前文本块”
- 不复制 raw chunk 全量历史
- raw chunk 仍以 command store 为 truth/replay owner
- 对 Android-facing contract 而言，owner key 是 `session_key`；`command_id / stream_id / last_seq` 只作为内部元数据

### 5.2.4 runtime 写入算法

当 relay runtime 收到新的 `output_chunk`：

1. 继续先写 command store
2. 根据 command 归属解析出 owning session
3. 从 command store 中该 `command_id + stream_id` 的已落块重建当前可见文本
4. 只有当聚合文本或 `last_seq` 实际前进时，才写 projection store
5. 让 `session-updates` 沿现有 snapshot payload 指纹机制推送新 snapshot

聚合规则建议：

- 输入：同一 `stream_id` 的 `output_chunk`
- owner key：`session_key`
- 文本规则：
  - 优先取最新完整 `text`
  - 再拼其后连续 `delta`
  - 遇到 gap 就停在最后连续 `seq`
- 进度规则：
  - `last_seq = max(last_seq, chunk.seq)`
  - `updated_at = chunk.sent_at / chunk.created_at` 的归一化时间

### 5.2.5 closeout 规则

当同一 command/session 收到 terminal `result` 或稳定 `history_round` 已 materialize：

- 先冻结 live output，状态改为 `completed`
- 若稳定 result 已覆盖这段文本，则清空该 session 的 `live_output`

目标是避免 detail 同时出现：

- 一份 live output
- 一份完全相同的 terminal result

### 5.2.6 为什么不让 `session-updates` 直接旁读 command store

不建议让 Android snapshot builder 同时依赖：

- projection store
- command store

原因：

- 这会让 Android-facing read truth 再次分叉
- `session-updates` 将不再是纯 store-driven push
- 后续 observer / subscription 调试成本会上升

因此应坚持：

- Android-facing owner 只有 projection store

## 5.3 Android app

Android 改动应尽量窄，保持现有 detail 主线不变。

### 5.3.1 domain / facade model

需要新增：

- `TaskSessionHistorySnapshotHeader.liveOutput: TaskSessionLiveOutput?`
- `TaskSessionDetail.liveOutput: TaskSessionLiveOutput?`

对应 façade parser：

- `OkHttpTaskSessionUpdatesFacadeRepository`
- `OkHttpTaskSessionHistorySnapshotFacadeRepository`

都要能解析 `session_snapshot.live_output`

### 5.3.2 detail 映射

在 `TaskSessionHistorySnapshotDetailMapper`：

- 把 `live_output` 映到 `TaskSessionDetail`
- 不把它硬塞进 timeline
- UI state 可再映成 `liveOutputText`

### 5.3.3 UI 承接

在 session 首页：

- 运行态 `Latest progress` 优先显示 `live_output.text`
- 若 `live_output` 为空，再 fallback 到当前 `recentContext.latestAssistantMessage`
- `StatusCard` 的 supporting text 也优先显示 `live_output.text`
- `StatusCard` 的 `Last progress` 继续读上游已合成的 `last_progress_at`

首轮不要求：

- 把 live output 放进 `Run records`
- 把 live output 保存为独立历史卡

### 5.3.4 为什么 Android 首轮不直接显示 chunk cursor

`command_id / stream_id / last_seq` 对 Android 主要是调试价值，不是产品主信息。

因此：

- relay lower layer 可保留
- UI 首轮默认不显示

## 6. 分阶段实施建议

## 阶段 A：最小闭环

目标：

- 让运行中 assistant 文本能通过 projection store -> session-updates -> Android detail 实时显示

范围：

### relay / PC repo

1. projection store 新增 session live output row
2. runtime 在 `handle_output_chunk()` 后增量更新 live output projection
3. `session_snapshot` / `session-updates` 返回 `live_output`
4. result closeout 时清理 live output

### Android repo

1. 解析 `live_output`
2. 映射到 detail domain/ui state，并更新 `TaskSessionDetailJsonCodec`
3. `Latest progress` 卡优先显示 `live_output.text`
4. 状态卡 supporting text 也优先显示 `live_output.text`

验收：

- detail 保持打开时，运行中 assistant 文本可持续增长
- 切后台/前台后，首帧 snapshot 可恢复当前 live output
- turn 结束后不会整段重复

## 阶段 B：history / round closeout 收口

目标：

- 让 live output 与稳定 round/result 的交接更自然

范围：

1. history round closeout 时验证重复抑制
2. 必要时把 live output 的最终可见文本合流进 `history_rounds.process.items` 或稳定 result builder
3. 补更系统的 duplicate / overwrite / reconnect validation

## 阶段 C：更丰富的产品表达

只有在前两阶段稳定后再考虑：

- 把 live output 作为更丰富的运行态 transcript 展示
- 增加“streaming / completed” 更显式的 UI 状态
- 为调试模式显示内部 `stream_id / last_seq`

## 7. 推荐先后顺序

建议实施顺序：

1. 先冻结 Android-facing `live_output` contract
2. relay projection store 落 owner row
3. `session_snapshot / session-updates` builder 接入 `live_output`
4. Android parser/domain/ui 接入 `live_output`
5. 再做 closeout / duplicate / reconnect 完整验证

不要反过来先改 Android UI，再去猜 relay 最终字段。

## 8. 风险与注意事项

### 8.1 最容易踩坑的点

- 把 raw chunk 当 timeline item 直接上屏，导致 UI 噪音过大
- 让 Android 同时依赖 projection store 和 command store 两套 truth
- 终态 closeout 时 live output 与 stable result 重复
- 使用 append-only merge，导致同一 logical stream 的文本不会更新

### 8.2 明确不做的事

本轮不做：

- Android 直连 raw `/pc-control output_chunk`
- Android 自己维护 `after_seq` / replay cursor
- 把 raw `stream.events.jsonl` 暴露给 Android
- 把 raw chunk 全量镜像进 projection store

## 9. 验证建议

### relay / PC

- unit:
  - `output_chunk -> live_output projection` 聚合
  - `result closeout -> clear live_output`
  - duplicate chunk / out-of-order chunk 行为
- integration:
  - `command_dispatch -> output_chunk(seq1..n) -> session-updates push`
  - reconnect 后 snapshot 恢复当前 live output

### Android

- parser:
  - `session_snapshot.live_output` 解析
- mapper:
  - `live_output` 优先覆盖运行态 latest progress
- screen:
  - ActiveRun 下显示 live output 文本
  - `live_output` 缺失时 fallback 到旧 recentContext

## 10. 一句话实施建议

这条主线现在可以正式开做，但建议的“正式接入”不是把 raw `output_chunk` 直接塞给 Android，而是：

- 让 relay projection store 成为 live output 的 Android-facing owner
- 在 `session_snapshot / session-updates` 增加聚合后的 `live_output`
- Android 继续只读投影

这样最贴近长期最佳方案，也最符合当前仓库已经收敛出的 `projection-first` 主线。
