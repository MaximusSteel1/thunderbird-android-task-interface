# TaskMail Next Session Handoff (2026-03-20, send-side layering)

## Scope closed in this session

- Android TaskMail send path no longer makes `RealTaskMailReplySender` and `RealTaskMailNewTaskSender` depend directly on
  mail-specific MIME construction/sending collaborators.
- New transport adapters now sit underneath the business senders:
  - `EmailTaskMailReplyTransport`
  - `EmailTaskMailNewTaskTransport`
- Project discovery `[SYNC]` sending and result reading were split:
  - outbound: `TransportBackedTaskMailProjectSyncRequester`
  - inbound: `MailStoreBackedTaskMailProjectSyncResultReader`
  - facade retained: `DefaultTaskMailProjectSyncRepository`

## Read first

- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/TASKMAIL-MAIL-RULES.md`
- `docs/taskmail/planning/android/taskmail-interface-extraction-inventory-v0.1.md`

## Validation performed

- `.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest :feature:taskmail:internal:detekt :feature:taskmail:internal:lintDebug :feature:taskmail:internal:spotlessCheck`
- `.\gradlew.bat :feature:taskmail:internal:spotlessApply`

## Practical next step

- Keep moving toward transport-pluggable send side by extracting a higher-level action transport or packet builder layer
  above the email transports, rather than letting future relay/VPS support reuse MIME-shaped request models directly.
- After that, revisit `TaskMailProjectSyncRepository` and decide whether the temporary facade should stay or whether the
  UI/use cases should read requester/result-reader contracts separately.

## Notes

- This session changed code and ran narrow TaskMail validation.
- Repo-wide `assemble/build` and device validation were not run in this session.
