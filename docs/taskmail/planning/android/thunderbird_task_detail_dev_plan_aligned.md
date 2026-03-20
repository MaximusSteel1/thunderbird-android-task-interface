# Thunderbird Android Task Detail Dev Plan (Aligned with Current Protocols)

## 1. Document purpose

This document revises the earlier `thunderbird_task_detail_dev_plan.md` and aligns it with the current protocol set in:

- `mail_protocol.md`
- `task_view_mail_parsing_rules.md`
- `android_reply_method_rules.md`
- `multi_question_protocol.md`
- `multimedia_mail_protocol.md`
- `session_scheduler_status.md`
- `README.md`

This is an **Android implementation plan**, not a new protocol proposal.
When this document conflicts with the current protocol docs, the current protocol docs win.

Its target remains unchanged:

**Add a task-focused detail page to Thunderbird Android that looks like a chat-style timeline, but is fundamentally a mail-protocol-driven session reader with lightweight local projection and basic reply/control abilities.**

---

## 2. Current boundaries and implementation stance

### 2.1 What this feature is

This feature is:

- a task/session detail reader inside Thunderbird Android
- a mail-driven task timeline view
- a lightweight local projection over mails actually seen on this device
- a reply/control surface that serializes back into the existing mail protocol

This feature is not:

- a second non-mail control protocol
- a fully independent chat product
- a complete task platform
- a full local mail archive mirror
- a token-streaming session UI

### 2.2 Core implementation choices

#### Choice A: chat-like appearance, but not a chat product

The page uses a vertical single-column timeline and chat-like bubbles because that is the easiest way to read task progression, questions, replies, and results on mobile.

But the semantic model is still:

- append-only mail events
- replacement-style progress in live mailbox
- explicit lifecycle / receipt / waiting states
- plain-text reply serialization back to protocol mail

#### Choice B: session-first parsing, thread-shaped UI

The page may still be called **Task Detail** and may visually resemble one mail thread.

However, the parsing and projection model must be **session-first**.

That means:

- the canonical logical unit is `session`
- mail conversation/thread shape is only the physical carrier
- `/new` inside an existing reply chain is a **logical session break**
- the UI may still show one coherent detail page, but repository/cache/parser must not merge different sessions just because they look like one mail thread

#### Choice C: lightweight local timeline cache, not full archive

The Android side only stores:

- mails actually seen on this device
- parsed session/thread summaries
- timeline projection items
- structured run-result / question / attachment metadata

It does not try to reconstruct a perfect full history from live mailbox.

This is required because:

- progress mails are replaceable in live mailbox
- old progress items may be deleted remotely
- complete facts belong to archive/runtime side, not to the Android live projection

#### Choice D: native page shell, local HTML islands

The whole Task Detail page must not become one large HTML page.

Correct layering:

- outer page shell: native Compose
- timeline list: native Compose
- message body: controlled HTML or plain text block
- composer / reply actions: native Compose

#### Choice E: protocol alignment beats product polish

The first version should remain restrained.
Do not add features that force Android to become smarter than the protocol.

Priority order:

1. parse correctly
2. project correctly
3. reply correctly
4. render cleanly
5. only then add richer UX

---

## 3. Protocol authority and hard constraints

### 3.1 Authority order

For this feature, protocol authority is:

1. current protocol docs in `docs/current/*`
2. current `README.md` / state description
3. plans
4. platform docs
5. archived docs

So this Android plan is subordinate to current protocol behavior.

### 3.2 Canonical mail/session rules the Android side must obey

The Android implementation must not violate these rules:

- canonical parsing unit is `session`
- session matching priority is:
  1. `In-Reply-To`
  2. `References`
  3. state capsule
  4. `[S:session_id]` in subject
- `/new` inside an existing reply chain creates a new logical session
- `[SYNC]` does not belong to any task session projection
- progress mail is replaceable
- question / paused / receipt mail is retained
- Android replies must always have `text/plain`
- `text/html` may exist as mirror presentation only
- Android must not rely on HTML as the only fact source

### 3.3 Current user-visible mail tags

Current visible mail tags remain:

- `ACCEPTED`
- `RUNNING`
- `STATUS`
- `QUESTION`
- `PAUSED`
- `DONE`
- `FAILED`
- `KILLED`
- `SYNC`

### 3.4 Tag classes

#### Replaceable progress

- `ACCEPTED`
- `RUNNING`
- `STATUS`

Semantics:

- the live mailbox may keep only the latest progress mail
- Android may keep previously seen progress projection locally
- locally retained old progress should be marked as archived-local projection, not treated as durable canonical mailbox history

#### Action-required

- `QUESTION`
- `PAUSED`

Semantics:

- retained in live mailbox
- user action is expected
- may affect reply mode and composer behavior directly

#### Durable receipt

- `DONE`
- `FAILED`
- `KILLED`

Semantics:

- retained in live mailbox
- represent explicit run outcome
- must remain visible in header/summary projection

### 3.5 State model must be split into three axes

The Android page must not collapse everything into one status field.

Use three separate axes:

#### A. Lifecycle axis

