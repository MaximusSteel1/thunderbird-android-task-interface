# TaskMail Next Session Handoff (2026-03-20, relay VPS bootstrap)

## Scope

This note tells the Android TaskMail side what currently exists on the PC/VPS side for relay bootstrap work, and what
Android should do when it is ready to start connection work.

It is intentionally narrow.

It does **not** redefine the Android mail protocol.

## Current PC/VPS Facts

As of `2026-03-20` in `E:\projects\mail_based_task_manager`:

- the PC-side outbound path has already been split into render / packet / transport layers
- a minimal relay server skeleton is now live on the development VPS
- the public health endpoint currently responds at:
  - `http://124.223.41.153:8787/healthz`
- the current health response shape is:
  - `status`
  - `service`
  - `listen.host`
  - `listen.port`
  - `session_count`
  - `auth.transport_token_id`

Current PC-side source references:

- `E:\projects\mail_based_task_manager\docs\plans\vps_relay_bootstrap_plan.md`
- `E:\projects\mail_based_task_manager\docs\plans\vps_relay_deploy_runbook.md`
- `E:\projects\mail_based_task_manager\docs\platform\relay_transport_protocol_draft.md`

## Important Boundary

Android should **not** treat this as a new Android-side protocol.

Current agreed boundary remains:

- Android-facing mail contract is still frozen on the existing mail rules
- relay/VPS work is an internal transport evolution first
- Android should preserve the current reply and parsing rules until an explicit cross-repo protocol update says
  otherwise

This means:

- do not change TaskMail read/reply semantics just because a VPS now exists
- do not invent Android-only relay packet shapes
- do not assume HTML/body/subject contracts have changed

## What Android Can Assume Right Now

Android can currently assume:

1. there is now a stable public development relay host and port
2. the relay health endpoint is reachable from outside the VPS
3. the current MVP connection direction should stay `client -> VPS`
4. the first protocol shape still follows:
   - `hello`
   - `hello_ack`
   - `packet`
   - `packet_ack`
   - `ping`
   - `error`

Android should **not** assume:

1. that a production-ready Android relay client already exists
2. that the relay token is stored in this repository
3. that SSH access is required for normal Android app integration work
4. that Android should stop using the current mail-based contract today

## How Android Should Start Connection Work

When Android is ready to begin relay connection work, the first steps should be:

1. Confirm plain network reachability to the current health endpoint:
   - `http://124.223.41.153:8787/healthz`
2. Add a development-only relay config seam on Android:
   - host
   - port
   - enabled/disabled switch
   - transport token input from local secret storage only
3. Implement only a narrow connection bootstrap first:
   - connect
   - send `hello`
   - receive `hello_ack`
   - surface connection failure clearly
4. Keep Android mail-based behavior intact until that narrow bootstrap is stable.

Recommended first Android validation order:

1. health endpoint reachability
2. authenticated `hello -> hello_ack`
3. local logging / debug surface for connection state
4. only then consider packet send/receive experiments

## Secret Handling

Do **not** commit relay secrets into the Android repository.

Specifically:

- do not copy the VPS SSH private key into this repo
- do not store the relay transport token in tracked files
- do not put server credentials into docs, source, or Gradle config

If Android needs the current development relay token, obtain it out-of-band from the maintainer and keep it in
developer-local secret/config storage only.

SSH access is not required for normal Android client integration. It is only a server-operations concern.

## Read First

Before starting Android relay work, read these in order:

1. `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
2. `docs/TASKMAIL-MAIL-RULES.md`
3. `E:\projects\mail_based_task_manager\docs\platform\relay_transport_protocol_draft.md`
4. `E:\projects\mail_based_task_manager\docs\plans\vps_relay_bootstrap_plan.md`

## Practical Next Step

The first Android-side relay slice should be:

- a development-only relay connectivity probe plus `hello -> hello_ack`

It should **not** be:

- a full transport cutover
- a replacement for current TaskMail mail parsing/reply logic
- a reason to copy SSH keys or other infrastructure secrets into the Android repo
