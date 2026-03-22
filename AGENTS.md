# AI Agent Guide for Thunderbird for Android

This file defines requirements for AI coding agents and automated systems contributing to this repository.

AI-generated or AI-assisted contributions are acceptable only if they comply with these rules and meet the same
standards as human-written contributions.

## Applicability

These requirements apply to:

- All modules in this repository
- All pull requests created fully or partially using AI tools
- Automated refactoring, formatting, or code generation

## Repository Context

Thunderbird for Android is a privacy-focused email client.

The repository implements a white-label architecture producing:

- `app-thunderbird`: Thunderbird for Android
- `app-k9mail`: K-9 Mail

Project documentation resides in the `docs/` directory.
Architectural Decision Records (ADRs) are located in `docs/architecture/adr/`.

Agents MUST consult relevant documentation before making architectural or structural changes.

## Documentation Language

Unless the task explicitly requires another language, agents MUST write new repository documentation and documentation
updates in Chinese by default.

When documentation needs to reference protocol field names, code identifiers, file paths, command lines, or
cross-repository contract titles, agents SHOULD preserve those technical identifiers in their original form and add
Chinese explanation around them instead of translating the identifiers themselves.

## Toolchain Notes

- Java 21+ is required to build this repository; `settings.gradle.kts` enforces this
- Set `JAVA_HOME` to a Java 21+ installation before running Gradle
- On Windows, prefer `.\gradlew.bat ...` when documenting or running commands from PowerShell
- 常见环境坑：如果 `.\gradlew.bat ...` 报
  `Gradle requires JVM 17 or later to run. Your build is currently configured to use JVM 11.`，不要继续按当前 shell
  默认 `java.exe` 重试；这通常表示 `JAVA_HOME` 或 `PATH` 先命中了较老的 JDK。
- 在这台工作站上，`where.exe java` 可能同时返回 Java 11 与 Java 21；运行 Gradle 前先把 `JAVA_HOME` 指到 Java 21+
  安装（例如 `C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot`），再用 `java -version` 或
  `.\gradlew.bat -version` 确认当前 Gradle JVM 已切到 Java 21+。

## Required Agent Workflow

Before making changes, agents MUST:

### 1. Understand the Request

- Confirm that requirements are clear and consistent with project rules
- If requirements are incomplete, ambiguous, or conflicting:
  - **Do NOT guess**
  - Document assumptions
  - Request clarification before proceeding

### 2. Research Context

- Read `README.md`, `docs/CONTRIBUTING.md`, and relevant documentation in `docs/`
- Review existing implementations in affected modules
- Check for related ADRs in `docs/architecture/adr/`
- Understand the module's role in the white-label architecture

#### Pitfall Recording

When agents encounter a non-obvious implementation, debugging, or verification pitfall that is likely to recur, agents
SHOULD record it in the narrowest durable place that will help the next contributor.

Agents SHOULD record a pitfall when one or more of the following is true:

- the issue is not obvious from the code, logs, or standard project documentation
- the issue is likely to recur in later work
- rediscovering the issue would cost meaningful time
- the issue can send future work toward the wrong module, document, implementation path, or validation flow

Agents SHOULD choose the lightest useful record:

- update the closest authoritative document when the pitfall affects current behavior, setup, validation, or workflow guidance
- add a concise handoff note when the pitfall materially affects the next session but is not yet stable enough for a
  permanent document update
- include the pitfall in a pull request description when it matters for review or follow-up but does not justify a
  repository document change

Agents SHOULD NOT create standalone pitfall documentation for one-off typos, transient environment glitches, or other
low-value issues that are obvious, cheap to rediscover, and unlikely to affect future contributors.

When recording a pitfall, keep it brief and include:

- the symptom or failure mode
- the trigger or conditions
- the confirmed cause or current best understanding
- the practical avoidance, workaround, or verification step

#### TaskMail Work

If the change affects TaskMail, agents SHOULD consult these documents first:

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md` - what is currently implemented
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md` - what has actually been revalidated
- `docs/TASKMAIL-DEBUG-VALIDATION.md` - retained debug-host and device-validation path, including formal-host vs debug-host routing caveats
- `docs/TASKMAIL-MAIL-RULES.md` - Android-side protocol and reply behavior authority
- `docs/taskmail/planning/android/taskmail-next-development-plan-v0.2.md` - current integrated near-term development plan
- `docs/taskmail/planning/android/taskmail-refresh-live-update-plan-v0.1.md` - refresh and live-update slice details
- `docs/taskmail/planning/android/taskmail-bootstrap-entry-and-new-thread-plan-v0.1.md` - bootstrap discovery and guided new-thread planning
- Local TaskMail protocol / PC-side reference workspace: `E:\projects\mail_based_task_manager`
- In that workspace, check `docs/current/mail_protocol.md` for current cross-client protocol behavior
- In that workspace, check `docs/current/android_reply_method_rules.md` when Android reply behavior must align with the
  current mail control plane

