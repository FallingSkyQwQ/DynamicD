# Phase 2 Traceability (DD -> AT)

## Scope
- `INT P0/P1`, `AGT P0(core)`, `RPL P0`, `SEC P0`.

## Mapping
- `DD-INT-001 -> AT-INT-001`
  - Implementation: `PlaceholderBridge` + placeholder registry + `/dd papi list`.
- `DD-INT-002 -> AT-INT-002`
  - Implementation: `LuckPermsBridge` with `has/grant/meta` and `/dd perms sync`.
- `DD-INT-003 -> AT-INT-003`
  - Implementation: `IntegrationRegistry` emits explicit degraded diagnostics when missing.
- `DD-INT-004 -> AT-INT-004`
  - Implementation: integration registry/SPI abstraction (`IntegrationRegistry`, bridge interfaces).

- `DD-AGT-001 -> AT-AGT-001`
  - Implementation: agent tool chain actions audited (`read/search/create/patch/compile/load/unload/run/rollback`).
- `DD-AGT-002 -> AT-AGT-002`
  - Implementation: `AgentLoopEngine` (turn loop, tool-call, result feedback, final summary).
- `DD-AGT-003 -> AT-AGT-003`
  - Implementation: `patch -> compile -> load` path in `AgentToolExecutor`.
- `DD-AGT-004 -> AT-AGT-004`
  - Implementation: patch strategy priority retained in `AgentToolchain`.
- `DD-AGT-005 -> AT-AGT-005`
  - Implementation: dangerous command / rollback requires explicit confirmation.
- `DD-AGT-007 -> AT-AGT-007`
  - Implementation: action-level permission gates + sandbox security decisions.

- `DD-RPL-001 -> AT-RPL-001`
  - Implementation: `ReplSessionManager` session isolation + timeout.
- `DD-RPL-002 -> AT-RPL-002`
  - Implementation: dangerous operations require explicit confirm flow.

- `DD-SEC-001 -> AT-SEC-001`
  - Implementation: `SandboxLevel` and `SecurityPolicy` capability checks.
- `DD-SEC-002 -> AT-SEC-002`
  - Implementation: `AuditLogger` fields and high-risk action recording.
- `DD-SEC-003 -> AT-SEC-003`
  - Implementation: denied actions return missing permission / sandbox reason.

## Automated Verification
- `AgentLoopEngineTest`
- `ReplSessionManagerTest`
- existing `ModuleManagerTest`, `CompilerFacadeTest`, `SnapshotManagerTest`, `PaperVersionCheckerTest`.
