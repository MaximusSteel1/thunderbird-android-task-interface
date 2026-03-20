# TaskMail Next Session Handoff - 2026-03-20 M1 Read-Side Skeleton

## Current scope boundary

This session started M1 from `docs/task_manager_migration_development_schedule.md`.

The implemented scope was intentionally limited to:

- landing Android read-side skeleton contracts
- splitting the current mail read path into `EmailIngress + EmailMessageParser`
- keeping current TaskMail behavior unchanged

This session did **not** start M2 local persistence yet.

## Read first

- `docs/task_manager_migration_tasklist.md`
- `docs/taskmail/planning/android/taskmail-interface-extraction-inventory-v0.1.md`
- `docs/task_manager_migration_development_schedule.md`
- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/TASKMAIL-MAIL-RULES.md`

## Code changes completed in this session

- added `data/ingress/MessageIngress.kt`
- added `data/ingress/EmailIngress.kt`
- added `data/parser/IncomingMessageParser.kt`
- added `data/parser/EmailMessageParser.kt`
- added `domain/model/UnifiedMessage.kt`
- added `domain/model/MessageSyncState.kt`
- added `domain/repository/UnifiedMessageRepository.kt`
- added `domain/repository/MessageSyncStateRepository.kt`
- added `sync/MessageSyncCoordinator.kt`
- updated `LegacyTaskMailMessageSource` to become a thin adapter over `EmailIngress + EmailMessageParser`
- updated `TaskMailModule` wiring for the new ingress/parser split
- added `EmailMessageParserTest`

## Validation completed in this session

- `.\gradlew.bat :feature:taskmail:internal:testDebugUnitTest`
- `.\gradlew.bat :feature:taskmail:internal:detekt`
- `.\gradlew.bat :feature:taskmail:internal:lintDebug`

All three completed successfully with `JAVA_HOME` pointed to JDK 21:

- `C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot`

## Important current decisions

- keep `TaskMailRepository` as the existing read-model boundary name for now
- do not force local persistence into M1 just to "finish" the abstraction story
- do not reduce mailbox fetch size in live code until the local `UnifiedMessage` path exists
- the `10`-message recent window is a steady-state M2+ policy, not an M1 behavior change

## Next concrete steps

1. Start M2 local persistence:
   - introduce `UnifiedMessage` local entity/DAO/storage
   - introduce sync-state local entity/DAO/storage
2. Implement the first real `MessageSyncCoordinator` path:
   - local-first read
   - cursor-based incremental sync
   - bootstrap/recovery fallback rules
3. Migrate session detail first:
   - read local unified data before refresh
   - keep current UI behavior stable
4. Only after the local-first detail path exists, start applying the steady-state `10`-message Task list refresh window

## Not yet done

- no local `UnifiedMessage` persistence implementation
- no `MessageSyncStateRepository` implementation
- no `MessageSyncCoordinator` implementation
- no ViewModel migration to local-first reads
- no live fetch-window reduction yet
