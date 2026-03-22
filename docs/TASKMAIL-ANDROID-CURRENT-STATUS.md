# TaskMail Android 当前状态

本文是本仓库 Android TaskMail 功能的当前事实源。

如果本文件与旧的 phase-planning 文档冲突，应以本文件为当前基线，并同步修正相关 planning / historical 文档。

## 日期

- 最后更新：2026-03-22

## 文档维护约定

- 本文件与 `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`、`docs/TASKMAIL-MAIL-RULES.md` 共同构成当前 authority 集
- Markdown 编码、换行与文件结尾遵循仓库 `.editorconfig`：`utf-8`、`lf`、保留 final newline
- 后续新增或更新说明默认使用中文；协议字段、代码标识、文件路径与命令保持原文

## 本文回答的问题

本文件只打算回答一个问题：

> Android TaskMail 当前到底已经具备哪些能力，以及这些能力中哪些已经或尚未被验证？

它不试图完整重述长期规划。

当前 authority 仍由 current-status / validation-ledger / mail-rules 文档组共同承担。

历史 planning 与 implementation-reference 材料仍保留在：

- `docs/taskmail/planning/README.md`
- `docs/taskmail/planning/android/taskmail-dual-mailbox-android-adjustments-v0.1.md`
- `docs/taskmail/planning/android/taskmail-refresh-live-update-plan-v0.1.md`
- `docs/taskmail/planning/android/taskmail-foreground-scoped-live-refresh-plan-v0.1.md`
- `docs/taskmail/planning/android/taskmail-guided-new-thread-mvp-implementation-checklist-v0.1.md`
- `docs/taskmail/planning/android/taskmail-guided-new-thread-mvp-ui-spec-v0.1.md`
- `docs/taskmail/planning/android/taskmail-guided-new-thread-mvp-implementation-map-v0.1.md`
- `docs/taskmail/planning/android/taskmail-next-session-handoff-2026-03-20-relay-vps-bootstrap.md`

旧的 phase 文档只保留历史 / 参考角色：

- `docs/TASKMAIL-ANDROID-PHASE2.md`
- `docs/TASKMAIL-ANDROID-PHASE3.md`

协议 authority 仍由以下文档承担：

- `docs/TASKMAIL-MAIL-RULES.md`

验证台账仍由以下文档承担：

- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`

## Evidence Basis

This status was updated from:

- repository inspection performed during the 2026-03-16 documentation-alignment pass
- repository inspection and TaskMail refresh/live-update implementation work performed later on 2026-03-16
- repository inspection and TaskMail dual-mailbox follow-up work performed later on 2026-03-16
- repository inspection and TaskMail dual-mailbox reply-target follow-up work performed on 2026-03-17
- repository inspection and TaskMail guided new-thread follow-up work performed later on 2026-03-17
- repository inspection and TaskMail runtime bot-mailbox settings follow-up work performed later on 2026-03-17
- repository inspection and TaskMail foreground-scoped live-refresh follow-up work performed later on 2026-03-18
- repository inspection and TaskMail rich-text detail body planning/protocol-freeze documentation work performed on
  2026-03-19
- TaskMail rich-text detail body Batch 1/2 implementation and narrow validation performed later on 2026-03-19
- TaskMail rich-text detail body Batch 3 and minimal Batch 4 implementation plus narrow validation performed later on
  2026-03-19
- TaskMail rich-text detail body Batch 5 preview/test corpus follow-up plus narrow validation performed later on
  2026-03-19
- TaskMail rich-text detail focused live-device placeholder validation performed later on 2026-03-19
- TaskMail rich-text detail fresh-build debug-preview/device validation follow-up performed later on 2026-03-19
- TaskMail rich-text representative Phase 0 sample follow-up plus narrow validation performed on 2026-03-21
- TaskMail Phase 4 `new task` durable direct-evidence follow-up plus narrow validation performed later on 2026-03-22
- TaskMail Phase 4 `new task` Android / PC three-scenario matrix reconciliation documentation follow-up performed later
  on 2026-03-22
- TaskMail Phase 4 `new task` persisted-evidence review-surface follow-up plus narrow validation performed later on
  2026-03-22
- TaskMail Phase 4 `new task` live fallback / persisted-evidence device closeout performed later on 2026-03-22
- the current Android documentation set
- current PC-side canonical protocol docs in `E:\projects\mail_based_task_manager\docs/current/`
- current PC-side mailbox parsing rules in `E:\projects\mail_based_task_manager\docs/current/task_view_mail_parsing_rules.md`
- current PC-side next-phase plan in `E:\projects\mail_based_task_manager\docs/plans/coding_backlog.md`, used only as a
  drift watch and not as Android current-state authority
- targeted Gradle validation evidence already recorded in `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- targeted manual/debug checklist coverage already recorded in `docs/TASKMAIL-DEBUG-VALIDATION.md`
- user-reported Android device follow-up observations recorded on 2026-03-17

This update includes narrow Gradle validation for the refresh/live-update slice, the later dual-mailbox follow-up, the
2026-03-17 dual-mailbox reply-target follow-up, and the later foreground-scoped live-refresh follow-up.

Manual device smoke for that slice is now partially recorded: workspace pull-to-refresh success was observed on-device on
2026-03-17, but refresh-warning failure-path behavior and confirmed post-sync summary freshness are still open.

This document distinguishes four levels:

- **Present in repository**: code or tests clearly exist in the current repo
- **Revalidated in prior executable session**: explicitly re-run in the validation session captured in the ledger
- **Validated in prior docs**: earlier docs claim smoke/build/test validation
- **Not revalidated in this pass**: not re-run during the current repository-inspection / documentation pass

## Current Baseline Summary

The Android TaskMail feature is no longer accurately described as a Phase 1.5 debug-only prototype.

The current repository baseline is better described as:

