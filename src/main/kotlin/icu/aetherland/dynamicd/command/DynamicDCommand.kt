package icu.aetherland.dynamicd.command

import icu.aetherland.dynamicd.agent.AgentToolAction
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
            sender.sendMessage("/dd snapshot <list|create|rollback>")
            sender.sendMessage("/dd agent <search|read|patch> ...")
            return true
        }
        val operator = sender.name
        val permissions = collectPermissions(sender)
        return when (args[0].lowercase()) {
            "modules" -> handleModules(sender, operator, permissions, args.drop(1))
            "snapshot" -> handleSnapshot(sender, operator, permissions, args.drop(1))
            "agent" -> handleAgent(sender, operator, permissions, args.drop(1))
            else -> {
                sender.sendMessage("Unknown subcommand")
                true
            }
        }
    }

    private fun handleModules(
        sender: CommandSender,
        operator: String,
        permissions: Set<String>,
        args: List<String>,
    ): Boolean {
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
                sender.sendMessage("load $moduleId => ${moduleManager.loadModule(moduleId, operator, permissions)}")
            }
            "unload" -> withModuleArg(sender, args) { moduleId ->
                sender.sendMessage("unload $moduleId => ${moduleManager.unloadModule(moduleId, operator)}")
            }
            "reload" -> withModuleArg(sender, args) { moduleId ->
                sender.sendMessage("reload $moduleId => ${moduleManager.reloadModule(moduleId, operator, permissions)}")
            }
            "compile" -> withModuleArg(sender, args) { moduleId ->
                val result = moduleManager.compileModule(moduleId, operator, permissions)
                sender.sendMessage("compile $moduleId => ${result.success}")
                result.diagnostics.forEach {
                    sender.sendMessage("[${it.code}] ${it.message} (${it.file}:${it.line}:${it.column})")
                }
            }
            "diag" -> withModuleArg(sender, args) { moduleId ->
                val result = moduleManager.compileModule(moduleId, operator, permissions)
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

    private fun handleSnapshot(
        sender: CommandSender,
        operator: String,
        permissions: Set<String>,
        args: List<String>,
    ): Boolean {
        if (args.isEmpty()) {
            sender.sendMessage("Usage: /dd snapshot <list|create|rollback>")
            return true
        }
        return when (args[0].lowercase()) {
            "list" -> {
                val snapshots = moduleManager.listSnapshots()
                if (snapshots.isEmpty()) {
                    sender.sendMessage("No snapshots")
                } else {
                    sender.sendMessage("Snapshots: ${snapshots.joinToString(", ")}")
                }
                true
            }
            "create" -> {
                val id = moduleManager.createSnapshot(operator, permissions)
                if (id == null) {
                    sender.sendMessage("snapshot create denied")
                } else {
                    sender.sendMessage("snapshot created: $id")
                }
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
                sender.sendMessage("rollback $snapshotId => ${moduleManager.rollback(snapshotId, operator, permissions)}")
                true
            }
            else -> {
                sender.sendMessage("Unknown snapshot action")
                true
            }
        }
    }

    private fun handleAgent(
        sender: CommandSender,
        operator: String,
        permissions: Set<String>,
        args: List<String>,
    ): Boolean {
        if (args.isEmpty()) {
            sender.sendMessage("Usage: /dd agent <search|read|patch> ...")
            return true
        }
        return when (args[0].lowercase()) {
            "search" -> {
                if (args.size < 2) {
                    sender.sendMessage("Usage: /dd agent search <query>")
                    return true
                }
                val hits = moduleManager.searchModules(args[1], operator, permissions)
                sender.sendMessage("agent search => ${if (hits.isEmpty()) "<none>" else hits.joinToString(", ")}")
                true
            }
            "read" -> {
                if (args.size < 2) {
                    sender.sendMessage("Usage: /dd agent read <moduleId>")
                    return true
                }
                val files = moduleManager.readModule(args[1], operator, permissions)
                sender.sendMessage("agent read => ${if (files.isEmpty()) "<none>" else files.joinToString(", ")}")
                true
            }
            "patch" -> {
                if (args.size < 2) {
                    sender.sendMessage("Usage: /dd agent patch <moduleId> [--ast|--token|--text]")
                    return true
                }
                val moduleId = args[1]
                val ast = args.contains("--ast") || (!args.contains("--token") && !args.contains("--text"))
                val token = args.contains("--token")
                val success = moduleManager.applyAgentPatch(
                    moduleId,
                    operator,
                    permissions,
                    astAvailable = ast,
                    tokenAvailable = token,
                )
                sender.sendMessage("agent patch strategy recorded => $success")
                true
            }
            else -> {
                sender.sendMessage("Usage: /dd agent <search|read|patch> ...")
                true
            }
        }
    }

    private fun collectPermissions(sender: CommandSender): Set<String> {
        val all = mutableSetOf<String>()
        AgentToolAction.entries.forEach { action ->
            val node = when (action) {
                AgentToolAction.READ,
                AgentToolAction.SEARCH,
                -> "dynamicd.agent.use"
                AgentToolAction.PATCH -> "dynamicd.agent.patch"
                AgentToolAction.COMPILE -> "dynamicd.agent.compile"
                AgentToolAction.LOAD -> "dynamicd.agent.load"
                AgentToolAction.ROLLBACK -> "dynamicd.agent.rollback"
            }
            if (sender.hasPermission(node)) {
                all.add(node)
            }
        }
        if (sender.hasPermission("dynamicd.ops")) {
            all.addAll(
                setOf(
                    "dynamicd.agent.use",
                    "dynamicd.agent.patch",
                    "dynamicd.agent.compile",
                    "dynamicd.agent.load",
                    "dynamicd.agent.rollback",
                ),
            )
        }
        return all
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
            return mutableListOf("modules", "snapshot", "agent")
                .filter { it.startsWith(args[0], ignoreCase = true) }
                .toMutableList()
        }
        if (args.size == 2 && args[0].equals("modules", ignoreCase = true)) {
            return mutableListOf("list", "load", "unload", "reload", "compile", "diag")
                .filter { it.startsWith(args[1], ignoreCase = true) }
                .toMutableList()
        }
        if (args.size == 2 && args[0].equals("snapshot", ignoreCase = true)) {
            return mutableListOf("list", "create", "rollback")
                .filter { it.startsWith(args[1], ignoreCase = true) }
                .toMutableList()
        }
        if (args.size == 2 && args[0].equals("agent", ignoreCase = true)) {
            return mutableListOf("search", "read", "patch")
                .filter { it.startsWith(args[1], ignoreCase = true) }
                .toMutableList()
        }
        return mutableListOf()
    }
}
