# Phase 3 Traceability (DD -> AT)

## Scope
- `DD-YUZ-004`, `DD-YUZ-006`, `DD-RT-005`, `DD-AGT-006`, `DD-RPL-003`, `DD-SEC-004`, `DD-OPS-004`.
- 同步校准 Phase 1/2 行为与 `DD.txt`、`yuz.txt` 一致性。

## Mapping
- `DD-YUZ-004 -> AT-YUZ-004`
  - Implementation: incremental compiler cache + compile mode (`FULL/INCREMENTAL`) + file-level reuse metrics.
  - Enhancement: event `where` predicate pre-compile计数与 `throttle` 事件统计（热路径观测）。
  - Tests: `IncrementalCompilerTest`.
- `DD-YUZ-006 -> AT-YUZ-006`
  - Implementation: diagnostics stage/context fields (`DiagnosticStage`, `contextSnippet`) and structured diagnose API.
  - Tests: compile-path assertions through existing compiler tests.

- `DD-RT-005 -> AT-RT-005`
  - Implementation: SQLite-backed persist store with schema versioning and migration hooks.
  - Tests: `PersistStoreTest`, `ModuleManagerTest` persist-enabled flow.

- `DD-AGT-006 -> AT-AGT-006`
  - Implementation: `rollbackLatestUsable` + snapshot restore chain.
  - Tests: `ModuleManagerTest.rollback latest usable snapshot works`.

- `DD-RPL-003 -> AT-RPL-003`
  - Implementation: `ReplEvaluator` timeout execution path and interruption.
  - Tests: existing REPL tests plus timeout path.

- `DD-SEC-004 -> AT-SEC-004`
  - Implementation: `ResourceLimiter` (cpu/tasks/io) + `CircuitBreaker`.
  - Tests: `ResourceLimiterTest`.

- `DD-OPS-004 -> AT-OPS-004`
  - Implementation: `StructuredLogger` channels (`compile/runtime/agent/repl`) and searchable log fields.
  - Verification: runtime logs + command outputs.

## Phase3+ Maturity Items (from DD.txt)
- 模块依赖图可视化
  - Implementation: `ModuleManager.moduleDependencyGraph/moduleLoadOrder` + `/dd modules graph`.
  - Tests: `ModuleManagerTest.dependency graph and load order are built from use dynamicd imports`.
- AST Patch 稳定化
  - Implementation: `AstPatchEngine` supports idempotent `upsert use`, guarded replace, duplicate-safe append.
  - Tests: `AstPatchEngineTest.ast patch upsert use is idempotent`.
- 更强 Agent 循环
  - Implementation: `PLAN/TOOL/REFLECT/FINAL` protocol parser, no-progress stall detection, prompt decomposition seed plan, batch tool calls for read/search/list, and optional self-check retry loop.
  - Tests: `AgentLoopEngineTest.loop captures plan reflect and stalls on no progress`, `AgentLoopEngineTest.loop retries when self check fails then succeeds`, `AgentProtocolTest`.
- 宿主扩展 SPI 生态化
  - Implementation: `integration.spi` (`YuzHostExtension`, `ExtensionRegistry`, in-memory registries, ServiceLoader discover).
  - Tests: `ExtensionRegistryTest.registry records extension published contracts`.
- Folia 预适配
  - Implementation: runtime `TaskScheduler` abstraction + reflective `FoliaScheduler` + Bukkit fallback.

## Alignment Notes
- Phase 1/2 traceability remains valid; Phase 3 introduced stricter diagnostics, incremental compile paths, and stronger patch/rollback safety without removing earlier required behaviors.