For TaskMail changes, do not treat older phase documents as the primary source of truth when they conflict with the
current-status or mail-rules documents.

For TaskMail repository research, agents SHOULD:

- Prefer narrow, module-scoped searches and direct file reads in `feature:taskmail`, relevant launcher/navigation modules,
  and the listed TaskMail docs before widening scope
- Avoid broad repo-wide searches for generic terms when the expected signal is TaskMail-specific; this repository contains
  substantial unrelated legacy code and wide searches create noise, slowdowns, and low-value output

#### TaskMail Common Pitfalls

When changing TaskMail behavior, agents MUST distinguish between:

- implementation status (`docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`)
- validation evidence (`docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`)
- Android-side protocol authority (`docs/TASKMAIL-MAIL-RULES.md`)

If those sources disagree with older planning or phase documents, do NOT silently code to the older document. Clarify
the conflict or update the documentation first.

For TaskMail reply changes, agents MUST:

- Preserve mode-driven reply semantics; do not collapse all replies into generic free text
- Treat single-question and multi-question waits differently; multi-question replies require structured `Answers:`
  payloads rather than one-tap shortcuts
- Display quick-answer labels to users but send canonical answer keys or values on the wire
- Treat `paused` as a first-class session state; paused sessions require explicit resume behavior rather than implicit
  continuation
- Preserve canonical TaskMail subject identity tokens when normalizing reply subjects, including `Re:`, `[STATUS]`,
  `[S:session_id]`, and backend routing tokens when present
- Avoid changing quoted-body, reply anchor, or transport behavior casually; these are protocol decisions, not UI-only
  tweaks

For TaskMail verification, agents SHOULD:

- Start with narrow module checks such as `.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest`
- Run `.\gradlew.bat :feature:taskmail:internal:detekt` and `.\gradlew.bat :feature:taskmail:internal:lintDebug`
  before widening scope
- Consult `docs/TASKMAIL-DEBUG-VALIDATION.md` before device/debug smoke work so debug deep links are not confused with
  the formal in-app launcher path
- When real-device interaction becomes unreliable for automation, pause and ask the user for a precise manual step
  instead of repeatedly brute-forcing taps or gestures; resume mailbox or log validation immediately after the user
  confirms the action
- Report when broader tasks are blocked by unrelated existing failures instead of expanding the change into unrelated
  files

#### TaskMail Handoff Maintenance

When a TaskMail slice spans multiple sessions or is paused before implementation and validation are fully closed,
agents MUST leave a concise handoff note under `docs/taskmail/planning/android/`.

For TaskMail handoff notes, agents SHOULD:

- prefer a file named `taskmail-next-session-handoff-YYYY-MM-DD.md`
- keep the handoff brief when the slice is still early or doc-only
- include the current decision or scope boundary
- include the most relevant "read first" documents
- include the next concrete implementation or validation steps
- explicitly state whether code changes or validation were performed in the current session

### 3. Make Changes

- Modify **only** files directly related to the requested change
- Follow existing patterns and conventions in the affected modules
- Maintain consistency with the established architecture

### 4. Verify Changes

