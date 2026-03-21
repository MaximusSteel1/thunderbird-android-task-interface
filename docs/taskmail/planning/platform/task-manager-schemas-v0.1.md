# Task Manager Schemas (v0.1)

This page is a reference index for an imported schema package.

It should not be treated as the current Android TaskMail protocol contract, current PC mail control-plane authority, or
the active cross-repo execution roadmap.

Use the current authority documents first:

- `docs/TASKMAIL-MAIL-RULES.md`
- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- `docs/TASKMAIL-ANDROID-VALIDATION-LEDGER.md`
- `docs/taskmail/planning/android/taskmail-android-public-plaintext-direct-connect-authority-v0.1.md`
- `E:\projects\mail_based_task_manager\docs\plans\android_pc_vps_evolution_authority.md`

This page indexes the extracted schema package imported from `task_manager_schemas_v0.1.zip`.

## Package Root

- `docs/taskmail/planning/platform/schemas/task-manager-schemas-v0.1/`

## Included Files

- [Package README](schemas/task-manager-schemas-v0.1/README.md)
- [Manifest](schemas/task-manager-schemas-v0.1/manifest.json)
- [Validation Report](schemas/task-manager-schemas-v0.1/validation_report.json)
- `common/base.schema.json`
- `tools/memory.search.{request,response}.schema.json`
- `tools/memory.read.{request,response}.schema.json`
- `tools/task.get.{request,response}.schema.json`
- `tools/task.patch.{request,response}.schema.json`
- `examples/*.json`

## Current Coverage

The package currently covers four first-phase tools:

- `memory.search`
- `memory.read`
- `task.get`
- `task.patch`

The bundled `validation_report.json` marks all eight request/response schemas as validated successfully in the generated package.