- real TaskMail mail is read from the local mail store
- when both user-mailbox and bot-mailbox TaskMail copies exist locally, repository aggregation now prefers the user-mailbox copy while preserving the legacy single-mailbox fallback
- `workspace -> session -> detail` is backed by the real repository
- formal in-app entry exists in the launcher/navigation stack
- drawer-level `Tasks` entry plumbing exists
- guided new-thread MVP surface now exists inside the TaskMail host:
  - dedicated `New task` route and screen
  - sender-account resolution for zero / one / multiple mailbox-account states
  - canonical first-task subject/body serialization with collapsed advanced fields
  - dedicated non-reply first-task transport to the configured bot mailbox
  - the current `new task` send flow now preflights relay bootstrap above the retained debug surface and, when
    bootstrap reaches `hello_ack`, sends the first business action over the direct relay `packet` path using
    `phase2-direct-outbound-contract-v1`
  - when bootstrap is unavailable or the direct path returns a fallback-classified transport or capability failure, the
    same `new task` flow falls back to the current mail transport and emits an explicit mail-fallback success message
  - hard direct rejections such as invalid payload or unauthorized now keep the draft and surface the direct-send
    failure instead of silently falling back to mail
  - the latest `new task` direct-or-fallback result is now normalized into `TaskMailDirectSendEvidence` and persisted as
    a per-sender-account local send record, so Phase 4 parity evidence survives screen reloads and sender-account
    reselection instead of living only in the current in-memory `TaskNewTaskViewModel` state
  - accepted direct runs now also persist `requestId` alongside `receiptId` and optional `transportMessageId`, so the
    Android latest-evidence record preserves the first-priority same-run bind key used by current Phase 5 narrow
    planning
- Android-side TaskMail bot-mailbox configuration now also exists:
  - dedicated `TaskMail bot mailbox` settings route and screen
  - formal launcher target reachable from Android `General settings`
  - runtime-saved bot-mailbox updates become visible to TaskMail send flows without app restart
- bootstrap discovery and repo-path assist now also exist inside the TaskMail host:
  - dedicated `Project list` route and screen
  - sender-account-aware `[SYNC]` request plus latest project-list parsing/rendering
  - `Use this repo` handoff that prefills `Repo:` in `New task`
- workspace now supports pull-to-refresh, real manual all-account mail sync, foreground-only 10-second account-scoped
  refresh while visible when sender-account resolution is unambiguous, and automatic reloads after local mail-store
  changes
- session detail is a real interaction surface, not a read-only viewer:
  - plain-text reply
  - reply attachments
  - single-question quick answers
  - multi-question structured `Answers:` template replies
  - local multi-question send gating that blocks missing required answers, unknown `question_id`, and non-canonical
    choice values while preserving legacy two-line answer compatibility
  - `/status`
  - `paused` session resume semantics through explicit `/resume` prefixing
- session detail body rendering is now partially rich-text capable in the repository:
  - the read path now preserves optional `htmlBody` in `TaskMailEnvelope` and `TaskMailMessage`
  - `TaskMessageBody` now carries `plainTextFallback`, `renderMode`, optional `richDocument`, and optional `sourceHtml`
  - repository timeline mapping can project supported `article.task-mail` HTML into `TaskRichTextDocument` while still
    preserving plain-text fallback and plain-text summary behavior
  - detail UI now prefers controlled rich-text blocks for supported content and falls back to plain text otherwise
  - raster inline images can now render from local attachment content URIs when Android has a resolvable timeline
    attachment source, while static SVG still stays on the controlled placeholder/fallback path
  - the controlled preview/debug corpus now also includes representative `External Deliveries`, `Attachment Notices`,
    and long-error `FAILED` detail samples for Phase 0 consumer-acceptance work
- session detail auto-refresh preserves in-progress draft text and selected reply attachments while local mail changes
  are reloaded, and now also runs foreground-only 10-second account-scoped refresh while visible when
  `replyContext.accountUuid` is present
- reply safety guards exist:
  - reply anchor is derived from the newest logical-session message
  - mixed-account or missing-anchor sessions disable reply instead of guessing
  - TaskMail reply transport resolves its outbound destination from TaskMail-specific bot-mailbox configuration instead
    of generic mail reply-recipient heuristics
- timeline now includes richer mail-side fidelity:
  - real attachment metadata on timeline items
  - `Open` / `Save` attachment actions
  - filtering `multipart/*` MIME containers from timeline attachments
  - collapsing duplicate physical mail copies by `Message-ID`, with conservative fallback heuristics for preview/full-body duplicates
- protocol-aware parsing now covers:
  - keeping `[SYNC]` bootstrap mail outside TaskMail session projection
  - flattened `TASK-STATE` / `TASK-QUESTION` capsules
  - reply-like status subjects staying on the outgoing path
  - `[PAUSED]` and `paused_from_status`
  - canonical quick-answer values vs display labels
  - stricter multi-question local send validation for required-answer completeness, canonical choice values, and unknown
    `question_id` rejection
