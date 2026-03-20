# TaskMail Android PC Current Alignment Gap Closure Plan (v0.1)

Updated: 2026-03-19

## Status

This remains the current recommended Android-side plan for closing the remaining gap between:

- current PC-side canonical TaskMail protocol/runtime facts in `E:\projects\mail_based_task_manager\docs\current\`
- current Android-side TaskMail implementation, docs, and validation evidence in this repository

This plan is intentionally narrower than any future platform-front-end direction.

Its job is to keep Android on a trustworthy current baseline before any broader UI expansion.

The 2026-03-19 recheck changes the plan interpretation in one important way:

- Slice 1 (authority/doc alignment), Slice 2 (minimum protocol compatibility hardening), and Slice 3 (multi-question send-validation hardening) should now be treated as closed in Android
- the active work is now Slice 4 validation closeout
- Slice 5 explicit UI scope remains deliberately deferred until Slice 4 is clearer
- the separate idle-push migration line stays outside this document

## Why This Plan Exists

The current Android TaskMail gap is no longer mainly "feature missing vs feature not started".

It is now a narrower mixed gap:

- Android already landed the first round of doc alignment and minimum protocol/send-guard hardening
- several already-implemented Android slices still lack the right real-device or real-mailbox closeout
- PC-side current truth now includes more explicit lifecycle, health, targeting, and retention facts than Android
  currently surfaces through UI

Before any larger platform-oriented frontend move, Android still needs a clean and reliable current-state baseline.

## Inputs

Primary Android-side inputs:

- `docs/TASKMAIL-MAIL-RULES.md`
- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/TASKMAIL-DEBUG-VALIDATION.md`

Primary PC-side inputs:

- `E:\projects\mail_based_task_manager\docs/current/README.md`
- `E:\projects\mail_based_task_manager\docs/current/mail_protocol.md`
- `E:\projects\mail_based_task_manager\docs/current/session_scheduler_status.md`
- `E:\projects\mail_based_task_manager\docs/current/android_reply_method_rules.md`
- `E:\projects\mail_based_task_manager\docs/current/task_view_mail_parsing_rules.md`
- `E:\projects\mail_based_task_manager\docs/current/multi_question_protocol.md`
- `E:\projects\mail_based_task_manager\docs/current/multimedia_mail_protocol.md`

Planning boundary inputs:

- `docs/taskmail/planning/android/taskmail-next-session-handoff-2026-03-18-gap-closure.md`
- `docs/taskmail/planning/android/taskmail-next-session-handoff-2026-03-18-idle-push.md`
- `docs/taskmail/planning/android/taskmail-idle-push-migration-plan-v0.1.md`

## Goal

Close the remaining Android gap in this order:

1. keep Android docs aligned with current PC-side canonical facts
2. close the highest-value real-mailbox and real-device validation gaps for already-landed Android slices
3. only then decide whether any current PC-side control-plane capabilities are worth surfacing explicitly in Android UI
4. keep idle-push migration and broader stability work separate unless PC-side canonical truth itself changes

## Non-Goals

This plan does not include:

- a platform-front-end rewrite
- replacing mail transport
- building direct `memory.*` / `task.*` platform client support
- broad Android UI expansion for every existing TaskMail protocol action
- reopening slices 1-3 unless PC-side canonical docs materially change again
- treating PC-side host receive-path `IDLE` support as proof that Android local mailbox refresh can already switch to IDLE
- folding the separate TaskMail idle-push migration plan into this gap-closure note

## 2026-03-19 PC Current Truth Recheck

The current PC-side canonical docs now clearly treat all of the following as current truth:

- thread/session lifecycle persists `active | ended`, and `/end` plus `/resume` are part of the live control plane
- `last_active_at` and `last_progress_at` are current persisted fields
- health visibility is current truth, including `normal`, `stale`, `suspected_stuck`, and `orphaned`
- the active working-set cap `4` is both the active-session cap and the real background concurrency cap
- same-workspace explicit session targeting is current truth for `/status <session_id>`, `/continue <session_id>`,
  `/pause <session_id>`, `/resume <session_id>`, `/end <session_id>`, and `/kill <session_id>`
- `/sessions` remains the current workspace discovery entrypoint and now also returns copyable targeted-command hints
- `/new` is an explicit logical new-session boundary; non-reply new task mail still creates a fresh thread/session
- title-based reuse of an existing session for a new non-reply mail has still not landed on PC and should not be treated
  as current truth
- live mailbox retention is intentionally split:
  - progress mail `[ACCEPTED]`, `[RUNNING]`, and `[STATUS]` is replaceable
  - action-required mail `[QUESTION]` and `[PAUSED]` is retained
  - receipt mail `[DONE]`, `[FAILED]`, and `[KILLED]` is retained
- permission inheritance and override behavior is now a current protocol fact, not a planning note
- inbound/outbound attachment behavior remains current protocol truth through the multimedia mail protocol
- the PC host receive path is now best-effort IMAP `IDLE` aware on supported servers, but that is still a bot-mailbox
  host/runtime fact, not automatic proof that Android local mailbox refresh can rely on IDLE

## Current Gap Summary

The remaining Android gaps to close are now:

### 1. Validation closeout gap

Several important Android slices exist in code, but are not yet closed with the right real-mailbox or real-device
evidence.

### 2. Explicit UI scope gap

PC-side current truth now includes more explicit control-plane capabilities than Android currently exposes through
dedicated UI, including:

- same-workspace targeted commands
- `/end`
- `/pause`
- `/new`
- `/rerun`
- `/kill`
- reply-side advanced fields such as `Permission`, `Profile`, `Timeout`, `Mode`, and `Acceptance`
- lifecycle/health-facing facts that Android currently preserves only partially or internally

