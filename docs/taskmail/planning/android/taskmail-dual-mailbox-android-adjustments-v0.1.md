# TaskMail Dual-Mailbox Android Adjustments (v0.1)

## Status

- Date: 2026-03-16
- Scope: Android-side changes required after the PC-side TaskMail control plane moved from a practical single-mailbox assumption to a recommended dual-mailbox topology
- Cross-repo alignment basis:
  - `E:\projects\mail_based_task_manager\docs/current/mail_protocol.md`
  - `E:\projects\mail_based_task_manager\docs/current/android_reply_method_rules.md`
  - `E:\projects\mail_based_task_manager\README.md`

This document is now a historical/reference note.

The dual-mailbox adjustments described here have already been folded into the Android current-status, validation-ledger,
and mail-rules documents.

## 1. Decision Summary

TaskMail should now be treated as a **dual-mailbox** conversation model:

- `user mailbox`: the mailbox the human reads and replies from on Android
- `bot mailbox`: the mailbox the runner logs into and processes

The Android impact is intentionally small.

This is **not** a protocol rewrite.

It is primarily:

- a mailbox-topology correction
- a send-target/account-selection correction
- a validation-path correction

The wire protocol for TaskMail replies, status parsing, waiting-state behavior, and session routing remains the same.

## 2. What Does Not Change on Android

The following Android-side protocol assumptions remain valid:

- reply routing still depends on real reply headers first:
  - `In-Reply-To`
  - `References`
  - state capsule
  - `[S:session_id]`
- plain-text reply remains the canonical outbound body
- `paused` still requires explicit `/resume`
- multi-question waits still require structured `Answers:`
- quick-answer UI may still show labels but must send canonical values
- `[SYNC]`, `[OC]`, and `[CX]` remain the same mail control actions
- Android still acts as a mail reply client, not a separate TaskMail control plane

Therefore:

- no new session/thread identity fields are needed
- no new Android-side parser mode is needed
- no reply serialization rewrite is needed

## 3. Required Android Changes

### 3.1 Mailbox Role Model

Android TaskMail should explicitly model two roles:

- `user mailbox`: the account currently used by the human on the device
- `bot mailbox`: a configured destination address, not a mailbox Android needs to log into

Required adjustment:

- stop assuming that the account used to send TaskMail mail is also the account the runner polls

### 3.2 Send Target for TaskMail Actions

All Android-generated TaskMail messages should now target the configured `bot mailbox` address:

- first-mail bootstrap discovery: `[SYNC]`
- new task mail: `[OC]` / `[CX]`
- reply continuation
- `/status`
- `/resume`
- structured waiting-state answers

Required adjustment:

- TaskMail compose/send code should resolve the recipient as `bot mailbox`, not “current account address” or “self”

### 3.3 Account Selection Rules

Android should send TaskMail messages **from the user mailbox account** and **to the bot mailbox address**.

Required adjustment:

- do not encourage or rely on “send to self”
- if account-selection UI exists, ensure the sending account is the user-facing mailbox
- if TaskMail currently infers the destination from the selected account, replace that assumption with explicit bot-mailbox destination configuration

### 3.4 Bootstrap / Guided New-Thread UX

The planned bootstrap flow remains valid, but its mailbox assumption changes:

- `[SYNC]` should still be offered as the low-friction first step
- the outbound `[SYNC]` mail should go to `bot mailbox`
- the resulting folder-list reply should be expected in `user mailbox`

Required adjustment:

- bootstrap/new-thread UI copy should describe the bot mailbox as the TaskMail service endpoint, not as the same account the user is reading

### 3.5 Session Reading Assumption

Android should continue reading TaskMail conversation mail from the user mailbox.

Required adjustment:

- do not add a requirement for Android to log into or display the bot mailbox
- do not change TaskMail session aggregation to expect bot-mailbox-local copies

The core UX should remain:

- user reads system replies in the user mailbox
- user replies from that mailbox
- runner consumes the corresponding inbound mail in the bot mailbox

## 4. Recommended Android Guardrails

### 4.1 Do Not Add the Bot Mailbox to the Phone

Recommended operational rule:

- the bot mailbox should not be configured as a normal mailbox account in the Android app for the user

Reason:

- dual-mailbox reliability depends on keeping human reading behavior and runner mailbox consumption separate

### 4.2 Make the Destination Explicit

If TaskMail gains dedicated settings or bootstrap UI, expose an explicit field or resolved value for:

- TaskMail service address

Preferred semantics:

- “Send TaskMail requests to this address”

Not:

- “Use the currently selected account address”

### 4.3 Preserve Reply Headers

Even in a dual-mailbox model, Android must still preserve:

- `In-Reply-To`
- `References`
- canonical TaskMail subject identity tokens

Dual-mailbox does not reduce the importance of correct reply anchoring.

## 5. Validation Impact

Dual-mailbox changes how Android-side validation should be interpreted.

### 5.1 What Should Be Treated as the Primary Smoke Path

Preferred live path:

1. Android/user mailbox sends mail to `bot mailbox`
2. PC-side runner consumes mail from `bot mailbox`
3. system reply returns to `user mailbox`
4. Android reads and replies from `user mailbox`

This should replace “same-account self-reply” as the recommended mental model for live validation.

### 5.2 What No Longer Proves the Right Thing

The following should no longer be treated as the preferred acceptance path:

- same-account send-to-self testing
- relying on whether reading a message changes `UNSEEN` state in the same mailbox

PC-side mail ingestion now uses IMAP UID increment plus local `UID/Message-ID` dedupe, so Android validation should focus on:

- correct destination address
- correct reply headers
- correct body serialization
- correct user-mailbox conversation continuity

## 6. Android Work Items

The concrete Android-side work should be limited to the following:

1. Add or confirm a TaskMail-level configuration source for `bot mailbox` destination address.
2. Update TaskMail send paths so `[SYNC]`, new-task mail, and replies target `bot mailbox`.
3. Review any account-selection logic so outbound TaskMail mail is sent from the user mailbox, not assumed-self.
4. Update bootstrap/new-thread UX copy to describe the dual-mailbox model correctly.
5. Update Android validation docs/checklists so dual-mailbox is the recommended live path.

## 7. Explicit Non-Goals

This dual-mailbox adjustment does **not** require Android to:

- introduce a second protocol
- change session grouping keys
- expose dedicated UI for every existing PC-side command
- parse new state-capsule fields
- log into the bot mailbox
- move away from the current reply-driven TaskMail model

## 8. Recommended Follow-Up Docs

When Android implementation starts, keep these docs aligned:

1. `docs/TASKMAIL-MAIL-RULES.md`
2. `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
3. `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
4. this document

If future Android work adds explicit UI for bot-mailbox configuration or bootstrap send-target selection, document that as implementation status rather than expanding this planning note into a status document.
