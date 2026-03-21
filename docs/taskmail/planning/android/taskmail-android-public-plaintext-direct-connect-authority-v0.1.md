# TaskMail Android Public Plaintext Direct-Connect Authority (v0.1)

Updated: 2026-03-21

## Status

This is the current Android-side macro planning authority for the public-IP plaintext direct-connect direction chosen on
2026-03-21.

It governs:

- the intended near-term and long-term connection direction for Android TaskMail
- which earlier assumptions are now retired
- the fallback boundary that must remain while the new path is built
- which current documents remain implementation-truth versus future-direction authority

It does not replace current implementation-truth or protocol-truth documents such as:

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/TASKMAIL-MAIL-RULES.md`
- `E:\projects\mail_based_task_manager\docs/current/mail_protocol.md`
- `E:\projects\mail_based_task_manager\docs/current/android_runner_communication_contract.md`

Those current-truth documents still describe what is implemented today.
This authority defines the direction the planning layer should now assume next.

## Purpose

This document exists to freeze one new cross-repo direction on the Android side:

- Android direct-connect becomes the intended primary TaskMail path
- the first direct path is allowed to use the current public plaintext relay entry
- the current mail path remains a required fallback and compatibility lane
- the previous mail-first / TLS-gated direct-connect assumptions are no longer the active planning baseline

This is an intentional direction reset.
It is not a claim that the repository already implements the new direct-connect product path.

## Current Fixed Assumptions

Unless a later authority doc reopens them, the following assumptions are now fixed for Android-side planning:

1. The intended primary Android TaskMail path is `Android -> VPS`.
2. Public plaintext transport is accepted as the current chosen default for this workstream, with explicit security and
   deployment tradeoffs.
3. The current mail path must remain available as fallback until direct-connect parity and rollback behavior are
   explicitly validated.
4. Token-based transport authentication is acceptable for the first direct-connect phases.
5. The current public relay transport may be used as the practical Android-facing direct-connect boundary in this phase.
6. PC remains the task-execution truth; the direct-connect change does not move task execution to the VPS.
7. Current implementation-truth docs remain authoritative for the repository state until code actually changes.

## Assumptions Explicitly Retired

The following earlier assumptions are no longer active planning authority in this repository:

1. `Android must remain mail-first for now.`
2. `The stable live connection target is PC -> VPS only.`
3. `Raw /relay is not an Android product protocol.`
4. `Public DNS + stock-client-trusted TLS are required before Android direct-connect work may begin.`
5. `Android must not hold relay transport credentials in the direct path.`

Those assumptions remain historically important because they explain older docs and code boundaries, but they are no
longer the chosen planning baseline after the 2026-03-21 decision.

## Chosen Product Boundary

For the current planning line, the chosen product boundary is:

- Android may connect to the public VPS endpoint directly
- the connection may use plaintext transport in the near term and, by current choice, as the accepted default unless a
  later authority doc changes that posture
- the direct path is allowed to grow beyond debug-only probing into the main interactive TaskMail path
- mail fallback remains required until direct and mail behavior are proven sufficiently aligned

This means the Android-side planning layer is now allowed to:

- treat direct transport as more than a debug/bootstrap seam
- plan direct-create, direct-reply, and direct-status flows as real product slices
- plan read-side state updates sourced from the direct path instead of waiting for a later API-only phase

## Guardrails

This direction change does not remove all constraints.
The following rules remain active:

- do not misrepresent current implementation state in `CURRENT-STATUS` or the validation ledger
- do not commit tokens, secrets, or operator credentials into the repository
- do not remove mail fallback before parity, fallback triggers, and rollback behavior are explicit
- do not let direct-connect planning casually rewrite established TaskMail business semantics without a corresponding
  contract note
- keep the plaintext decision explicit in docs and implementation boundaries rather than hiding it behind TLS-oriented
  wording that no longer matches the chosen direction

## What This Authority Changes Immediately

The following planning consequences now apply immediately:

1. The old mail-first/TLS-gated Android direct-connect blockers are no longer the phase gate for starting Android
   direct-connect planning.
2. Android relay/bootstrap work is no longer constrained to stay debug-only forever.
3. The current active phased plan must now be rewritten around direct-connect as the main path plus mail fallback.
4. Earlier Phase 0 artifact notes about relay TLS trust remain valid historical evidence, but they no longer block the
   newly chosen plaintext direct-connect direction.

## Immediate Android-Side Planning Consequences

The immediate repository-side outcomes of this authority are now:

1. the Android direct-connect baseline is frozen in one reviewable note
2. the staged Android execution plan is organized around direct-connect main path plus mail fallback
3. the earlier mail-first/TLS-gated planning docs are no longer part of the active docs set
4. current implementation-truth docs remain unchanged until code changes land

## Cross-Repo Alignment Note

This file is the Android-side authority reset.

The adjacent PC-side authority and coordinated execution docs have since been rewritten around the same public
plaintext direct-connect direction.

Use them as cross-repo planning references, but do not let them override this repository's current implementation-truth
or protocol-truth documents.

## Cleanup Consequence

The earlier mail-first/TLS-gated Android planning set was intentionally pruned during the 2026-03-21 cleanup.
Current direction should now be read from this file, the paired staged plan, and the current implementation-truth docs.