- validation status is still uneven:
  - narrow unit/regression tests, TaskMail-internal `detekt`, TaskMail-internal `lintDebug`, and app `fossDebug` assembles were re-run in executable sessions captured on 2026-03-16
  - the refresh/live-update slice and later foreground-scoped live-refresh follow-up were revalidated with focused workspace/detail ViewModel tests plus clean TaskMail-internal `detekt`, `lintDebug`, and a later clean `:feature:taskmail:internal:testDebugUnitTest` rerun
  - the guided new-thread slice now has focused ViewModel/UI/domain/data coverage plus clean TaskMail-internal `detekt` and `lintDebug`
  - the runtime bot-mailbox settings follow-up now has focused repository/ViewModel/UI/navigation coverage plus clean TaskMail-internal, launcher, and legacy-settings `detekt`/`lintDebug`
  - the bootstrap discovery / repo-path assist slice now has focused navigation/parser/ViewModel/UI coverage plus clean TaskMail-internal `detekt` and `lintDebug`
  - the multi-question send-validation follow-up now has focused UiState/ViewModel coverage plus a clean rerun of
    `:feature:taskmail:internal:testDebugUnitTest`, `detekt`, and `lintDebug`
  - a 2026-03-21 representative-sample follow-up then added focused projector/detail coverage for
    `External Deliveries`, `Attachment Notices`, and long-error `FAILED` rich-text samples plus another clean rerun of
    `:feature:taskmail:internal:testDebugUnitTest`, `detekt`, and `lintDebug`
  - a later 2026-03-21 Phase 1 relay-bootstrap follow-up then extracted a reusable `RelayBootstrapManager` above the
    debug ViewModel, kept the current debug screen behavior intact, and added focused manager coverage plus another clean
    rerun of `:feature:taskmail:internal:testDebugUnitTest`, `detekt`, and `lintDebug`
  - a later 2026-03-21 Phase 1 relay-bootstrap classification follow-up then added structured
    `RelayBootstrapStatus` / `RelayBootstrapResult` vocabulary aligned with the current PC-side Phase 1 bootstrap note,
    kept mail-fallback routing explicit on non-success results, and added another clean rerun of
    `:feature:taskmail:internal:testDebugUnitTest`, `detekt`, and `lintDebug`
  - a later 2026-03-21 `new task` relay-bootstrap reuse follow-up then moved that structured bootstrap result above the
    retained debug surface into the formal `new task` flow, preserved the current mail send path, and added another
    clean rerun of `:feature:taskmail:internal:testDebugUnitTest`, `detekt`, and `lintDebug`
  - a later 2026-03-21 Phase 2 direct-outbound follow-up then added relay `packet` / `packet_ack` support plus the
    first Android direct `new task` sender, updated the formal `new task` flow to prefer direct send on `hello_ack`
    while preserving explicit mail fallback and hard-rejection stop behavior, and added another clean rerun of
    `:feature:taskmail:internal:testDebugUnitTest`, `detekt`, and `lintDebug`
  - a later 2026-03-22 Phase 4 durable-evidence follow-up then normalized the latest `new task` send result into
    machine-readable `TaskMailDirectSendEvidence`, persisted the latest record per sender account, rehydrated that
    evidence into `TaskNewTaskViewModel`, and added another clean rerun of focused repository/ViewModel tests,
    `detekt`, and `lintDebug`
  - a later 2026-03-22 Phase 4 matrix-reconciliation follow-up then mapped the current Android `new task`
    `direct accepted` / `fallback_to_mail` / `hard_rejection_stop` evidence into the shared parity checklist,
    mismatch ledger, and rollback trigger docs, aligned Android-side wording with the current PC-side classifier and
    mail-baseline terms, kept the ledger intentionally empty pending confirmed cross-repo mismatches, and added another
    clean rerun of focused direct / ViewModel / relay-sender / protocol / persistence tests
  - a later 2026-03-22 Phase 4 persisted-evidence review-surface follow-up then added a dedicated latest-evidence card
    on the formal `New task` screen, locked accepted and fallback/error review states in focused screen coverage, and
    added another clean rerun of focused `TaskNewTaskScreenKtTest` / `TaskNewTaskViewModelTest`, `detekt`, and
    `lintDebug`
  - 2026-03-22 的后续窄实现已把 accepted direct `requestId` 从 relay sender 贯通到
    `TaskMailDirectSendEvidence`、本地 `TaskMailNewTaskSendRecord` 持久化、formal `New task` latest-evidence
    card，以及对应的 sender / usecase / ViewModel / screen / persistence 聚焦测试，并补跑
    `:feature:taskmail:internal:testDebugUnitTest`、`detekt`、`lintDebug`
  - a later 2026-03-22 Phase 4 live fallback / persisted-evidence device follow-up then used the formal host on the
    attached device to confirm that a reinstall-left relay `not_configured` state still produces explicit
    `[Mail fallback]` user-visible send feedback, persists `TaskMailDirectOutcome.MailFallbackSucceeded` /
    `TaskMailDirectSwitchGate.FallbackRequired` plus the bootstrap failure reason into the local send-record store,
    keeps that same evidence reviewable after screen reload and recent-tasks cold start on the formal `New task`
    surface, and still closes mailbox-side on `[DONE][S:thread_093] Phase 4 live review 20260322 A`
  - a later 2026-03-22 relay re-provision / fresh direct device follow-up then repaired the saved live relay config on
    the attached device, revalidated debug-host `Healthz` plus `hello_ack`, confirmed formal-host user-visible
    `[Relay]` feedback on `Phase 4 direct parity 20260322 C`, persisted `hello_ack` / `DirectAccepted` /
    `KeepDirectDefault` into the app-private send record, and closed the same run on `thread_095` with PC
    `canonical_summary.json` plus mailbox-side `[DONE]`
  - a 2026-03-17 manual device follow-up confirmed workspace pull-to-refresh without blanking loaded content and showed the latest local outgoing after reply, but an offline rerun did not surface a refresh warning and no backend-fed summary update was confirmed during that observation window
  - a later 2026-03-17 guided new-thread device follow-up first closed the explicit failure path (`TaskMail bot mailbox is not configured.` with draft retention), then, after reinstalling a debug build with a non-empty bot-mailbox default, confirmed canonical first-task delivery to the bot mailbox plus mailbox-side `[ACCEPTED]`, `[RUNNING]`, and `[DONE]` replies on `thread_054`
  - a 2026-03-18 device follow-up then confirmed `General settings -> TaskMail bot mailbox -> save -> return to TaskMail -> resend without reinstall/restart` on the latest Thunderbird debug APK
  - the same guided new-thread and later Phase 4 live follow-ups still leave multi-account sender selection on device,
    `Project list` rendering plus repo-prefill smoke, and raw first-task body verification open
  - full repo-wide quality and current device smoke are still not closed

In short:

> The repository contains a formal TaskMail feature with read path, host integration, drawer entry, guided new-thread first-task sending, reply interaction, attachment handling, and protocol-aware paused/multi-question behavior, but full release-confidence validation is still incomplete.

## What Is Present in Repository

### 1. Real Mail Read Path

The repository contains the real-data TaskMail read stack:

- `LegacyTaskMailMessageSource`
- `LegacyTaskMailBodyExtractor`
- `LegacyTaskMailAttachmentMetadataExtractor`
- `DefaultTaskMailRepository`

The repository also contains tests and validation helpers around repository behavior, including the JSON replay path under:

- `feature/taskmail/internal/src/test/kotlin/net/thunderbird/feature/taskmail/internal/validation/`

The current repository baseline also includes read-model guards for live-mail edge cases:

- when Android sees both user-mailbox and bot-mailbox TaskMail traffic, session projection prefers the user mailbox so service-mailbox copies do not dominate reply eligibility or duplicate workspace/session cards
- `[SYNC]` bootstrap mail remains outside TaskMail session projection and does not create TaskMail workspace/session entries
- reply-like `[DONE]` / `[FAILED]` / `[PAUSED]` user replies stay on the outgoing/user path instead of being misclassified as system mail
- flattened `TASK-STATE` / `TASK-QUESTION` capsules are parsed instead of leaking machine fields into UI
- `multipart/alternative` and similar MIME containers are filtered from timeline attachment rendering
- duplicate physical mail copies can be collapsed by shared `Message-ID`, while preview/full-body fallback heuristics remain conservative

### 2. Logical Session Aggregation

The repository contains logic for grouping TaskMail data into logical sessions instead of exposing only raw physical mail threads.

Current documented grouping behavior remains:

- prefer `session_id`
- otherwise fall back to `thread_id`
- otherwise fall back to physical mail thread grouping

The latest state capsule also carries forward paused metadata such as `paused_from_status`, so a session can remain protocol-aware even when the newest state is `paused`.

### 3. Formal Host Integration

The repository contains formal TaskMail host integration in the launcher/navigation stack.

Read-only inspection found:

- `FeatureLauncherTarget.TaskMail`
- `FeatureLauncherTarget.TaskMailSettings`
- TaskMail route registration in `FeatureLauncherNavHost`
- `TaskMailNavigation` / `DefaultTaskMailNavigation`
- `taskMailModule` included from `app-common`

This means TaskMail is formally reachable in-app and is no longer only a debug deep-link surface.

### 4. Drawer Entry Path

The repository contains drawer-side `Tasks` entry plumbing.

Read-only inspection found:

- `OnTasksClick`
- `OpenTasks`
- drawer UI wiring for `Tasks`
- `navigation_drawer_dropdown_action_tasks`

This means the docs should no longer describe drawer integration as purely hypothetical future work.

### 5. Guided New-Thread Surface

The repository now also contains a dedicated first-task TaskMail flow inside the formal TaskMail host.

Read-only inspection and later follow-up work found:

- `TaskMailRoute.NewTask`
- `TaskNewTaskScreen`
- `TaskNewTaskViewModel`
- `RunTaskMailDirectOrFallback`
- `TaskMailDirectSendEvidence`
- `TaskMailNewTaskSendRecordRepository`
- `FileBackedTaskMailNewTaskSendRecordRepository`
- `GetTaskMailSenderAccounts`
- `SendTaskMailNewTask`
- `LegacyTaskMailNewTaskMimeMessageFactory`
- `RealTaskMailNewTaskSender`

Current repository behavior for this slice includes:

- a `New task` entry from the TaskMail workspace top bar and empty state
- a dedicated first-task screen inside the TaskMail host instead of routing through generic mail compose
- sender-account blocking when no finished setup mailbox exists
- automatic sender-account preselection when exactly one finished setup mailbox exists
- explicit sender-account selection when multiple finished setup mailboxes exist
- editable title derivation from the first non-empty `Task:` line
- collapsed advanced fields for `Workdir:`, `Mode:`, `Timeout:`, `Permission:`, `Profile:`, and `Acceptance:`
- canonical first-task subject/body serialization for `[OC]` / `[CX]` mail
- dedicated non-reply MIME building with `To = bot mailbox`, `Cc = empty`, and no reply headers
- user-visible send failures for missing, invalid, or non-unique bot-mailbox configuration instead of a generic preparation error
- the latest `new task` send attempt now produces reviewable direct evidence fields such as `outcome`, `switchGate`,
  optional `requestId`, `receiptId`, optional `transportMessageId`, fallback reason, and error message
- the latest persisted `new task` evidence is restored for the currently selected sender account so the Android side can
  review the most recent direct / fallback / hard-stop result without depending on the current screen instance alone
- the formal `New task` form now also surfaces that restored latest evidence in a dedicated review card, so the current
  sender-account selection has an in-repo UI path for reviewing the most recent direct / fallback / hard-stop outcome
- a dedicated `TaskMail bot mailbox` settings screen that:
  - is reachable from Android `General settings`
  - loads the current saved or build-default bot mailbox value
  - saves a trimmed runtime bot-mailbox address into TaskMail-specific global settings
  - makes the updated value immediately visible to both first-task and reply send paths without app restart
- current real-device smoke now also confirms:
  - single-account read-only sender rendering on-device
  - on-device title derivation from the first non-empty `Task:` line
  - canonical bot-mailbox delivery for a minimal `[CX]` first-task mail
  - mailbox-side `ACCEPTED`, `RUNNING`, then `DONE` replies for that Android-originated first-task thread
  - terminal summary for that smoke thread reports `No repo files were modified.`
- a sibling `Project list` route inside the same TaskMail host that:
  - reads the latest parsed `[SYNC] Project Folder List` result for the selected sender account
  - can request a fresh `[SYNC]` mail without projecting that bootstrap traffic into TaskMail workspace/detail UI
  - renders discovered roots/projects with `Use this repo`
  - hands the selected repo path back into `New task` so `Repo:` is prefilled but still editable

### 6. Session Detail Interaction Surface

The repository contains a real interaction-oriented session detail flow.

Read-only inspection found:

- `TaskMailReplySender`
- `RealTaskMailReplySender`
- `SendTaskMailReply`
- reply-aware `TaskSessionDetailViewModel`
- `TaskReplyComposer`
- `/status` support in use case and tests
- structured multi-question draft template support
- reply attachment selection/removal support
- timeline attachment open/save support

Current repository behavior is beyond a read-only viewer. The implemented interaction surface includes:

- plain-text replies
- attachment-only continuation replies
- single-question quick answers
- multi-question structured `Answers:` replies
- `/status`
- paused-session send paths that prepend `/resume`

Current non-parity boundary for this same screen:

- timeline rendering now prefers controlled rich-text blocks only when a supported `article.task-mail` fragment is
  present and safely projected
- raster inline image blocks can now render as bitmap previews when the attachment resolver exposes a local content URI,
  but static SVG still renders as a controlled placeholder plus normal attachment row rather than a true vector preview
- unsupported HTML and unmatched/external image references still degrade to safe text fallback instead of trying to load
  remote content
- workspace/session summary surfaces remain plain-text-first even when detail mail includes HTML

The repository also contains reply safety guardrails:

- standard reply transport preserves `In-Reply-To`, `References`, and canonical TaskMail subject identity tokens instead of inventing an Android-only reply protocol
- TaskMail reply transport resolves `To` from TaskMail-specific bot-mailbox configuration, clears `Cc`, and fails fast
  when the destination is missing or invalid instead of guessing through generic reply-recipient inference
- reply anchor comes from the newest logical-session message, not route params alone
- mixed-account logical sessions disable reply
- missing-anchor sessions disable reply

### 7. Refresh and Live Update Surface

The repository now also contains a TaskMail-local freshness layer for the existing workspace and detail UI.

Read-only inspection and later 2026-03-18 foreground-refresh follow-up work found:

- `RefreshTaskMail`
- `ObserveTaskMailStoreChanges`
- `LegacyTaskMailSyncRequester`
- `LegacyTaskMailStoreChangeObserver`
- `TaskMailForegroundRefreshLifecycleEffect`
- `TaskMailForegroundRefreshTickerFactory`
- `DefaultTaskMailForegroundRefreshTickerFactory`
- workspace pull-to-refresh wiring in `TaskWorkspaceContent`
- manual refresh state plus foreground-refresh loop ownership in `TaskWorkspaceViewModel`
- detail refresh state plus foreground-refresh loop ownership in `TaskSessionDetailViewModel`
- foreground refresh lifecycle events in `TaskWorkspaceContract` and `TaskSessionDetailContract`
- local mail-store change observation in both workspace and session detail view models

Current repository behavior for this slice includes:

- pull-to-refresh on the workspace screen
- a real manual all-account mail sync request before workspace/detail reload
- a foreground-only 10-second account-scoped sync loop while the workspace is visible, but only when exactly one
  TaskMail sender account can be resolved
- a foreground-only 10-second account-scoped sync loop while session detail is visible, resolved from
  `replyContext.accountUuid`
- background foreground-refresh ticks stay separate from the user-visible manual refresh spinner/error state
- automatic workspace reload when local TaskMail mail changes arrive
- automatic session-detail reload when local TaskMail mail changes arrive
- stale workspace/detail content remains visible during refresh and on refresh failure
- detail auto-refresh preserves in-progress draft text and selected reply attachments for the same session key

### 8. Protocol-Aware Behavior Present in Repository

The repository currently contains protocol-aware behavior that matters for Android/PC alignment:

- dual-mailbox TaskMail reads now prefer the user mailbox while keeping the legacy single-mailbox fallback for older setups
- `[SYNC]` remains a low-input bootstrap mail action outside TaskMail session projection
- `[PAUSED]` and `paused_from_status` are parsed and preserved
- state capsule parsing and repository projection now also preserve `lifecycle`, `last_active_at`, and
  `last_progress_at` internally
- single-question quick-answer UI displays labels but uses canonical values on send
- multi-question sessions do not expose one-tap quick answers and instead prefill a structured `Answers:` template
- reply subject normalization preserves TaskMail identity tokens such as status labels and `[S:session_id]`

Important boundary:

- the PC-side current protocol/runtime now also includes `Permission`, `/pause`, `/end`, `active|ended` lifecycle,
  `last_active_at`, `last_progress_at`, active-cap `4`, observe-facing health visibility, and split live-mail
  retention semantics
- the Android UI does not yet expose dedicated controls for those capabilities
- the Android repository now preserves the newest lifecycle / progress timestamps internally, but still does not surface
  dedicated lifecycle/health UI
- that should be read as “PC current truth still leads Android UI / health parity,” not as a protocol disagreement

### 9. Debug Validation Path

The debug-specific path still exists and remains useful:

- `TaskMailDebugActivity`
- debug deep link routing
- debug wiring in app debug config
- a reusable `RelayBootstrapManager` now sits above the debug ViewModel so current relay config, `healthz`, connect, and
  disconnect orchestration are no longer owned only by `TaskMailRelayDebugViewModel`
- relay bootstrap now also has a structured `hello_ack` / failure-classification result shape so the repository no
  longer needs to read raw relay exception text as the only bootstrap outcome
- that structured bootstrap result is now also reused by the formal `new task` flow as the gate before the first
  direct business packet
- Android relay transport now also supports relay `packet` / `packet_ack` and can map `TaskMailNewTaskDraft` into the
  first shared Phase 2 direct outbound contract for `action = new_task`
- when the direct path returns `packet_ack.accepted = true`, Android no longer duplicates that same `new task` request
  over mail; later TaskMail status and read-side updates still remain mail-driven today
- when relay returns `packet_ack.accepted = false`, Android now also honors an optional ack-level `error_code` and a
  recognized hard-rejection code prefix in `error_message`, so direct validation/auth failures do not silently fall back
  to mail just because they surfaced through the ack path
- 2026-03-21 的 live-device smoke 现已补齐当前 Phase 2 `new task` slice 的三条关键分支：
  - accepted direct ingress
  - fallback-classified direct failure 回退到 mail，并在 `thread_084` 完成任务
  - hard direct rejection (`invalid_payload`) 保留 draft、本地显示错误，并且在 `thread_085` 之后不再创建新的 PC 线程

因此，当前仓库里实际实现的 Phase 2 `new task` slice 可以视为已完成主路径与负路径的 live closeout；后续
status/result 仍走 mail；同日更晚些时候，`reply` / `/status` 的 Phase 5 guarded direct slice 已在独立
planning-layer contract 下开始实现，但它不属于这里描述的 Phase 2 `new task` slice，也还没有把 current protocol /
direct-default authority 一并改写。

Later on 2026-03-22, and then again on 2026-03-23, the first Android-side Phase 5 post-creation session-action
implementation slices landed under the shared planning-layer `post_creation_session_action_contract_v1` boundary.

That follow-up implemented all of the following in-repo:

- canonical `workspace_id` plumbing from workspace/detail navigation into `TaskMailRoute.SessionDetail`,
  `TaskSessionKey`, cache JSON, snapshot-backed repository summaries, and detail lookup compatibility fallbacks
- a dedicated relay `RelayTaskMailDirectSessionActionSender` plus `SendTaskMailDirectSessionAction` use case that maps
  the frozen v1 packet wrapper for:
  - `current-session plain reply`
  - `current-session /status`
- guarded `TaskSessionDetailViewModel` direct-lane gating that now reuses `RunTaskMailDirectOrFallback` only when:
  - canonical `workspace_id + session_id` is available from the current detail route
  - the action is current-session plain reply or current-session `/status`
- file-backed `TaskMailSessionActionSendRecord` persistence keyed by canonical current-session target, so the latest
  post-creation direct-or-fallback result is no longer only transient ViewModel state
- `TaskSessionDetailViewModel` latest-record rehydration plus a detail review surface for the latest post-creation
  direct evidence, including `outcome`, `switchGate`, `bootstrapStatus`, and optional send identifiers / fallback
  reasons

That same follow-up explicitly keeps these boundaries:

- quick answer, multi-question `Answers:`, paused `/resume`, and attachment-bearing continuation still stay on the
  retained mail path
- direct `reply` / direct `/status` accepted still does not mean final task outcome; canonical mail truth remains the
  user-visible source of status/result convergence
- the new guarded detail lane does not by itself promote `reply` / `/status` into current Layer 1 protocol authority or
  direct-default behavior