This is now mainly a product and scope-control question, not a "protocol fact still unknown" question.

### 3. Scope-separation gap

There is now a higher risk of mixing together two different workstreams:

- PC-current alignment and Android validation closeout
- TaskMail foreground-polling removal / idle-push migration

Those should stay separate unless a PC-side canonical protocol/runtime change requires otherwise.

## Recommended Workstreams

## Slice 1: Authority and Documentation Alignment

### Status

Closed in Android on 2026-03-18.

### What Landed

- Android current docs were updated so PC current truth is no longer described as mere future watchpoints
- Android docs now distinguish:
  - PC protocol/runtime fact
  - Android implementation fact
  - Android validation fact

### Reopen only if

- PC-side canonical docs change again in a way that reintroduces drift

## Slice 2: Minimum Protocol Compatibility Hardening

### Status

Closed in Android on 2026-03-18.

### What Landed

- Android now parses and preserves:
  - `lifecycle`
  - `last_active_at`
  - `last_progress_at`
- lifecycle/progress compatibility is now treated as landed internal preservation, not a future idea

### Remaining boundary

- observe-facing health is still PC current truth ahead of Android UI
- dedicated Android lifecycle/health UI is still undecided

## Slice 3: Multi-Question Send Validation Hardening

### Status

Closed in Android on 2026-03-18.

### What Landed

- Android now blocks multi-question send when required answers are incomplete
- unknown `question_id` values are rejected locally
- choice answers must use canonical values on send
- legacy two-line compatibility remains preserved

### Reopen only if

- PC-side canonical multi-question policy changes again

## Slice 4: Real-Mailbox and Real-Device Validation Closeout

### Status

Current active slice.

### Purpose

Close the highest-value evidence gaps for already-landed Android slices.

### Priority order

1. reply-to-configured-bot-mailbox real-mailbox smoke
2. formal-host `Project list -> [SYNC] -> Use this repo -> Repo:` device smoke
3. formal-host new-task entry device smoke
4. multi-account sender-selection device smoke
5. dual-mailbox coexistence and `[SYNC]` exclusion device smoke
6. refresh/live-update closeout:
   - failure warning visibility
   - passive foreground refresh while staying on workspace/detail
   - summary freshness after backend mail lands
   - detail draft/attachment preservation while editing
7. widen beyond narrow TaskMail validation only after the above evidence boundary is clearer

### Done when

- these items move out of "pending current real-device / real-mailbox smoke" in the validation ledger
- Android current-state docs no longer need to caveat those slices as partially closed only

## Slice 5: Explicit UI Scope Decision

### Status

Deliberately deferred until Slice 4 is clearer.

### Purpose

Decide what should remain protocol-compatible but hidden, versus what is worth making explicit in Android UI.

### Candidate controls

- same-workspace targeted command UX for `/status`, `/continue`, `/pause`, `/resume`, `/end`, and `/kill`
- explicit `/new` and `/rerun` affordances where Android already has a sensible entry point
- reply-side advanced fields:
  - `Permission`
  - `Profile`
  - `Timeout`
  - `Mode`
  - `Acceptance`
- minimal lifecycle/health surfacing when it materially improves user understanding

### Guardrails

- do not imply title-based session reuse; PC current truth still says new non-reply mail creates a fresh session
- do not imply cross-workspace switching; current PC targeting remains same-workspace only
- do not expand UI just because the protocol supports it

## Recommended Execution Order

1. Keep Slices 1-3 closed unless PC-side canonical truth changes
2. Execute Slice 4 validation closeout
3. Reassess Slice 5 only after Slice 4 has a clear interpretation
4. Keep the idle-push migration on its own plan track

## Verification Strategy

For Android-side follow-up, continue to start narrow:

- `.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest`
- `.\gradlew.bat :feature:taskmail:internal:detekt`
- `.\gradlew.bat :feature:taskmail:internal:lintDebug`

Widen only when the touched Android slice actually reaches host/navigation/settings surfaces outside TaskMail internal.

For real-device and real-mailbox follow-up, keep using:

- `docs/TASKMAIL-DEBUG-VALIDATION.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`

and prefer precise manual confirmation when automation becomes unreliable.

## Risks and Guardrails

### Risk 1: Reopening already-closed slices without a real PC-side delta

That would create churn without closing the actual remaining gap.

### Risk 2: Confusing same-workspace explicit targeting with general session switching

PC current truth still does not support hidden title-based guessing or cross-workspace switching.

### Risk 3: Misreading PC host-side IDLE support as Android local mailbox readiness

The PC host now supports best-effort bot-mailbox receive-side `IDLE`, but Android local mailbox freshness is a separate
problem and already has its own migration note and environment caveat.

### Risk 4: Premature UI expansion

If Android expands explicit control UI before Slice 4 validation is clearer, scope will balloon and drift will get
worse.

## Success Criteria

This plan is successful when:

- Android current docs stay aligned with the latest PC-side canonical TaskMail facts
- Slices 1-3 remain closed unless PC truth materially changes
- Slice 4 moves the highest-value Android evidence gaps out of the pending bucket
- any Slice 5 UI additions are deliberate, minimal, and do not overstate current PC behavior
- the gap-closure plan and the idle-push migration plan no longer compete for the same problem statement

## Documentation Follow-up

When slices advance, update in this order:

1. PC-side canonical docs if the protocol/runtime truth changed
2. `docs/TASKMAIL-MAIL-RULES.md`
3. `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
4. `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
5. current gap-closure handoff note
