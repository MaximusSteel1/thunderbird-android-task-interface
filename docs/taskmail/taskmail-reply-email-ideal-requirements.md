# TaskMail Reply Email Ideal Requirements

## Status

Draft target specification derived from the current `TaskMail` Android implementation and related parsing logic.

This document describes what a `TaskMail` reply email should ideally contain on the wire.
It is intentionally stricter than the current implementation where that improves session stability,
reply safety, and downstream parsing reliability.

## Purpose

This document defines the target shape of a `TaskMail` reply email for:

- reply message generation
- reply validation in the Android UI
- future backend and client interoperability
- session continuity across physical mail threads

It is not a description of current behavior only.
When current code differs from this document, treat this document as the desired product target
and record the mismatch as implementation debt.

## Scope

This document applies to user-originated `TaskMail` replies, including:

- free-text replies
- quick-answer replies
- structured-answer replies
- `/status` replies

It does not define the format of system-generated status or question emails.

## Ideal Reply Email Shape

### 1. Subject

A `TaskMail` reply email should preserve task identity in the subject line.

Required:

- The wire subject should use the canonical reply prefix `Re:`.
- The subject should preserve the original `TaskMail` task title text.
- The subject should preserve or synthesize the stable logical session token `[S:<session_id>]` whenever a session ID is known.
- The subject should preserve the backend token when known, e.g. `[CX]` or `[OC]`.
- The subject should preserve an existing `TaskMail` status token from the anchor subject when one is already part of the conversation, e.g. `[QUESTION]`, `[DONE]`, or `[RUNNING]`.

Constraints:

- Reply subject rewriting must not drop `TaskMail` identity tokens that are needed for later session detection.
- The canonical stored subject should not depend on localized reply prefixes such as `AW:`, `FW:`, `Fwd:`, or Chinese reply markers.

Preferred examples:

```text
Re: [QUESTION] [S:session-42] [CX] Analyze floor_shear
Re: [DONE] [S:thread_026] [OC] Timeline test
```

### 2. Threading Headers

A `TaskMail` reply email should be a real mail-thread reply, not a loosely related new message.

Required:

- `In-Reply-To` must point to the selected reply anchor message ID.
- `References` must include the prior reference chain plus the selected reply anchor message ID.
- The reply anchor should be the newest available message that belongs to the same logical `TaskMail` session and can still be loaded safely for reply.

Constraints:

- Reply must not be enabled when the logical session spans multiple accounts.
- Reply must not be enabled when the anchor message cannot be resolved to a stable local message reference.
- The chosen anchor should come from the newest logical-session message, not merely the newest physical thread copy.

### 3. Addressing

Required:

- The sender identity should be the identity that originally received the anchor message when that can be determined.
- The primary reply target should follow normal reply resolution rules: `Reply-To`, then `List-Post`, then `From`.
- `TaskMail` reply should behave as a normal reply, not as reply-all by default.

Preferred:

- `Cc` should only be included when the resolved reply target explicitly requires it.
- `Bcc` should be empty unless there is an explicit future product rule that says otherwise.

### 4. Body Format

Required:

- The reply body should be sent as `text/plain`.
- The body should contain only the user payload for this reply.
- The body should not include the quoted original message body by default.
- The body should not include machine-readable `TaskMail` state or question capsules.

Rationale:

- Reply-like messages are intentionally treated as user messages by the current detector.
- Quoted metadata and embedded capsules increase parsing noise and make timeline extraction less reliable.

### 5. Body Content by Reply Kind

#### 5.1 Free-Text Reply

Required:

- The body may contain arbitrary plain text entered by the user.
- Leading and internal whitespace should be preserved unless the transport requires normalization.

Allowed:

- Attachments may be included.
- A free-text reply may be attachment-only if product requirements accept files as the complete response.

#### 5.2 Quick-Answer Reply

Required:

- The body should be the exact selected choice value.
- Quick-answer reply should only be available when exactly one pending question is active for the session.

Allowed:

- Attachments may be included if the product treats them as supplementary context.

#### 5.3 Structured-Answer Reply

Structured reply is the preferred format when more than one pending question is active.