- live mailbox / device closeout for that latest-record review surface is now positively evidenced on the current installed build, but shared-artifact strong-bind closeout is still open

### 2026-03-23 Live/Manual Closeout Update

2026-03-23 这轮 closeout 先处理了两个设备前置条件，然后只保留了 v1 scope 的两条 formal-host live/manual 样本：

- 旧的 `net.thunderbird.android.debug` 与当前工作站 debug keystore 签名不一致，必须先卸载，再用 `.\gradlew.bat :app-thunderbird:installFullDebug` 重装当前 build
- relay 目前没有正式设置入口，只能通过 debug deep link `app://taskmail/debug/relay` 手动恢复 `Relay transport token`；恢复后已确认 `Healthz` 和 `Connect` 都成功，再回正式 `Tasks` 做验证
- 本轮样本固定为 `thread_019 / Phase5 status closeout 20260323 A` 与 `thread_020 / Phase5 reply closeout 20260323 B`

本轮已经补齐的 live/manual 结论如下：

- `thread_019` 的 current-session `/status` 在 Android 侧留下了 `actionType=Status`、`target=workspace_cb2404bf828c/thread_019`、`bootstrapStatus=hello_ack`、`outcome=MailFallbackSucceeded`、`switchGate=FallbackRequired` 的 latest direct evidence，同时 PC mailbox 侧收敛到 canonical `[STATUS][S:thread_019] Phase5 status closeout 20260323 A`
- `thread_019` detail 实际返回重开后，`Latest direct result` 卡仍能恢复为 `Status query / Mail fallback succeeded / Fallback required / Hello ack`
- `thread_020` 的 current-session plain reply `PHASE5_REPLY_CLOSEOUT_20260323_B` 在 Android 侧留下了 `actionType=Reply`、`target=workspace_cb2404bf828c/thread_020`、`bootstrapStatus=hello_ack`、`outcome=MailFallbackSucceeded`、`switchGate=FallbackRequired` 的 latest direct evidence；PC mailbox 侧先收到 reply ingress，然后因原线程为 `FAILED` 触发 fresh recovery run `20260323_020701_9913`，最终收敛到 canonical `[DONE][S:thread_020] Phase5 reply closeout 20260323 B`
- `thread_020` 发送后曾瞬时出现 `Unable to load session`，但返回重开 detail 后即可恢复；按 formal-host 要求从桌面图标冷启动 Thunderbird，再经 `Tasks` 回到 detail 后，`Latest direct result` 卡仍能恢复为 `Plain reply / Mail fallback succeeded / Fallback required / Hello ack`
- 当前 live closeout bundle 已能选中 Android `session_action` 记录并显示 `action_type` / `target_session_identity`，但 same-run bind 仍只有 `last_summary`，因为 Android fallback 记录还没有 `requestId` / `transportMessageId`，PC 当前 post-creation fallback canonical artifacts 也还没有保留 `action_type` / `target_session_identity` / action-specific ingress anchors

因此当前最准确的状态判断是：

- 设备侧 durable evidence / persistence 不再是 blocker
- shared-artifact / strong-bind 才是 `Batch D` 剩余主线
- `thread_020` 的 `Unable to load session` 更像 post-send UI drift 候选，而不是持久性丢失

The debug path should still be treated as:

- a retained validation and fallback path
- not the only way TaskMail exists in the repository

## Validation Status

### Revalidated in Recorded Executable Sessions

The executable validation session captured in `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md` re-ran narrow validation for:

- feature launcher TaskMail route wiring
- drawer `Tasks` event/effect plumbing
- drawer-to-launcher TaskMail handoff
- TaskMail API route contract
- debug-host back-stack exit fallback
- detail timeline newest-first UI mapping
- dual-mailbox user-mailbox preference in repository session/detail aggregation
- `[SYNC]` bootstrap mail staying outside TaskMail session projection
- reply-like detail direction regression coverage
- flattened capsule parsing regression coverage
- lifecycle / `last_active_at` / `last_progress_at` parser and repository-preservation coverage
- multipart pseudo-attachment filtering regression coverage
- duplicate timeline-copy collapse regression coverage
- reply sender, `/status`, paused `/resume`, single-question quick answers, and multi-question structured-answer behavior
- reply attachment selection plus attachment-only continuation behavior
- refresh/live-update ViewModel behavior, including manual refresh, foreground lifecycle/account-targeting behavior,
  local-change reload, stale-content retention, and detail draft/attachment preservation
- guided new-thread sender-account resolution, canonical first-task serialization, explicit bot-mailbox first-task send
  failure surfacing, and latest per-sender-account `new task` direct-evidence persistence / rehydration
- runtime bot-mailbox settings storage, settings route/navigation wiring, settings screen behavior, and legacy general-settings entry plumbing
- TaskMail-internal `detekt`
- TaskMail-internal `lintDebug`
- Thunderbird and K-9 `fossDebug` app assemble

Exact commands and scope are recorded in:

- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`

### Manual / Debug Checklist Assets

The repository documentation also contains targeted manual coverage guidance in:

- `docs/TASKMAIL-DEBUG-VALIDATION.md`

That checklist already covers:

- timeline attachment open/save actions
- attachment-only reply behavior
- non-TaskMail mail filtering
- grouping and scrolling checks

### Still Not Fully Revalidated in the Current 2026-03-19 Status Update

The following are still not fully re-run or closed in the current update:

- full repo-wide build / assemble tasks
- fresh device validation for the new raster inline image preview path, plus any future true SVG detail preview beyond
  the current placeholder-card rendering path
- broader live-mailbox/device validation for the controlled rich-text detail renderer beyond the targeted `thread_071`
  inline-image placeholder pass and the controlled fresh-build preview checks
- refresh/live-update device closeout, especially refresh-warning visibility, confirmed post-sync workspace/session
  summary freshness, and detail auto-refresh while editing
- dual-mailbox coexistence device smoke with both user and bot mailboxes configured on Android
- `[SYNC]` bootstrap smoke proving it stays outside TaskMail session/detail projection on Android
- guided new-thread and bootstrap discovery device smoke, especially formal-host entry, selected sender-account routing,
  `[SYNC]` project-list rendering plus repo prefill, and raw outgoing first-task body verification
- full device smoke validation checklist
- `connectedAndroidTest`
- full repo-wide `lint`
- full repo-wide `detekt`
- full clean `spotlessCheck`

Therefore this file still does **not** newly certify that the current repository passes all quality gates right now.

## Recommended Interpretation of Current State

For documentation and planning purposes, the safest current interpretation is:

- **Read path**: implemented in repository
- **Dual-mailbox read preference**: implemented in repository, with legacy single-mailbox fallback still preserved
- **Dual-mailbox reply target**: implemented in repository through TaskMail-specific bot-mailbox destination resolution
- **Formal host entry**: implemented in repository
- **Drawer entry**: implemented in repository
- **Guided new-thread MVP**: implemented in repository with focused automated coverage, a closed single-account
  success-path device smoke, and a later formal-host `New task` recent-tasks cold-start review pass; multi-account
  sender-selection on-device coverage is still open
- **Phase 2 direct `new task`**: implemented in repository, and the current `new task` slice now has live evidence for
  accepted direct ingress, fallback-to-mail, and hard rejection with draft retention; later status/result delivery
  remains mail-based today, while the later Phase 5 guarded detail slice now allows canonical-target
  `current-session plain reply` and `current-session /status` to attempt a non-default direct lane under shared
  planning-layer contract gating; quick answer, structured reply, paused `/resume`, attachment continuation, and
  current-protocol authority/default promotion remain outside that guarded slice
- **Phase 4 `new task` durable direct evidence**: implemented in repository through machine-readable
  `TaskMailDirectSendEvidence` plus per-sender-account local record persistence and rehydration; the first Android/PC
  three-scenario matrix readout is now documented in the shared Phase 4 artifacts, and the formal `New task` screen
  now has a stable review surface with live-device proof after screen reload and recent-tasks cold start on the formal
  host；same-run Android / PC summary parity 现在已有 `thread_083`、`thread_093`、`thread_094` 与 fresh
  relay-reprovisioned direct sample `thread_095` 这些正向行；后续 formal-host `thread_097` 又生成了
  `taskmail_daily_closeout_bundle.json`，并把 same-run bind 提升到与 PC canonical outcome 的 strong
  `transport_message_id` 对齐；再之后的 `thread_098` 则在安装当前 formal-host build 后进一步闭环了
  `request_id`-first bind。当前剩余工作已不再是 narrow evidence blocker，而是基于现有 rollback / mismatch guardrails
  做显式 `new_task` direct-default review
- **Phase 5 `reply` / `/status` guarded durable evidence**: 仓库实现已落地，并已在正式 host 上拿到首组 live/manual closeout 样本 `thread_019` 与 `thread_020`；当前安装 build 已有正向设备证据表明 latest post-creation direct evidence card 能跨 detail reload 与 formal-host desktop-launch cold start 还原，`/status` 可在当前 fallback gate 下收敛到 canonical `[STATUS]`，plain reply 可在 failed thread 上经 fresh recovery run 收敛到 canonical `[DONE]`；但当前 live bundles 仍只有 weak `last_summary` bind，因为 Android fallback `TaskMailSessionActionSendRecord` 还缺 `requestId` / `transportMessageId`，PC 当前 post-creation fallback canonical artifacts 也还没有保留 `action_type` / `target_session_identity` / action-specific ingress anchors
- **TaskMail bot-mailbox runtime settings**: implemented in repository with focused automated coverage and a closed live-device save-then-send smoke from Android general settings
- **Bootstrap discovery / repo-path assist**: implemented in repository with focused automated coverage, but current manual/device smoke for `Project list -> Use this repo -> Repo:` prefill is still open
- **Session detail interaction**: implemented in repository, including attachments and structured multi-question replies
- **Session detail rich-text source/model boundary**: implemented in repository with optional `htmlBody` preservation
  and richer `TaskMessageBody` shape, plus narrow automated validation
- **Session detail rich-text/HTML rendering**: partially implemented in repository through controlled HTML projection and
  a minimal detail renderer for supported block types, Batch 5 preview/test corpus, a focused live-device
  inline-image placeholder pass on `thread_071`, fresh-build controlled device preview checks for static SVG
  placeholder rendering plus unmatched-image safe text fallback, and a later repository/UI follow-up that now enables
  raster inline-image preview plus attachment deduplication; fresh device validation for that new raster path and any
  broader live-mailbox/device validation are still open
- **Multi-question send gating**: implemented locally for required-answer completeness, canonical choice values, unknown
  `question_id` rejection, and legacy two-line compatibility
- **Lifecycle / progress timestamp compatibility**: implemented internally in parser/repository models for
  `lifecycle`, `last_active_at`, and `last_progress_at`
- **Refresh and live update**: implemented in repository on workspace/detail, including focused automated coverage for
  foreground-only account-scoped refresh while visible, later live closeout for post-sync workspace/detail summary
  freshness, and a later smoke pass for draft / attachment retention while editing; refresh-warning failure behavior and
  broader passive foreground-refresh smoke are currently deferred
- **Paused / multi-question protocol compatibility**: implemented at the Android repository level
- **Dedicated UI for full protocol superset**: not implemented
- **Full validation / release confidence**: not fully established

This is more accurate than either extreme:

- “still only a Phase 1.5 debug-only prototype”
- “fully finished and completely validated final feature”

## Refactor Closeout Still Open

The 2026-03-20 split-architecture refactor pass has materially changed the Android TaskMail baseline.

At this point, the repository already contains:

- a local-first `UnifiedMessage` cache path on the Android read side
- explicit ingress / parser / sync seams instead of one collapsed mail-read path
- local-first workspace and session-detail loading behavior
- incremental `UnifiedMessage` upsert plus incremental-first session-detail snapshot rebuild with full-rebuild fallback
- first send-side transport seams for reply, new-task, and project-sync request flows while email remains the only
  production transport

However, several Android-side closeout items are still open before this refactor should be described as fully settled:

1. Workspace summary projection is not yet closed to the same degree as detail snapshot projection.
   Detail now has a dedicated local snapshot repository and incremental-first rebuild behavior, but workspace summary
   still needs a final decision on whether it remains cached-message aggregation or moves to a dedicated incremental
   summary snapshot.
2. Steady-state delta fetch is still not a strict cursor-native `fetchSince()` implementation.
   The current steady-state sync window is intentionally narrow, but the ingress path still behaves more like a recent
   mailbox-window scan plus thread expansion than a true minimal delta query.
3. Foreground auto-refresh policy is still an explicit product/performance choice.
   The current foreground-only 10-second polling path remains present in repository behavior; whether it should stay,
   be widened, or be removed in favor of local-store change observation plus manual refresh is still open.
4. Validation closeout remains incomplete.
   Focused TaskMail-internal tests and app debug assembles have been re-run during the refactor, but repo-wide quality
   gates, `connectedAndroidTest`, and fresh device smoke for the migrated local-first behavior are still open.
5. Relay/VPS work is now the next major engineering stream, but it has not replaced the Android mail path.
   Email remains the only Android production transport, and Android should not present relay/VPS as complete until a
   separate transport-integration slice lands and is validated.

## Remaining Documentation / Planning Gaps

The main remaining gaps are no longer about whether the feature exists in the repository. They are about validation closeout and future scope choices.

### 1. Validation Evidence Still Needs Widening

The repository now has a real feature surface, but full device and repo-wide quality evidence is still missing.

### 2. Protocol Superset vs Explicit Android UI Still Needs Deliberate Scope Control

PC-side protocol currently includes capabilities such as:

- `Permission`
- `/pause`
- `/end`
- `/new`
- `/sessions`
- `/rerun`
- `/kill`

Android should stay protocol-compatible, but whether any of these become explicit UI controls is a separate product / validation decision.

### 3. Future Doc Updates Should Stay Cross-Repo Aligned

If the PC-side control plane changes, update:

1. `mail_based_task_manager/docs/current/*`
2. `docs/TASKMAIL-MAIL-RULES.md`
3. `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
4. `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`

in that order.

Planning-only changes in `mail_based_task_manager/docs/plans/coding_backlog.md` should not be copied into Android
current-state docs until they are promoted into PC-side `docs/current/*` or real implementation evidence.

## Cross-Repo Current Facts Still Ahead of Android

The main currently visible PC-side facts that Android should now acknowledge as current truth are:

- lifecycle split between `active` and `ended`, including `/end` and reactivation through `/resume`
- persisted `last_active_at` and `last_progress_at`
- active working-set cap `4`, including auto-ending the least recently active non-running session when required
- observe-facing health visibility such as `normal`, `stale`, `suspected_stuck`, and `orphaned`
- revised live-mail retention semantics where older progress mail can be replaced while action-required and receipt mail
  are retained

Android should no longer describe these items as planning-only watchpoints.

At the same time, Android should not overstate its own parity yet:

- current repository now preserves lifecycle / progress timestamps internally, but observe-facing health and retention
  semantics still are not surfaced through Android models/UI
- dedicated `/end` or lifecycle/health UI is still absent
- validation evidence in this repository still does not claim end-to-end Android coverage for these newer lifecycle /
  progress / health-facing facts

## Current Next Step

With Phase 3 now frozen, the Phase 4 `new task` parity baseline already narrowed to guarded review, and the first
Android-side Phase 5 `reply` / `/status` guarded lane plus durable-evidence slice now landed, the next engineering
focus should be:

- keep the current `new_task` reading in observation mode rather than re-opening the already-closed narrow bind blocker,
  unless fresh same-run evidence regresses from the current `request_id`-first readout
- treat Phase 5 `Batch D` as strong-bind closeout work now, not as another sample-collection pass
- do not rerun `thread_019` / `thread_020` unless the installed build or fallback behavior changes
- close the remaining shared-artifact gap on the Android side by adding `requestId` and/or `transportMessageId` to fallback `TaskMailSessionActionSendRecord`
- close the remaining shared-artifact gap on the PC side by preserving post-creation fallback `action_type`, `target_session_identity`, action-specific `ingress_message_id`, and `terminal_mail_subject` in canonical artifacts
- after those artifacts exist, rerun the same narrow live/manual pair only to upgrade the current `last_summary` weak bind into a stronger same-run bind readout
- treat `thread_020` post-send `Unable to load session` as a narrow UI drift candidate after the strong-bind gap is closed, rather than reopening the already-closed persistence question
- preserve mail fallback, quick answer, structured `Answers:`, paused `/resume`, attachment continuation, and any
  targeted-session variant outside this guarded Phase 5 scope
- keep the formal-host cold-start validation path as launcher-first/manual-`Tasks` smoke, because debug deep links and
  direct adb activity starts do not exercise the same host boundary

## 2026-03-22 Phase 3 Validation Note

在 `2026-03-22` 的后续验证里，Phase 3 有两条重要结论被进一步坐实：

1. `detail` direct inbound 现在不仅有先前的 live smoke 记录，也有共享 Phase 3 fixture package 对齐下的 Android 可执行证据。
   当前 Android 测试已能在本机读取相邻 PC 工作区导出的 `phase3_direct_inbound_v1` fixtures，并重新跑通 fixture contract
   与 direct observer 用例，锁定 `session_snapshot/session_delta`、canonical `workspace_id` 复用、以及 gap 后的
   `detail_refresh` 重订阅语义。
2. live smoke 里看到的 duplicate pending question，目前更应读取为 source `[QUESTION]` mail / extractor 输入重复，
   而不是已经证实的 `timeline merge + business_event_key reconciliation` 回归。
   Android 侧现已新增 parser 与 repository 回归测试，确认当原始 QUESTION mail 自己重复携带同一个
   `TASK-QUESTION` capsule 时，即使**没有 direct overlay 参与**，detail 仍会原样显示重复 pending question。

因此，当前最稳妥的状态解读是：

- Phase 3 `detail` auto-refresh closeout 仍然成立
- direct websocket 合同对齐现在有更强的自动化证据
- 但“真实设备上 direct websocket update 明确早于 durable mail sync 驱动 UI”这条 live 优先级证据仍可继续补抓
- duplicate pending question 目前应作为独立的 source-mail / extractor 输入问题继续处理，而不是先把它读成 Phase 3 merge 回归

## 2026-03-22 Phase 3 Freeze Note

在同日后续的跨仓库收口里，Phase 3 的冻结边界进一步明确为：

- duplicate pending question / duplicate answer line 现象现已按 PC 侧 source `[QUESTION]` 输入问题处理，并已在上游修正；
  Android 端当前不再把它视为 Phase 3 本地实现 blocker
- `detail` 编辑态下 refresh 期间的 draft / attachment 保持已补过烟测，不再作为 Phase 3 收尾阻塞项
- refresh-warning failure 可见性、更宽的 passive foreground-refresh smoke，以及 direct-vs-mail ordering 的更强
  live 证据都明确后延，不再阻塞当前阶段冻结
- 因此，当前 Android 侧决策是先冻结 Phase 3，在现有 mail fallback 仍保留的前提下继续推进 Phase 4