- `ACTIVE`
- `ENDED`

This is thread/session lifecycle.
It is not a bubble tag.

#### B. Run / receipt axis

- `ACCEPTED`
- `RUNNING`
- `STATUS`
- `DONE`
- `FAILED`
- `KILLED`

This captures current system progress or terminal run result.

#### C. Waiting / interaction axis

- `QUESTION`
- `PAUSED`
- optional local waiting projections

This captures whether the thread/session is waiting for user input or paused in the control plane.

### 3.6 Important negative rules

The Android implementation must not:

- treat `ENDED` as a message status tag
- redefine `DONE` as “message render finished”
- persist local `QUEUED` as if it were protocol mail
- merge `[SYNC]` into task timeline state
- continue sessions by subject similarity alone
- parse task state by scraping human HTML paragraphs when capsule/structured blocks exist

---

## 4. Product shape after alignment

### 4.1 What the page should feel like

The page should feel like a clean, low-friction task conversation view.

The page should not feel like:

- generic email reading
- a full interactive agent product
- a log console
- a web app embedded into mail

### 4.2 Page layout

The page still uses three vertical zones.

#### Header

Shows session/thread summary:

- normalized task title
- thread id
- session id
- optional task id
- backend kind / transport if available
- lifecycle: `ACTIVE` / `ENDED`
- current waiting state if any: `QUESTION` / `PAUSED`
- last receipt: `DONE` / `FAILED` / `KILLED`
- updated time
- optional health if already available from projection later

#### Timeline

Single-column timeline with chat-like items.

Allowed visual item families:

- mail bubble item
- local system event item
- day divider item
- question set item (can still render as bubble-backed card)
- artifact summary item (may still be attached to a bubble in v1)

#### Composer

A mode-aware reply bar.
Not a plain free-text bar only.

At minimum it must support mode switching implied by protocol state.

### 4.3 What the page does not do in v1

The page still does not do:

- token-level streaming
- patching the same bubble body repeatedly
- rich collaborative editing
- hidden implicit session switching
- full attachment/file browser
- full archive reconstruction

A new mail still creates a new timeline item.

---

## 5. Reply/composer model must be upgraded

The earlier plan only supported a few direct actions.
That is no longer enough.

### 5.1 Canonical reply modes

The composer state should be built around these modes:

- `continue_session`
- `continue_target_session`
- `answer_single_question`
- `answer_multi_question`
- `resume_session`
- `command`
- `new_session_from_reply`

The UI can hide some complexity, but the ViewModel/domain model must preserve it.

### 5.2 State-aware behavior

#### Normal continuation states

For `accepted`, `running`, `done`, `failed`, `killed`:

The composer may offer:

- continue reply
- `/rerun`
- `/new`
- same-workspace explicit targeting later

For `failed` / `killed`, do not hide `/rerun` and `/new`.

#### Waiting for answers

- single-question: natural-language reply is allowed
- multi-question: must serialize into structured `Answers:` block
- attachment-only reply may supplement input, but must not replace structured answers when required

#### Paused

- plain reply must not implicitly resume
- `/resume` is required
- if paused with pending questions, `/resume` must be combined with proper answer flow

### 5.3 Sending contract

Android outgoing replies must:

- be real reply mails
- preserve `In-Reply-To`
- preserve `References`
- preserve subject including `[S:session_id]` when present
- always include `text/plain`
- optionally include mirrored `text/html`
- keep first screen to the newly added content
- place quoted/original content below

### 5.4 First-version UI simplification

Even if the UI stays simple, the internal model must already support:

- free-text continuation
- `/resume`
- `/status`
- `/kill`
- `/end`
- `/new`
- structured multi-question answers
- attachment-aware reply semantics

If the UI does not expose all buttons in the first iteration, the draft model must still be able to represent them.

---

## 6. Domain architecture

Keep a 5-layer implementation shape.

### 6.1 Mail classifier

Responsibilities:

- detect whether a mail is task-related
- distinguish task flow mail from `[SYNC]`
- identify system mail / new task mail / user reply / direct kill / sync control

### 6.2 Mail parser

Responsibilities:

- parse headers
- resolve session identity using the current priority order
- detect `/new` logical boundary
- parse state capsule
- parse question capsules
- parse structured run-result blocks
- extract user `reply_delta`
- extract attachment metadata and artifact metadata
- produce canonical parsed domain packets

### 6.3 Local projection cache

Responsibilities:

- store what this device has actually seen
- deduplicate by message identity
- maintain session summary projection
- maintain timeline projection
- retain seen old progress as archived-local projection
- persist structured question/answer state and attachment/artifact metadata needed by UI

### 6.4 Repository

Responsibilities:

- provide session/thread detail summary
- provide timeline projection
- provide current composer mode / pending question state
- hide parser/cache/bridge details from UI

### 6.5 Compose UI

Responsibilities:

- render summary header and timeline
- render controlled HTML/plain-text blocks
- render question-set cards/forms when needed
- collect reply inputs
- never redefine protocol semantics on its own

---

## 7. Module and directory recommendation

