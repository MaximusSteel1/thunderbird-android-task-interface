# AGENTS

Purpose: help AI agents make correct progress with minimal unnecessary process.

Priority order:

1. Satisfy the user's actual goal.
2. Prefer paths that fit the repo's longer-term direction.
3. When working on TaskMail, prioritize the TaskMail mainline and mainline blockers.
4. Preserve correctness and maintainability.
5. Keep context, scope, and process as small as possible.

Do not use the priorities above as a reason to widen scope. Prefer the smallest coherent change that still moves the
goal, long-term plan, or TaskMail mainline forward.

## Context Loading

- Start from the nearest relevant files, modules, and docs.
- Expand context only when the local evidence is insufficient or risk is high.
- Read broader docs or ADRs only when touching build, architecture, module boundaries, or repo-wide workflow.
- Review nearby implementations before introducing a new pattern.

## Core Defaults

- Follow local patterns before inventing new ones.
- Keep related code, tests, and docs aligned when the change crosses file boundaries.
- Avoid unrelated cleanup unless it directly helps finish the task.
- Prefer explicit reporting of what was verified over pretending full validation happened.
- New or updated repository docs should default to Chinese unless the task explicitly needs another language.
- In user-facing explanations, assume a mixed technical background by default.
- Prefer plain language over jargon, and briefly explain necessary technical terms when they matter to the decision.

## Architecture / Tech Defaults

- Keep the current `:api` / `:internal` split intact unless the task explicitly changes it.
- New code defaults: Kotlin, Compose for new feature work, Atomic Design components, Koin, Coroutines/Flow, MVI.
- Testing defaults: fakes over mocks, AAA structure, `testSubject` naming.
- Avoid new frameworks or architectural patterns without a concrete reason.

## TaskMail

Truth layers:

- implementation status: `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- validation evidence: `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- Android protocol authority: `docs/TASKMAIL-MAIL-RULES.md`

Useful supporting docs when relevant:

- `docs/TASKMAIL-DEBUG-VALIDATION.md`
- `docs/taskmail/planning/android/taskmail-next-development-plan-v0.2.md`
- `docs/taskmail/planning/android/taskmail-refresh-live-update-plan-v0.1.md`
- `docs/taskmail/planning/android/taskmail-bootstrap-entry-and-new-thread-plan-v0.1.md`
- PC-side reference workspace: `E:\projects\mail_based_task_manager`
- PC-side protocol docs when relevant:
  - `E:\projects\mail_based_task_manager\docs\current\mail_protocol.md`
  - `E:\projects\mail_based_task_manager\docs\current\android_reply_method_rules.md`

TaskMail loading rule:

- Do not read every TaskMail document by default.
- Read the smallest relevant subset first.
- Do not let older phase docs override current-status or mail-rules.

TaskMail behavior defaults:

- Preserve mode-driven reply semantics unless the task explicitly changes them.
- Preserve the distinction between single-question and multi-question waits.
- Multi-question replies use structured `Answers:` payloads, not shortcut free text.
- Treat `paused` as a first-class state.
- Preserve canonical subject identity tokens such as `Re:`, `[STATUS]`, `[S:session_id]`, and backend routing tokens.
- Avoid changing quoted-body, reply-anchor, or transport behavior unless the task actually requires it.

TaskMail prioritization:

- Prefer work that advances the mainline or closes a mainline blocker.
- Do not drift into side tracks, polish-only work, or parallel sublines unless asked.

TaskMail verification defaults:

- Start narrow, for example `.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest`
- Then widen only as needed, for example `detekt` / `lintDebug`
- If device automation becomes unreliable, ask the user for a precise manual step instead of brute-forcing taps

TaskMail handoff:

- If the slice spans sessions, usually leave a concise handoff under `docs/taskmail/planning/android/`
- Prefer `taskmail-next-session-handoff-YYYY-MM-DD.md`

## Verification

- Run the narrowest relevant checks you can.
- Report what ran, what did not run, and why.
- If repo-wide verification is blocked by unrelated failures, do not fix unrelated files just to make the repo green.

## Toolchain Notes

- Gradle requires Java 21+ in this repo.
- On Windows, prefer `.\gradlew.bat ...`.
- If Gradle picks Java 11, fix `JAVA_HOME` / `PATH` first instead of retrying blindly.

## Decision Rule

Proceed by default. Pause and ask only when uncertainty could materially affect:

- externally visible behavior
- architecture or module boundaries
- long-term technical direction
- hard-to-revert implementation choices

When pausing, state the decision point and the assumptions that would change the implementation.
