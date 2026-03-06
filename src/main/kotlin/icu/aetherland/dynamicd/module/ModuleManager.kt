package icu.aetherland.dynamicd.module

import icu.aetherland.dynamicd.agent.AgentToolAction
import icu.aetherland.dynamicd.agent.AgentToolchain
import icu.aetherland.dynamicd.compiler.CompileResult
import icu.aetherland.dynamicd.compiler.CompilerFacade
import icu.aetherland.dynamicd.ops.SnapshotManager
import icu.aetherland.dynamicd.runtime.EventBridge
import org.bukkit.Bukkit
import org.bukkit.event.HandlerList
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class ModuleManager(
    private val plugin: JavaPlugin,
    private val modulesRoot: File,
    private val eventBridge: EventBridge,
    private val snapshotManager: SnapshotManager,
    private val agentToolchain: AgentToolchain,
) {
    private val modules = mutableMapOf<String, ModuleDescriptor>()

    fun discoverModules(): List<ModuleDescriptor> {
        modulesRoot.mkdirs()
        val found = modulesRoot.listFiles()
            ?.filter { it.isDirectory }
            ?.map { dir ->
                modules.getOrPut(dir.name) {
                    ModuleDescriptor(id = dir.name, directory = dir)
                }
            }
            .orEmpty()
        return found
    }

    fun restoreModules() {
        discoverModules()
            .forEach { descriptor ->
                val result = compileModule(descriptor.id, "system")
                if (result.success) {
                    loadModule(descriptor.id, "system")
                } else {
                    plugin.logger.warning("Module ${descriptor.id} not restored due to compile errors")
                }
            }
    }

    fun shutdown() {
        modules.keys.toList().forEach { unloadModule(it, "system") }
    }

    fun listModules(): List<ModuleDescriptor> = discoverModules()

    fun compileModule(moduleId: String, operator: String): CompileResult {
        val module = requireModule(moduleId)
        agentToolchain.recordAction(operator, AgentToolAction.COMPILE, moduleId, "requested")
        val result = CompilerFacade.compileModule(moduleId, module.directory)
        module.lastCompileResult = result
        module.state = if (result.success) ModuleState.COMPILED else ModuleState.PREPARED
        val decision = if (result.success) "allowed" else "denied:compile_error"
        agentToolchain.recordAction(operator, AgentToolAction.COMPILE, moduleId, decision)
        return result
    }

    fun loadModule(moduleId: String, operator: String): Boolean {
        val module = requireModule(moduleId)
        val compileResult = module.lastCompileResult ?: compileModule(moduleId, operator)
        if (!compileResult.success) {
            return false
        }
        if (module.state == ModuleState.ENABLED) {
            return true
        }
        agentToolchain.recordAction(operator, AgentToolAction.LOAD, moduleId, "requested")
        unloadRuntimeBindings(module)
        compileResult.registry.events.forEach { path ->
            val listener = eventBridge.createListener(moduleId, path)
            if (listener != null) {
                Bukkit.getPluginManager().registerEvents(listener, plugin)
                module.listeners.add(listener)
            }
        }
        module.state = ModuleState.ENABLED
        agentToolchain.recordAction(operator, AgentToolAction.LOAD, moduleId, "allowed")
        return true
    }

    fun unloadModule(moduleId: String, operator: String): Boolean {
        val module = modules[moduleId] ?: return false
        unloadRuntimeBindings(module)
        module.state = ModuleState.DISABLED
        agentToolchain.recordAction(operator, AgentToolAction.LOAD, moduleId, "unloaded")
        return true
    }

    fun reloadModule(moduleId: String, operator: String): Boolean {
        unloadModule(moduleId, operator)
        val compile = compileModule(moduleId, operator)
        if (!compile.success) {
            return false
        }
        return loadModule(moduleId, operator)
    }

    fun createSnapshot(operator: String): String {
        val id = snapshotManager.createSnapshot(modules.values)
        agentToolchain.recordAction(operator, AgentToolAction.ROLLBACK, "snapshot:create", "allowed", id)
        return id
    }

    fun rollback(snapshotId: String, operator: String): Boolean {
        val snapshot = snapshotManager.readSnapshot(snapshotId)
        if (snapshot.isEmpty()) {
            agentToolchain.recordAction(operator, AgentToolAction.ROLLBACK, snapshotId, "denied:not_found", snapshotId)
            return false
        }
        modules.keys.toList().forEach { unloadModule(it, operator) }
        discoverModules()
        snapshot.keys.forEach { id ->
            val compile = compileModule(id, operator)
            if (compile.success) {
                loadModule(id, operator)
            }
        }
        agentToolchain.recordAction(operator, AgentToolAction.ROLLBACK, snapshotId, "allowed", snapshotId)
        return true
    }

    private fun unloadRuntimeBindings(module: ModuleDescriptor) {
        module.tasks.forEach { it.cancel() }
        module.tasks.clear()
        module.listeners.forEach { listener -> HandlerList.unregisterAll(listener) }
        module.listeners.clear()
    }

    private fun requireModule(moduleId: String): ModuleDescriptor {
        return discoverModules().firstOrNull { it.id == moduleId }
            ?: throw IllegalArgumentException("Module not found: $moduleId")
    }
}
