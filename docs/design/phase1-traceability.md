# Phase 1 Traceability (DD -> AT)

## Scope
- Baseline scope: `PLG P0`, `YUZ P0(basic)`, `RT P0(basic)`, `OPS P0(basic)`.
- Agent pre-embed scope: `AGT-001`, `AGT-004`, `AGT-007`, `AGT-005(basic confirm path)`.

## Mapping
- `DD-PLG-001 -> AT-PLG-001`
  - Implementation: Paper version gate in plugin startup.
  - Tests: `PaperVersionCheckerTest`.
- `DD-PLG-002 -> AT-PLG-002`
  - Implementation: structured onLoad phase logs (`compiler-init/cache-init/integration-detect` + durations).
  - Verification: startup log format assertions (manual server check).
- `DD-PLG-003 -> AT-PLG-003`
  - Implementation: enabled module state persistence + restore.
  - Tests: `ModuleStateStoreTest`, `ModuleManagerTest` restore/rollback paths.
- `DD-PLG-004 -> AT-PLG-004`
  - Implementation: unload cancels tasks and unregisters listeners.
  - Tests: `ModuleManagerTest.reload keeps single binding and unload cancels timers`.

- `DD-YUZ-001 -> AT-YUZ-001`
  - Implementation: lexical/parser/check pipeline before load.
  - Tests: `CompilerFacadeTest`.
- `DD-YUZ-002 -> AT-YUZ-002`
  - Implementation: nullable `Player?` access check (`E0401`).
  - Tests: `CompilerFacadeTest.fails nullable player access without guard`.
- `DD-YUZ-003 -> AT-YUZ-003`
  - Implementation: compile registry extraction for events/commands/permissions/timers.
  - Tests: `CompilerFacadeTest.extracts event command and permission registry`.
- `DD-YUZ-005 -> AT-YUZ-005`
  - Implementation: async context sync-only API rejection (`E0500`).
  - Tests: `CompilerFacadeTest.fails sync api in async block`.

- `DD-RT-001 -> AT-RT-001`
  - Implementation: state transitions `prepare/compile/load/enable/disable/reload`.
  - Tests: `ModuleManagerTest.reload keeps single binding and unload cancels timers`.
- `DD-RT-002 -> AT-RT-002`
  - Implementation: reload compile-first and binding replacement without duplicates.
  - Tests: `ModuleManagerTest.reload keeps single binding and unload cancels timers`.
- `DD-RT-003 -> AT-RT-003`
  - Implementation: timer binding cancellation on unload.
  - Tests: `ModuleManagerTest.reload keeps single binding and unload cancels timers`.
- `DD-RT-004 -> AT-RT-004`
  - Implementation: module fault isolation via guarded runtime execution.
  - Tests: `ModuleManagerTest.isolates module runtime fault`.

- `DD-OPS-001 -> AT-OPS-001`
  - Implementation: `/dd modules list/load/unload/reload/compile/diag`.
  - Verification: command manual check in Paper runtime.
- `DD-OPS-002 -> AT-OPS-002`
  - Implementation: snapshot create + list + unique id.
  - Tests: `SnapshotManagerTest.create read and list snapshots`.
- `DD-OPS-003 -> AT-OPS-003`
  - Implementation: snapshot rollback restores enabled set.
  - Tests: `ModuleManagerTest.snapshot rollback restores enabled modules only`.

- `DD-AGT-001 -> AT-AGT-001`
  - Implementation: explicit tool actions `read/search/patch/compile/load/rollback` with audit logs.
  - Tests: `ModuleManagerTest.agent patch decision recorded with fallback strategy`.
- `DD-AGT-004 -> AT-AGT-004`
  - Implementation: enforced patch strategy priority `AST > TOKEN > TEXT` with downgrade reason.
  - Tests: `ModuleManagerTest.agent patch decision recorded with fallback strategy`.
- `DD-AGT-005 -> AT-AGT-005`
  - Implementation: dangerous rollback requires explicit `--confirm` or bypass permission.
  - Verification: command manual check in Paper runtime.
- `DD-AGT-007 -> AT-AGT-007`
  - Implementation: action-level permission checks and denial diagnostics (`E0900` for compile).
  - Tests: `ModuleManagerTest.denies compile without permission and returns E0900`.

