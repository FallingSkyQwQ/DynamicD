package icu.aetherland.dynamicd.agent.loop

import icu.aetherland.dynamicd.agent.AgentToolAction
import icu.aetherland.dynamicd.module.ModuleManager
import org.bukkit.Bukkit

class AgentToolExecutor(
    private val moduleManager: ModuleManager,
) {
    fun execute(
        operator: String,
        permissions: Set<String>,
        toolName: String,
        args: String,
    ): String {
        return when (toolName.lowercase()) {
            "list" -> moduleManager.listModules().joinToString(",") { it.id }
            "read" -> moduleManager.readModule(args.trim(), operator, permissions).joinToString(",")
            "search" -> moduleManager.searchModules(args.trim(), operator, permissions).joinToString(",")
            "create" -> createModule(operator, permissions, args)
            "patch" -> patchModule(operator, permissions, args)
            "compile" -> moduleManager.compileModule(args.trim(), operator, permissions).let { "success=${it.success}" }
            "load" -> moduleManager.loadModule(args.trim(), operator, permissions).toString()
            "unload" -> moduleManager.unloadModule(args.trim(), operator).toString()
            "rollback" -> moduleManager.rollback(args.trim(), operator, permissions).toString()
            "run" -> runCommand(operator, permissions, args.trim())
            else -> "unsupported_tool:$toolName"
        }
    }

    private fun createModule(operator: String, permissions: Set<String>, args: String): String {
        val parts = args.split(" ", limit = 2)
        val moduleId = parts.firstOrNull()?.trim().orEmpty()
        if (moduleId.isBlank()) {
            return "error:module_id_required"
        }
        val source = parts.getOrNull(1)?.trim().orEmpty().ifBlank { """module "dynamicd:$moduleId"""" }
        val allowed = moduleManager.createModule(moduleId, source, operator, permissions)
        return "created=$allowed"
    }

    private fun patchModule(operator: String, permissions: Set<String>, args: String): String {
        val parts = args.split(" ", limit = 2)
        val moduleId = parts.firstOrNull()?.trim().orEmpty()
        val instruction = parts.getOrNull(1)?.trim().orEmpty()
        if (moduleId.isBlank() || instruction.isBlank()) {
            return "error:module_id_and_instruction_required"
        }
        val patched = moduleManager.patchModuleText(moduleId, instruction, operator, permissions)
        if (!patched) {
            return "patched=false"
        }
        val compiled = moduleManager.compileModule(moduleId, operator, permissions)
        if (!compiled.success) {
            return "patched=true compiled=false"
        }
        return "patched=true compiled=true loaded=${moduleManager.loadModule(moduleId, operator, permissions)}"
    }

    private fun runCommand(operator: String, permissions: Set<String>, command: String): String {
        val result = moduleManager.runDangerousCommand(command, operator, permissions)
        return if (result.allowed) {
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
                "run=true"
            } catch (ex: Exception) {
                "run=false reason=${ex.message}"
            }
        } else {
            "run=false reason=${result.missing ?: "denied"}"
        }
    }
}