```text
feature/taskmail/
  api/
    TaskMailEntry.kt
    TaskMailNavigator.kt

  domain/
    model/
      ProtocolMailTag.kt
      ThreadLifecycle.kt
      WaitingState.kt
      ReceiptStatus.kt
      UiBadgeState.kt
      RenderFormat.kt
      ReplyMode.kt
      MessageDirection.kt
      MessageRole.kt
      BackendKind.kt
      BackendTransport.kt
      AttachmentKind.kt
      ParsedTaskMail.kt
      TaskSessionSummary.kt
      TimelineItem.kt
      QuestionSet.kt
      QuestionItem.kt
      QuestionAnswer.kt
      AttachmentRef.kt
      ArtifactRef.kt
      RunResult.kt
      TaskRunPacket.kt
      TaskReplyDraft.kt

    parser/
      TaskMailClassifier.kt
      TaskMailParser.kt
      StateCapsuleParser.kt
      QuestionCapsuleParser.kt
      RunResultParser.kt
      ReplyRoutingResolver.kt
      ReplyDeltaExtractor.kt
      AttachmentMetadataParser.kt

    usecase/
      ClassifyAndCacheTaskMailUseCase.kt
      ObserveTaskSessionUseCase.kt
      ObserveTaskTimelineUseCase.kt
      BuildReplyDraftUseCase.kt
      SendTaskReplyUseCase.kt
      ResolveComposerModeUseCase.kt

  data/
    local/
      entity/
        TaskSessionEntity.kt
        TaskTimelineItemEntity.kt
        TaskRunResultEntity.kt
        TaskPendingQuestionEntity.kt
        TaskAttachmentEntity.kt
        TaskArtifactEntity.kt
      dao/
        TaskSessionDao.kt
        TaskTimelineDao.kt
        TaskRunResultDao.kt
        TaskPendingQuestionDao.kt
        TaskAttachmentDao.kt
        TaskArtifactDao.kt
      mapper/
        TaskMailEntityMapper.kt
      TaskTimelineLocalDataSource.kt

    repository/
      TaskTimelineRepositoryImpl.kt

    bridge/
      TaskMailSyncBridge.kt
      TaskMailSendBridge.kt

  presentation/
    detail/
      TaskDetailViewModel.kt
      TaskDetailUiState.kt
      TaskDetailAction.kt
      TaskDetailEffect.kt

    components/
      ThreadHeaderCard.kt
      TaskTimelineList.kt
      TaskBubbleItem.kt
      SystemEventItem.kt
      DayDividerItem.kt
      QuestionSetCard.kt
      ArtifactSummaryBlock.kt
      TaskComposerBar.kt
      HtmlBodyBlock.kt
      PlainTextBodyBlock.kt

    navigation/
      TaskDetailRoute.kt
      TaskDetailDestination.kt
```

### 7.1 Module principles

- parser must stay out of UI
- repository must not hold Compose-specific state
- cache stores projection, not full raw MIME mirror
- sending still goes through existing mail sending infrastructure
- HTML rendering is a view concern, not the source of truth

---

## 8. Kotlin model freeze (revised)

Below is the recommended first freeze.

### 8.1 Mail tag

```kotlin
enum class ProtocolMailTag {
    ACCEPTED,
    RUNNING,
    STATUS,
    QUESTION,
    PAUSED,
    DONE,
    FAILED,
    KILLED,
    SYNC
}
```

### 8.2 Lifecycle

```kotlin
enum class ThreadLifecycle {
    ACTIVE,
    ENDED
}
```

### 8.3 Waiting / interaction state

```kotlin
enum class WaitingState {
    NONE,
    QUESTION,
    PAUSED
}
```

### 8.4 Receipt state

```kotlin
enum class ReceiptStatus {
    DONE,
    FAILED,
    KILLED
}
```

### 8.5 Local UI-only badge

```kotlin
enum class UiBadgeState {
    QUEUED,
    LOCAL_ONLY,
    ARCHIVED_PROGRESS
}
```

### 8.6 Render format

```kotlin
enum class RenderFormat {
    HTML,
    PLAIN_TEXT
}
```

### 8.7 Reply mode

```kotlin
enum class ReplyMode {
    CONTINUE_SESSION,
    CONTINUE_TARGET_SESSION,
    ANSWER_SINGLE_QUESTION,
    ANSWER_MULTI_QUESTION,
    RESUME_SESSION,
    COMMAND,
    NEW_SESSION_FROM_REPLY
}
```

### 8.8 Direction and role

```kotlin
enum class MessageDirection {
    INCOMING,
    OUTGOING
}

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM,
    TOOL
}
```

### 8.9 Backend identity

```kotlin
enum class BackendKind {
    OPENCODE,
    CODEX,
    UNKNOWN
}

enum class BackendTransport {
    CLI,
    SDK,
    UNKNOWN
}
```

### 8.10 Question set models