Required:

- The body should contain at least one structured answer.
- Each answer should be expressed in a stable machine-friendly form.
- The preferred one-line form is:

```text
question_id: value
```

- The preferred multi-answer template starts with:

```text
Answers:
```

Preferred example:

```text
Answers:
phase2_entry_position: below
phase2_icon_strings: reuse
```

Allowed compatibility forms:

- `question_id: <known_question_id>` followed by the answer on the next non-blank line
- a full-width colon character as a separator when needed by input method behavior

Constraints:

- Attachments may supplement a structured reply, but should not replace structured answers entirely.
- When structured reply is required, attachment-only submission should be treated as incomplete.

#### 5.4 Status Query Reply

Required:

- The body must be exactly `/status`.
- No extra prose should be added before or after `/status`.

Constraints:

- `/status` reply must not include attachments.

### 6. Attachments

Required:

- Reply attachments should be sent as ordinary outgoing attachments.
- Reply attachments should not be converted into quoted inline artifacts.

Allowed:

- Attachments are allowed for free-text, quick-answer, and structured-answer replies.

Not allowed:

- Attachments on `/status` replies

### 7. Content That Should Not Be Included

A `TaskMail` reply email should ideally avoid all of the following unless a future protocol explicitly requires them:

- quoted original message blocks
- `On ... wrote:` blocks
- `-----Original Message-----` blocks
- localized original-message quote blocks
- `---TASK-STATE-BEGIN--- ... ---TASK-STATE-END---`
- `---TASK-QUESTION-BEGIN--- ... ---TASK-QUESTION-END---`
- copied structured metadata such as `Session ID`, `Thread ID`, `Repo`, `Workdir`, `Backend`, or `Status`

## Ideal Validation Rules

The Android client should apply these validation rules before sending:

- Reply is available only when a valid reply context exists.
- Free-text reply can send when body is non-blank or at least one attachment is selected.
- Quick-answer reply can send only when exactly one pending-question choice set is active and the selected choice belongs to that set.
- Structured reply can send only when at least one valid structured answer is present.
- `/status` can send only when no attachments are selected.
- Sending should be blocked when the session is cross-account and no single safe reply identity exists.

## Current Code Alignment

The current implementation already aligns with part of this target:

- replies are built as real mail-thread replies with `In-Reply-To` and `References`
- `TaskMail` reply sends plain-text bodies
- quoted text is disabled for `TaskMail` replies
- `/status` is modeled as a dedicated reply kind
- reply context is intentionally anchored to the newest logical-session message

## Known Gaps Versus This Ideal Spec

Based on current code, the following gaps exist:

- Reply subject generation currently strips only the German `AW:` prefix before adding `Re:`, even though parsing accepts additional reply-like prefixes.
- Reply subject generation currently reuses the source subject and does not synthesize missing session or backend tokens when the anchor subject is incomplete.
- Structured reply validation currently allows attachment-only send in some cases, while this ideal spec requires at least one structured answer.
- The current reply request context does not carry canonical session metadata such as `session_id`, backend token, or canonical subject, which limits subject normalization.

## Suggested Follow-Up Work

To move the implementation toward this target, the client will likely need:

- a canonical `TaskMail` reply-subject builder that understands session ID, backend, and anchor subject
- stricter structured-reply validation in the UI layer
- a clearer separation between canonical reply metadata and raw anchor-message data

## Source Basis

This target was derived from the current behavior and contracts in:

- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/data/LegacyTaskMailMimeMessageFactory.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/data/DefaultTaskMailRepository.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/parser/TaskMailSubjectParser.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/domain/parser/TaskMailMessageDetector.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/TaskSessionDetailUiState.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/ui/detail/TaskSessionDetailViewModel.kt`
- `feature/taskmail/internal/src/main/kotlin/net/thunderbird/feature/taskmail/internal/data/LegacyTaskMailBodyExtractor.kt`
- `legacy/core/src/main/java/com/fsck/k9/helper/ReplyToParser.java`
- `legacy/core/src/main/java/com/fsck/k9/helper/IdentityHelper.kt`
