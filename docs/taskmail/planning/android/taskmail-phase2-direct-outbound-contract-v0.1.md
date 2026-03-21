# TaskMail Android Phase 2 Direct Outbound Contract Mirror (v0.1)

Updated: 2026-03-21

## Status

This is the Android-side mirror note for the first shared Phase 2 direct outbound contract freeze.

The adjacent PC-side canonical note is:

- `E:\projects\mail_based_task_manager\docs\plans\phase2_direct_outbound_contract_v1.md`

This note does not claim that Android already sends direct business traffic.
It freezes how Android should map the current `New task` form into the first direct payload once implementation starts.

## Scope

The v0.1 Android mirror covers only:

- direct `new task`

It does not widen Android direct behavior to:

- plain continuation reply
- `/status`
- `/pause`
- `/resume`
- `/end`
- direct read-side updates

Those remain outside the first direct slice and stay on the current mail path.

## Shared Reading

The current Android repository state is still:

- Phase 1 bootstrap reuse landed in repository
- formal `new task` still sends real mail
- relay bootstrap result is only a preflight and fallback signal today

The Phase 2 first slice is therefore:

- replace the current `new task` preflight-plus-mail path with one direct packet send
- keep the current mail `new task` path as fallback
- keep read-side session and detail updates mail-driven until a later phase changes them

## Android Mapping To The Shared V1 Payload

Android should map the current `TaskMailNewTaskDraft` to the shared payload as follows:

- `senderAccountId -> origin.sender_account_uuid`
- `backend.wireValue -> new_task.backend`
- `repoPath -> new_task.repo_path`
- `taskText -> new_task.task_text`
- `subjectTitle -> new_task.subject_title`
- `workdir` blank or null -> `new_task.workdir = null`
- `workdir` non-empty -> `new_task.workdir`
- `timeoutMinutes -> new_task.timeout_minutes`
- `mode.wireValue -> new_task.mode`
- `profile` blank or null -> `new_task.profile = null`
- `profile` non-empty -> `new_task.profile`
- `permission = Default -> new_task.permission = null`
- `permission = Highest -> new_task.permission = highest`
- `acceptanceCriteria` after the current trim and empty-item filtering -> `new_task.acceptance`

Android should keep using the current `TaskMailNewTaskBodySerializer` and subject builder as:

- the mail fallback projection
- the quickest parity reference when comparing direct intent against current mail behavior

## Android Transport Reading

Android should treat the shared contract as:

- current bootstrap seam stays reused
- `hello -> hello_ack` remains the gate before any direct business packet is sent
- the first business payload rides inside the existing relay `packet` wrapper
- Android must not pretend that current PC outbound status-delivery `task_run_packet` meaning is already the Android
  product contract

In this first direct slice, Android reuses the current relay transport shell without inheriting the old PC-only mail
delivery business meaning.

## Android Fallback Rule

The current Android fallback rule remains active in Phase 2 v0.1:

- bootstrap unavailable:
  - fall back to current mail `new task`
- connect or transport send failure before accepted packet:
  - fall back to current mail `new task`
- explicit server capability rejection such as unsupported action:
  - fall back to current mail `new task`
- explicit server hard rejection such as invalid payload or unauthorized:
  - do not silently fall back
  - keep the draft
  - show the direct-send failure
- accepted direct packet:
  - do not also send mail
  - show a success message that still reminds the user that the task will appear after later TaskMail status mail

## Android UI Consequences

The first direct slice should preserve these current UI truths:

- `New task` still requires the same local validation before any send attempt
- current sender-account resolution and selection behavior may stay in place even though `sender_account_uuid` is only
  provenance in the direct payload
- current success UX should still avoid promising immediate workspace appearance before the first status mail arrives
- current debug visibility should distinguish:
  - direct accepted
  - direct rejected
  - direct unavailable and mail fallback used

## Not In This Slice

Do not widen the first direct implementation to include:

- reply composer direct serialization
- status polling over direct transport
- direct timeline projection
- direct workspace hydration
- mail fallback removal

## Current Conclusion

The Android-side Phase 2 first-slice contract is now frozen tightly enough to start implementation.

The next Android implementation step should be:

- keep the existing bootstrap preflight
- replace `new task` mail send with direct packet send on the success path
- preserve the existing mail fallback for unsupported or failed direct cases
