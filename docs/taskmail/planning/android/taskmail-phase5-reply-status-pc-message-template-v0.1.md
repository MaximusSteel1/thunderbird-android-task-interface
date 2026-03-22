# TaskMail Phase 5 `reply` / `/status` PC Message Template（v0.1）

更新时间：2026-03-22

## 状态

- 本文承接：
  - `docs/taskmail/planning/android/taskmail-phase5-reply-status-direct-contract-prerequisites-v0.1.md`
  - `docs/taskmail/planning/android/taskmail-phase5-reply-status-pc-coordination-checklist-v0.1.md`
- 本文只提供一份可直接发给相邻 PC 侧的短消息模板。
- 本文不替代 protocol freeze，也不要求本仓直接改 PC 代码。

## 使用方式

- 优先直接发送下面的“短版模板”
- 如果对方需要更多上下文，再附上 checklist / prerequisites 文档路径
- 文中“推荐”一词表示 Android 侧收敛建议，不表示替相邻 PC 侧单方面拍板

## 短版模板

```text
这边同步一下 Android 侧对 Phase 5 下一步的读法。

当前 Android 侧状态是：
- `new_task` 仍保持 `ready_for_direct_default_review`
- 当前不需要再单独新增 `new_task` activation/config
- `reply` / `/status` 仍继续走 mail path，还没有进入 direct 实现

如果下一步要推进 `reply` / `/status` 的 direct contract freeze，这边希望你们先在 `docs/current/*` 层回答并冻结下面几件事：

1. 第一批 scope
- Android 侧当前推荐先收窄到：
  - current-session plain reply
  - current-session `/status`

2. contract ownership
- Android 侧当前推荐：
  - 不扩写 `phase2-direct-outbound-contract-v1`
  - 另起一份 direct post-creation session-action contract

3. target boundary
- Android 侧当前推荐第一批先冻结为 current-session only
- 不先把 targeted-session variant / cross-workspace switching 拉进来

4. current-session identity / reply-anchor 等价物
- 请明确第一批 direct action 最小 target identity 是什么
- 请明确 direct `reply` 如何表达当前 mail reply 的 anchor 等价物
- 请明确 direct `/status` 第一批是否完全禁止 targeted-session variant

5. canonical semantics
- 请明确 direct `reply` accepted 后，后续结果是否仍按当前 canonical mail truth layer 产出
- 请明确 direct `/status` accepted 后，是否仍产出当前 canonical status mail
- 如果仍产出，请确认 subject / terminal semantics 是否与 mail path 完全一致

6. machine-readable classification
- 请至少明确 direct post-creation action 的：
  - `fallback_required`
  - `switch_blocker`
  - `hard_stop`

7. closeout artifacts
- 如果后续会进入 live closeout，请确认是否能保留：
  - `action_type`
  - `target_session_identity`
  - `request_id`
  - `ingress_message_id`
  - `terminal_mail_subject`
  - `last_summary`
  - same-run bind readout

Android 侧在这些问题冻结前不会做的事是：
- 不把 `TaskSessionDetailViewModel` 接到 direct seam
- 不猜 direct `reply` / `/status` packet schema
- 不把 quick answer / `Answers:` / `/resume` / attachment continuation 自动并进第一批

如果你们这边能把上面这些问题冻结到 current-protocol / closeout 读法层，Android 这边下一步就进入 direct `reply` / `/status` 的实现 planning。
```

## 极简版模板

如果只想发最短版本，可用下面这段：

```text
Android 侧这边当前建议：`new_task` 继续 guarded observation，`reply` / `/status` 暂不实现。

如果要推进 post-creation direct contract，请你们先在 `docs/current/*` 冻结：
1. 第一批 scope 是否只做 current-session plain reply + current-session `/status`
2. 是否另起 session-action contract，而不是扩 `phase2-direct-outbound-contract-v1`
3. 第一批是否只做 current-session only target
4. direct `reply` 的 anchor 等价物、direct `/status` 的 canonical outcome
5. `fallback_required` / `switch_blocker` / `hard_stop`
6. 后续 closeout artifact 是否能保留 `request_id` / `ingress_message_id` / `terminal_mail_subject` / `last_summary` / same-run bind readout

这些没冻结前，Android 不会接 direct detail send path。
```
