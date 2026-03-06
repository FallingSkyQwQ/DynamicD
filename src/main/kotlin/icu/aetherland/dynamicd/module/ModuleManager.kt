package icu.aetherland.dynamicd.module

import icu.aetherland.dynamicd.agent.AgentToolAction
import icu.aetherland.dynamicd.agent.AgentToolchain
import icu.aetherland.dynamicd.agent.patch.AstPatchEngine
import icu.aetherland.dynamicd.agent.patch.PatchRequest
import icu.aetherland.dynamicd.compiler.CompileRegistry
import icu.aetherland.dynamicd.compiler.CompileResult
import icu.aetherland.dynamicd.compiler.CompileMode
import icu.aetherland.dynamicd.compiler.CompilerFacade
import icu.aetherland.dynamicd.compiler.Diagnostic
import icu.aetherland.dynamicd.compiler.DiagnosticLevel
import icu.aetherland.dynamicd.compiler.DiagnosticStage
import icu.aetherland.dynamicd.integration.IntegrationRegistry
import icu.aetherland.dynamicd.integration.PlaceholderRegistrar
import icu.aetherland.dynamicd.integration.PlaceholderSpec
import icu.aetherland.dynamicd.ops.SnapshotManager
import icu.aetherland.dynamicd.persist.PersistEntry
import icu.aetherland.dynamicd.persist.PersistStore
import icu.aetherland.dynamicd.runtime.RuntimeBridge
import icu.aetherland.dynamicd.security.Capability
import icu.aetherland.dynamicd.security.CircuitBreaker
import icu.aetherland.dynamicd.security.ResourceBudget
import icu.aetherland.dynamicd.security.ResourceLimiter
import icu.aetherland.dynamicd.security.SandboxLevel
import icu.aetherland.dynamicd.security.SecurityDecision
import icu.aetherland.dynamicd.security.SecurityPolicy
import java.io.File
import java.time.Instant