```kotlin
data class QuestionItem(
    val questionId: String,
    val label: String,
    val prompt: String,
    val required: Boolean,
    val kind: String?,
    val options: List<String> = emptyList(),
    val defaultValue: String? = null
)

data class QuestionAnswer(
    val questionId: String,
    val rawText: String,
    val canonicalValue: String?
)

data class QuestionSet(
    val questionSetId: String,
    val questions: List<QuestionItem>,
    val receivedAnswers: List<QuestionAnswer> = emptyList()
)
```

### 8.11 Attachments / artifacts

```kotlin
enum class AttachmentKind {
    NORMAL,
    INLINE_IMAGE,
    EXTERNAL_DELIVERY
}

data class AttachmentRef(
    val name: String,
    val mimeType: String?,
    val sizeBytes: Long?,
    val kind: AttachmentKind,
    val localPath: String? = null,
    val cid: String? = null
)

data class ArtifactRef(
    val name: String,
    val mimeType: String?,
    val sizeBytes: Long?,
    val isInlinePreview: Boolean,
    val externalUrl: String? = null
)
```

### 8.12 Run result

```kotlin
data class RunResult(
    val changedFiles: List<String> = emptyList(),
    val testsPassed: Boolean? = null,
    val errorType: String? = null,
    val errorMessage: String? = null,
    val artifactsDir: String? = null,
    val backendTransport: BackendTransport? = null,
    val artifacts: List<ArtifactRef> = emptyList()
)
```

### 8.13 Parsed mail model

```kotlin
data class ParsedTaskMail(
    val threadId: String,
    val sessionId: String?,
    val messageId: String,
    val inReplyTo: String?,
    val references: List<String> = emptyList(),
    val createdAt: Long,
    val subject: String,
    val direction: MessageDirection,
    val role: MessageRole,
    val protocolTag: ProtocolMailTag?,
    val lifecycle: ThreadLifecycle?,
    val waitingState: WaitingState?,
    val receiptStatus: ReceiptStatus?,
    val pausedFromStatus: String?,
    val renderFormat: RenderFormat,
    val htmlBody: String?,
    val plainTextBody: String?,
    val replyDelta: String?,
    val questionSet: QuestionSet?,
    val receivedAnswers: List<QuestionAnswer> = emptyList(),
    val runResult: RunResult?,
    val inputAttachments: List<AttachmentRef> = emptyList(),
    val outputArtifacts: List<ArtifactRef> = emptyList(),
    val backendKind: BackendKind?,
    val backendTransport: BackendTransport?,
    val sourceUid: Long?,
    val isTaskSessionMail: Boolean
)
```

### 8.14 Session summary model

```kotlin
data class TaskSessionSummary(
    val threadId: String,
    val sessionId: String?,
    val taskId: String?,
    val title: String,
    val backendKind: BackendKind?,
    val backendTransport: BackendTransport?,
    val lifecycle: ThreadLifecycle,
    val waitingState: WaitingState,
    val lastReceipt: ReceiptStatus?,
    val updatedAt: Long,
    val lastProgressAt: Long?,
    val lastActiveAt: Long?
)
```

### 8.15 Timeline items

```kotlin
sealed interface TimelineItem {
    val id: String
    val createdAt: Long
}

data class MailBubbleItem(
    override val id: String,
    override val createdAt: Long,
    val messageId: String,
    val direction: MessageDirection,
    val role: MessageRole,
    val protocolTag: ProtocolMailTag?,
    val lifecycleSnapshot: ThreadLifecycle?,
    val waitingStateSnapshot: WaitingState?,
    val receiptStatus: ReceiptStatus?,
    val renderFormat: RenderFormat,
    val htmlBody: String?,
    val plainTextBody: String?,
    val questionSet: QuestionSet?,
    val attachments: List<AttachmentRef> = emptyList(),
    val artifacts: List<ArtifactRef> = emptyList(),
    val uiBadge: UiBadgeState?,
    val isVisible: Boolean = true
) : TimelineItem

data class SystemEventItem(
    override val id: String,
    override val createdAt: Long,
    val text: String
) : TimelineItem

data class DayDividerItem(
    override val id: String,
    override val createdAt: Long,
    val label: String
) : TimelineItem
```

### 8.16 Internal canonical packet

```kotlin
data class TaskRunPacket(
    val threadId: String,
    val sessionId: String?,
    val messageId: String,
    val createdAt: Long,
    val protocolTag: ProtocolMailTag?,
    val lifecycle: ThreadLifecycle?,
    val waitingState: WaitingState?,
    val receiptStatus: ReceiptStatus?,
    val pausedFromStatus: String?,
    val bodyHtml: String?,
    val bodyText: String?,
    val replyDelta: String?,
    val questionSet: QuestionSet?,
    val receivedAnswers: List<QuestionAnswer> = emptyList(),
    val inputAttachments: List<AttachmentRef> = emptyList(),
    val outputArtifacts: List<ArtifactRef> = emptyList(),
    val runResult: RunResult?,
    val backendKind: BackendKind?,
    val backendTransport: BackendTransport?
)
```

### 8.17 Outgoing draft model

