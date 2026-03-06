package icu.aetherland.dynamicd.module

import icu.aetherland.dynamicd.agent.AgentToolAction
import icu.aetherland.dynamicd.agent.AgentToolchain
import icu.aetherland.dynamicd.compiler.CompileRegistry
import icu.aetherland.dynamicd.compiler.CompileResult
import icu.aetherland.dynamicd.compiler.CompilerFacade
import icu.aetherland.dynamicd.compiler.Diagnostic
import icu.aetherland.dynamicd.compiler.DiagnosticLevel
import icu.aetherland.dynamicd.ops.SnapshotManager
import icu.aetherland.dynamicd.runtime.RuntimeBridge
import java.io.File
import java.time.Instant

class ModuleManager(
    private val modulesRoot: File,
    private val runtimeBridge: RuntimeBridge,
    private val snapshotManager: SnapshotManager,
    private val agentToolchain: AgentToolchain,
    private val logger: (String) -> Unit,
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

    fun restoreModules(enabledModuleIds: Set<String>) {
        val targets = discoverModules().filter { enabledModuleIds.isEmpty() || it.id in enabledModuleIds }
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
        val result = CompilerFacade.compileModule(moduleId, module.directory)
        module.lastCompileResult = result
        module.state = if (result.success) ModuleState.COMPILED else ModuleState.PREPARED
        val decision = if (result.success) "allowed" else "denied:compile_error"
        agentToolchain.recordAction(operator, AgentToolAction.COMPILE, moduleId, decision)
        return result
    }

    fun loadModule(moduleId: String, operator: String, grantedPermissions: Set<String>): Boolean {
        val module = requireModule(moduleId)
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
            module.state = ModuleState.ENABLED
            module.lastRuntimeError = null
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
        unloadRuntimeBindings(module)
        module.activeCommands.forEach { cmd -> activeCommands.remove(cmd) }
        module.activeCommands.clear()
        module.state = ModuleState.DISABLED
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
            ),
        )
    }

    private fun requireModule(moduleId: String): ModuleDescriptor {
        return discoverModules().firstOrNull { it.id == moduleId }
            ?: throw IllegalArgumentException("Module not found: $moduleId")
    }
}