- Run appropriate Gradle tasks (see [Build and Verification Requirements](#build-and-verification-requirements))
- Ensure all checks pass before proposing changes

## Architectural Requirements

### Module Types

- `app-*` - Application entry points (`app-thunderbird`, `app-k9mail`)
- `app-common` - Wiring layer for features and dependency injection
- `feature:*` - User-facing features (split into `:api` and `:internal` modules per ADR-0009)
- `core:*` - Shared infrastructure and utilities (split into `:api` and `:internal` modules per ADR-0009)
- `library:*` - Reusable libraries
- `legacy:*` - Migration targets (contains the original K-9 Mail codebase; avoid adding new logic here)

### API / Internal Boundary

Agents MUST:

- Depend only on other modules' `:api` modules
- Never depend on another module's `:internal` or `:internal-*` modules
- Only `app-common`, `app-thunderbird`, and `app-k9mail` may depend on `:internal` modules (for DI wiring)
- Keep implementation details internal using the `internal` modifier
- Bind implementations in `app-common` or app modules only

New code MUST NOT violate the API/internal boundary, see [ADR-0009](docs/architecture/adr/0009-api-internal-split.md).

If existing code violates this boundary, agents MUST NOT replicate the pattern and SHOULD move the code toward the
intended architecture when modifying it.

Agents MUST NOT change module structure, dependency graphs, or architectural boundaries unless explicitly requested.

## Technology Requirements

Agents MUST use:

- Kotlin for new code
- Jetpack Compose for UI (mandatory for new features)
- Atomic Design system components (see `docs/architecture/design-system.md`)
- Koin for dependency injection (constructor injection)
- Coroutines and Flow for concurrency
- MVI (Unidirectional Data Flow) pattern (see `docs/architecture/ui-architecture.md`)

Testing libraries:

- `assertk`
- `kotlinx-coroutines-test`
- Turbine

Testing policy:

- Prefer **fakes over mocks** (see [Testing Guide](docs/contributing/testing-guide.md))
- Avoid mocking frameworks unless strongly justified
- Use Arrange-Act-Assert (AAA) pattern
- Name the object under test `testSubject`

Agents MUST NOT introduce alternative frameworks.

## Coding Requirements

Agents MUST:

- Make small, focused, reviewable changes
- Prioritize privacy, security, and correctness over convenience or shorter code
- Modify only files directly related to the requested change
- Follow the exact naming and formatting conventions of the file and module being modified
- NOT reformat, modernize, or clean up unrelated code
- Avoid speculative refactoring

### UI Constraints

- Use Atomic Design components from the design system (see `docs/architecture/design-system.md`)
- Raw Material components are NOT allowed outside design system modules and the catalog app
- For existing View-based code, maintain consistency using legacy design system components
- Do NOT introduce new design systems; extend the existing system within its modules

### Logging and Privacy

Privacy is a core value of this project.

Agents MUST:

- Use `net.thunderbird.core.logging.Logger` via dependency injection
- NEVER log PII (Personally Identifiable Information)
- NEVER log credentials, passwords, or authentication tokens
- NEVER log message content or email addresses

## Security Requirements

This is a privacy-focused email client. Security is non-negotiable.

Agents MUST NOT:

- Add telemetry, analytics, ads, or tracking of any kind
- Add permissions without explicit request and justification
- Introduce insecure cryptography or unsafe networking
- Hardcode secrets, credentials, API keys, or tokens
- Modify OAuth configuration unless explicitly instructed
- Change licensing headers or license terms
- Introduce dependencies with known vulnerabilities

All external input MUST be treated as untrusted. Validate and sanitize user input.

## Build and Verification Requirements

Before proposing changes, agents MUST run the narrowest relevant Gradle tasks and ensure they pass.

### Build

- `./gradlew assemble`
- `./gradlew build`
- `./gradlew :app-thunderbird:assembleDebug`
- `./gradlew :app-k9mail:assembleDebug`

### Tests

- `./gradlew test`
- `./gradlew connectedAndroidTest`

### Code Quality

- `./gradlew lint`
- `./gradlew detekt`
- `./gradlew spotlessCheck`
- `./gradlew spotlessApply` (to fix formatting issues)

### Bug Fix Requirements

When fixing bugs, agents MUST:

- Add or update tests to cover the bug
- Ensure tests **fail before the fix** (when applicable)
- Ensure all relevant tasks **pass after the fix**
- Run at least: `./gradlew test lint detekt spotlessCheck`

### Limitations

If required tasks cannot be executed locally (for example, no Android device or emulator for
`connectedAndroidTest`):

- Agents MUST explicitly state which tasks were not run and why
- Include this information in the pull request description

If repo-wide verification is blocked by pre-existing unrelated failures outside the requested change scope:

- Do NOT modify unrelated files only to make a global task pass unless explicitly asked
- Run the narrowest relevant tasks for the affected modules
- Report the exact blocking task and note that the failure is outside the change scope
- Keep formatting changes scoped to files directly related to the request

## Commit Requirements

This repository uses Conventional Commits for all commit messages.

Agents MUST:

- Use appropriate prefixes (`feat:`, `fix:`, `refactor:`, `style:`, `test:`, `chore:`)
- Keep commits small and logically scoped
- Separate formatting-only changes into `style:` commits
- Separate refactoring from functional changes
- Avoid mixing behavior changes and formatting in a single commit

## Pull Request Requirements

Pull requests MUST include:

- A clear description of changes
- The reason for the change
- Exact Gradle commands used for testing
- Known risks or trade-offs
- Disclosure of AI assistance (if applicable)

AI assistance does not reduce review standards.

## Escalation

### When to Stop and Ask

If uncertain about:

- Requirements (incomplete, ambiguous, or conflicting)
- Architectural decisions
- Module boundaries or dependencies
- Technology choices
- Security implications

**Then:**

1. Stop - Do not proceed with uncertain changes
2. Document assumptions - Write down what you understand and what is unclear
3. Request clarification - Ask specific questions

### What NOT to Do

Agents MUST NOT:

- Invent architecture or design patterns
- Bypass module boundaries to "make it work"
- Prioritize elegance over established project conventions
- Guess at requirements or implementation details
- Make breaking changes without explicit approval

When in doubt, ask. It is always better to clarify than to guess wrong.