```kotlin
sealed interface TaskReplyDraft {
    data class ContinueSession(
        val text: String,
        val profile: String? = null,
        val permission: String? = null,
        val timeout: String? = null,
        val mode: String? = null,
        val taskOverride: String? = null,
        val acceptance: List<String> = emptyList(),
        val attachments: List<AttachmentRef> = emptyList()
    ) : TaskReplyDraft

    data class ContinueTargetSession(
        val targetSessionId: String,
        val text: String,
        val profile: String? = null,
        val permission: String? = null,
        val timeout: String? = null,
        val mode: String? = null,
        val taskOverride: String? = null,
        val acceptance: List<String> = emptyList(),
        val attachments: List<AttachmentRef> = emptyList()
    ) : TaskReplyDraft

    data class AnswerSingleQuestion(
        val text: String,
        val attachments: List<AttachmentRef> = emptyList()
    ) : TaskReplyDraft

    data class AnswerMultiQuestion(
        val questionSetId: String,
        val answers: List<QuestionAnswer>,
        val attachments: List<AttachmentRef> = emptyList()
    ) : TaskReplyDraft

    data class ResumeSession(
        val text: String? = null,
        val answers: List<QuestionAnswer> = emptyList(),
        val attachments: List<AttachmentRef> = emptyList(),
        val permission: String? = null,
        val profile: String? = null
    ) : TaskReplyDraft

    data class Command(
        val firstLineCommand: String,
        val body: String? = null
    ) : TaskReplyDraft

    data class NewSessionFromReply(
        val text: String,
        val profile: String? = null,
        val permission: String? = null,
        val timeout: String? = null,
        val mode: String? = null,
        val acceptance: List<String> = emptyList(),
        val attachments: List<AttachmentRef> = emptyList()
    ) : TaskReplyDraft
}
```

---

## 9. Local storage design (revised)

### 9.1 Storage principle

Still do not store:

- full raw MIME mirror
- every attachment binary payload forever
- full archive reconstruction

But compared to the older plan, v1 must leave room for:

- pending question sets
- input attachment metadata
- output artifact metadata
- backend transport projection
- lifecycle/waiting split

### 9.2 Tables

Recommended minimum tables now:

- `task_session`
- `task_timeline_item`
- `task_run_result`
- `task_pending_question`
- `task_attachment`
- `task_artifact`

This is slightly larger than the original three-table plan, but avoids immediate schema churn.

### 9.3 `task_session`

Responsibilities:

- summary for header
- current lifecycle / waiting / receipt projection
- backend identity

Suggested fields:

```kotlin
@Entity(tableName = "task_session")
data class TaskSessionEntity(
    @PrimaryKey val threadId: String,
    val sessionId: String?,
    val taskId: String?,
    val title: String,
    val backendKind: String?,
    val backendTransport: String?,
    val lifecycle: String,
    val waitingState: String,
    val lastReceipt: String?,
    val lastProgressAt: Long?,
    val lastActiveAt: Long?,
    val updatedAt: Long
)
```

### 9.4 `task_timeline_item`

Responsibilities:

- renderable timeline projection
- one seen message -> one timeline entry

```kotlin
@Entity(
    tableName = "task_timeline_item",
    indices = [
        Index("threadId"),
        Index("sessionId"),
        Index("messageId", unique = true)
    ]
)
data class TaskTimelineItemEntity(
    @PrimaryKey val id: String,
    val threadId: String,
    val sessionId: String?,
    val messageId: String,
    val createdAt: Long,
    val direction: String,
    val role: String,
    val protocolTag: String?,
    val lifecycleSnapshot: String?,
    val waitingStateSnapshot: String?,
    val receiptStatus: String?,
    val renderFormat: String,
    val htmlBody: String?,
    val plainTextBody: String?,
    val uiBadgeState: String?,
    val sourceUid: Long?,
    val isVisible: Boolean
)
```

### 9.5 `task_run_result`

```kotlin
@Entity(
    tableName = "task_run_result",
    primaryKeys = ["threadId", "messageId"]
)
data class TaskRunResultEntity(
    val threadId: String,
    val messageId: String,
    val changedFilesJson: String?,
    val testsPassed: Boolean?,
    val errorType: String?,
    val errorMessage: String?,
    val artifactsDir: String?,
    val backendTransport: String?
)
```

### 9.6 `task_pending_question`

```kotlin
@Entity(
    tableName = "task_pending_question",
    primaryKeys = ["threadId", "questionSetId", "questionId"]
)
data class TaskPendingQuestionEntity(
    val threadId: String,
    val sessionId: String?,
    val questionSetId: String,
    val questionId: String,
    val label: String,
    val prompt: String,
    val required: Boolean,
    val kind: String?,
    val optionsJson: String?,
    val defaultValue: String?,
    val receivedAnswerRaw: String?,
    val receivedAnswerCanonical: String?
)
```

### 9.7 `task_attachment`

```kotlin
@Entity(
    tableName = "task_attachment",
    primaryKeys = ["threadId", "messageId", "name"]
)
data class TaskAttachmentEntity(
    val threadId: String,
    val messageId: String,
    val name: String,
    val mimeType: String?,
    val sizeBytes: Long?,
    val kind: String,
    val localPath: String?,
    val cid: String?
)
```