class ModuleManager(
    private val modulesRoot: File,
    private val runtimeBridge: RuntimeBridge,
    private val snapshotManager: SnapshotManager,
    private val agentToolchain: AgentToolchain,
    private val integrationRegistry: IntegrationRegistry,
    private val placeholderBridge: PlaceholderRegistrar,
    private val securityPolicy: SecurityPolicy,
    private val defaultSandboxLevel: SandboxLevel,
    private val logger: (String) -> Unit,
    private val persistStore: PersistStore? = null,
    private val resourceLimiter: ResourceLimiter = ResourceLimiter(
        ResourceBudget(cpuSteps = 1_000_000, maxTasks = 200, ioQuota = 10_000_000),
    ),
    private val circuitBreaker: CircuitBreaker = CircuitBreaker(),
    private val astPatchEngine: AstPatchEngine = AstPatchEngine(),
) {
    private val modules = mutableMapOf<String, ModuleDescriptor>()
    private val activeCommands = mutableMapOf<String, String>()

    fun discoverModules(): List<ModuleDescriptor> {
        modulesRoot.mkdirs()
        return modulesRoot.listFiles()
            ?.filter { it.isDirectory }
            ?.map { dir ->
                modules.getOrPut(dir.name) {
                    ModuleDescriptor(id = dir.name, directory = dir)
                }
            }
            .orEmpty()
    }

    fun restoreModules(enabledModuleIds: Set<String>, compileOnStartup: Boolean = true) {
        val targets = discoverModules().filter { enabledModuleIds.isEmpty() || it.id in enabledModuleIds }
        if (compileOnStartup) {
            val compiled = targets.associateWith { descriptor ->
                try {
                    compileModule(descriptor.id, "system", AgentToolchain.SYSTEM_PERMISSIONS)
                } catch (ex: Exception) {
                    descriptor.state = ModuleState.DISABLED
                    descriptor.lastRuntimeError = ex.message
                    logger("module=${descriptor.id} restore=failed reason=${ex.message}")
                    null
                }
            }
            targets.forEach { descriptor ->
                val result = compiled[descriptor]
                if (result?.success == true) {
                    loadModule(descriptor.id, "system", AgentToolchain.SYSTEM_PERMISSIONS)
                } else {
                    logger("module=${descriptor.id} restore=skipped reason=compile_error")
                }
            }
            return
        }
        targets.forEach { descriptor ->
            try {
                val result = compileModule(descriptor.id, "system", AgentToolchain.SYSTEM_PERMISSIONS)
                if (result.success) {
                    loadModule(descriptor.id, "system", AgentToolchain.SYSTEM_PERMISSIONS)
                } else {
                    logger("module=${descriptor.id} restore=skipped reason=compile_error")
                }
            } catch (ex: Exception) {
                descriptor.state = ModuleState.DISABLED
                descriptor.lastRuntimeError = ex.message
                logger("module=${descriptor.id} restore=failed reason=${ex.message}")
            }
        }
    }

    fun applyAgentPatch(
        moduleId: String,
        operator: String,
        grantedPermissions: Set<String>,
        astAvailable: Boolean,
        tokenAvailable: Boolean,
    ): Boolean {
        val authorization = agentToolchain.authorize(AgentToolAction.PATCH, grantedPermissions)
        if (!authorization.allowed) {
            agentToolchain.recordAction(
                operator = operator,
                action = AgentToolAction.PATCH,
                target = moduleId,
                decision = "denied:missing_permission:${authorization.missingPermission}",
            )
            return false
        }
        val decision = agentToolchain.selectPatchStrategy(astAvailable, tokenAvailable)
        agentToolchain.recordPatchDecision(operator, moduleId, decision)
        return true
    }

    fun createModule(moduleId: String, source: String, operator: String, grantedPermissions: Set<String>): Boolean {
        val auth = agentToolchain.authorize(AgentToolAction.CREATE, grantedPermissions)
        val sec = checkSecurity(Capability.MODULE_PATCH)
        if (!auth.allowed || !sec.allowed) {
            agentToolchain.recordAction(
                operator,
                AgentToolAction.CREATE,
                moduleId,
                "denied:${auth.missingPermission ?: sec.missing}",
            )
            return false
        }
        val dir = File(modulesRoot, moduleId)
        dir.mkdirs()
        File(dir, "mod.yuz").writeText(source.trim() + "\n")
        discoverModules()
        agentToolchain.recordAction(operator, AgentToolAction.CREATE, moduleId, "allowed")
        return true
    }

    fun patchModuleText(moduleId: String, instruction: String, operator: String, grantedPermissions: Set<String>): Boolean {
        val auth = agentToolchain.authorize(AgentToolAction.PATCH, grantedPermissions)
        val sec = checkSecurity(Capability.MODULE_PATCH)
        if (!auth.allowed || !sec.allowed) {
            agentToolchain.recordAction(
                operator,
                AgentToolAction.PATCH,
                moduleId,
                "denied:${auth.missingPermission ?: sec.missing}",
            )
            return false
        }
        val module = requireModule(moduleId)
        val file = module.directory.walkTopDown().firstOrNull { it.isFile && it.extension == "yuz" } ?: return false
        val astResult = astPatchEngine.apply(file, PatchRequest(instruction))
        if (astResult.success) {
            agentToolchain.recordAction(operator, AgentToolAction.PATCH, moduleId, "allowed:ast")
            return true
        }
        val tokenPatched = tryTokenPatch(file, instruction)
        if (tokenPatched) {
            agentToolchain.recordAction(
                operator,
                AgentToolAction.PATCH,
                moduleId,
                "allowed:token_fallback reason=${astResult.conflictReason}",
            )
            return true
        }
        val patchComment = "\n// text patch fallback: $instruction\n"
        file.appendText(patchComment)
        agentToolchain.recordAction(
            operator,
            AgentToolAction.PATCH,
            moduleId,
            "allowed:text_fallback reason=${astResult.conflictReason}",
        )
        return true
    }

    fun runDangerousCommand(command: String, operator: String, grantedPermissions: Set<String>): SecurityDecision {
        val auth = agentToolchain.authorize(AgentToolAction.RUN, grantedPermissions)
        val sec = checkSecurity(Capability.COMMAND_RUN)
        val dangerous = isDangerousCommand(command)
        if (!auth.allowed) {
            val missing = auth.missingPermission ?: "dynamicd.agent.command"
            agentToolchain.recordAction(operator, AgentToolAction.RUN, command, "denied:missing_permission:$missing")
            return SecurityDecision(false, "missing $missing")
        }
        if (dangerous && "dynamicd.agent.command.dangerous" !in grantedPermissions) {
            val missing = "dynamicd.agent.command.dangerous"
            agentToolchain.recordAction(operator, AgentToolAction.RUN, command, "denied:missing_permission:$missing")
            return SecurityDecision(false, "missing $missing")
        }
        if (!sec.allowed) {
            agentToolchain.recordAction(operator, AgentToolAction.RUN, command, "denied:${sec.missing}")
            return sec
        }
        agentToolchain.recordAction(operator, AgentToolAction.RUN, command, "allowed")
        return SecurityDecision(true)
    }

    fun readModule(moduleId: String, operator: String, grantedPermissions: Set<String>): List<String> {
        val authorization = agentToolchain.authorize(AgentToolAction.READ, grantedPermissions)
        if (!authorization.allowed) {
            agentToolchain.recordAction(
                operator = operator,
                action = AgentToolAction.READ,
                target = moduleId,
                decision = "denied:missing_permission:${authorization.missingPermission}",
            )
            return emptyList()
        }
        val module = requireModule(moduleId)
        val files = module.directory.walkTopDown()
            .filter { it.isFile && it.extension == "yuz" }
            .map { it.name }
            .toList()
        agentToolchain.recordAction(operator, AgentToolAction.READ, moduleId, "allowed")
        return files
    }

    fun searchModules(query: String, operator: String, grantedPermissions: Set<String>): List<String> {
        val authorization = agentToolchain.authorize(AgentToolAction.SEARCH, grantedPermissions)
        if (!authorization.allowed) {
            agentToolchain.recordAction(
                operator = operator,
                action = AgentToolAction.SEARCH,
                target = query,
                decision = "denied:missing_permission:${authorization.missingPermission}",
            )
            return emptyList()
        }
        val hits = discoverModules().map { it.id }.filter { it.contains(query, ignoreCase = true) }
        agentToolchain.recordAction(operator, AgentToolAction.SEARCH, query, "allowed")
        return hits
    }

    fun shutdown() {
        modules.keys.toList().forEach { unloadModule(it, "system") }
    }

    fun listModules(): List<ModuleDescriptor> = discoverModules()

    fun listSnapshots(): List<String> = snapshotManager.listSnapshots()

    fun integrationDiagnostics() = integrationRegistry.diagnostics()

    fun moduleDependencyGraph(): Map<String, List<String>> {
        val known = discoverModules().map { it.id }.toSet()
        return discoverModules()
            .associate { descriptor ->
                val deps = discoverDependencies(descriptor.directory)
                    .filter { it in known && it != descriptor.id }
                descriptor.id to deps.sorted()
            }
            .toSortedMap()
    }

    fun moduleLoadOrder(): List<String> {
        val graph = moduleDependencyGraph()
        val remainingDeps = graph.mapValues { it.value.toMutableSet() }.toMutableMap()
        val queue = ArrayDeque(remainingDeps.filterValues { it.isEmpty() }.keys.sorted())
        val ordered = mutableListOf<String>()
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            ordered += node
            remainingDeps.forEach { (moduleId, deps) ->
                if (node in deps) {
                    deps.remove(node)
                    if (deps.isEmpty() && moduleId !in ordered && moduleId !in queue) {
                        queue.addLast(moduleId)
                    }
                }
            }
        }
        return if (ordered.size == graph.size) ordered else graph.keys.sorted()
    }

    fun enabledModuleIds(): Set<String> {
        return modules.values
            .filter { it.state == ModuleState.ENABLED }
            .map { it.id }
            .toSet()
    }

    fun moduleState(moduleId: String): ModuleState? = modules[moduleId]?.state

    fun moduleTasks(moduleId: String): Int = modules[moduleId]?.tasks?.size ?: 0

    fun moduleListeners(moduleId: String): Int = modules[moduleId]?.listeners?.size ?: 0

    fun moduleLastRuntimeError(moduleId: String): String? = modules[moduleId]?.lastRuntimeError

    fun runModuleSafely(moduleId: String, block: () -> Unit): Boolean {
        return try {
            block()
            true
        } catch (ex: Exception) {
            modules[moduleId]?.lastRuntimeError = ex.message
            logger("module=$moduleId runtime=fault isolated=true reason=${ex.message}")
            false
        }
    }

    fun compileModule(moduleId: String, operator: String, grantedPermissions: Set<String>): CompileResult {
        val module = requireModule(moduleId)
        val authorization = agentToolchain.authorize(AgentToolAction.COMPILE, grantedPermissions)
        if (!authorization.allowed) {
            val denied = deniedCompileResult(moduleId, authorization.missingPermission ?: "unknown")
            module.lastCompileResult = denied
            module.state = ModuleState.PREPARED
            agentToolchain.recordAction(
                operator,
                AgentToolAction.COMPILE,
                moduleId,
                "denied:missing_permission:${authorization.missingPermission}",
            )
            return denied
        }

        agentToolchain.recordAction(operator, AgentToolAction.COMPILE, moduleId, "requested")
        val mode = if (module.lastCompileResult == null) CompileMode.FULL else CompileMode.INCREMENTAL
        val result = CompilerFacade.compileModule(moduleId, module.directory, mode)
        module.lastCompileResult = result
        module.state = if (result.success) ModuleState.COMPILED else ModuleState.PREPARED
        val decision = if (result.success) "allowed" else "denied:compile_error"
        agentToolchain.recordAction(operator, AgentToolAction.COMPILE, moduleId, decision)
        return result
    }

    fun loadModule(moduleId: String, operator: String, grantedPermissions: Set<String>): Boolean {
        val module = requireModule(moduleId)
        val cbState = circuitBreaker.state(moduleId)
        if (cbState.tripped) {
            logger("module=$moduleId circuit_open reason=${cbState.reason}")
            return false
        }
        val authorization = agentToolchain.authorize(AgentToolAction.LOAD, grantedPermissions)
        if (!authorization.allowed) {
            agentToolchain.recordAction(
                operator,
                AgentToolAction.LOAD,
                moduleId,
                "denied:missing_permission:${authorization.missingPermission}",
            )
            return false
        }

        val compileResult = module.lastCompileResult ?: compileModule(moduleId, operator, grantedPermissions)
        if (!compileResult.success) {
            return false
        }
        val missingDependencies = compileResult.registry.dependencyImports.filter { dep ->
            val depModule = modules[dep] ?: discoverModules().firstOrNull { it.id == dep } ?: return@filter true
            depModule.state != ModuleState.ENABLED
        }
        if (missingDependencies.isNotEmpty()) {
            logger("module=$moduleId dependency_missing=${missingDependencies.joinToString(",")}")
            return false
        }
        val integrationMissing = compileResult.registry.requiredIntegrations.filter {
            !integrationRegistry.diagnostics().any { d -> d.integration == it && d.enabled }
        }
        if (integrationMissing.isNotEmpty()) {
            logger("module=$moduleId integration_missing=${integrationMissing.joinToString(",")}")
            return false
        }
        if (module.state == ModuleState.ENABLED) {
            return true
        }

        agentToolchain.recordAction(operator, AgentToolAction.LOAD, moduleId, "requested")
        return try {
            unloadRuntimeBindings(module)
            compileResult.registry.commands.forEach { raw ->
                val normalized = raw.lowercase()
                val owner = activeCommands[normalized]
                if (owner != null && owner != moduleId) {
                    throw IllegalStateException("command_conflict:$normalized owner=$owner")
                }
            }

            compileResult.registry.events.forEach { path ->
                val listener = runtimeBridge.bindEvent(moduleId, path)
                if (listener != null) {
                    module.listeners.add(listener)
                }
            }

            compileResult.registry.timers.forEach { spec ->
                val budget = resourceLimiter.registerTask(moduleId)
                if (!budget.allowed) {
                    circuitBreaker.trip(moduleId, budget.missing ?: "task budget exceeded")
                    throw IllegalStateException(budget.missing)
                }
                val task = runtimeBridge.bindTimer(moduleId, spec)
                if (task != null) {
                    module.tasks.add(task)
                }
            }

            compileResult.registry.commands.forEach { raw ->
                val normalized = raw.lowercase()
                activeCommands[normalized] = moduleId
                module.activeCommands.add(normalized)
            }
            compileResult.registry.placeholders.forEach { name ->
                val key = name.substringAfter(":", name).substringAfter("_", name)
                placeholderBridge.register(
                    PlaceholderSpec(
                        namespace = "dd",
                        key = key,
                        valueProvider = { _ -> "module=$moduleId placeholder=$name" },
                    ),
                )
            }
            module.state = ModuleState.ENABLED
            module.lastRuntimeError = null
            restorePersistState(moduleId)
            agentToolchain.recordAction(operator, AgentToolAction.LOAD, moduleId, "allowed")
            true
        } catch (ex: Exception) {
            module.lastRuntimeError = ex.message
            module.state = ModuleState.DISABLED
            unloadRuntimeBindings(module)
            agentToolchain.recordAction(operator, AgentToolAction.LOAD, moduleId, "denied:runtime_fault:${ex.message}")
            false
        }
    }

    fun unloadModule(moduleId: String, operator: String): Boolean {
        val module = modules[moduleId] ?: return false
        savePersistState(moduleId)
        unloadRuntimeBindings(module)
        module.activeCommands.forEach { cmd -> activeCommands.remove(cmd) }
        module.activeCommands.clear()
        module.state = ModuleState.DISABLED
        resourceLimiter.reset(moduleId)
        agentToolchain.recordAction(operator, AgentToolAction.LOAD, moduleId, "unloaded at=${Instant.now()}")
        return true
    }

    fun reloadModule(moduleId: String, operator: String, grantedPermissions: Set<String>): Boolean {
        val compile = compileModule(moduleId, operator, grantedPermissions)
        if (!compile.success) {
            return false
        }
        unloadModule(moduleId, operator)
        return loadModule(moduleId, operator, grantedPermissions)
    }

    fun createSnapshot(operator: String, grantedPermissions: Set<String>): String? {
        val authorization = agentToolchain.authorize(AgentToolAction.ROLLBACK, grantedPermissions)
        if (!authorization.allowed) {
            agentToolchain.recordAction(
                operator,
                AgentToolAction.ROLLBACK,
                "snapshot:create",
                "denied:missing_permission:${authorization.missingPermission}",
            )
            return null
        }
        val id = snapshotManager.createSnapshot(modules.values)
        agentToolchain.recordAction(operator, AgentToolAction.ROLLBACK, "snapshot:create", "allowed", id)
        return id
    }

    fun rollback(snapshotId: String, operator: String, grantedPermissions: Set<String>): Boolean {
        val authorization = agentToolchain.authorize(AgentToolAction.ROLLBACK, grantedPermissions)
        if (!authorization.allowed) {
            agentToolchain.recordAction(
                operator,
                AgentToolAction.ROLLBACK,
                snapshotId,
                "denied:missing_permission:${authorization.missingPermission}",
                snapshotId,
            )
            return false
        }

        val snapshot = snapshotManager.readSnapshot(snapshotId)
        if (snapshot.isEmpty()) {
            agentToolchain.recordAction(operator, AgentToolAction.ROLLBACK, snapshotId, "denied:not_found", snapshotId)
            return false
        }

        modules.keys.toList().forEach { unloadModule(it, operator) }
        val discovered = discoverModules().map { it.id }.toSet()
        snapshot.forEach { (id, enabled) ->
            if (id in discovered && enabled) {
                val compile = compileModule(id, operator, grantedPermissions)
                if (compile.success) {
                    loadModule(id, operator, grantedPermissions)
                }
            }
        }
        agentToolchain.recordAction(operator, AgentToolAction.ROLLBACK, snapshotId, "allowed", snapshotId)
        return true
    }

    fun rollbackLatestUsable(operator: String, grantedPermissions: Set<String>): Boolean {
        val latest = listSnapshots().lastOrNull() ?: return false
        return rollback(latest, operator, grantedPermissions)
    }

    private fun unloadRuntimeBindings(module: ModuleDescriptor) {
        module.tasks.forEach { task -> task.cancel() }
        module.tasks.clear()
        module.listeners.forEach { listener -> listener.unregister() }
        module.listeners.clear()
    }

    private fun deniedCompileResult(moduleId: String, missingPermission: String): CompileResult {
        return CompileResult(
            moduleId = moduleId,
            success = false,
            diagnostics = listOf(
                Diagnostic(
                    code = "E0900",
                    level = DiagnosticLevel.ERROR,
                    stage = DiagnosticStage.SECURITY,
                    message = "Permission denied for compile action",
                    file = moduleId,
                    line = 1,
                    column = 1,
                    expected = "permission granted",
                    actual = "missing $missingPermission",
                    suggestion = "Grant $missingPermission",
                ),
            ),
            registry = CompileRegistry(
                events = emptyList(),
                commands = emptyList(),
                permissions = emptyList(),
                timers = emptyList(),
                placeholders = emptyList(),
                requiredIntegrations = emptySet(),
            ),
            symbolIndex = icu.aetherland.dynamicd.compiler.SymbolIndex(
                moduleId = moduleId,
                exportedFunctions = emptyList(),
                events = emptyList(),
                commands = emptyList(),
                dependencies = emptyList(),
                records = emptyList(),
                enums = emptyList(),
                traits = emptyList(),
            ),
            metrics = icu.aetherland.dynamicd.compiler.CompileMetrics(
                mode = CompileMode.FULL,
                totalMillis = 0,
                filesCompiled = 0,
                filesReused = 0,
            ),
        )
    }

    private fun requireModule(moduleId: String): ModuleDescriptor {
        return discoverModules().firstOrNull { it.id == moduleId }
            ?: throw IllegalArgumentException("Module not found: $moduleId")
    }

    private fun checkSecurity(capability: Capability): SecurityDecision {
        return securityPolicy.check(defaultSandboxLevel, capability)
    }

    private fun isDangerousCommand(command: String): Boolean {
        val lowered = command.trim().lowercase()
        val head = lowered.split(" ").firstOrNull().orEmpty()
        return head in setOf("op", "deop", "stop", "restart", "reload", "lp", "luckperms")
    }

    private fun savePersistState(moduleId: String) {
        val store = persistStore ?: return
        val module = modules[moduleId] ?: return
        val keys = discoverPersistKeys(module.directory)
        keys.forEach { key ->
            store.upsert(
                PersistEntry(
                    moduleId = moduleId,
                    key = key,
                    value = "persist:$key",
                    schemaVersion = 1,
                ),
            )
        }
    }

    private fun restorePersistState(moduleId: String) {
        val store = persistStore ?: return
        val entries = store.getModuleState(moduleId)
        if (entries.isNotEmpty()) {
            logger("module=$moduleId persist_restored=${entries.size}")
        }
    }

    private fun discoverPersistKeys(moduleDir: File): List<String> {
        val keys = mutableListOf<String>()
        moduleDir.walkTopDown()
            .filter { it.isFile && it.extension == "yuz" }
            .forEach { file ->
                file.readLines().forEach { raw ->
                    val line = raw.trim()
                    val m = Regex("persist\\s+(\\w+)\\s*:").find(line)
                    if (m != null) {
                        keys += m.groupValues[1]
                    }
                }
            }
        return keys.distinct()
    }

    private fun discoverDependencies(moduleDir: File): List<String> {
        val deps = mutableListOf<String>()
        moduleDir.walkTopDown()
            .filter { it.isFile && it.extension == "yuz" }
            .forEach { file ->
                file.readLines().forEach { raw ->
                    val line = raw.trim()
                    val dep = Regex("use\\s+dynamicd:([a-zA-Z0-9_\\-]+)")
                        .find(line)
                        ?.groupValues
                        ?.getOrNull(1)
                    if (!dep.isNullOrBlank()) {
                        deps += dep
                    }
                }
            }
        return deps.distinct()
    }

    private fun tryTokenPatch(file: File, instruction: String): Boolean {
        val source = file.readText()
        val payload = instruction.removePrefix("replace ").split("=>", limit = 2)
        if (payload.size != 2) {
            return false
        }
        val from = payload[0].trim()
        val to = payload[1].trim()
        if (from.isBlank() || from !in source) {
            return false
        }
        file.writeText(source.replace(from, to))
        return true
    }
}
