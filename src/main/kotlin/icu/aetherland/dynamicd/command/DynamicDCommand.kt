package icu.aetherland.dynamicd.command

import icu.aetherland.dynamicd.module.ModuleManager
import icu.aetherland.dynamicd.security.DangerousActionGuard
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter

class DynamicDCommand(
    private val moduleManager: ModuleManager,
    private val dangerousActionGuard: DangerousActionGuard,
) : CommandExecutor, TabCompleter {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            sender.sendMessage("/dd modules <list|load|unload|reload|compile|diag>")
            sender.sendMessage("/dd snapshot <create|rollback>")
            return true
        }
        val operator = sender.name
        return when (args[0].lowercase()) {
            "modules" -> handleModules(sender, operator, args.drop(1))
            "snapshot" -> handleSnapshot(sender, operator, args.drop(1))
            else -> {
                sender.sendMessage("Unknown subcommand")
                true
            }
        }
    }

    private fun handleModules(sender: CommandSender, operator: String, args: List<String>): Boolean {
        if (args.isEmpty()) {
            sender.sendMessage("Usage: /dd modules <list|load|unload|reload|compile|diag>")
            return true
        }
        return when (args[0].lowercase()) {
            "list" -> {
                val modules = moduleManager.listModules()
                sender.sendMessage("Modules: " + modules.joinToString(", ") { "${it.id}(${it.state})" })
                true
            }
            "load" -> withModuleArg(sender, args) { moduleId ->
                sender.sendMessage("load $moduleId => ${moduleManager.loadModule(moduleId, operator)}")
            }
            "unload" -> withModuleArg(sender, args) { moduleId ->
                sender.sendMessage("unload $moduleId => ${moduleManager.unloadModule(moduleId, operator)}")
            }
            "reload" -> withModuleArg(sender, args) { moduleId ->
                sender.sendMessage("reload $moduleId => ${moduleManager.reloadModule(moduleId, operator)}")
            }
            "compile" -> withModuleArg(sender, args) { moduleId ->
                val result = moduleManager.compileModule(moduleId, operator)
                sender.sendMessage("compile $moduleId => ${result.success}")
                result.diagnostics.forEach {
                    sender.sendMessage("[${it.code}] ${it.message} (${it.file}:${it.line}:${it.column})")
                }
            }
            "diag" -> withModuleArg(sender, args) { moduleId ->
                val result = moduleManager.compileModule(moduleId, operator)
                result.diagnostics.forEach {
                    sender.sendMessage("[${it.code}] ${it.message} expected=${it.expected} actual=${it.actual} suggestion=${it.suggestion}")
                }
                if (result.diagnostics.isEmpty()) {
                    sender.sendMessage("No diagnostics")
                }
            }
            else -> {
                sender.sendMessage("Unknown modules action")
                true
            }
        }
    }

    private fun handleSnapshot(sender: CommandSender, operator: String, args: List<String>): Boolean {
        if (args.isEmpty()) {
            sender.sendMessage("Usage: /dd snapshot <create|rollback>")
            return true
        }
        return when (args[0].lowercase()) {
            "create" -> {
                val id = moduleManager.createSnapshot(operator)
                sender.sendMessage("snapshot created: $id")
                true
            }
            "rollback" -> {
                if (args.size < 2) {
                    sender.sendMessage("Usage: /dd snapshot rollback <id> [--confirm]")
                    return true
                }
                val snapshotId = args[1]
                val confirmed = args.contains("--confirm")
                if (!dangerousActionGuard.isConfirmed(sender, confirmed)) {
                    sender.sendMessage("Rollback requires explicit --confirm")
                    return true
                }
                sender.sendMessage("rollback $snapshotId => ${moduleManager.rollback(snapshotId, operator)}")
                true
            }
            else -> {
                sender.sendMessage("Unknown snapshot action")
                true
            }
        }
    }

    private fun withModuleArg(sender: CommandSender, args: List<String>, op: (String) -> Unit): Boolean {
        if (args.size < 2) {
            sender.sendMessage("Module id required")
            return true
        }
        op(args[1])
        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>,
    ): MutableList<String> {
        if (args.size == 1) {
            return mutableListOf("modules", "snapshot").filter { it.startsWith(args[0]) }.toMutableList()
        }
        if (args.size == 2 && args[0].equals("modules", ignoreCase = true)) {
            return mutableListOf("list", "load", "unload", "reload", "compile", "diag")
                .filter { it.startsWith(args[1]) }
                .toMutableList()
        }
        if (args.size == 2 && args[0].equals("snapshot", ignoreCase = true)) {
            return mutableListOf("create", "rollback").filter { it.startsWith(args[1]) }.toMutableList()
        }
        return mutableListOf()
    }
}