### 9.8 `task_artifact`

```kotlin
@Entity(
    tableName = "task_artifact",
    primaryKeys = ["threadId", "messageId", "name"]
)
data class TaskArtifactEntity(
    val threadId: String,
    val messageId: String,
    val name: String,
    val mimeType: String?,
    val sizeBytes: Long?,
    val isInlinePreview: Boolean,
    val externalUrl: String?
)
```

---

## 10. Parsing and projection rules for Android

### 10.1 Session-first projection

The repository must project by `session`, not by subject.

Implementation consequences:

- keep `threadId` because the runtime still uses hybrid thread/session storage
- keep `sessionId` because routing/control are session-sensitive
- when `/new` creates a new logical session inside an existing reply chain, projection must split there

### 10.2 User mail body extraction

For user reply mails, the parser should prefer the current protocol’s `reply_delta` extraction rules:

1. strip state capsules
2. strip question capsules
3. strip quoted history tail
4. trim whitespace

UI and repository should not treat the full quoted reply body as the new contribution.

### 10.3 System mail extraction

System mail parsing priority:

1. subject tag
2. state capsule
3. question capsules
4. structured run-result block
5. human-readable body summary

Facts come from machine-readable blocks first.
HTML or human prose is not the primary fact source.

### 10.4 Progress replacement handling

For replaceable progress mails:

- first seen progress -> insert normally
- later new progress for same session -> keep old locally if already seen
- mark old local progress as `ARCHIVED_PROGRESS`
- never claim unseen remote-old progress existed locally

### 10.5 `[SYNC]`

`[SYNC]` remains excluded from task session projection.

It may still be displayed elsewhere as a separate system tool mail, but it must not enter Task Detail state or timeline.

---

## 11. Question-set and multi-question support

The older plan treated questions too lightly. That is no longer sufficient.

### 11.1 Required model behavior

The Android side must support:

- one mail containing multiple `TASK-QUESTION` capsules
- one shared `question_set_id`
- parsing invalid mixed question-set ids as invalid group
- projecting pending questions as a structured set
- preserving `question_id -> canonical value` for answers

### 11.2 UI expectation for v1

V1 can remain restrained.
It does not need a very fancy form system.

But it must support:

- rendering a question-set block cleanly
- collecting answer per question
- serializing multi-question answers into protocol-compliant `Answers:` text
- resuming paused question sets correctly

### 11.3 Serialization constraint

For multi-question answers, Android must not send free prose and expect NLP.
It must serialize structured `Answers:` blocks.

---

## 12. Attachments, artifacts, and HTML body handling

### 12.1 V1 storage posture

V1 still does not become a full local file/archive browser.

But it must now model:

- inbound input attachments
- outbound artifact metadata
- inline preview possibility
- external delivery possibility

### 12.2 Incoming attachments

Attachment behavior must align with current protocol:

- attachment-only reply may continue a session
- if current state is waiting for answers, attachment-only reply defaults to answer path
- attachments are supplemental input, not a replacement for structured answers when required

### 12.3 Outgoing artifacts

Task detail should be able to project:

- normal attached outputs
- inline image preview outputs
- external delivery outputs for large files

The first version may show them as a compact artifact section inside or below the relevant bubble.

### 12.4 HTML body handling

HTML remains **presentation only**.

The Android page should support:

- controlled HTML subset
- inline image preview when already materialized in the outgoing mail body/attachments
- fallback to plain text when HTML render fails

Not supported in v1:

- scripts
- dynamic widgets
- remote interactive pages
- arbitrary browser-like capability

---

## 13. UI and ViewModel design (revised)

### 13.1 `TaskDetailUiState`

```kotlin
data class TaskDetailUiState(
    val isLoading: Boolean = true,
    val session: ThreadHeaderUi? = null,
    val timeline: List<TimelineItemUi> = emptyList(),
    val composer: ComposerUiState = ComposerUiState(),
    val error: String? = null
)
```

### 13.2 `ThreadHeaderUi`

```kotlin
data class ThreadHeaderUi(
    val title: String,
    val taskId: String?,
    val threadId: String,
    val sessionId: String?,
    val backendKind: BackendKind?,
    val backendTransport: BackendTransport?,
    val lifecycle: ThreadLifecycle,
    val waitingState: WaitingState,
    val lastReceipt: ReceiptStatus?,
    val updatedAt: Long,
    val lastProgressAt: Long?,
    val lastActiveAt: Long?
)
```

### 13.3 `ComposerUiState`

```kotlin
data class ComposerUiState(
    val mode: ReplyMode = ReplyMode.CONTINUE_SESSION,
    val text: String = "",
    val pendingQuestionSet: QuestionSet? = null,
    val selectedAttachments: List<AttachmentRef> = emptyList(),
    val isSending: Boolean = false,
    val canSend: Boolean = false,
    val suggestedCommand: String? = null
)
```

### 13.4 Actions

