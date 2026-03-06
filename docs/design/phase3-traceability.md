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
- yuz 前端重建（全规范级第一里程碑）
  - Implementation: typed lexer (`TokenType`, block-comment/string diagnostics), parser扩展（`module/version/use/export fn/state/persist/placeholder`）, semantic analyzer entry (`module required`, duplicate symbol, nullable/effect checks).
  - Tests: `LexerTest`, `CompilerFacadeTest.requires module declaration`, `CompilerFacadeTest.only exported functions are in symbol index`.
- yuz 语义系统扩展（全规范级第二里程碑）
  - Implementation: parser & AST support for `record/enum/trait/impl/match`, impl trait/target binding (`E0601/E0602`), impl method completeness (`E0605`) and duplicate impl guard (`E0607`), signature-level trait/impl compatibility (param types + return type, `E0609`), enum-match exhaustiveness (`E0608`) with typed variable inference, generic non-exhaustive warning (`W0604`), and Result flow guards (`E0701/E0702/E0703`) with nullable `T?` false-positive suppression.
  - Symbol index extended with `records/enums/traits`.
  - Tests: `CompilerFacadeTest.fails impl target and trait validation`, `CompilerFacadeTest.fails when impl misses required trait methods`, `CompilerFacadeTest.fails when impl method signature mismatches trait`, `CompilerFacadeTest.enum match requires exhaustive cases without else`, `CompilerFacadeTest.result match requires both ok and err without else`, `CompilerFacadeTest.fails question mark outside result context`, `CompilerFacadeTest.fails ok err return outside result function`, `CompilerFacadeTest.match without else emits warning`.
- 模块依赖图可视化
  - Implementation: `ModuleManager.moduleDependencyGraph/moduleLoadOrder` + `/dd modules graph`.
  - Tests: `ModuleManagerTest.dependency graph and load order are built from use dynamicd imports`.
- AST Patch 稳定化
  - Implementation: `AstPatchEngine` supports idempotent `upsert use`, guarded replace, duplicate-safe append.
  - Tests: `AstPatchEngineTest.ast patch upsert use is idempotent`.
- 更强 Agent 循环
  - Implementation: `PLAN/TOOL/REFLECT/FINAL` protocol parser, no-progress stall detection, prompt decomposition seed plan, batch tool calls for read/search/list, and optional self-check retry loop.
  - Enhancement: `AgentMemoryStore` long-horizon session memory (read recent context, append result memory).
  - Tests: `AgentLoopEngineTest.loop captures plan reflect and stalls on no progress`, `AgentLoopEngineTest.loop retries when self check fails then succeeds`, `AgentProtocolTest`, `AgentServiceTest.service persists session logs`.
- 宿主扩展 SPI 生态化
  - Implementation: `integration.spi` (`YuzHostExtension`, `ExtensionRegistry`, in-memory registries, ServiceLoader discover).
  - Tests: `ExtensionRegistryTest.registry records extension published contracts`.
- Folia 预适配
  - Implementation: runtime `TaskScheduler` abstraction + reflective `FoliaScheduler` + Bukkit fallback.
- 生产压测结论基础能力
  - Implementation: `BenchService` and `/dd bench run|report|suite` with scenarios (`standard/mixed/soak`) and metrics (cold/warm compile, reload latency, incremental reuse ratio, reload success rate, reload P95/P99, synthetic event throughput, agent success rate, soak samples, soak stage snapshots `start/mid/end`, failure sample/count, success trend delta, verdict), plus multi-module suite aggregation report (`failedModuleCount`, `failureBuckets`, aggregated P95/P99, verdict).
  - Tests: `BenchServiceTest.runs and persists benchmark report`, `AgentServiceTest.service persists session logs`.

## Alignment Notes
- Phase 1/2 traceability remains valid; Phase 3 introduced stricter diagnostics, incremental compile paths, and stronger patch/rollback safety without removing earlier required behaviors.
