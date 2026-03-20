# TaskMail Android Current Status

This document is the current truth source for the Android TaskMail feature in this repository.

If this file conflicts with older phase-planning documents, treat this file as the current baseline and update the phase documents accordingly.

## Date

- Last updated: 2026-03-20

## Scope of This Status

This file is intended to answer only one question:

> What is currently present in the Android TaskMail feature, and what has or has not been validated?

It does not try to restate long-term planning in full.

Current authority remains in the current-status / validation-ledger / mail-rules document set.

Historical planning and implementation-reference material remains under:

- `docs/taskmail/planning/README.md`
- `docs/taskmail/planning/android/taskmail-dual-mailbox-android-adjustments-v0.1.md`
- `docs/taskmail/planning/android/taskmail-refresh-live-update-plan-v0.1.md`
- `docs/taskmail/planning/android/taskmail-foreground-scoped-live-refresh-plan-v0.1.md`
- `docs/taskmail/planning/android/taskmail-guided-new-thread-mvp-implementation-checklist-v0.1.md`
- `docs/taskmail/planning/android/taskmail-guided-new-thread-mvp-ui-spec-v0.1.md`
- `docs/taskmail/planning/android/taskmail-guided-new-thread-mvp-implementation-map-v0.1.md`
- `docs/taskmail/planning/android/taskmail-next-session-handoff-2026-03-20-relay-vps-bootstrap.md`

Older phase documents remain historical/reference only:

- `docs/TASKMAIL-ANDROID-PHASE2.md`
- `docs/TASKMAIL-ANDROID-PHASE3.md`

Protocol authority remains:

- `docs/TASKMAIL-MAIL-RULES.md`

Validation ledger remains:

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
  - a 2026-03-17 manual device follow-up confirmed workspace pull-to-refresh without blanking loaded content and showed the latest local outgoing after reply, but an offline rerun did not surface a refresh warning and no backend-fed summary update was confirmed during that observation window
  - a later 2026-03-17 guided new-thread device follow-up first closed the explicit failure path (`TaskMail bot mailbox is not configured.` with draft retention), then, after reinstalling a debug build with a non-empty bot-mailbox default, confirmed canonical first-task delivery to the bot mailbox plus mailbox-side `[ACCEPTED]`, `[RUNNING]`, and `[DONE]` replies on `thread_054`
  - a 2026-03-18 device follow-up then confirmed `General settings -> TaskMail bot mailbox -> save -> return to TaskMail -> resend without reinstall/restart` on the latest Thunderbird debug APK
  - the same guided new-thread follow-up still leaves multi-account sender selection on device, formal-host entry coverage for the first-task screen, and all bootstrap discovery manual/device smoke open
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

Read-only inspection and 2026-03-17 follow-up work found:

- `TaskMailRoute.NewTask`
- `TaskNewTaskScreen`
- `TaskNewTaskViewModel`
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
- guided new-thread sender-account resolution, canonical first-task serialization, and explicit bot-mailbox first-task send failure surfacing
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
- **Guided new-thread MVP**: implemented in repository with focused automated coverage and a closed single-account success-path device smoke, but formal-host entry and multi-account on-device coverage are still open
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
  foreground-only account-scoped refresh while visible and a clean current `:feature:taskmail:internal:testDebugUnitTest`
  rerun; refresh-warning failure behavior, confirmed post-sync summary freshness, and detail auto-refresh while editing
  still remain open on device
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

With the P0 document-alignment slice, P1 parser/model compatibility slice, and P2 multi-question send-validation
slice now closed, the next engineering focus should be:

- real-mailbox/device closeout for the current send path, starting with proof that Android replies target the configured
  bot mailbox on live mail
- foreground-refresh device/manual closeout while the user stays on workspace/detail, especially passive arrival
  without pull gesture, post-sync summary freshness, detail draft/attachment preservation, and confirming the loop stops
  when those screens leave the foreground
- bootstrap discovery device/manual smoke from the formal TaskMail workspace host, especially `Project list` open, `[SYNC]` request/refresh, on-device project-list rendering, and `Use this repo -> Repo:` prefill handoff
- guided new-thread device/manual smoke closeout that still remains after `thread_054`, especially sender-account resolution across multiple accounts, formal-host entry for the first-task screen, and raw outgoing first-task body verification
- narrow validation guardrail closeout for the already-implemented refresh/live-update and dual-mailbox slices, especially refresh-warning visibility, post-sync summary freshness, detail draft/attachment preservation, dual-mailbox coexistence, and `[SYNC]` exclusion smoke
- deciding whether any newer lifecycle/health-facing fields remain internal-only or now need minimal UI surfacing
- preserving protocol compatibility and dual-mailbox send-target rules without over-promising dedicated UI for the full protocol superset