```kotlin
sealed interface TaskDetailAction {
    data object Load : TaskDetailAction
    data class ComposerTextChanged(val text: String) : TaskDetailAction
    data class ReplyModeChanged(val mode: ReplyMode) : TaskDetailAction
    data class MultiQuestionAnswerChanged(
        val questionId: String,
        val value: String
    ) : TaskDetailAction
    data object SendClicked : TaskDetailAction
    data object ResumeClicked : TaskDetailAction
    data object StatusClicked : TaskDetailAction
    data object KillClicked : TaskDetailAction
    data object EndClicked : TaskDetailAction
    data object NewClicked : TaskDetailAction
}
```

### 13.5 Effects

```kotlin
sealed interface TaskDetailEffect {
    data class ShowToast(val message: String) : TaskDetailEffect
    data object ScrollToBottom : TaskDetailEffect
    data class OpenAttachment(val attachment: AttachmentRef) : TaskDetailEffect
}
```

### 13.6 Compose tree

```text
TaskDetailRoute
  └── TaskDetailScreen
        ├── ThreadHeaderCard
        ├── TaskTimelineList
        │     ├── DayDividerItem
        │     ├── SystemEventItem
        │     └── TaskBubbleItem
        │            ├── BubbleMetaRow
        │            ├── HtmlBodyBlock / PlainTextBodyBlock
        │            ├── QuestionSetCard (when present)
        │            ├── ArtifactSummaryBlock (when present)
        │            └── BubbleBadgeRow
        └── TaskComposerBar
```

### 13.7 Rendering rules

#### Header

The header is where lifecycle and high-level waiting state belong.
Do not overload bubble badges with thread lifecycle.

#### Bubble badges

Bubble badges may show:

- protocol tag
- receipt tag
- local archived-progress badge

But `ENDED` should normally be shown in header/system event, not as if it were a mail tag.

#### Question set card

A question-set card should appear when the latest relevant system mail contains pending questions.
It can still visually belong to the corresponding bubble.

#### HTML block

Still only support a controlled subset.
Plain-text fallback remains mandatory.

---

## 14. Sending-chain design (revised)

### 14.1 Draft model

All sends should go through `TaskReplyDraft`.

### 14.2 Serialization principles

The send bridge must produce protocol-compliant mail.

General principles:

- always emit `text/plain`
- `text/html` is optional mirror, never sole fact source
- preserve reply headers correctly
- first non-empty line must be the command when mode requires command semantics
- multi-question mode must serialize `Answers:` block
- `/resume` must be explicit for paused threads

### 14.3 First-version actions that must be supported in domain/send layer

At the domain/send layer, support at least:

- normal continuation
- `/resume`
- `/status`
- `/kill`
- `/end`
- `/new`
- structured multi-question answer
- attachment-aware reply

The UI may expose these incrementally, but send logic should not be redesigned again later.

---

## 15. Repository and bridge contracts

### 15.1 Repository interface suggestion

```kotlin
interface TaskTimelineRepository {
    fun observeSession(threadId: String): Flow<TaskSessionSummary>
    fun observeTimeline(threadId: String): Flow<List<TimelineItem>>
    fun observePendingQuestionSet(threadId: String): Flow<QuestionSet?>
    suspend fun classifyAndCache(mail: RawMailSnapshot)
    suspend fun sendReply(threadId: String, draft: TaskReplyDraft)
}
```

### 15.2 `classifyAndCache` flow

1. classify mail
2. if `[SYNC]`, short-circuit away from task session pipeline
3. parse mail
4. resolve session identity and `/new` boundary
5. map into `TaskRunPacket`
6. upsert session summary
7. insert timeline item by `messageId`
8. upsert run result if present
9. upsert pending question set if present
10. upsert attachment/artifact metadata if present
11. mark prior seen progress as `ARCHIVED_PROGRESS` when newer progress arrives

### 15.3 Bridge responsibilities

#### `TaskMailSyncBridge`

Feeds newly synced mails into classification/parsing/cache.

#### `TaskMailSendBridge`

Turns `TaskReplyDraft` into actual outgoing mail headers/body/attachments while preserving existing protocol rules.

---

## 16. Development breakdown (revised)

### Phase 0: freeze aligned models and sample corpus

Deliverables:

- revised enums / domain models
- `TaskRunPacket`
- sample mails for:
  - new task
  - running/done
  - question/answer
  - paused/resume
  - `/new` in existing reply chain
  - multi-question waiting state
  - attachment-only reply
  - artifact mail with inline image / external delivery

Goal:

Freeze semantics before UI or database work.

### Phase 1: parser/session projection mainline

Deliverables:

- `TaskMailClassifier`
- `ReplyRoutingResolver`
- `ReplyDeltaExtractor`
- `TaskMailParser`
- `RunResultParser`
- `QuestionCapsuleParser`
- attachment/artifact metadata parsing

Required coverage:

1. `[OC]` / `[CX]` new task
2. normal reply continuation
3. `[QUESTION] -> ANSWER -> DONE`
4. `[RUNNING] -> [DONE]`
5. `/kill`
6. `/pause` / `/resume`
7. `/end`
8. `/new` logical split
9. `[SYNC]` exclusion
10. run-result extraction
11. multi-question extraction
12. attachment/artifact extraction

