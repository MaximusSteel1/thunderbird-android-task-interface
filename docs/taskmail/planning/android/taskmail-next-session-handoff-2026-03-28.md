# TaskMail Next Session Handoff 2026-03-28

## 当前判断

- `new_task -> POST /v1/android/create-session` 的真机提交已确认会真实到达 VPS facade，并成功在 `pc-home` 上创建 `new_task` command。
- 当前 live blocker 不是 Android 表单、也不是 PC 离线，而是 Android-facing facade 的同步等待窗口过短。
- `mail_runner/relay_server/android_create_session_facade.py` 当前 `_ACK_WAIT_SECONDS = 5.0`，而 live 环境里 command 常常要等到下一轮 `pc-control` heartbeat 才会被派发并回 `accepted`。
- 结果是：Android 先报 `timed out waiting for submit ack from the target pc`，但同一条 command 在几秒后又会被 `accepted`，甚至正常 `done`。
- 因为 Android 没拿到 `submit_ack + session_binding`，本轮 `provisional session seed -> detail` 没有成立；后续 workspace/detail 也没有把这条 session 补进本地承接层。

## 本次真机验证结果

### 设备与配置

- 设备：`24090RA29C`
- 包名：`net.thunderbird.android.debug`
- relay debug 已配置可用 `android_app_token`
- app 已补回邮箱账号，可真实发送 `new_task`

### 第一次真实提交

- Android 页面选择：
  - `pc-home`
  - workspace `workspace_cb2404bf828c`
  - repo `E:\projects\android_task_manager`
- Android UI 结果：
  - `TaskMail send failed`
  - `timed out waiting for submit ack from the target pc`
  - `command_id=cmd:android-create-session:20260327_234929:128322db`
- PC 侧后验读取：
  - `created_at = 2026-03-27T23:49:29`
  - `acked_at = 2026-03-27T23:49:43`
  - `ack_status = accepted`
  - 同条 command 已进入 `running`
- 结论：
  - facade 超时发生时，这条 command 其实还在等派发；
  - 约 `14s` 后它才拿到 `accepted`，明显超出当前 `5s` 等待窗口。

### 第二次真实提交

- 因用户在页面上改了输入，本次实际提交内容变成：
  - `task_text = say hi`
  - `title = say hi`
- 本次 route target 实际落成：
  - workspace `workspace_63d4d3909a8d`
  - repo `D:\projects\android_task_manager`
- Android UI 结果：
  - 再次报 `timed out waiting for submit ack from the target pc`
  - `command_id=cmd:android-create-session:20260328_000047:9906647c`
- 命令刚超时时，PC 侧读取结果：
  - `status = queued`
  - `ack_status = null`
- 约 20 秒后复查同一条 command：
  - `created_at = 2026-03-28T00:00:47`
  - `acked_at = 2026-03-28T00:00:58`
  - `ack_status = accepted`
  - `final_status = done`
  - `summary = hi`
- 结论：
  - 第二次也复现同一问题；
  - 这次 `accepted` 约在创建后 `11s` 才返回，依然晚于 facade 的 `5s` 窗口。

## Android 承接层观察

- 两次超时后，Android app-private `taskmail_session_details.json` 都没有写入这次 session。
- 已直接检查：
  - `thread_20260328_000047_feea64` 不在 `taskmail_session_details.json`
  - `workspace_63d4d3909a8d` 不在 `taskmail_session_details.json`
  - `D:\projects\android_task_manager` 不在 `taskmail_session_details.json`
- 直接打开：
  - `app://taskmail/session/thread_20260328_000047_feea64?workspaceId=workspace_63d4d3909a8d`
- Android 页面结果：
  - `Unable to load session`
  - `Task session detail was not found.`
- 这说明当前并不存在“虽然 create-session timeout，但 detail/projection 还能自行把 session 拉起来”的补救链路。

## 已确认的根因读法

1. Android facade 发送请求时，relay server 只等待 `5s`。
2. live `pc-control` 派发默认依赖 heartbeat/pending-dispatch 周期。
3. 当前 `pc-home` heartbeat 周期是 `15s` 量级。
4. 所以 command 经常先在 VPS command store 里 `queued`，等下一次 heartbeat 才派发到 PC。
5. Android 先超时退出；等晚到的 `accepted` 回来时，Android 已不会再收到 `submit_ack + session_binding`。

## 下一步建议

1. 先修 create-session facade 的 live blocker，再继续后半段设备验证。
   - 候选修法 A：把 `_ACK_WAIT_SECONDS` 提高到明显大于 `pc-control` heartbeat`
   - 候选修法 B：`enqueue_command` 后主动触发 pending dispatch，而不是只等下一轮 heartbeat
2. 修完后优先重跑同一条真机场景：
   - `android_app_token -> create-session -> submit_ack -> session_binding -> provisional detail`
3. 只有这一步闭环后，后续这些验证才有意义：
   - workspace 是否立即出现该 session
   - detail 是否从 `VPS-native projection` 直接承接
   - `reply/status` 第一跳 UI 更新是否连续

## 本次未完成项

- 没能完成 `create-session -> session_binding -> provisional session -> detail` 真机闭环。
- 没能完成 Batch D 的 workspace/detail direct continuity 真机闭环。
- 当前得到的是一个明确的 live blocker，而不是通过结果。