### Phase 2: local session/timeline cache

Deliverables:

- Room entities
- DAO
- mapper
- repository implementation

Goal:

Stable local projection of what this device has seen.

### Phase 3: static Compose skeleton

Deliverables:

- `TaskDetailViewModel`
- `TaskDetailUiState`
- `ThreadHeaderCard`
- `TaskTimelineList`
- `TaskComposerBar`
- `QuestionSetCard`
- `ArtifactSummaryBlock`

Goal:

Run fake data first. Do not integrate storage immediately.

### Phase 4: real data integration

Deliverables:

- `observeSession()`
- `observeTimeline()`
- `observePendingQuestionSet()`
- real UI refresh
- bottom-scroll policy

### Phase 5: sending integration

Deliverables:

- continue reply
- `/resume`
- `/status`
- `/kill`
- `/end`
- `/new`
- multi-question answer serialization

### Phase 6: entry and fallback integration

Deliverables:

- entry from mail/thread context into Task Detail
- feature flag
- fallback to regular mail reading when parsing or feature conditions fail

---

## 17. Test plan (revised)

### 17.1 Unit tests

At minimum cover:

1. classifier recognizes `[OC]` / `[CX]`
2. `[SYNC]` is excluded from task session pipeline
3. reply routing resolves `In-Reply-To`
4. `/new` creates logical session split
5. `[RUNNING]` maps to timeline item correctly
6. old seen progress becomes `ARCHIVED_PROGRESS` when newer progress arrives
7. `[QUESTION]` creates pending question projection
8. multiple question capsules must share one `question_set_id`
9. `[DONE]` maps to durable receipt
10. `/end` changes lifecycle to `ENDED`
11. `/end` does not overwrite previous receipt
12. `paused_from_status` is preserved when available
13. structured `Answers:` serialization is correct
14. run-result extraction works
15. attachment/artifact metadata extraction works

### 17.2 Integration tests

Recommended coverage:

1. real mail sync path triggers `classifyAndCache`
2. local DB writes refresh UI
3. continue reply sends successfully
4. `/resume` sends successfully
5. multi-question answer sends correctly
6. attachment-aware reply keeps protocol body valid
7. parse failure falls back to regular mail reading

### 17.3 UI tests

Verify:

- timeline order is correct
- header lifecycle / waiting / receipt are not mixed
- question-set card appears correctly
- HTML fallback to plain text works
- new message does not aggressively steal scroll position
- paused state suggests resume instead of plain reply

---

## 18. Logging and diagnostics

Keep logs restrained but structured.

Recommended categories:

1. `classified`
   - is task session mail or not
   - tag / coarse category

2. `parsed`
   - thread id
   - session id
   - message id
   - lifecycle
   - waiting state
   - receipt
   - question set id if present

3. `cached`
   - inserted / deduped
   - archived old progress or not
   - session summary updated or not

4. `rendered`
   - session loaded
   - timeline count
   - HTML fallback happened or not

5. `sent`
   - draft type
   - target thread/session
   - command first line if any

---

## 19. Acceptance criteria

V1 may be considered freezeable when all of the following are true:

1. task session mails are recognized correctly
2. session routing follows the current priority order
3. `/new` creates a new logical session projection
4. lifecycle / waiting / receipt / local UI states are kept separate
5. seen task mails are projected into a stable local timeline
6. Task Detail page renders a restrained chat-style view
7. the page can show:
   - `ACCEPTED`
   - `RUNNING`
   - `QUESTION`
   - `PAUSED`
   - `DONE`
   - `FAILED`
   - `KILLED`
8. the page can project pending question sets
9. the page can display basic attachment/artifact metadata
10. the domain/send layer can execute:
   - normal reply
   - `/resume`
   - `/status`
   - `/kill`
   - `/end`
   - `/new`
   - multi-question structured answers
11. parse failure still falls back to regular mail reading
12. the feature works without a full local archive mirror

---

## 20. Implementation advice for the next real step

Do not start with UI polishing.

The next real steps should be:

### Batch 1

- freeze aligned domain types
- freeze `TaskRunPacket`
- prepare representative mail corpus

### Batch 2

- finish classifier/parser/question/run-result/attachment parsing
- lock unit tests

### Batch 3

- build DB schema and repository
- stabilize append-only local projection

### Batch 4

- build Compose screen with fake data
- lock header/timeline/composer/question-set layout

### Batch 5

- wire repository into real screen
- wire sending chain into reply modes

### Batch 6

- integrate entry path and fallback path
- then iterate UX only after protocol correctness is stable

---

## 21. Final judgment

The route still makes sense.

The key correction is not to abandon the original idea, but to tighten it:

- keep the restrained chat-style task detail UI
- change the underlying model from thread-first to session-first
- upgrade the composer from “few buttons” to reply-mode-driven
- reserve data-model room for question sets, attachments, artifacts, and backend transport
- keep HTML as presentation, not fact source

If those changes are made, this feature remains low-risk enough to land incrementally, and it stays compatible with the current protocol family instead of drifting away from it.
